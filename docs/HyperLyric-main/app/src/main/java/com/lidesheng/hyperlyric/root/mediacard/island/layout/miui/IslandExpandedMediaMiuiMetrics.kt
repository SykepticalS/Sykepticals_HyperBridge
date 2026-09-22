package com.lidesheng.hyperlyric.root.mediacard.island.layout.miui

import android.content.Context
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx

/** Geometry for the information-first MIUI island player. */
internal object IslandExpandedMediaMiuiMetrics {
    const val ROOT_HEIGHT_DP = 168f
    const val HORIZONTAL_MARGIN_DP = 21f
    const val APP_NAME_TOP_DP = HORIZONTAL_MARGIN_DP
    const val TITLE_TOP_DP = 27f
    const val ARTIST_GAP_DP = 1f

    const val ACTION_BUTTON_SCALE = MediaLayoutSharedMetrics.COMPACT_ACTION_SCALE
    const val ACTION_START_DP = 12f
    const val ACTION_TOP_DP = 3f
    const val ACTION_GAP_DP = 2f

    const val DEVICE_SIZE_DP = 34f
    const val PROGRESS_HEIGHT_DP = MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP
    const val PROGRESS_OVERLAP_DP = 10f
    const val PROGRESS_BOTTOM_FALLBACK_DP = 10f

    const val TIME_TEXT_SIZE_SP = 8f
    const val APP_NAME_TEXT_SIZE_SP = 10f

    fun rootHeightPx(context: Context): Int {
        return context.islandExpandedMediaDimenPx("expanded_max_height", ROOT_HEIGHT_DP)
    }
}
