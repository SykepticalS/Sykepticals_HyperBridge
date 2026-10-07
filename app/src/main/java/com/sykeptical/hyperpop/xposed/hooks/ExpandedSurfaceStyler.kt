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
 * Removes the blur material over the expanded island and, when a pill radius
 * is active, points Xiaomi's own plate at that radius. The stroke, its color,
 * and the `stokeWidth` outset stay on Xiaomi's drawable. Ambient Flow stays;
 * the flow mask fades it into the black expanded plate. The original corner
 * radius is put back on restore.
 */
object ExpandedSurfaceStyler {
    private const val BLUR = "miui.systemui.util.MiBlurCompat"
    private val reflect = ReflectionLookup()
    private val actives = java.util.concurrent.ConcurrentHashMap<Int, Active>()
    private val retiringIds = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())

    private class Active(
        val owner: java.lang.ref.WeakReference<View>,
        val ownerId: Int,
        val background: java.lang.ref.WeakReference<View>,
        val original: Drawable?,
        val originalCornerRadiusPx: Float?,
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

    /**
     * A replaced owner keeps its plate while Xiaomi morphs it into its compact
     * slot. The next [arm] leaves it alone; [restore] ends it.
     */
    fun retire(view: View) {
        val id = System.identityHashCode(view)
        if (actives.containsKey(id)) retiringIds += id
    }

    fun arm(view: View, black: Boolean) {
        val id = System.identityHashCode(view)
        actives.values.toList().forEach { other ->
            if (other.ownerId == id || other.ownerId in retiringIds) return@forEach
            val owner = other.owner.get()
            if (owner != null) restore(owner) else actives.remove(other.ownerId)
        }
        if (actives.containsKey(id)) return
        val background = backgroundView(view) ?: return
        val original = invoke(background, "getDrawable") as? Drawable
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
            owner = java.lang.ref.WeakReference(view),
            ownerId = id,
            background = java.lang.ref.WeakReference(background),
            original = original,
            originalCornerRadiusPx = (original as? GradientDrawable)?.cornerRadius,
            black = black,
            expandedPlate = expandedPlate,
            expandedBackground = expandedBackground,
            expandedHadClip = expandedHadClip,
            cleared = cleared,
        )
        actives[id] = installed
        if (black) stripBlur(view, installed)
    }

    /**
     * Xiaomi just installed its own plate. Point that plate's corner radius at
     * the clip so the native stroke follows the pill. The stroke itself is
     * left as Xiaomi set it.
     */
    fun alignNativeOutline(background: View) {
        val current = actives.values.firstOrNull { it.background.get() === background } ?: return
        applyCorner(current, background)
    }

    fun frame(view: View, progress: Float, radiusPx: Float) {
        val current = actives[System.identityHashCode(view)] ?: return
        val background = current.background.get() ?: backgroundView(view)
        current.progress = progress
        current.radiusPx = radiusPx
        applyPlate(current)
        if (current.black && !current.blurStripped) stripBlur(view, current)
        background?.invalidate()
    }

    fun onDrawableReplaced(view: View) {
        val current = actives[System.identityHashCode(view)] ?: return
        val background = current.background.get() ?: backgroundView(view) ?: return
        applyCorner(current, background)
    }

    fun onBlurReapplied(view: View) {
        val current = actives[System.identityHashCode(view)] ?: return
        if (!current.blurStripped) return
        stripBlur(view, current)
    }

    fun restore(view: View) {
        val id = System.identityHashCode(view)
        retiringIds -= id
        val current = actives.remove(id) ?: return
        val background = current.background.get() ?: backgroundView(view)
        val radius = current.originalCornerRadiusPx
        if (background != null && radius != null) {
            val showing = invoke(background, "getDrawable") as? GradientDrawable
            if (showing != null && showing === current.original) showing.cornerRadius = radius
        }
        if (current.blurStripped) restoreBlur(view, current, teardown = true)
        val expanded = invoke(view, "getExpandedView") as? View
        expanded?.background = current.expandedBackground
        expanded?.clipToOutline = current.expandedHadClip
        current.cleared.forEach { saved -> saved.view.get()?.background = saved.background }
    }

    fun owns(view: View): Boolean = actives.containsKey(System.identityHashCode(view))

    /** Ownership summary for the debug trace. Reads only. */
    fun debugState(view: View): String {
        val current = actives[System.identityHashCode(view)] ?: return "owned=false"
        val expanded = invoke(view, "getExpandedView") as? View
        val background = current.background.get() ?: backgroundView(view)
        val plate = expanded?.background === current.expandedPlate && current.expandedPlate != null
        return "owned=true black=${current.black} blurStripped=${current.blurStripped} " +
            "plateInstalled=$plate radius=${current.radiusPx} " +
            "bgActual=${background?.let { invoke(it, "getActualTop") }}/" +
            "${background?.let { invoke(it, "getActualHeight") }}"
    }

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
        // currentIslandData is already null when the drag hands the real island back.
        runCatching { invoke(fake, "updateBackgroundBg", expanded, false) }
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

    private fun applyPlate(current: Active) {
        val radius = ExpandedSurfaceStyle.plateCornerRadius(current.radiusPx)
        current.background.get()?.let { applyCorner(current, it) }
        current.expandedPlate?.let { expanded ->
            expanded.setColor(ExpandedSurfaceStyle.ownedPlateFill())
            expanded.setStroke(0, Color.TRANSPARENT)
            expanded.cornerRadius = radius
        }
        current.cleared.forEach { saved ->
            val target = saved.view.get() ?: return@forEach
            if (target.background != null) target.background = null
        }
    }

    private fun applyCorner(current: Active, background: View) {
        val radius = ExpandedSurfaceStyle.plateCornerRadius(current.radiusPx)
        if (radius <= 0f) return
        (invoke(background, "getDrawable") as? GradientDrawable)?.cornerRadius = radius
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
