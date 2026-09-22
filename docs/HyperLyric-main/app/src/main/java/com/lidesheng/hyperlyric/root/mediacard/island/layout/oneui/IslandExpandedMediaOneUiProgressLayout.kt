package com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui

import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaOneUiProgressLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val progress = ids.mediaProgressBar
            bridge.constrainWidth(layout, progress, 0)
            bridge.constrainHeight(
                layout,
                progress,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.PROGRESS_HEIGHT_DP)
            )
            bridge.clearAll(layout, progress)
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.START,
                ids.headerTitle,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.END,
                ids.headerTitle,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.TOP,
                ids.headerArtist,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.TOP,
                -context.islandExpandedMediaDp(
                    IslandExpandedMediaOneUiMetrics.PROGRESS_OVERLAP_DP
                )
            )
            anchorTime(environment)
        }
    }

    private fun anchorTime(environment: IslandExpandedMediaLayoutEnvironment) {
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
                    IslandExpandedMediaOneUiMetrics.PROGRESS_OVERLAP_DP
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
                    IslandExpandedMediaOneUiMetrics.PROGRESS_OVERLAP_DP
                )
            )
        }
    }
}
