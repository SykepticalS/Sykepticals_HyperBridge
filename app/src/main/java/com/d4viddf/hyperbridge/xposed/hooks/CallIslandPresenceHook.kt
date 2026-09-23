package com.d4viddf.hyperbridge.xposed.hooks

import android.content.ComponentName
import android.view.View
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.xposed.log
import com.d4viddf.hyperbridge.xposed.mediacard.compat.IslandProbeUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/**
 * Hides a SystemUI-owned call island while the person is inside the calling app or the
 * system in-call screen. The notification stays posted. Source-owned focus islands are left
 * to HyperOS, which already hides them and plays the return-to-island animation.
 */
object CallIslandPresenceHook {
    private const val CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private val inCallPackages = setOf(
        "com.android.incallui",
        "com.android.dialer",
        "com.google.android.dialer",
    )
    private val hosts = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val forcedHidden = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    @Volatile private var foregroundPackage: String? = null

    fun install(module: XposedModule, param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        val type = runCatching { loader.loadClass(CONTENT_VIEW) }.getOrElse {
            module.log("HyperBridge: call island presence unavailable: ${it.message}")
            return
        }
        val updates = type.methods.filter { method ->
            method.name == "updateBigIslandView" || method.name == "updateSmallIslandView"
        }
        if (updates.isEmpty()) {
            module.log("HyperBridge: call island presence found no island update method")
            return
        }
        updates.forEach { method ->
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as? View
                if (view != null) {
                    hosts[view] = true
                    apply(view)
                }
                result
            }
        }
        watchForeground(module, loader)
        module.log("HyperBridge: call island presence hooked ${updates.size} update methods")
    }

    private fun watchForeground(module: XposedModule, loader: ClassLoader) {
        seedForeground()
        val names = listOf(
            "com.android.systemui.shared.system.TaskStackChangeListeners",
            "com.android.systemui.shared.system.TaskStackChangeListeners\$Impl",
        )
        var hooked = false
        names.forEach { name ->
            val type = runCatching { loader.loadClass(name) }.getOrNull() ?: return@forEach
            type.methods.filter { it.name == "onTaskMovedToFront" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    chain.args.firstNotNullOfOrNull { packageFrom(it) }?.let { foregroundPackage = it }
                    visibleHosts().forEach(::apply)
                    result
                }
                hooked = true
            }
        }
        if (!hooked) {
            module.log("HyperBridge: call island foreground watch failed open")
        }
    }

    private fun seedForeground() {
        runCatching {
            val service = Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService")
                .invoke(null) ?: return
            foregroundPackage = packageFrom(invokeNoArg(service, "getFocusedRootTaskInfo"))
        }
    }

    private fun visibleHosts(): List<View> = synchronized(hosts) { hosts.keys.toList() }

    private fun apply(view: View) {
        val hide = shouldHide(view)
        val hiddenByUs = forcedHidden[view] == true
        if (hide) {
            if (view.visibility != View.INVISIBLE) view.visibility = View.INVISIBLE
            forcedHidden[view] = true
        } else if (hiddenByUs) {
            view.visibility = View.VISIBLE
            forcedHidden[view] = false
        }
    }

    private fun shouldHide(view: View): Boolean {
        val data = IslandProbeUtils.getCurrentIslandData(view) ?: return false
        val snapshot = IslandOwnedNotification.fromIslandData(data) ?: return false
        if (!snapshot.owned) return false
        if (snapshot.extras.getBoolean(IslandProtocol.EXTRA_CALL_FOCUS, false)) return false
        val type = snapshot.extras.getString(IslandProtocol.EXTRA_SEMANTIC_TYPE)
            ?: snapshot.sbn?.notification?.extras?.getString(IslandProtocol.EXTRA_SEMANTIC_TYPE)
        if (type != "CALL") return false
        val source = snapshot.extras.getString(IslandProtocol.EXTRA_SOURCE_PACKAGE)
            ?: snapshot.sbn?.notification?.extras?.getString(IslandProtocol.EXTRA_SOURCE_PACKAGE)
            ?: return false
        val foreground = foregroundPackage ?: return false
        return foreground == source || foreground in inCallPackages
    }

    private fun packageFrom(value: Any?): String? {
        if (value == null) return null
        if (value is ComponentName) return value.packageName
        return listOf("topActivity", "realActivity", "baseActivity").firstNotNullOfOrNull { name ->
            componentField(value, name)?.packageName
        }
    }

    private fun componentField(value: Any, name: String): ComponentName? {
        val field = runCatching { value.javaClass.getField(name) }.getOrNull()
            ?: runCatching {
                value.javaClass.getDeclaredField(name).apply { isAccessible = true }
            }.getOrNull()
            ?: return null
        return field.get(value) as? ComponentName
    }

    private fun invokeNoArg(target: Any, name: String): Any? = runCatching {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: return null
        method.invoke(target)
    }.getOrNull()
}
