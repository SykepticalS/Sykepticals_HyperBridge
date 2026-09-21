package com.d4viddf.hyperbridge.xposed.hooks

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.d4viddf.hyperbridge.models.IslandWindowImePolicy
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap

/**
 * Stops the Dynamic Island window from becoming the IME target while the user is
 * typing in another app. Reply composer temporarily opts out via [IslandReplyComposer].
 */
object IslandWindowImeHook {
    private const val WINDOW_VIEW = "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private val windowLoaders = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var windowManagerHooked = false
    @Volatile private var active = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookWindowManager(module)
        hookWindowView(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { hookWindowView(module, it) }
    }

    fun isActive(): Boolean = active

    fun sanitize(view: View?) {
        val root = view?.rootView ?: return
        if (!isIslandRoot(root)) return
        applyToParams(root, root.layoutParams as? WindowManager.LayoutParams ?: return, commit = true)
    }

    private fun hookWindowManager(module: XposedModule) {
        if (windowManagerHooked) return
        windowManagerHooked = true
        runCatching {
            val clazz = Class.forName("android.view.WindowManagerImpl")
            val methods = clazz.declaredMethods.filter { method ->
                (method.name == "addView" || method.name == "updateViewLayout") &&
                    method.parameterTypes.size >= 2 &&
                    View::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    ViewGroup.LayoutParams::class.java.isAssignableFrom(method.parameterTypes[1])
            }
            check(methods.isNotEmpty()) { "WindowManagerImpl view layout methods missing" }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val view = chain.args.getOrNull(0) as? View
                    val params = chain.args.getOrNull(1) as? WindowManager.LayoutParams
                    if (view != null && params != null && isIslandRoot(view)) {
                        applyToParams(view, params, commit = false)
                    }
                    chain.proceed()
                }
            }
            active = true
            module.log("HyperBridge: island IME passthrough hooked wm methods=${methods.size}")
        }.onFailure {
            windowManagerHooked = false
            module.log("HyperBridge: island IME passthrough wm hook unavailable: ${it.message}")
        }
    }

    private fun hookWindowView(module: XposedModule, loader: ClassLoader) {
        val id = System.identityHashCode(loader)
        if (!windowLoaders.add(id)) return
        runCatching {
            val clazz = loader.loadClass(WINDOW_VIEW)
            val attached = clazz.declaredMethods.firstOrNull {
                it.name == "onAttachedToWindow" && it.parameterCount == 0
            } ?: return@runCatching
            module.hook(attached).intercept { chain ->
                val result = chain.proceed()
                sanitize(chain.thisObject as? View)
                result
            }
            active = true
            module.log("HyperBridge: island IME passthrough hooked ${clazz.simpleName}.onAttachedToWindow")
        }.onFailure {
            windowLoaders.remove(id)
        }
    }

    private fun applyToParams(view: View, params: WindowManager.LayoutParams, commit: Boolean) {
        val composerOpen = IslandReplyComposer.isOpen()
        val nextFlags = IslandWindowImePolicy.apply(params.flags, composerOpen)
        val nextSoftInput = if (composerOpen) {
            IslandWindowImePolicy.composerSoftInputMode(params.softInputMode)
        } else {
            params.softInputMode
        }
        val changed = nextFlags != params.flags || nextSoftInput != params.softInputMode
        if (!changed) return
        params.flags = nextFlags
        params.softInputMode = nextSoftInput
        if (!commit) return
        runCatching {
            view.context.getSystemService(WindowManager::class.java)?.updateViewLayout(view.rootView, params)
        }
    }

    private fun isIslandRoot(view: View): Boolean {
        var current: Class<*>? = view.javaClass
        while (current != null) {
            val name = current.name
            if (name.contains("DynamicIsland", ignoreCase = true)) return true
            current = current.superclass
        }
        return false
    }
}
