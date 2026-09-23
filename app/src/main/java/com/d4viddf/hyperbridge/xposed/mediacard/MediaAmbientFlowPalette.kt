package com.d4viddf.hyperbridge.xposed.mediacard

import android.graphics.Bitmap
import com.d4viddf.hyperbridge.xposed.mediacard.compat.ColorExtractor

internal data class MediaAmbientFlowPalette(
    val mainColor: Int,
    val colors: IntArray
)

internal object MediaAmbientFlowPaletteExtractor {
    fun extractCoverMainColor(bitmap: Bitmap): Int? =
        ColorExtractor.extractThemePalette(bitmap, 3).onBlackBackground.firstOrNull()
}
