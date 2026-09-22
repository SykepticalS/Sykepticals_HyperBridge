package com.lidesheng.hyperlyric.root.mediacard.island.layout.miui

import android.view.View
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaElements
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal object IslandExpandedMediaMiuiActionController {
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
        val state = states.getOrPut(view) { ActionPaddingState.capture(view) }
        view.setPadding(
            (state.left * IslandExpandedMediaMiuiMetrics.ACTION_BUTTON_SCALE).roundToInt(),
            (state.top * IslandExpandedMediaMiuiMetrics.ACTION_BUTTON_SCALE).roundToInt(),
            (state.right * IslandExpandedMediaMiuiMetrics.ACTION_BUTTON_SCALE).roundToInt(),
            (state.bottom * IslandExpandedMediaMiuiMetrics.ACTION_BUTTON_SCALE).roundToInt()
        )
    }

    private fun restore(view: View?) {
        view ?: return
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
