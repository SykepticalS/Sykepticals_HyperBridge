package com.lidesheng.hyperlyric.root.mediacard.island.layout.coloros

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaColorOsProgressLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val progress = ids.mediaProgressBar
            val startTarget = if (coverHidden) 0 else ids.albumArt
            val startSide = if (coverHidden) {
                IslandExpandedMediaConstraintSide.START
            } else {
                IslandExpandedMediaConstraintSide.END
            }
            val startMargin = context.islandExpandedMediaDp(
                if (coverHidden) {
                    IslandExpandedMediaColorOsMetrics.CONTENT_END_DP
                } else {
                    IslandExpandedMediaColorOsMetrics.CONTENT_GAP_DP
                }
            )
            bridge.constrainWidth(layout, progress, 0)
            bridge.constrainHeight(
                layout,
                progress,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.PROGRESS_HEIGHT_DP)
            )
            bridge.clearAll(layout, progress)
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.START,
                startTarget,
                startSide
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
                IslandExpandedMediaConstraintSide.TOP,
                ids.headerArtist,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(layout, progress, IslandExpandedMediaConstraintSide.START, startMargin)
            bridge.setMargin(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.CONTENT_END_DP)
            )
            bridge.setMargin(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.TOP,
                -context.islandExpandedMediaDp(
                    IslandExpandedMediaColorOsMetrics.PROGRESS_OVERLAP_DP
                )
            )

            applyTimeBounds(environment)
        }
    }

    private fun applyTimeBounds(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val elapsed = ids.mediaElapsedTime
            bridge.constrainWidth(
                layout,
                elapsed,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.TIME_WIDTH_DP)
            )
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
                    IslandExpandedMediaColorOsMetrics.PROGRESS_OVERLAP_DP
                )
            )

            val total = ids.mediaTotalTime
            bridge.constrainWidth(
                layout,
                total,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.TIME_WIDTH_DP)
            )
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
                    IslandExpandedMediaColorOsMetrics.PROGRESS_OVERLAP_DP
                )
            )
        }
    }
}
