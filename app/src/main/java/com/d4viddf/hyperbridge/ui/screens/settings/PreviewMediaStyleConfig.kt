package com.d4viddf.hyperbridge.ui.screens.settings

import androidx.compose.ui.graphics.Color

internal data class PreviewMediaColorConfig(
    val textPrimary: Color,
    val backgroundStart: Color,
    val backgroundEnd: Color,
)

internal object PreviewMediaStyleConfig {
    fun getColorConfig(trackIndex: Int, style: Int, isDarkTheme: Boolean = false): PreviewMediaColorConfig {
        return when (style) {
            1 -> if (trackIndex == 0) {
                PreviewMediaColorConfig(Color(0xFFFFEDE5), Color(0xFF8E4D27), Color(0xFF8E4D27))
            } else {
                PreviewMediaColorConfig(Color(0xFFFFEDE8), Color(0xFF96482E), Color(0xFF96482E))
            }
            2 -> if (trackIndex == 0) {
                PreviewMediaColorConfig(Color(0xFFFFFBFF), Color(0xFF5F402D), Color(0xFF70370F))
            } else {
                PreviewMediaColorConfig(Color(0xFFFFFBFF), Color(0xFF603E36), Color(0xFF733424))
            }
            3 -> if (trackIndex == 0) {
                PreviewMediaColorConfig(Color(0xFFFFFBFF), Color(0xFF5F3F2E), Color(0xFF703711))
            } else {
                PreviewMediaColorConfig(Color(0xFFFFFBFF), Color(0xFF643D30), Color(0xFF793017))
            }
            4 -> if (trackIndex == 0) {
                PreviewMediaColorConfig(Color(0xFFFFEDE5), Color(0xFF8E4E26), Color(0xFF8E4E26))
            } else {
                PreviewMediaColorConfig(Color(0xFFFFEDE8), Color(0xFF97472E), Color(0xFF97472E))
            }
            5 -> if (isDarkTheme) {
                PreviewMediaColorConfig(Color.White, Color(0xFF121316), Color(0xFF121316))
            } else {
                PreviewMediaColorConfig(Color(0xFF1D1D1F), Color(0xFFF3F3F5), Color(0xFFF3F3F5))
            }
            else -> PreviewMediaColorConfig(Color.White, Color.Transparent, Color.Transparent)
        }
    }
}
