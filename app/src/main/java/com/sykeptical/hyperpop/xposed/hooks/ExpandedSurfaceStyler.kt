package com.sykeptical.hyperpop.xposed.hooks

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedSurfaceStyle
import com.sykeptical.hyperpop.service.animation.expanded.FlowMask
import java.lang.reflect.Modifier

/**
 * Replaces Xiaomi's expanded-island plate with a HyperPop drawable and removes
 * the blur material that would otherwise cover it. Ambient Flow stays; the
 * flow mask fades it into the plate. The original drawable and backgrounds
 * are put back on restore.
 */
object ExpandedSurfaceStyler {
    private const val BLUR = "miui.systemui.util.MiBlurCompat"
    private val reflect = ReflectionLookup()
    private val installing = ThreadLocal<Boolean>()
    private var active: Active? = null
    private var activeView = java.lang.ref.WeakReference<View>(null)

    private class Active(
        val ownerId: Int,
        val background: java.lang.ref.WeakReference<View>,
        val original: Drawable?,
        var copy: GradientDrawable?,
        val outsetPx: Int?,
        val black: Boolean,
        var progress: Float = 0f,
        var radiusPx: Float = 0f,
        var blurStripped: Boolean = false,
        var expandedPlate: GradientDrawable? = null,
        val expandedBackground: Drawable? = null,
        val expandedHadClip: Boolean = false,
        val cleared: List<SavedBackground> = emptyList(),
    )

    private class SavedBackground(
        val view: java.lang.ref.WeakReference<View>,
        val background: Drawable?,
    )

