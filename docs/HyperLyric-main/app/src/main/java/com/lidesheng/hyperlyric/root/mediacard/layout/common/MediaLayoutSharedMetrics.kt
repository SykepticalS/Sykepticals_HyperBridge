package com.lidesheng.hyperlyric.root.mediacard.layout.common

/**
 * Baseline values shared by the notification-center and Super Island media
 * hosts.  The hosts still own their anchors and lifecycle; this object only
 * keeps the common visual vocabulary in one place.
 */
internal object MediaLayoutSharedMetrics {
    const val NATIVE_ACTION_WIDTH_FALLBACK_DP = 60f
    const val NATIVE_ACTION_HEIGHT_FALLBACK_DP = 50f

    const val STANDARD_PROGRESS_HEIGHT_DP = 38f
    const val STANDARD_PROGRESS_OVERLAP_DP = 8f
    const val STANDARD_TIME_WIDTH_DP = 48f

    const val IOS_COVER_SIZE_DP = 60f
    const val IOS_PROGRESS_TOP_MARGIN_DP = 3f
    const val IOS_PROGRESS_TOP_MARGIN_WITHOUT_COVER_DP = 14f
    const val IOS_ACTION_MIN_GAP_AFTER_PROGRESS_DP = 2f
    const val IOS_ACTION_BOTTOM_MARGIN_OFFSET_DP = 1f
    const val IOS_TIME_OUTER_MARGIN_DP = 10f
    const val IOS_TIME_WIDTH_DP = STANDARD_TIME_WIDTH_DP

    const val COMPACT_ACTION_SCALE = 0.8f

    const val PIXEL_PRIMARY_ACTION_SCALE = 0.9f
    const val PIXEL_SECONDARY_ACTION_SCALE = 0.6f
    const val PIXEL_COMPONENT_GAP_DP = 8.5f
    const val PIXEL_CONTENT_MARGIN_DP = 17f
    const val PIXEL_BOTTOM_MARGIN_DP = 8f
    const val PIXEL_TOP_MARGIN_DP = 17f
    const val PIXEL_DEVICE_SIZE_DP = 34f
    const val PIXEL_APP_ICON_SIZE_DP = 24f
    const val PIXEL_BOTTOM_OFFSET_DP = 10f
    const val PIXEL_ARTIST_GAP_DP = 4f
    const val PIXEL_TITLE_END_GAP_DP = 6f
}
