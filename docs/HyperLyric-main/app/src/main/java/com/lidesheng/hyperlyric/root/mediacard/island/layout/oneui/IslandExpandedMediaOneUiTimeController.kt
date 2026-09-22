package com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui

import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.lidesheng.hyperlyric.root.mediacard.island.IslandExpandedMediaElements
import java.util.Collections
import java.util.WeakHashMap

/** Removes native time-label insets so the labels meet the progress endpoints. */
internal object IslandExpandedMediaOneUiTimeController {
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
        val verticalGravity = textView.gravity and Gravity.VERTICAL_GRAVITY_MASK
        textView.gravity = horizontalGravity or
            if (verticalGravity == 0) Gravity.CENTER_VERTICAL else verticalGravity
        if (textView.paddingLeft != 0 || textView.paddingRight != 0) {
            textView.setPaddingRelative(
                0,
                textView.paddingTop,
                0,
                textView.paddingBottom
            )
        }
    }

    private fun restore(view: View?) {
        val textView = view as? TextView ?: return
        states.remove(textView)?.restore(textView)
    }

    private data class TimeTextState(
        val gravity: Int,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int,
        val paddingRelative: Boolean,
        val paddingStart: Int,
        val paddingEnd: Int
    ) {
        fun restore(textView: TextView) {
            textView.gravity = gravity
            if (paddingRelative) {
                textView.setPaddingRelative(
                    paddingStart,
                    paddingTop,
                    paddingEnd,
                    paddingBottom
                )
            } else {
                textView.setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    paddingBottom
                )
            }
        }

        companion object {
            fun capture(textView: TextView): TimeTextState {
                return TimeTextState(
                    gravity = textView.gravity,
                    paddingLeft = textView.paddingLeft,
                    paddingTop = textView.paddingTop,
                    paddingRight = textView.paddingRight,
                    paddingBottom = textView.paddingBottom,
                    paddingRelative = textView.isPaddingRelative,
                    paddingStart = textView.paddingStart,
                    paddingEnd = textView.paddingEnd
                )
            }
        }
    }
}
