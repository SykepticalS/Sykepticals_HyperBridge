package com.lidesheng.hyperlyric.root.mediacard.island.layout.ios

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearHorizontal
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearVertical
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaIosHeaderLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        resizeCover(environment)
        anchorSongInfo(environment)
    }

    private fun resizeCover(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val coverSize = context.islandExpandedMediaDp(
                IslandExpandedMediaIosMetrics.COVER_SIZE_DP
            )
            bridge.constrainWidth(layout, ids.albumArt, coverSize)
            bridge.constrainHeight(layout, ids.albumArt, coverSize)
            bridge.setMargin(
                layout,
                ids.albumArt,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaIosMetrics.COVER_TOP_DP)
            )
            bridge.setMargin(
                layout,
                ids.albumArt,
                IslandExpandedMediaConstraintSide.START,
                context.islandExpandedMediaDp(IslandExpandedMediaIosMetrics.COVER_START_DP)
            )
        }
    }

    private fun anchorSongInfo(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            anchorTitleToTop()
            applySongInfoHorizontalConstraints(ids.headerTitle)
            applySongInfoHorizontalConstraints(ids.headerArtist)
            bridge.clearVertical(layout, ids.headerArtist)
            bridge.connect(
                layout,
                ids.headerArtist,
                IslandExpandedMediaConstraintSide.TOP,
                ids.headerTitle,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                ids.headerArtist,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaIosMetrics.ARTIST_GAP_DP)
            )
        }
    }

    private fun IslandExpandedMediaLayoutEnvironment.anchorTitleToTop() {
        bridge.clearVertical(layout, ids.headerTitle)
        bridge.connect(
            layout,
            ids.headerTitle,
            IslandExpandedMediaConstraintSide.TOP,
            0,
            IslandExpandedMediaConstraintSide.TOP
        )
        bridge.setMargin(
            layout,
            ids.headerTitle,
            IslandExpandedMediaConstraintSide.TOP,
            context.islandExpandedMediaDimenPx("media_control_title_top_margin", 21f) +
                context.islandExpandedMediaDp(
                    IslandExpandedMediaIosMetrics.UPPER_CONTENT_SHIFT_DP
                )
        )
    }

    private fun IslandExpandedMediaLayoutEnvironment.applySongInfoHorizontalConstraints(
        viewId: Int
    ) {
        bridge.clearHorizontal(layout, viewId)
        bridge.connect(
            layout,
            viewId,
            IslandExpandedMediaConstraintSide.START,
            ids.albumArt,
            IslandExpandedMediaConstraintSide.END
        )
        bridge.connect(
            layout,
            viewId,
            IslandExpandedMediaConstraintSide.END,
            0,
            IslandExpandedMediaConstraintSide.END
        )
        bridge.setMargin(
            layout,
            viewId,
            IslandExpandedMediaConstraintSide.START,
            context.islandExpandedMediaDp(IslandExpandedMediaIosMetrics.INFO_START_GAP_DP)
        )
        bridge.setMargin(
            layout,
            viewId,
            IslandExpandedMediaConstraintSide.END,
            context.islandExpandedMediaDp(
                IslandExpandedMediaIosMetrics.MUSIC_WAVE_END_DP +
                    IslandExpandedMediaIosMetrics.MUSIC_WAVE_SIZE_DP +
                    IslandExpandedMediaIosMetrics.MUSIC_WAVE_INFO_GAP_DP
            )
        )
    }
}
