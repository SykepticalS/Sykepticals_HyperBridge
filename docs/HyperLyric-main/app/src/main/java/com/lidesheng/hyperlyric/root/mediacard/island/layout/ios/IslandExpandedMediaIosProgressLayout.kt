package com.lidesheng.hyperlyric.root.mediacard.island.layout.ios

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearVertical
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaIosProgressLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val progressBar = ids.mediaProgressBar
            val progressAnchor = if (coverHidden) ids.headerArtist else ids.albumArt
            val progressTopMargin = context.islandExpandedMediaDp(
                if (coverHidden) {
                    IslandExpandedMediaIosMetrics.PROGRESS_TOP_MARGIN_WITHOUT_COVER_DP
                } else {
                    IslandExpandedMediaIosMetrics.PROGRESS_TOP_MARGIN_DP
                }
            )

            bridge.clearVertical(layout, progressBar)
            bridge.constrainHeight(
                layout,
                progressBar,
                context.islandExpandedMediaDimenPx(
                    "media_hyper_seekbar_height",
                    IslandExpandedMediaIosMetrics.PROGRESS_HEIGHT_DP
                )
            )
            bridge.connect(
                layout,
                progressBar,
                IslandExpandedMediaConstraintSide.TOP,
                progressAnchor,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                progressBar,
                IslandExpandedMediaConstraintSide.TOP,
                progressTopMargin
            )
            bridge.setMargin(
                layout,
                progressBar,
                IslandExpandedMediaConstraintSide.LEFT,
                0
            )
            bridge.setMargin(
                layout,
                progressBar,
                IslandExpandedMediaConstraintSide.RIGHT,
                0
            )

            applyTimeBounds(environment)
        }
    }

    private fun applyTimeBounds(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            bridge.constrainWidth(
                layout,
                ids.mediaElapsedTime,
                context.islandExpandedMediaDp(IslandExpandedMediaIosMetrics.TIME_WIDTH_DP)
            )
            bridge.setMargin(
                layout,
                ids.mediaElapsedTime,
                IslandExpandedMediaConstraintSide.LEFT,
                context.islandExpandedMediaDp(IslandExpandedMediaIosMetrics.TIME_OUTER_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                ids.mediaProgressBar,
                IslandExpandedMediaConstraintSide.LEFT,
                0
            )
            bridge.constrainWidth(
                layout,
                ids.mediaTotalTime,
                context.islandExpandedMediaDp(IslandExpandedMediaIosMetrics.TIME_WIDTH_DP)
            )
            bridge.setMargin(
                layout,
                ids.mediaTotalTime,
                IslandExpandedMediaConstraintSide.LEFT,
                0
            )
            bridge.setMargin(
                layout,
                ids.mediaTotalTime,
                IslandExpandedMediaConstraintSide.RIGHT,
                context.islandExpandedMediaDp(IslandExpandedMediaIosMetrics.TIME_OUTER_MARGIN_DP)
            )
        }
    }
}
