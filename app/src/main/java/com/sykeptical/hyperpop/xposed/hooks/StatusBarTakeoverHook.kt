package com.sykeptical.hyperpop.xposed.hooks

import android.view.View
import com.sykeptical.hyperpop.service.animation.expanded.IslandRect
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference

/**
 * Fades the status-bar clock, system icons, and privacy chip while an expanded
 * island covers them. Xiaomi's stretch animator writes translationX on these
 * containers, so the fade uses transitionAlpha, which that animator does not.
 * Only the phone instance (`from == 0`) is captured.
 */
object StatusBarTakeoverHook {
    private const val STRETCH =
        "com.android.systemui.statusbar.phone.IslandStretchAnimation"

    private val left = Ref()
    private val right = Ref()
    private val privacy = Ref()
    @Volatile private var loggedFailure = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        runCatching {
            val stretch = param.defaultClassLoader.loadClass(STRETCH)
            val init = stretch.declaredMethods.firstOrNull {
                it.name == "initMiuiViewsOnViewCreated" && it.parameterTypes.size >= 4
            } ?: error("initMiuiViewsOnViewCreated missing")
            init.isAccessible = true
            runCatching { module.deoptimize(init) }
            module.hook(init).intercept { chain ->
                val result = chain.proceed()
                capture(chain.thisObject, chain.args)
                result
            }
            module.log("HyperPop: status-bar takeover hook installed")
        }.onFailure {
            if (!loggedFailure) {
                loggedFailure = true
                module.log("HyperPop: status-bar takeover hook unavailable: ${it.message}")
            }
        }
    }

    fun apply(alpha: Float) {
        containers().forEach { view ->
            runCatching { view.transitionAlpha = alpha }
        }
    }

    fun restore() = apply(1f)

    fun groupBounds(): IslandRect? {
        val rects = containers().mapNotNull { view ->
            if (view.width <= 0 || view.height <= 0) return@mapNotNull null
            val location = IntArray(2)
            runCatching { view.getLocationInWindow(location) }.getOrNull() ?: return@mapNotNull null
            IslandRect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        if (rects.isEmpty()) return null
        return IslandRect(
            left = rects.minOf { it.left },
            top = rects.minOf { it.top },
            right = rects.maxOf { it.right },
            bottom = rects.maxOf { it.bottom },
        )
    }

    private fun capture(target: Any, args: List<Any?>) {
        val from = runCatching {
            val field = target.javaClass.getDeclaredField("from")
            field.isAccessible = true
            field.getInt(target)
        }.getOrDefault(-1)
        if (from != 0) return
        (args.getOrNull(1) as? View)?.let { left.hold(it) }
        (args.getOrNull(2) as? View)?.let { right.hold(it) }
        (args.getOrNull(3) as? View)?.let { privacy.hold(it) }
    }

    private fun containers(): List<View> = listOfNotNull(left.get(), right.get(), privacy.get())

    private class Ref {
        private var view = WeakReference<View>(null)
        fun hold(next: View) {
            view = WeakReference(next)
        }
        fun get(): View? = view.get()
    }
}
