package com.lidesheng.hyperlyric.root.mediacard.island.layout.pixel

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaElements
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Runtime details shared by the real, dummy and FakeView Pixel players. */
internal object IslandExpandedMediaPixelStyleController {
    private val actionStates = Collections.synchronizedMap(
        WeakHashMap<View, ActionPaddingState>()
    )
    private val timeStates = Collections.synchronizedMap(
        WeakHashMap<View, Int>()
    )
    private val coverSourceStates = Collections.synchronizedMap(
        WeakHashMap<View, Int>()
    )
    private val appIconStates = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, PixelAppIconState>()
    )

    fun apply(elements: IslandExpandedMediaElements, appIcon: Drawable?) {
        elements.actionButtons.forEach(::applyAction)
        hideTime(elements.elapsedTime)
        hideTime(elements.totalTime)
        val coverSource = elements.coverSource
        val player = elements.player as? ViewGroup ?: return
        if (appIcon == null) {
            restoreCoverSource(coverSource)
            appIconStates.remove(player)?.restore()
            return
        }
        hideCoverSource(coverSource)
        val state = appIconStates[player]
            ?: PixelAppIconState.create(player)?.also { appIconStates[player] = it }
            ?: return
        state.apply(appIcon)
    }

    fun restore(elements: IslandExpandedMediaElements) {
        elements.actionButtons.forEach(::restoreAction)
        restoreTime(elements.elapsedTime)
        restoreTime(elements.totalTime)
        restoreCoverSource(elements.coverSource)
        (elements.player as? ViewGroup)?.let { player ->
            appIconStates.remove(player)?.restore()
        }
    }

    fun applyToFakeView(
        fakeExpandedView: View,
        referenceElements: IslandExpandedMediaElements,
        appIcon: Drawable?
    ) {
        referenceElements.actionButtons.forEach { reference ->
            if (reference.id != 0) {
                applyAction(fakeExpandedView.findViewById(reference.id))
            }
        }
        hideTime(fakeExpandedView.findViewById(referenceElements.elapsedTime.id))
        hideTime(fakeExpandedView.findViewById(referenceElements.totalTime.id))
        val fakeCoverSource = fakeExpandedView.findViewById<View>(referenceElements.coverSource.id)

        val title = fakeExpandedView.findViewById<View>(referenceElements.title.id)
        val player = title?.parent as? ViewGroup ?: return
        if (appIcon == null) {
            fakeCoverSource?.let(::restoreCoverSource)
            appIconStates.remove(player)?.restore()
            return
        }
        fakeCoverSource?.let(::hideCoverSource)
        val state = appIconStates[player]
            ?: PixelAppIconState.create(player)?.also { appIconStates[player] = it }
            ?: return
        state.apply(appIcon)
    }

    private fun applyAction(view: View?) {
        view ?: return
        val state = actionStates.getOrPut(view) { ActionPaddingState.capture(view) }
        val scale = if (view.isPrimaryAction()) {
            IslandExpandedMediaPixelMetrics.PRIMARY_ACTION_SCALE
        } else {
            IslandExpandedMediaPixelMetrics.BOTTOM_ACTION_SCALE
        }
        view.setPadding(
            (state.left * scale).roundToInt(),
            (state.top * scale).roundToInt(),
            (state.right * scale).roundToInt(),
            (state.bottom * scale).roundToInt()
        )
    }

    private fun restoreAction(view: View) {
        actionStates.remove(view)?.restore(view)
    }

    private fun hideTime(view: View?) {
        view ?: return
        if (!timeStates.containsKey(view)) {
            timeStates[view] = view.visibility
        }
        view.visibility = View.GONE
    }

    private fun restoreTime(view: View) {
        timeStates.remove(view)?.let { view.visibility = it }
    }

    private fun hideCoverSource(view: View?) {
        view ?: return
        if (!coverSourceStates.containsKey(view)) {
            coverSourceStates[view] = view.visibility
        }
        view.visibility = View.GONE
    }

    private fun restoreCoverSource(view: View?) {
        view ?: return
        coverSourceStates.remove(view)?.let { view.visibility = it }
    }

    private fun View.isPrimaryAction(): Boolean {
        return runCatching { resources.getResourceEntryName(id) == "action2" }
            .getOrDefault(false)
    }

    private data class ActionPaddingState(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun restore(view: View) {
            view.setPadding(left, top, right, bottom)
        }

        companion object {
            fun capture(view: View): ActionPaddingState {
                return ActionPaddingState(
                    left = view.paddingLeft,
                    top = view.paddingTop,
                    right = view.paddingRight,
                    bottom = view.paddingBottom
                )
            }
        }
    }

    private data class PixelAppIconState(val icon: ImageView) {
        fun apply(drawable: Drawable) {
            val copy = runCatching {
                drawable.constantState
                    ?.newDrawable(icon.resources, icon.context.theme)
                    ?.mutate()
            }.getOrNull() ?: drawable
            icon.setImageDrawable(copy)
            icon.imageTintList = null
            icon.alpha = 1f
            icon.visibility = View.VISIBLE
        }

        fun restore() {
            (icon.parent as? ViewGroup)?.removeView(icon)
        }

        companion object {
            fun create(player: ViewGroup): PixelAppIconState? = runCatching {
                val context = player.context
                val density = context.resources.displayMetrics.density
                val size = (IslandExpandedMediaPixelMetrics.APP_ICON_SIZE_DP * density)
                    .roundToInt()
                val icon = ImageView(context).apply {
                    id = View.generateViewId()
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val paramsClass = Class.forName(
                    "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams",
                    false,
                    player.javaClass.classLoader
                )
                val params = paramsClass.getConstructor(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).newInstance(size, size) as ViewGroup.MarginLayoutParams
                paramsClass.getField("startToStart").setInt(params, 0)
                paramsClass.getField("topToTop").setInt(params, 0)
                params.marginStart = (
                    IslandExpandedMediaPixelMetrics.APP_ICON_MARGIN_DP * density
                    ).roundToInt()
                params.topMargin = (
                    IslandExpandedMediaPixelMetrics.APP_ICON_MARGIN_DP * density
                    ).roundToInt()
                player.addView(icon, params)
                PixelAppIconState(icon)
            }.getOrNull()
        }
    }
}
