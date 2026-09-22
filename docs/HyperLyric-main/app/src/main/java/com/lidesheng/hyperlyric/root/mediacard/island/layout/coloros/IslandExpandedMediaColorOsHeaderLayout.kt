package com.lidesheng.hyperlyric.root.mediacard.island.layout.coloros

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaColorOsHeaderLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        resizeCover(environment)
        placeAppIcon(environment)
        anchorSongInfo(environment)
    }

    private fun resizeCover(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            bridge.constrainWidth(
                layout,
                ids.albumArt,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.COVER_SIZE_DP)
            )
            bridge.constrainHeight(
                layout,
                ids.albumArt,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.COVER_SIZE_DP)
            )
            bridge.clearAll(layout, ids.albumArt)
            bridge.connect(
                layout,
                ids.albumArt,
                IslandExpandedMediaConstraintSide.START,
                0,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                ids.albumArt,
                IslandExpandedMediaConstraintSide.TOP,
                0,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.setMargin(
                layout,
                ids.albumArt,
                IslandExpandedMediaConstraintSide.START,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.COVER_START_DP)
            )
            bridge.setMargin(
                layout,
                ids.albumArt,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.COVER_TOP_DP)
            )
        }
    }

    private fun placeAppIcon(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val appIcon = ids.mediaSeamless
            bridge.constrainWidth(
                layout,
                appIcon,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.APP_ICON_SIZE_DP)
            )
            bridge.constrainHeight(
                layout,
                appIcon,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.APP_ICON_SIZE_DP)
            )
            bridge.clearAll(layout, appIcon)
            bridge.connect(
                layout,
                appIcon,
                IslandExpandedMediaConstraintSide.END,
                0,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                appIcon,
                IslandExpandedMediaConstraintSide.TOP,
                0,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.setMargin(
                layout,
                appIcon,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.APP_ICON_END_DP)
            )
            bridge.setMargin(
                layout,
                appIcon,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.APP_ICON_TOP_DP)
            )
        }
    }

    private fun anchorSongInfo(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
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
            val title = ids.headerTitle
            bridge.clearAll(layout, title)
            bridge.connect(
                layout,
                title,
                IslandExpandedMediaConstraintSide.START,
                startTarget,
                startSide
            )
            bridge.connect(
                layout,
                title,
                IslandExpandedMediaConstraintSide.END,
                ids.mediaSeamless,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                title,
                IslandExpandedMediaConstraintSide.TOP,
                0,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.setMargin(layout, title, IslandExpandedMediaConstraintSide.START, startMargin)
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(
                    IslandExpandedMediaColorOsMetrics.APP_ICON_TEXT_GAP_DP
                )
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.TITLE_TOP_DP)
            )

            val artist = ids.headerArtist
            bridge.clearAll(layout, artist)
            bridge.connect(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.START,
                startTarget,
                startSide
            )
            bridge.connect(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.END,
                0,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.TOP,
                title,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(layout, artist, IslandExpandedMediaConstraintSide.START, startMargin)
            bridge.setMargin(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.CONTENT_END_DP)
            )
            bridge.setMargin(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.ARTIST_GAP_DP)
            )
        }
    }
}
