package com.sykeptical.hyperpop.xposed.hooks

import android.graphics.Color
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
    private val reflect = ReflectionLookup()
    private var active: Active? = null
    private var activeView = java.lang.ref.WeakReference<View>(null)

    private class Active(
        val ownerId: Int,
        val background: java.lang.ref.WeakReference<View>,
        val original: Drawable?,
        var copy: GradientDrawable?,
        val nativeFill: Int?,
        val outsetPx: Int?,
        val black: Boolean,
        var progress: Float = 0f,
        var radiusPx: Float = 0f,
        var blurStripped: Boolean = false,
    )

    fun arm(view: View, black: Boolean) {
        val previous = activeView.get()
        if (previous != null && previous !== view) restore(previous)
        if (active?.ownerId == System.identityHashCode(view)) return
        restore(view)
        val background = backgroundView(view) ?: return
        val original = invoke(background, "getDrawable") as? Drawable
        val copy = (original as? GradientDrawable)?.constantState?.newDrawable()?.mutate() as? GradientDrawable
        if (copy != null) {
            copy.setStroke(0, Color.TRANSPARENT)
            invoke(background, "setDrawable", copy)
        }
        val outset = setOutset(background, 0)
        active = Active(
            ownerId = System.identityHashCode(view),
            background = java.lang.ref.WeakReference(background),
            original = original,
            copy = copy,
            nativeFill = copy?.color?.defaultColor,
            outsetPx = outset,
            black = black,
        )
        activeView = java.lang.ref.WeakReference(view)
    }

    fun frame(view: View, progress: Float, radiusPx: Float) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view)) return
        val background = current.background.get() ?: backgroundView(view)
        val showing = background?.let { invoke(it, "getDrawable") as? Drawable }
        if (showing != null && showing !== current.copy) onDrawableReplaced(view)
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
        val edge = ExpandedSurfaceStyle.ownedEdge(radiusPx)
        current.copy?.setStroke(edge.strokeWidthPx, Color.TRANSPARENT)
        current.copy?.cornerRadius = edge.cornerRadiusPx
        background?.invalidate()
    }

    fun onDrawableReplaced(view: View) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view)) return
        val background = backgroundView(view) ?: return
        val drawable = invoke(background, "getDrawable") as? Drawable ?: return
        if (drawable === current.copy) return
        val copy = (drawable as? GradientDrawable)?.constantState?.newDrawable()?.mutate() as? GradientDrawable
            ?: return
        copy.setStroke(0, Color.TRANSPARENT)
        current.copy = copy
        invoke(background, "setDrawable", copy)
        if (current.black && (current.nativeFill != null || current.progress >= 1f)) {
            copy.setColor(
                ExpandedSurfaceStyle.fill(
                    current.nativeFill ?: ExpandedSurfaceStyle.BLACK,
                    black = true,
                    current.progress,
                ),
            )
        }
        copy.cornerRadius = ExpandedSurfaceStyle.ownedEdge(current.radiusPx).cornerRadiusPx
    }

    fun onBlurReapplied(view: View) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view) || !current.blurStripped) return
        stripBlur(view, current)
    }

    fun restore(view: View) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view)) return
        val background = current.background.get() ?: backgroundView(view)
        background?.let {
            invoke(it, "setDrawable", current.original)
            current.outsetPx?.let { outset -> setOutset(it, outset) }
        }
        if (current.blurStripped) restoreBlur(view, current, teardown = true)
        active = null
        if (activeView.get() === view) activeView = java.lang.ref.WeakReference(null)
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

    private fun setOutset(background: View, width: Int): Int? {
        val field = reflect.field(background.javaClass, "stokeWidth") ?: return null
        val previous = runCatching { field.getInt(background) }.getOrNull() ?: return null
        if (previous != width) runCatching { field.setInt(background, width) }
        return previous
    }

    private fun invoke(target: Any, name: String, vararg args: Any?): Any? {
        val method = reflect.method(target.javaClass, name, args.size) ?: return null
        return runCatching { method.invoke(target, *args) }.getOrNull()
    }

    private fun invokeBlur(loader: ClassLoader, name: String, vararg args: Any?) {
        val type = loader.loadClass(BLUR)
        val types = listOf(type) + type.declaredClasses.toList()
        for (candidate in types) {
            val method = reflect.method(candidate, name, args.size) ?: continue
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
