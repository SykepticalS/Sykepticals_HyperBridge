package com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui

import android.view.View
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaElements
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaActionIconScaler
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Mirrors the compact One UI action-button padding on real and fake players. */
internal object IslandExpandedMediaOneUiActionController {
    private val states = Collections.synchronizedMap(
        WeakHashMap<View, ActionPaddingState>()
    )

    fun apply(elements: IslandExpandedMediaElements) {
        elements.actionButtons.forEach(::apply)
    }

    fun restore(elements: IslandExpandedMediaElements) {
        elements.actionButtons.forEach(::restore)
    }

    fun applyToFakeView(
        fakeExpandedView: View,
        referenceElements: IslandExpandedMediaElements
    ) {
        referenceElements.actionButtons.forEach { reference ->
            if (reference.id == 0) return@forEach
            apply(fakeExpandedView.findViewById(reference.id))
        }
    }

    private fun apply(view: View?) {
        view ?: return
        MediaActionIconScaler.apply(
            view,
            IslandExpandedMediaOneUiMetrics.ACTION_BUTTON_SCALE
        )
        val state = states.getOrPut(view) { ActionPaddingState.capture(view) }
        view.setPadding(
            (state.left * IslandExpandedMediaOneUiMetrics.ACTION_BUTTON_SCALE).roundToInt(),
            (state.top * IslandExpandedMediaOneUiMetrics.ACTION_BUTTON_SCALE).roundToInt(),
            (state.right * IslandExpandedMediaOneUiMetrics.ACTION_BUTTON_SCALE).roundToInt(),
            (state.bottom * IslandExpandedMediaOneUiMetrics.ACTION_BUTTON_SCALE).roundToInt()
        )
    }

    private fun restore(view: View?) {
        view ?: return
        MediaActionIconScaler.restore(view)
        states.remove(view)?.restore(view)
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
}
