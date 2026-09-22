package com.lidesheng.hyperlyric.root.mediacard.island.layout.pixel

import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics

/** Geometry for the information-first Pixel media player in the island host. */
internal object IslandExpandedMediaPixelMetrics {
    const val ROOT_HEIGHT_DP = 168f
    // Pixel's island values already compensate for the shorter host: its
    // title starts at 52dp versus 70dp in the notification layout.
    const val MIDDLE_HORIZONTAL_MARGIN_DP = MediaLayoutSharedMetrics.PIXEL_CONTENT_MARGIN_DP
    const val BOTTOM_HORIZONTAL_MARGIN_DP = MediaLayoutSharedMetrics.PIXEL_BOTTOM_MARGIN_DP
    const val TOP_MARGIN_DP = MediaLayoutSharedMetrics.PIXEL_TOP_MARGIN_DP
    const val MIDDLE_TOP_DP = 52f
    const val TITLE_END_GAP_DP = MediaLayoutSharedMetrics.PIXEL_TITLE_END_GAP_DP
    const val ARTIST_TOP_GAP_DP = MediaLayoutSharedMetrics.PIXEL_ARTIST_GAP_DP
    const val DEVICE_END_MARGIN_DP = MediaLayoutSharedMetrics.PIXEL_CONTENT_MARGIN_DP
    const val DEVICE_SIZE_DP = MediaLayoutSharedMetrics.PIXEL_DEVICE_SIZE_DP

    const val APP_ICON_SIZE_DP = MediaLayoutSharedMetrics.PIXEL_APP_ICON_SIZE_DP
    const val APP_ICON_MARGIN_DP = MediaLayoutSharedMetrics.PIXEL_CONTENT_MARGIN_DP

    const val PRIMARY_ACTION_SCALE = MediaLayoutSharedMetrics.PIXEL_PRIMARY_ACTION_SCALE
    const val BOTTOM_ACTION_SCALE = MediaLayoutSharedMetrics.PIXEL_SECONDARY_ACTION_SCALE
    const val PROGRESS_HEIGHT_DP = MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP
    const val COMPONENT_GAP_DP = MediaLayoutSharedMetrics.PIXEL_COMPONENT_GAP_DP
    const val BOTTOM_MARGIN_DP = MediaLayoutSharedMetrics.PIXEL_BOTTOM_OFFSET_DP
}
