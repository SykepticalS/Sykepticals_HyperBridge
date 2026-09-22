package com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui

import android.content.Context
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx

/** Geometry for the compact, information-first One UI island player. */
internal object IslandExpandedMediaOneUiMetrics {
    const val ROOT_HEIGHT_DP = 168f
    const val HORIZONTAL_MARGIN_DP = 26f
    const val IDENTITY_TOP_DP = 18f
    const val IDENTITY_HEIGHT_DP = 18f
    const val TITLE_GAP_DP = 8f
    const val ARTIST_GAP_DP = 1f

    const val PROGRESS_HEIGHT_DP = MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP
    const val PROGRESS_OVERLAP_DP = 10f

    // One UI is already compact in the notification card; the smaller island
    // host needs a 0.75 visual action scale to avoid a
    // crowded action row while retaining the existing action-slot layout.
    const val ACTION_BUTTON_SCALE = 0.75f
    const val ACTION_GAP_DP = 4f
    // Keep the bottom edge consistent with the notification implementation;
    // 8dp made the compact action row sit too close to the island edge.
    const val ACTION_BOTTOM_DP = 12f

    const val APP_ICON_SIZE_DP = 16f
    const val APP_NAME_GAP_DP = 6f
    const val APP_NAME_TEXT_SIZE_SP = 12f
    const val APP_NAME_ALPHA = 0.65f

    fun rootHeightPx(context: Context): Int {
        return context.islandExpandedMediaDimenPx("expanded_max_height", ROOT_HEIGHT_DP)
    }
}
