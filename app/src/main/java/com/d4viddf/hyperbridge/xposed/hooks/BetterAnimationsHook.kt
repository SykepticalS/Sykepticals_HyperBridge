package com.d4viddf.hyperbridge.xposed.hooks

import android.graphics.Rect
import android.os.Bundle
import com.d4viddf.hyperbridge.service.animation.BetterAnimationsPolicy
import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

/**
 * Sends only the first app island toward the camera cutout. Xiaomi remains responsible for the
 * app-exit state transition and the close-end reveal; existing-island exits stay fully native.
 */
object BetterAnimationsHook {
    private const val WINDOW_CONTROLLER =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"
    private const val REQUEST_CLOSE_POSITION = "request_close_position"
    private const val POSITION = "position"
    private const val PACKAGE_NAME = "packageName"

    private val pluginLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
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

                if (event != REQUEST_CLOSE_POSITION || result !is Bundle) {
                    return@intercept result
                }
                val packageName = request?.getString(PACKAGE_NAME)
                val view = invokeNoArg(chain.thisObject, "getView")
                val closingViews = packageName
                    ?.let { requestHasIsland(view, it) }
                    .orEmpty()
                    .filter(::isAppExpanded)
                val activeCount = countActiveIslands(view, closingViews)
                    ?: return@intercept result
                if (!BetterAnimationsPolicy.shouldUseCenteredExit(
                        enabled = HookConfig.betterAnimationsEnabled(),
                        freeform = freeform,
                        interrupted = interrupted,
                        hasClosingIsland = closingViews.isNotEmpty(),
                        activeIslandCount = activeCount,
                    )
                ) {
                    return@intercept result
                }

                val cutout = (invokeNoArg(view, "getCutoutRect") as? Rect)
                    ?.takeIf { it.width() > 0 && it.height() > 0 }
                    ?: return@intercept result
                Bundle(result).apply {
                    putParcelable(POSITION, Rect(cutout))
                }.also {
                    module.log(
                        "HyperBridge: better-animations centered first app exit " +
                            "pkg=$packageName rect=${cutout.width()}x${cutout.height()}",
                    )
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
        val state = invokeNoArg(contentView, "getState") ?: return false
        return state.javaClass.simpleName == "AppExpanded"
    }

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
}
