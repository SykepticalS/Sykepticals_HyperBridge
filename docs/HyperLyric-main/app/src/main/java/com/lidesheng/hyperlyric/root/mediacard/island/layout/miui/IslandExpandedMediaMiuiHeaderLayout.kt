package com.lidesheng.hyperlyric.root.mediacard.island.layout.miui

import android.view.View
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaMiuiHeaderLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            // The MIUI template uses a dynamic application-name TextView. The
            // ConstraintSet supplies the safe fallback title position; the
            // runtime controller moves it directly below that TextView.
            bridge.setVisibility(layout, ids.albumArt, View.GONE)
            anchorTitle(environment)
            anchorArtist(environment)
            anchorDeviceSwitch(environment)
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
                0,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.START,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.HORIZONTAL_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.HORIZONTAL_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.TITLE_TOP_DP)
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
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.ARTIST_GAP_DP)
            )
        }
    }

    private fun anchorDeviceSwitch(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val deviceSwitch = ids.mediaSeamless
            if (hideDeviceSwitch) {
                bridge.setVisibility(layout, deviceSwitch, View.GONE)
                return
            }
            bridge.setVisibility(layout, deviceSwitch, View.VISIBLE)
            bridge.constrainWidth(
                layout,
                deviceSwitch,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.DEVICE_SIZE_DP)
            )
            bridge.constrainHeight(
                layout,
                deviceSwitch,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.DEVICE_SIZE_DP)
            )
            bridge.clearAll(layout, deviceSwitch)
            bridge.connect(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.END,
                0,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.TOP,
                ids.action0,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.connect(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.BOTTOM,
                ids.action0,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.HORIZONTAL_MARGIN_DP)
            )
        }
    }
}
