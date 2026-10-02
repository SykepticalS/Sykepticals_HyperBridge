package com.sykeptical.hyperpop.xposed.hooks

import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import com.sykeptical.hyperpop.island.backend.IslandProtocol
import com.sykeptical.hyperpop.models.IslandGenerationGuard
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Island-only dismissal adapted from HyperIsland's ActiveIslandDismissHook (MIT).
 * Never cancels the original source notification.
 */
object ActiveIslandDismissHook {
    private const val CONTROLLER = "miui.systemui.notification.focus.FocusNotificationController"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loaders = ConcurrentHashMap.newKeySet<Int>()
    private val pending = ConcurrentHashMap<String, PendingDismissal>()
    @Volatile private var controller = WeakReference<Any>(null)
    @Volatile private var active = false

    private class PendingDismissal {
        @Volatile var cancelled = false
        var expected: StatusBarNotification? = null
        var expectedGeneration: Long = Long.MIN_VALUE
        var completion: ((Boolean) -> Unit)? = null
        lateinit var runnable: Runnable
    }

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { hook(module, it) }
    }

    fun isActive(): Boolean = active

    fun invalidate(notificationKey: String) {
        if (notificationKey.isBlank()) return
        pending.remove(notificationKey)?.let { request ->
            request.cancelled = true
            mainHandler.removeCallbacks(request.runnable)
        }
    }

    fun dismiss(expected: StatusBarNotification, expectedGeneration: Long): Boolean {
        val key = expected.key
        if (key.isNullOrBlank()) return false
        return enqueueDismissal(
            notificationKey = key,
            expected = expected,
            expectedGeneration = expectedGeneration,
        )
    }

    fun dismissKey(
        notificationKey: String,
        completion: ((Boolean) -> Unit)? = null,
    ): Boolean {
        return enqueueDismissal(
            notificationKey = notificationKey,
            expected = null,
            expectedGeneration = Long.MAX_VALUE,
            completion = completion,
        )
    }

    private fun enqueueDismissal(
        notificationKey: String,
        expected: StatusBarNotification?,
        expectedGeneration: Long,
        completion: ((Boolean) -> Unit)? = null,
    ): Boolean {
        if (notificationKey.isBlank()) return false
        val request = PendingDismissal()
        request.expected = expected
        request.expectedGeneration = expectedGeneration
        request.completion = completion
        request.runnable = Runnable {
            if (request.cancelled || pending[notificationKey] !== request) return@Runnable
            pending.remove(notificationKey, request)
            val target = controller.get()
            if (target == null) {
                request.completion?.invoke(false)
                return@Runnable
            }
            val current = resolveSbn(target, notificationKey)
            if (expected != null) {
                if (current == null) {
                    request.completion?.invoke(false)
                    return@Runnable
                }
                val generation = current.notification.extras.getLong(IslandProtocol.EXTRA_GENERATION, Long.MIN_VALUE)
                if (!IslandGenerationGuard.isCurrent(expectedGeneration, generation, isOwned(current))) {
                    request.completion?.invoke(false)
                    return@Runnable
                }
                if (current.postTime != expected.postTime) {
                    request.completion?.invoke(false)
                    return@Runnable
                }
            }
            val updateNoFloat = (current ?: expected)?.notification?.extras
                ?.getBoolean("miui.island.updateNoFloat", false) == true
            val removed = runCatching {
                val islandOnly = target.javaClass.declaredMethods.firstOrNull {
                    it.name == "removeIslandDataByKey" && it.parameterCount == 2 &&
                        it.parameterTypes[0] == String::class.java &&
                        it.parameterTypes[1] == Boolean::class.javaPrimitiveType
                } ?: return@runCatching false
                islandOnly.isAccessible = true
                islandOnly.invoke(target, notificationKey, updateNoFloat)
                true
            }.getOrDefault(false)
            request.completion?.invoke(removed)
        }
        pending.put(notificationKey, request)?.let { previous ->
            previous.cancelled = true
            mainHandler.removeCallbacks(previous.runnable)
            previous.completion?.invoke(false)
        }
        mainHandler.post(request.runnable)
        return true
    }

    private fun hook(module: XposedModule, loader: ClassLoader) {
        val id = System.identityHashCode(loader)
        if (!loaders.add(id)) return
        runCatching {
            val clazz = loader.loadClass(CONTROLLER)
            clazz.declaredConstructors.forEach { constructor ->
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    controller = WeakReference(chain.thisObject)
                    result
                }
            }
            clazz.declaredMethods.filter { it.name == "onNotificationPosted" && it.parameterCount >= 1 }.forEach { method ->
                module.hook(method).intercept { chain ->
                    controller = WeakReference(chain.thisObject)
                    val sbn = chain.args.getOrNull(0) as? StatusBarNotification
                    if (sbn != null) {
                        invalidate(sbn.key)
                        clearTimeoutRemovedKey(chain.thisObject, sbn.key)
                    }
                    chain.proceed()
                }
            }
            active = clazz.declaredMethods.any { it.name == "removeIslandDataByKey" && it.parameterCount == 2 }
        }.onFailure {
            loaders.remove(id)
            module.log("HyperPop: island-only dismissal unavailable: ${it.message}")
        }
    }

    private fun clearTimeoutRemovedKey(target: Any, key: String) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            val field = runCatching { clazz.getDeclaredField("islandTimeoutRemovedList") }.getOrNull()
            if (field != null) {
                runCatching {
                    field.isAccessible = true
                    (field.get(target) as? MutableCollection<*>)?.remove(key)
                }
                return
            }
            clazz = clazz.superclass
        }
    }

    private fun resolveSbn(target: Any, key: String): StatusBarNotification? {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            runCatching {
                clazz.getDeclaredField("sbnMap").apply { isAccessible = true }
                    .get(target).let { it as? Map<*, *> }
                    ?.get(key) as? StatusBarNotification
            }.getOrNull()?.let { return it }
            clazz = clazz.superclass
        }
        return null
    }

    private fun isOwned(sbn: StatusBarNotification): Boolean =
        sbn.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
}
