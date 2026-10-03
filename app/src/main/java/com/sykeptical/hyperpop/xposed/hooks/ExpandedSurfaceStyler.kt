package com.sykeptical.hyperpop.xposed.hooks

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedSurfaceStyle
import java.lang.reflect.Modifier

/**
 * Recolors Xiaomi's expanded-island drawable and removes the blur material
 * that would otherwise cover it. The original drawable is put back on restore.
 * Child content is not repainted.
 */
object ExpandedSurfaceStyler {
    private const val BLUR = "miui.systemui.util.MiBlurCompat"
    private var active: Active? = null

    private class Active(
        val ownerId: Int,
        val original: Drawable?,
        var copy: GradientDrawable?,
        val nativeFill: Int?,
        val strokePadPx: Float,
        val black: Boolean,
        var progress: Float = 0f,
        var radiusPx: Float = 0f,
        var blurStripped: Boolean = false,
    )

    fun arm(view: View, black: Boolean) {
        if (active?.ownerId == System.identityHashCode(view)) return
        restore(view)
        val background = backgroundView(view) ?: return
        val original = invoke(background, "getDrawable") as? Drawable
        val copy = (original as? GradientDrawable)?.constantState?.newDrawable()?.mutate() as? GradientDrawable
        if (copy != null) invoke(background, "setDrawable", copy)
        active = Active(
            ownerId = System.identityHashCode(view),
            original = original,
            copy = copy,
            nativeFill = copy?.color?.defaultColor,
            strokePadPx = strokePad(view),
            black = black,
        )
    }

    fun frame(view: View, progress: Float, radiusPx: Float) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view)) return
        current.progress = progress
        current.radiusPx = radiusPx
        if (current.black) {
            val start = current.nativeFill
            if (start != null) {
                current.copy?.setColor(ExpandedSurfaceStyle.fill(start, black = true, progress))
            } else if (progress >= 1f) {
                current.copy?.setColor(ExpandedSurfaceStyle.BLACK)
            }
            if (progress > 0.08f && !current.blurStripped) stripBlur(view, current)
            if (progress <= 0.02f && current.blurStripped) restoreBlur(view, current, teardown = false)
        }
        current.copy?.cornerRadius = radiusPx + current.strokePadPx
        backgroundView(view)?.invalidate()
    }

    fun onDrawableReplaced(view: View) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view)) return
        val background = backgroundView(view) ?: return
        val drawable = invoke(background, "getDrawable") as? Drawable ?: return
        if (drawable === current.copy) return
        val copy = (drawable as? GradientDrawable)?.constantState?.newDrawable()?.mutate() as? GradientDrawable
            ?: return
        invoke(background, "setDrawable", copy)
        current.copy = copy
        if (current.black && (current.nativeFill != null || current.progress >= 1f)) {
            copy.setColor(
                ExpandedSurfaceStyle.fill(
                    current.nativeFill ?: ExpandedSurfaceStyle.BLACK,
                    black = true,
                    current.progress,
                ),
            )
        }
        copy.cornerRadius = current.radiusPx + current.strokePadPx
    }

    fun onBlurReapplied(view: View) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view) || !current.blurStripped) return
        stripBlur(view, current)
    }

    fun restore(view: View) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view)) return
        backgroundView(view)?.let { invoke(it, "setDrawable", current.original) }
        if (current.blurStripped) restoreBlur(view, current, teardown = true)
        active = null
    }

    fun owns(view: View): Boolean = active?.ownerId == System.identityHashCode(view)

    private fun stripBlur(view: View, current: Active) {
        val expanded = invoke(view, "getExpandedView") as? View ?: return
        val loader = view.context.classLoader
        runCatching {
            invokeBlur(loader, "setMiViewBlurModeCompat", expanded, 0)
            invokeBlur(loader, "clearMiBackgroundBlendColorCompat", expanded)
            expanded.background = null
            current.blurStripped = true
        }
    }

    private fun restoreBlur(view: View, current: Active, teardown: Boolean) {
        val expanded = invoke(view, "getExpandedView") as? View
        // Clear the flag first so a blur call that re-enters updateBackgroundBg
        // does not strip the material again.
        current.blurStripped = false
        if (expanded == null) return
        if (!teardown) {
            runCatching { invoke(view, "updateBackgroundBg", expanded, false) }
            return
        }
        // updateBackgroundBg NPEs once currentIslandData is cleared. Turning
        // the blur mode back on is enough; the next real call restores blends.
        runCatching { invokeBlur(view.context.classLoader, "setMiViewBlurModeCompat", expanded, 1) }
    }

    private fun backgroundView(view: View): View? = invoke(view, "getBackgroundView") as? View

    private fun strokePad(view: View): Float {
        val id = view.resources.getIdentifier("island_stroke", "dimen", view.context.packageName)
        if (id == 0) return 0f
        return view.resources.getDimension(id)
    }

    private fun invoke(target: Any, name: String, vararg args: Any?): Any? {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == args.size }
            ?: target.javaClass.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == args.size }
            ?: return null
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    private fun invokeBlur(loader: ClassLoader, name: String, vararg args: Any?) {
        val type = loader.loadClass(BLUR)
        val types = listOf(type) + type.declaredClasses.toList()
        for (candidate in types) {
            val method = candidate.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.size == args.size
            } ?: continue
            method.isAccessible = true
            if (Modifier.isStatic(method.modifiers)) {
                method.invoke(null, *args)
            } else {
                val instance = candidate.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
                method.invoke(instance, *args)
            }
            return
        }
    }
}
