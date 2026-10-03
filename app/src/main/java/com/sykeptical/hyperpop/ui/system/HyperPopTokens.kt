package com.sykeptical.hyperpop.ui.system

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sykeptical.hyperpop.R
import java.io.File

/**
 * Settings typeface used by HyperOS.
 *
 * MiuiSystemUI bundles MiSans-compatible text metrics. On this phone the variable
 * face is `/system/fonts/MiSansVF.ttf`. Barlow from `docs/` is the packaged
 * fallback so a build machine does not silently render Roboto.
 */
val MiSans: FontFamily by lazy { loadHyperPopFontFamily() }

private fun loadHyperPopFontFamily(): FontFamily {
    val misans = File("/system/fonts/MiSansVF.ttf")
    if (misans.exists()) {
        runCatching {
            fun face(weight: Int) = Font(
                file = misans,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
            return FontFamily(face(400), face(500), face(600))
        }
    }
    return runCatching {
        FontFamily(Font(R.font.barlow_thin, FontWeight.Normal))
    }.getOrDefault(FontFamily.Default)
}

/**
 * Spacing taken from MiuiSystemUI `miuix_*` dimens
 * (OS3.0.305.0.WPATRXM, `MiuiSystemUI.apk`).
 */
object HyperPopSpace {
    /** `miuix_theme_padding_horizontal_common` */
    val screen = 12.dp
    /** `miuix_preference_category_divider_gap_height` */
    val groupGap = 16.dp
    /** `miuix_theme_content_padding_horizontal_common` */
    val rowHorizontal = 16.dp
    /** `miuix_preference_item_padding_top` / bottom */
    val rowVertical = 14.dp
    /** `miuix_appcompat_action_bar_title_horizontal_padding` */
    val titleInset = 26.dp
    /** `miuix_preference_category_vertical_padding` */
    val sectionTop = 16.dp
    val sectionBottom = 8.dp
}

/**
 * Control geometry from `SlidingButtonHelper.initResource` and
 * `miuix.androidbasewidget.widget.SeekBar` in MiuiSystemUI.
 */
object HyperPopSize {
    /** `miuix_theme_radius_common` — measured card corner is the same 16dp. */
    val radius: Dp = 16.dp
    /** `miuix_theme_radius_big` */
    val sheetRadius: Dp = 36.dp
    /** `miuix_theme_radius_small` */
    val controlRadius: Dp = 12.dp
    /** `miuix_theme_radius_tiny` */
    val iconRadius: Dp = 8.dp
    val buttonHeight: Dp = 50.dp
    /** `miuix_preference_item_min_height` */
    val rowMin: Dp = 56.dp
    val icon: Dp = 22.dp
    val iconTile: Dp = 32.dp
    /**
     * Sliding button: width 49dp, frame height 28dp + 2dp vertical padding each side.
     * Thumb is `sliding_button_slider_size` 20dp, inset by 4dp.
     */
    val switchWidth: Dp = 49.dp
    val switchHeight: Dp = 32.dp
    val switchThumb: Dp = 20.dp
    val switchThumbInset: Dp = 4.dp
    /** `miuix_appcompat_seekbar_height` */
    val sliderHeight: Dp = 28.dp
    val sliderThumb: Dp = 20.dp
}

object HyperPopColor {
    /** `miuix_color_blue_light_primary_default`, also the discarded `#FF3482FF` in SlidingButtonHelper. */
    val accent = Color(0xFF3482FF)
    /** `miuix_color_blue_dark_primary_default` */
    val accentDark = Color(0xFF277AF7)
    val onAccent = Color.White
    val darkBackground = Color(0xFF000000)
    /** Opaque settings group fill measured on this phone's Settings app. */
    val darkCard = Color(0xFF242424)
    val darkCardRaised = Color(0xFF2C2C2C)
    /** `miuix_color_white_level3` — on-surface secondary, dark. */
    val darkSecondary = Color(0xCCFFFFFF)
    /** `miuix_color_white_level7` — sliding button bar off, dark. */
    val darkTrack = Color(0x33FFFFFF)
    val lightBackground = Color(0xFFF7F7F7)
    val lightCard = Color(0xFFFFFFFF)
    /** `miuix_color_black_level3` */
    val lightSecondary = Color(0xCC000000)
    /** `miuix_color_black_level7` — sliding button bar off, light. */
    val lightTrack = Color(0x1A000000)
    val warning = Color(0xFFE6A23C)
    val danger = Color(0xFFE15D5D)
    val success = Color(0xFF3DDC84)
}

object HyperPopMotion {
    const val page = 420
    const val control = 180
    const val sheet = 320
    const val highlight = 1400
}

@Immutable
data class HyperPopTypeScale(
    val display: TextStyle,
    val largeTitle: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val secondary: TextStyle,
    val settingLabel: TextStyle,
    val settingDescription: TextStyle,
    val button: TextStyle,
    val caption: TextStyle,
    val numeric: TextStyle,
)

private fun role(
    size: Int,
    line: Int,
    weight: FontWeight,
) = TextStyle(
    fontFamily = MiSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

/**
 * Sizes are `miuix_font_size_*` from MiuiSystemUI:
 * title1 32, title2 24, title3 20, headline1 17, body1 16, subtitle 14, footnote1 13, button 17.
 */
val HyperPopType = HyperPopTypeScale(
    display = role(32, 40, FontWeight.Medium),
    largeTitle = role(32, 40, FontWeight.Medium),
    title = role(20, 26, FontWeight.Medium),
    section = role(14, 18, FontWeight.Normal),
    body = role(16, 22, FontWeight.Normal),
    secondary = role(14, 20, FontWeight.Normal),
    settingLabel = role(17, 22, FontWeight.Normal),
    settingDescription = role(14, 18, FontWeight.Normal),
    button = role(17, 22, FontWeight.Medium),
    caption = role(13, 16, FontWeight.Normal),
    numeric = role(14, 18, FontWeight.Medium),
)

fun hyperPopTypography(): Typography = Typography(
    displayLarge = HyperPopType.display,
    displayMedium = HyperPopType.largeTitle,
    headlineLarge = HyperPopType.largeTitle,
    headlineMedium = HyperPopType.title,
    headlineSmall = HyperPopType.title,
    titleLarge = HyperPopType.title,
    titleMedium = HyperPopType.settingLabel,
    titleSmall = HyperPopType.section,
    bodyLarge = HyperPopType.body,
    bodyMedium = HyperPopType.secondary,
    bodySmall = HyperPopType.settingDescription,
    labelLarge = HyperPopType.button,
    labelMedium = HyperPopType.caption,
    labelSmall = HyperPopType.caption,
)

@Composable
fun hyperPopReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}

fun motionMillis(base: Int, reduced: Boolean): Int = if (reduced) 0 else base

val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun hyperPopIsDark(): Boolean = isSystemInDarkTheme()

@Composable
fun hyperPopAccent(): Color = if (hyperPopIsDark()) HyperPopColor.accentDark else HyperPopColor.accent
