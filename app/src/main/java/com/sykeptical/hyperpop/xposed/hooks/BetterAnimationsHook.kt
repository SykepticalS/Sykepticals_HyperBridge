package com.sykeptical.hyperpop.xposed.hooks

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import com.sykeptical.hyperpop.service.animation.AppExitTargetBounds
import com.sykeptical.hyperpop.service.animation.BetterAnimationsPolicy
import com.sykeptical.hyperpop.service.animation.CenteredExitSession
import com.sykeptical.hyperpop.xposed.HookConfig
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

/**
 * Retargets eligible primary app exits toward the camera cutout, then uses Xiaomi's native
 * cutout-to-big reveal. Xiaomi still owns island priority and slot movement: during a priority
 * handoff the previous lower-priority big island is moved to the right-hand small slot natively.
 */
object BetterAnimationsHook {
    private const val WINDOW_CONTROLLER =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"
    private const val REQUEST_CLOSE_POSITION = "request_close_position"
    private const val CLOSE_APP_START = "close_app_start"
    private const val CLOSE_APP_END = "close_app_end"
    private const val APP_TO_RECENT = "app_to_recent"
    private const val POSITION = "position"
    private const val PACKAGE_NAME = "packageName"

    private val pluginLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val centeredExit = CenteredExitSession()
    @Volatile private var centeredContent = WeakReference<Any>(null)
    private val activeStateGetters = listOf(
        "getCurrentBigIslandState",
        "getCurrentSmallIslandState",
        "getCurrentExpandedState",
        "getCurrentTempShowBigIslandState",
    )

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookPlugin(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hookPlugin(module, loader)
        }
    }

    private fun hookPlugin(module: XposedModule, loader: ClassLoader) {
        if (!pluginLoaders.add(loader)) return
        runCatching {
            val controller = loader.loadClass(WINDOW_CONTROLLER)
            val sendEvent = controller.declaredMethods.singleOrNull {
                it.name == "sendWindowAnimEvent" &&
                    it.parameterTypes.contentEquals(
                        arrayOf(
                            String::class.java,
                            Boolean::class.javaPrimitiveType,
                            Boolean::class.javaPrimitiveType,
                            Bundle::class.java,
                        ),
                    )
            } ?: error("sendWindowAnimEvent(String, boolean, boolean, Bundle) not found")

            module.hook(sendEvent).intercept { chain ->
                val event = chain.args.getOrNull(0) as? String
                val freeform = chain.args.getOrNull(1) as? Boolean ?: false
                val interrupted = chain.args.getOrNull(2) as? Boolean ?: false
                val request = chain.args.getOrNull(3) as? Bundle
                val result = chain.proceed()

                val packageName = request?.getString(PACKAGE_NAME)
                if (!HookConfig.betterAnimationsEnabled()) {
                    clearCenteredExit()
                    return@intercept result
                }

                when (event) {
                    REQUEST_CLOSE_POSITION -> prepareCenteredExit(
                        module = module,
                        controller = chain.thisObject,
                        packageName = packageName,
                        freeform = freeform,
                        interrupted = interrupted,
                        originalResult = result,
                    )

                    CLOSE_APP_START -> {
                        if (interrupted) {
                            clearCenteredExit()
                        } else {
                            val generation = centeredExit.markStarted(packageName)
                            if (generation != null) {
                                val content = centeredContent.get()
                                if (content != null) {
                                    val controller = chain.thisObject
                                    val moduleRef = module
                                    val pkg = packageName
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        animateCenteredReveal(moduleRef, controller, content, pkg, generation)
                                    }, 350)
                                }
                            }
                        }
                        result
                    }

                    CLOSE_APP_END -> {
                        if (interrupted) {
                            clearCenteredExit()
                            return@intercept result
                        }
                        centeredExit.complete(packageName)
                        centeredContent = WeakReference(null)
                        result
                    }

                    APP_TO_RECENT -> {
                        if (centeredExit.abort(packageName)) centeredContent = WeakReference(null)
                        result
                    }

                    else -> result
                }
            }
            module.log("HyperPop: better-animations hook installed loader=${loader.hashCode()}")
        }.onFailure {
            pluginLoaders.remove(loader)
            module.log(
                "HyperPop: better-animations hook unavailable " +
                    "loader=${loader.hashCode()}: ${it.message}",
            )
        }
    }

    private fun prepareCenteredExit(
        module: XposedModule,
        controller: Any,
        packageName: String?,
        freeform: Boolean,
        interrupted: Boolean,
        originalResult: Any?,
    ): Any? {
        clearCenteredExit()
        if (originalResult !is Bundle || packageName.isNullOrBlank()) return originalResult
        val view = invokeNoArg(controller, "getView") ?: return originalResult
        val closingViews = requestHasIsland(view, packageName).filter(::isAppExpanded)
        val active = inspectActiveIslands(view, closingViews) ?: return originalResult
        val cutout = (invokeNoArg(view, "getCutoutRect") as? Rect)
            ?.takeIf { it.width() > 0 && it.height() > 0 }
            ?: return originalResult
        val nativeTarget = originalResult.getParcelable(POSITION, Rect::class.java)
        val nativeTargetSlot = BetterAnimationsPolicy.classifyNativeTarget(
            native = nativeTarget?.toExitTargetBounds(),
            cutout = cutout.toExitTargetBounds(),
        )
        if (!BetterAnimationsPolicy.shouldUseCenteredExit(
                enabled = true,
                freeform = freeform,
                interrupted = interrupted,
                hasClosingIsland = closingViews.isNotEmpty(),
                activeIslandCount = active.count,
                hasCurrentBigIsland = active.hasCurrentBigIsland,
                nativeTargetSlot = nativeTargetSlot,
            )
        ) {
            return originalResult
        }

        val closingContent = closingViews.last()
        val generation = centeredExit.arm(packageName)
        centeredContent = WeakReference(closingContent)
        return Bundle(originalResult).apply {
            putParcelable(POSITION, Rect(cutout))
        }.also {
            val mode = if (active.count == 0) "first" else "priority_handoff"
            module.log(
                "HyperPop: better-animations centered app exit " +
                    "mode=$mode pkg=$packageName gen=$generation " +
                    "nativeTarget=$nativeTargetSlot rect=${cutout.width()}x${cutout.height()}",
            )
        }
    }

    private fun animateCenteredReveal(
        module: XposedModule,
        controller: Any,
        content: Any,
        packageName: String?,
        generation: Long,
    ) {
        val view = invokeNoArg(controller, "getView") ?: return
        if (invokeNoArg(view, "getCurrentBigIslandState") !== content || !hasState(content, "BigIsland")) {
            module.log(
                "HyperPop: better-animations reveal skipped; closing island no longer owns big slot " +
                    "pkg=$packageName gen=$generation",
            )
            return
        }
        val coordinator = invokeNoArg(content, "getDynamicIslandEventCoordinator") ?: return
        val animationController = invokeNoArg(coordinator, "getAnimationController") ?: return
        val animation = findCompatibleMethod(
            animationController.javaClass,
            "hiddenToBigIslandAnimation",
            content.javaClass,
        ) ?: return
        runCatching {
            animation.apply { isAccessible = true }.invoke(animationController, content)
        }.onSuccess {
            module.log(
                "HyperPop: better-animations native cutout-to-big reveal started " +
                    "pkg=$packageName gen=$generation",
            )
        }.onFailure {
            module.log(
                "HyperPop: better-animations reveal unavailable " +
                    "pkg=$packageName gen=$generation: ${it.message}",
            )
        }
    }

    private fun clearCenteredExit() {
        centeredExit.abort()
        centeredContent = WeakReference(null)
    }

    private fun requestHasIsland(view: Any?, packageName: String): List<Any> {
        view ?: return emptyList()
        val method = findMethod(view.javaClass, "requestHasIsland", arrayOf(String::class.java))
            ?: return emptyList()
        return runCatching {
            (method.apply { isAccessible = true }.invoke(view, packageName) as? List<*>)
                .orEmpty()
                .filterNotNull()
        }.getOrDefault(emptyList())
    }

    private fun isAppExpanded(contentView: Any): Boolean {
        return hasState(contentView, "AppExpanded")
    }

    private fun hasState(contentView: Any, name: String): Boolean =
        invokeNoArg(contentView, "getState")?.javaClass?.simpleName == name

    private data class ActiveIslandSnapshot(
        val count: Int,
        val hasCurrentBigIsland: Boolean,
    )

    /** Returns null when this ROM does not expose the complete state surface; native wins then. */
    private fun inspectActiveIslands(view: Any?, closingViews: List<Any>): ActiveIslandSnapshot? {
        view ?: return null
        val methods = activeStateGetters.associateWith { name ->
            findMethod(view.javaClass, name, emptyArray()) ?: return null
        }
        val closing = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()).apply {
            addAll(closingViews)
        }
        val states = LinkedHashMap<String, Any?>()
        val active = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        methods.forEach { (name, method) ->
            val state = runCatching {
                method.apply { isAccessible = true }.invoke(view)
            }.getOrElse { return null }
            states[name] = state
            if (state != null && state !in closing) active += state
        }

        val currentBig = states["getCurrentBigIslandState"]
        return ActiveIslandSnapshot(
            count = active.size,
            hasCurrentBigIsland = currentBig != null && currentBig !in closing,
        )
    }

    private fun Rect.toExitTargetBounds(): AppExitTargetBounds = AppExitTargetBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )

    private fun invokeNoArg(target: Any?, name: String): Any? {
        target ?: return null
        val method = findMethod(target.javaClass, name, emptyArray()) ?: return null
        return runCatching { method.apply { isAccessible = true }.invoke(target) }.getOrNull()
    }

    private fun findMethod(type: Class<*>, name: String, parameterTypes: Array<Class<*>>): Method? {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(parameterTypes)
            }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun findCompatibleMethod(type: Class<*>, name: String, argumentType: Class<*>): Method? {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull {
                it.name == name && it.parameterCount == 1 &&
                    it.parameterTypes[0].isAssignableFrom(argumentType)
            }?.let { return it }
            current = current.superclass
        }
        return null
    }
}
