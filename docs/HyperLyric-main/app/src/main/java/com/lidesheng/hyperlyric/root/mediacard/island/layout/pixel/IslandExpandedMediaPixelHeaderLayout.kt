package com.lidesheng.hyperlyric.root.mediacard.island.layout.pixel

import android.view.View
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaPixelHeaderLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            // Pixel puts the application identity in a small runtime icon and
            // keeps the native album-art column out of the compact island.
            bridge.setVisibility(layout, ids.albumArt, View.GONE)
            hideTime(environment, ids.mediaElapsedTime)
            hideTime(environment, ids.mediaTotalTime)
            anchorDeviceSwitch(environment)
            anchorTitle(environment)
            anchorArtist(environment)
        }
    }

    private fun hideTime(
        environment: IslandExpandedMediaLayoutEnvironment,
        viewId: Int
    ) {
        with(environment) {
            bridge.setVisibility(layout, viewId, View.GONE)
            bridge.constrainWidth(layout, viewId, 0)
            bridge.constrainHeight(layout, viewId, 0)
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
            val size = context.islandExpandedMediaDimenPx(
                "media_control_seamless",
                IslandExpandedMediaPixelMetrics.DEVICE_SIZE_DP
            )
            bridge.constrainWidth(layout, deviceSwitch, size)
            bridge.constrainHeight(layout, deviceSwitch, size)
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
                0,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.setMargin(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaPixelMetrics.DEVICE_END_MARGIN_DP)
            )
            bridge.setMargin(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaPixelMetrics.TOP_MARGIN_DP)
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
                ids.actionButtons[2],
                IslandExpandedMediaConstraintSide.START
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
                context.islandExpandedMediaDp(
                    IslandExpandedMediaPixelMetrics.MIDDLE_HORIZONTAL_MARGIN_DP
                )
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(IslandExpandedMediaPixelMetrics.TITLE_END_GAP_DP)
            )
            bridge.setMargin(
                layout,
                title,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaPixelMetrics.MIDDLE_TOP_DP)
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
                context.islandExpandedMediaDp(IslandExpandedMediaPixelMetrics.ARTIST_TOP_GAP_DP)
            )
        }
    }
}
