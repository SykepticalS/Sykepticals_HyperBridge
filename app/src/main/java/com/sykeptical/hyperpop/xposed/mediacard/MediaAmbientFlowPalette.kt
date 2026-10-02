package com.sykeptical.hyperpop.xposed.mediacard

import android.graphics.Bitmap
import com.sykeptical.hyperpop.xposed.mediacard.compat.ColorExtractor

internal data class MediaAmbientFlowPalette(
    val mainColor: Int,
    val colors: IntArray
)

internal object MediaAmbientFlowPaletteExtractor {
    fun extractCoverMainColor(bitmap: Bitmap): Int? =
        ColorExtractor.extractThemePalette(bitmap, 3).onBlackBackground.firstOrNull()
}
