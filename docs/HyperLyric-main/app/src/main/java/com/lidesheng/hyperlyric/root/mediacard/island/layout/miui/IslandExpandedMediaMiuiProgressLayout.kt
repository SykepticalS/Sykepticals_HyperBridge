package com.lidesheng.hyperlyric.root.mediacard.island.layout.miui

import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp
import kotlin.math.min

internal object IslandExpandedMediaMiuiProgressLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val progress = ids.mediaProgressBar
            val systemBottomMargin = context.islandExpandedMediaDimenPx(
                "media_control_seekbar_bottom_margin",
                IslandExpandedMediaMiuiMetrics.PROGRESS_BOTTOM_FALLBACK_DP
            )
            // The notification resource is normally 16dp.  Cap it for the
            // 168dp island host; otherwise the bottom-anchored progress row
            // leaves the upper action chain unnecessarily compressed.
            val bottomMargin = min(
                systemBottomMargin,
                context.islandExpandedMediaDp(
                    IslandExpandedMediaMiuiMetrics.PROGRESS_BOTTOM_FALLBACK_DP
                )
            )
            bridge.constrainWidth(layout, progress, 0)
            bridge.constrainHeight(
                layout,
                progress,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.PROGRESS_HEIGHT_DP)
            )
            bridge.clearAll(layout, progress)
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.START,
                0,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.END,
                0,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.BOTTOM,
                0,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.START,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.HORIZONTAL_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.HORIZONTAL_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.BOTTOM,
                bottomMargin
            )
            anchorTimes(environment)
        }
    }

    private fun anchorTimes(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val elapsed = ids.mediaElapsedTime
            bridge.constrainWidth(layout, elapsed, ViewGroup.LayoutParams.WRAP_CONTENT)
            bridge.clearAll(layout, elapsed)
            bridge.connect(
                layout,
                elapsed,
                IslandExpandedMediaConstraintSide.START,
                ids.mediaProgressBar,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                elapsed,
                IslandExpandedMediaConstraintSide.TOP,
                ids.mediaProgressBar,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                elapsed,
                IslandExpandedMediaConstraintSide.TOP,
                -context.islandExpandedMediaDp(
                    IslandExpandedMediaMiuiMetrics.PROGRESS_OVERLAP_DP
                )
            )

            val total = ids.mediaTotalTime
            bridge.constrainWidth(layout, total, ViewGroup.LayoutParams.WRAP_CONTENT)
            bridge.clearAll(layout, total)
            bridge.connect(
                layout,
                total,
                IslandExpandedMediaConstraintSide.END,
                ids.mediaProgressBar,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                total,
                IslandExpandedMediaConstraintSide.TOP,
                ids.mediaProgressBar,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                total,
                IslandExpandedMediaConstraintSide.TOP,
                -context.islandExpandedMediaDp(
                    IslandExpandedMediaMiuiMetrics.PROGRESS_OVERLAP_DP
                )
            )
        }
    }
}
