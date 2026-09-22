package com.lidesheng.hyperlyric.ui.page.hooksettings.media.preview

import androidx.compose.ui.graphics.Color

internal data class PreviewMediaColorConfig(
    val textPrimary: Color,
    val textSecondary: Color,
    val backgroundStart: Color,
    val backgroundEnd: Color
)

internal object PreviewMediaStyleConfig {

    fun getColorConfig(
        trackIndex: Int,
        style: Int,
        isDarkTheme: Boolean = false
    ): PreviewMediaColorConfig {
        return when (style) {
            1 -> { // Cover Art / Collage
                if (trackIndex == 0) {
                    PreviewMediaColorConfig(
                        Color(0xffffede5),
                        Color(0xffffede5),
                        Color(0xff8e4d27),
                        Color(0xff8e4d27)
                    )
                } else {
                    PreviewMediaColorConfig(
                        Color(0xffffede8),
                        Color(0xffffede8),
                        Color(0xff96482e),
                        Color(0xff96482e)
                    )
                }
            }

            2 -> { // Blurred Cover
                if (trackIndex == 0) {
                    PreviewMediaColorConfig(
                        Color(0xfffffbff),
                        Color(0xfff5ded2),
                        Color(0xff5f402d),
                        Color(0xff70370f)
                    )
                } else {
                    PreviewMediaColorConfig(
                        Color(0xfffffbff),
                        Color(0xfff6ddd7),
                        Color(0xff603e36),
                        Color(0xff733424)
                    )
                }
            }

            3 -> { // Radial Gradient
                if (trackIndex == 0) {
                    PreviewMediaColorConfig(
                        Color(0xfffffbff),
                        Color(0xfff5ded3),
                        Color(0xff5f3f2e),
                        Color(0xff703711)
                    )
                } else {
                    PreviewMediaColorConfig(
                        Color(0xfffffbff),
                        Color(0xfff8ddd5),
                        Color(0xff643d30),
                        Color(0xff793017)
                    )
                }
            }

            4 -> { // Linear Gradient
                if (trackIndex == 0) {
                    PreviewMediaColorConfig(
                        Color(0xffffede5),
                        Color(0xffffede5),
                        Color(0xff8e4e26),
                        Color(0xff8e4e26)
                    )
                } else {
                    PreviewMediaColorConfig(
                        Color(0xffffede8),
                        Color(0xffffede8),
                        Color(0xff97472e),
                        Color(0xff97472e)
                    )
                }
            }

            5 -> { // Soft Cover
                if (isDarkTheme) {
                    PreviewMediaColorConfig(
                        Color(0xffffffff),
                        Color(0xccffffff),
                        Color(0xff121316),
                        Color(0xff121316)
                    )
                } else {
                    PreviewMediaColorConfig(
                        Color(0xff1d1d1f),
                        Color(0xa61d1d1f),
                        Color(0xfff3f3f5),
                        Color(0xfff3f3f5)
                    )
                }
            }

            else -> { // Default Style (0) - Managed by MediaPreviewCard logic directly
                PreviewMediaColorConfig(
                    Color.White,
                    Color.White,
                    Color.Transparent,
                    Color.Transparent
                )
            }
        }
    }
}
