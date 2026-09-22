package com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui

import android.view.View
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaOneUiHeaderLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            // One UI puts application identity above the song metadata and does
            // not reserve a cover-art column in the compact island container.
            bridge.setVisibility(layout, ids.albumArt, View.GONE)
            anchorIdentity(environment)
            anchorTitle(environment)
            anchorArtist(environment)
        }
    }

    private fun anchorIdentity(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val identity = ids.mediaSeamless
            bridge.constrainWidth(layout, identity, 0)
            bridge.constrainHeight(
                layout,
                identity,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.IDENTITY_HEIGHT_DP)
            )
            bridge.clearAll(layout, identity)
            bridge.connect(
                layout,
                identity,
                IslandExpandedMediaConstraintSide.START,
                0,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                identity,
                IslandExpandedMediaConstraintSide.END,
                0,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                identity,
                IslandExpandedMediaConstraintSide.TOP,
                0,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.setMargin(
                layout,
                identity,
                IslandExpandedMediaConstraintSide.START,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.HORIZONTAL_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                identity,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.HORIZONTAL_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                identity,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.IDENTITY_TOP_DP)
            )
        }
    }

    private fun anchorTitle(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val title = ids.headerTitle
            bridge.constrainWidth(layout, title, 0)
            bridge.clearAll(layout, title)
            bridge.connect(
                layout,
                title,
                IslandExpandedMediaConstraintSide.START,
                0,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                title,
                IslandExpandedMediaConstraintSide.END,
                0,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                title,
                IslandExpandedMediaConstraintSide.TOP,
                ids.mediaSeamless,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.START,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.HORIZONTAL_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.HORIZONTAL_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.TITLE_GAP_DP)
            )
        }
    }

    private fun anchorArtist(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val artist = ids.headerArtist
            bridge.constrainWidth(layout, artist, 0)
            bridge.clearAll(layout, artist)
            bridge.connect(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.START,
                ids.headerTitle,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.END,
                ids.headerTitle,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.TOP,
                ids.headerTitle,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                artist,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.ARTIST_GAP_DP)
            )
        }
    }
}
