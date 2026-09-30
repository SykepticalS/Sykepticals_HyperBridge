package com.d4viddf.hyperbridge.xposed.hooks

import android.graphics.Rect
import android.os.Bundle
import com.d4viddf.hyperbridge.service.animation.BetterAnimationsPolicy
import com.d4viddf.hyperbridge.service.animation.CenteredExitSession
import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

/**
 * Sends only the first app island toward the camera cutout, then uses Xiaomi's native cutout-to-big
 * animation at the matching close-end handoff. Existing-island exits stay fully native.
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
                        if (interrupted) clearCenteredExit() else centeredExit.markStarted(packageName)
                        result
                    }

                    CLOSE_APP_END -> {
                        if (interrupted) {
                            clearCenteredExit()
                            return@intercept result
                        }
                        val generation = centeredExit.complete(packageName)
                        val content = centeredContent.get()
                        centeredContent = WeakReference(null)
                        if (generation != null && content != null) {
                            animateCenteredReveal(module, chain.thisObject, content, packageName, generation)
                        }
                        result
                    }

                    APP_TO_RECENT -> {
                        if (centeredExit.abort(packageName)) centeredContent = WeakReference(null)
                        result
                    }

                    else -> result
                }
            }
            module.log("HyperBridge: better-animations hook installed loader=${loader.hashCode()}")
        }.onFailure {
            pluginLoaders.remove(loader)
            module.log(
                "HyperBridge: better-animations hook unavailable " +
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
        val activeCount = countActiveIslands(view, closingViews) ?: return originalResult
        if (!BetterAnimationsPolicy.shouldUseCenteredExit(
                enabled = true,
                freeform = freeform,
                interrupted = interrupted,
                hasClosingIsland = closingViews.isNotEmpty(),
                activeIslandCount = activeCount,
            )
        ) {
            return originalResult
        }

        val cutout = (invokeNoArg(view, "getCutoutRect") as? Rect)
            ?.takeIf { it.width() > 0 && it.height() > 0 }
            ?: return originalResult
        val closingContent = closingViews.last()
        val generation = centeredExit.arm(packageName)
        centeredContent = WeakReference(closingContent)
        return Bundle(originalResult).apply {
            putParcelable(POSITION, Rect(cutout))
        }.also {
            module.log(
                "HyperBridge: better-animations centered first app exit " +
                    "pkg=$packageName gen=$generation rect=${cutout.width()}x${cutout.height()}",
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
                "HyperBridge: better-animations reveal skipped; first island no longer owns big slot " +
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
                "HyperBridge: better-animations native cutout-to-big reveal started " +
                    "pkg=$packageName gen=$generation",
            )
        }.onFailure {
            module.log(
                "HyperBridge: better-animations reveal unavailable " +
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

    /** Returns null when this ROM does not expose the complete state surface; native wins then. */
    private fun countActiveIslands(view: Any?, closingViews: List<Any>): Int? {
        view ?: return null
        val methods = activeStateGetters.associateWith { name ->
            findMethod(view.javaClass, name, emptyArray()) ?: return null
        }
        val closing = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()).apply {
            addAll(closingViews)
        }
        val active = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        methods.values.forEach { method ->
            val state = runCatching {
                method.apply { isAccessible = true }.invoke(view)
            }.getOrElse { return null }
            if (state != null && state !in closing) active += state
        }
        return active.size
    }

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
