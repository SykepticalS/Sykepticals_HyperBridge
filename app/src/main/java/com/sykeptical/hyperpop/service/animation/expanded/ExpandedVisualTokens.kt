package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Shared expanded-island measurements. Component policies read these and then
 * apply their own rules. Nothing here is a device coordinate.
 *
 * Legacy values are the pre-band layout. Pill mode reads the camera-band tokens.
 */
object ExpandedVisualTokens {
    const val LEGACY_BODY_GAP_DP = 4f
    const val LEGACY_COLUMN_SAFETY_DP = 8f
    const val TIGHT_SAFETY_DP = 4f
    const val LEGACY_HORIZONTAL_PAD_DP = 4f
    const val LEGACY_EDGE_PAD_DP = 2f
    const val LEGACY_MIN_LIFT_DP = 4f

    const val CAMERA_SIDE_GAP_DP = 6f
    const val CAMERA_VERTICAL_GAP_DP = 4f
    const val CAMERA_TEXT_GAP_DP = 6f
    const val PILL_EDGE_INSET_DP = 2f
    const val OUTER_HORIZONTAL_DP = 12f
    const val BELOW_BAND_GAP_DP = 6f
    const val ROW_GAP_DP = 4f
    const val MAJOR_CONTROL_GAP_DP = 10f
    const val SECONDARY_CONTROL_GAP_DP = 6f

    const val MEDIA_ART_INSET_DP = 22f
    const val MEDIA_ART_TOP_DP = 12f
    const val MEDIA_TITLE_BELOW_ART_DP = 6f
    const val MEDIA_ART_SIZE_DP = 52.5f
    const val MEDIA_SEAMLESS_SIZE_DP = 34f
    const val MEDIA_ACTION_TOP_DP = 6f
    const val MEDIA_PROGRESS_BOTTOM_DP = 10f

    const val ACTION_PILL_VISUAL_HEIGHT_DP = 28f
    const val ACTION_PILL_TEXT_DP = 13f
    const val ACTION_PILL_GAP_DP = 6f
    const val MIN_TOUCH_DP = 44f
    const val PRIMARY_CONTROL_DP = 52f
    const val FOCUS_ROOT_MARGIN_DP = 14f
    const val FOCUS_AREA_D_PADDING_DP = 8f
    const val FOCUS_PILL_MARGIN_VERTICAL_DP = 8f

    const val FADE_MIN_DP = 6f
    const val FADE_MAX_DP = 16f
    const val START_FADE_MAX_DP = 8f
    const val FADE_SPAN_FRACTION = 0.18f
    const val MIN_BAND_TEXT_DP = 64f

    const val PILL_BOTTOM_PAD_DP = 10f
    const val PILL_CAP_DP = 56f
    const val PILL_MIN_SCALE = 0.92f

    fun px(dp: Float, density: Float): Int = (dp * density).toInt()
}
