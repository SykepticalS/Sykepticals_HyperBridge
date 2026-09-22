package com.lidesheng.hyperlyric.root.mediacard.island.layout.coloros

import android.content.Context
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx

/** Geometry for ColorOS-style content inside the Super Island player. */
internal object IslandExpandedMediaColorOsMetrics {
    const val ROOT_HEIGHT_DP = 168f
    const val COVER_SIZE_DP = 72f
    const val COVER_TOP_DP = 16f
    const val COVER_START_DP = 16f
    const val CONTENT_GAP_DP = 10f
    const val CONTENT_END_DP = 16f

    const val TITLE_TOP_DP = 16f
    const val ARTIST_GAP_DP = 3f
    const val APP_ICON_SIZE_DP = 22f
    const val APP_ICON_TOP_DP = 16f
    const val APP_ICON_END_DP = 16f
    const val APP_ICON_TEXT_GAP_DP = 8f

    const val PROGRESS_HEIGHT_DP = MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP
    // The island's shorter vertical budget allows the time labels to sit one
    // dp deeper into the progress container than the notification card.
    const val PROGRESS_OVERLAP_DP = 10f
    const val TIME_WIDTH_DP = MediaLayoutSharedMetrics.STANDARD_TIME_WIDTH_DP
    const val ACTION_BOTTOM_DP = 10f

    fun rootHeightPx(context: Context): Int {
        return context.islandExpandedMediaDimenPx("expanded_max_height", ROOT_HEIGHT_DP)
    }
}
