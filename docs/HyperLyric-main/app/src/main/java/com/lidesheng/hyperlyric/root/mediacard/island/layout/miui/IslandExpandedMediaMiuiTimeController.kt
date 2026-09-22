package com.lidesheng.hyperlyric.root.mediacard.island.layout.miui

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaElements
import java.util.Collections
import java.util.WeakHashMap

internal object IslandExpandedMediaMiuiTimeController {
    private val states = Collections.synchronizedMap(
        WeakHashMap<TextView, TimeTextState>()
    )

    fun apply(elements: IslandExpandedMediaElements) {
        apply(elements.elapsedTime, Gravity.START)
        apply(elements.totalTime, Gravity.END)
    }

    fun restore(elements: IslandExpandedMediaElements) {
        restore(elements.elapsedTime)
        restore(elements.totalTime)
    }

    fun applyToFakeView(
        fakeExpandedView: View,
        referenceElements: IslandExpandedMediaElements
    ) {
        apply(
            fakeExpandedView.findViewById(referenceElements.elapsedTime.id),
            Gravity.START
        )
        apply(
            fakeExpandedView.findViewById(referenceElements.totalTime.id),
            Gravity.END
        )
    }

    private fun apply(view: View?, horizontalGravity: Int) {
        val textView = view as? TextView ?: return
        states.getOrPut(textView) { TimeTextState.capture(textView) }
            .apply(horizontalGravity)
    }

    private fun restore(view: View?) {
        val textView = view as? TextView ?: return
        states.remove(textView)?.restore()
    }

    private data class TimeTextState(
        val view: TextView,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int,
        val minEms: Int,
        val gravity: Int,
        val textSizePx: Float
    ) {
        fun apply(horizontalGravity: Int) {
            view.setPadding(0, paddingTop, 0, paddingBottom)
            view.minEms = 0
            view.gravity = horizontalGravity or Gravity.CENTER_VERTICAL
            view.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                IslandExpandedMediaMiuiMetrics.TIME_TEXT_SIZE_SP
            )
        }

        fun restore() {
            view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            view.minEms = minEms
            view.gravity = gravity
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
        }

        companion object {
            fun capture(view: TextView): TimeTextState {
                return TimeTextState(
                    view = view,
                    paddingLeft = view.paddingLeft,
                    paddingTop = view.paddingTop,
                    paddingRight = view.paddingRight,
                    paddingBottom = view.paddingBottom,
                    minEms = view.minEms,
                    gravity = view.gravity,
                    textSizePx = view.textSize
                )
            }
        }
    }
}