    fun arm(view: View, black: Boolean) {
        val previous = activeView.get()
        if (previous != null && previous !== view) restore(previous)
        if (active?.ownerId == System.identityHashCode(view)) return
        restore(view)
        val background = backgroundView(view) ?: return
        val original = invoke(background, "getDrawable") as? Drawable
        val plate = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setStroke(0, Color.TRANSPARENT)
            if (black) {
                setColor(ExpandedSurfaceStyle.ownedPlateFill())
            } else {
                (original as? GradientDrawable)?.color?.defaultColor?.let { setColor(it) }
            }
        }
        installDrawable(background, plate)
        val outset = setOutset(background, 0)
        val expanded = invoke(view, "getExpandedView") as? View
        val content = invoke(view, "getCurrentIslandData")?.let { invoke(it, "getView") as? View }
        val expandedBackground = expanded?.background
        val expandedHadClip = expanded?.clipToOutline == true
        val expandedPlate = if (black) newPlate() else null
        val cleared = if (black) takeBackgrounds(content) else emptyList()
        if (expandedPlate != null && expanded != null) {
            expanded.background = expandedPlate
            expanded.clipToOutline = true
        }
        val installed = Active(
            ownerId = System.identityHashCode(view),
            background = java.lang.ref.WeakReference(background),
            original = original,
            copy = plate,
            outsetPx = outset,
            black = black,
            expandedPlate = expandedPlate,
            expandedBackground = expandedBackground,
            expandedHadClip = expandedHadClip,
            cleared = cleared,
        )
        active = installed
        activeView = java.lang.ref.WeakReference(view)
        if (black) stripBlur(view, installed)
    }

    /** True when Xiaomi is trying to replace the plate this takeover owns. */
    fun blocksReplacement(background: View): Boolean {
        if (installing.get() == true) return false
        return active?.background?.get() === background
    }

    /** Puts the owned plate back. Safe to call from the setDrawable hook. */
    fun reassert(background: View) {
        val current = active ?: return
        if (current.background.get() !== background) return
        val plate = current.copy ?: return
        installDrawable(background, plate)
        applyPlate(current)
        val owner = activeView.get()
        if (current.black && owner != null) stripBlur(owner, current)
    }

    fun frame(view: View, progress: Float, radiusPx: Float) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view)) return
        val background = current.background.get() ?: backgroundView(view)
        val showing = background?.let { invoke(it, "getDrawable") as? Drawable }
        if (showing != null && showing !== current.copy) onDrawableReplaced(view)
        current.progress = progress
        current.radiusPx = radiusPx
        applyPlate(current)
        if (current.black && !current.blurStripped) stripBlur(view, current)
        background?.invalidate()
    }

    fun onDrawableReplaced(view: View) {
        val current = active ?: return
        if (current.ownerId != System.identityHashCode(view)) return
        val background = backgroundView(view) ?: return
        val drawable = invoke(background, "getDrawable") as? Drawable ?: return
        if (drawable === current.copy) return
        val plate = current.copy ?: return
        installDrawable(background, plate)
        applyPlate(current)
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
            installDrawable(it, current.original)
            current.outsetPx?.let { outset -> setOutset(it, outset) }
        }
        if (current.blurStripped) restoreBlur(view, current, teardown = true)
        val expanded = invoke(view, "getExpandedView") as? View
        expanded?.background = current.expandedBackground
        expanded?.clipToOutline = current.expandedHadClip
        current.cleared.forEach { saved -> saved.view.get()?.background = saved.background }
        active = null
        if (activeView.get() === view) activeView = java.lang.ref.WeakReference(null)
    }

    fun owns(view: View): Boolean = active?.ownerId == System.identityHashCode(view)

    private class FakeStyle(
        val expanded: java.lang.ref.WeakReference<View>,
        val background: Drawable?,
        val hadClip: Boolean,
        var plate: GradientDrawable,
        var masked: List<java.lang.ref.WeakReference<View>> = emptyList(),
    )

    private val fakes = java.util.Collections.synchronizedMap(java.util.WeakHashMap<View, FakeStyle>())

    /**
     * The fake island that tracks the swipe into a window carries its own
     * blur material and its own copy of the content. Give it the same black
     * plate and Ambient Flow fade as the real island while it is shown.
     */
    fun blackenFake(fake: View, radiusPx: Float, relativeMask: FlowMask?, cardTopInFake: Int) {
        val expanded = invoke(fake, "getFakeExpandedView") as? View ?: return
        val plate = newPlate().apply { cornerRadius = radiusPx.coerceAtLeast(0f) }
        val style = fakes[fake]?.also { it.plate = plate }
            ?: FakeStyle(java.lang.ref.WeakReference(expanded), expanded.background, expanded.clipToOutline, plate)
        fakes[fake] = style
        stripFakeBlur(fake, style)
        style.masked.forEach { it.get()?.setRenderEffect(null) }
        style.masked = if (relativeMask != null) {
            val location = IntArray(2)
            fake.getLocationInWindow(location)
            ExpandedFlowMaskApplicator.applyFromCardTop(expanded, relativeMask, location[1] + cardTopInFake)
                .map { java.lang.ref.WeakReference(it) }
        } else {
            emptyList()
        }
    }

    /** Xiaomi reapplied the fake's blur while HyperPop owns it. */
    fun onFakeBlurReapplied(fake: View) {
        val style = fakes[fake] ?: return
        stripFakeBlur(fake, style)
    }

    fun restoreFake(fake: View) {
        val style = fakes.remove(fake) ?: return
        style.masked.forEach { it.get()?.setRenderEffect(null) }
        val expanded = style.expanded.get() ?: return
        expanded.background = style.background
        expanded.clipToOutline = style.hadClip
        runCatching { invokeBlur(fake.context.classLoader, "setMiViewBlurModeCompat", expanded, 1) }
        invoke(fake, "updateBackgroundBg", expanded, false)
    }

    private fun stripFakeBlur(fake: View, style: FakeStyle) {
        val expanded = style.expanded.get() ?: return
        runCatching {
            invokeBlur(fake.context.classLoader, "setMiViewBlurModeCompat", expanded, 0)
            invokeBlur(fake.context.classLoader, "clearMiBackgroundBlendColorCompat", expanded)
        }
        expanded.background = style.plate
    }

    private fun stripBlur(view: View, current: Active) {
        val expanded = invoke(view, "getExpandedView") as? View ?: return
        val loader = view.context.classLoader
        runCatching {
            invokeBlur(loader, "setMiViewBlurModeCompat", expanded, 0)
            invokeBlur(loader, "clearMiBackgroundBlendColorCompat", expanded)
            expanded.background = current.expandedPlate
            expanded.clipToOutline = current.expandedPlate != null
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

    private fun installDrawable(background: View, drawable: Drawable?) {
        installing.set(true)
        try {
            invoke(background, "setDrawable", drawable)
        } finally {
            installing.set(false)
        }
    }

    private fun applyPlate(current: Active) {
        val plate = current.copy ?: return
        if (current.black) plate.setColor(ExpandedSurfaceStyle.ownedPlateFill())
        val edge = ExpandedSurfaceStyle.ownedEdge(current.radiusPx)
        plate.setStroke(edge.strokeWidthPx, Color.TRANSPARENT)
        plate.cornerRadius = edge.cornerRadiusPx
        current.expandedPlate?.let { expanded ->
            expanded.setColor(ExpandedSurfaceStyle.ownedPlateFill())
            expanded.setStroke(edge.strokeWidthPx, Color.TRANSPARENT)
            expanded.cornerRadius = edge.cornerRadiusPx
        }
        current.cleared.forEach { saved ->
            val target = saved.view.get() ?: return@forEach
            if (target.background != null) target.background = null
        }
    }

    private fun newPlate(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(ExpandedSurfaceStyle.ownedPlateFill())
        setStroke(0, Color.TRANSPARENT)
    }

    private fun takeBackgrounds(root: View?): List<SavedBackground> {
        val content = root ?: return emptyList()
        val saved = mutableListOf<SavedBackground>()
        fun take(target: View) {
            val background = target.background ?: return
            saved.add(SavedBackground(java.lang.ref.WeakReference(target), background))
            target.background = null
        }
        take(content)
        val group = content as? ViewGroup ?: return saved
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is ImageView || child.background == null) continue
            if (child.height == 0 || child.height >= content.height / 2) take(child)
        }
        return saved
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
