package com.lidesheng.hyperlyric.root.mediacard.island.layout.ios

import android.content.Context
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx

/**
 * Fixed geometry for the first Super Island custom layout.
 *
 * The ConstraintSet remains the source of truth for the player and dummy
 * holders.  Values that belong to SystemUI's current resource baseline are
 * resolved by the preset with a dp fallback instead of using raw pixels.
 */
internal object IslandExpandedMediaIosMetrics {
    const val ROOT_HEIGHT_DP = 168f
    // Keep the cover visually substantial.  The 168dp host absorbs its
    // reduced height through the surrounding vertical gaps instead.
    const val COVER_TOP_DP = 15f
    const val COVER_START_DP = 15f
    const val COVER_SIZE_DP = 55f
    const val UPPER_CONTENT_SHIFT_DP = 0f
    const val INFO_START_GAP_DP = 12f
    const val ARTIST_GAP_DP = 3f

    const val MUSIC_WAVE_SIZE_DP = 23f
    const val MUSIC_WAVE_TOP_DP = 24f
    const val MUSIC_WAVE_END_DP = 24f
    const val MUSIC_WAVE_INFO_GAP_DP = 6f

    const val PROGRESS_HEIGHT_DP = MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP
    const val PROGRESS_TOP_MARGIN_DP = 2f
    const val PROGRESS_TOP_MARGIN_WITHOUT_COVER_DP = 10f
    const val ACTION_MIN_GAP_AFTER_PROGRESS_DP = 2f
    const val ACTION_BOTTOM_MARGIN_OFFSET_DP = 1f
    const val ACTION_HEIGHT_DP = MediaLayoutSharedMetrics.NATIVE_ACTION_HEIGHT_FALLBACK_DP
    const val TIME_OUTER_MARGIN_DP = MediaLayoutSharedMetrics.IOS_TIME_OUTER_MARGIN_DP
    const val TIME_WIDTH_DP = MediaLayoutSharedMetrics.IOS_TIME_WIDTH_DP

    const val ACTION_SCALE = 1f
    const val DEVICE_SWITCH_SCALE = 0.9f
    const val ACTION_OUTER_MARGIN_DP = 6f

    fun rootHeightPx(context: Context): Int {
        return context.islandExpandedMediaDimenPx("expanded_max_height", ROOT_HEIGHT_DP)
    }
}
