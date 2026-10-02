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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** MiSans on HyperOS, because the system sans-serif family is MiSansVF. */
val MiSans = FontFamily.Default

object HyperPopSpace {
    val screen = 12.dp
    val groupGap = 20.dp
    val rowHorizontal = 16.dp
    val rowVertical = 14.dp
    val sectionTop = 18.dp
    val sectionBottom = 8.dp
}

object HyperPopSize {
    val radius: Dp = 16.dp
    val sheetRadius: Dp = 28.dp
    val buttonHeight: Dp = 50.dp
    val rowMin: Dp = 56.dp
    val icon: Dp = 22.dp
    val switchWidth: Dp = 52.dp
    val switchHeight: Dp = 32.dp
    val switchThumb: Dp = 26.dp
    val sliderHeight: Dp = 8.dp
    val sliderThumb: Dp = 22.dp
}

object HyperPopColor {
    val accent = Color(0xFF4B6FF6)
    val onAccent = Color.White
    val darkBackground = Color(0xFF000000)
    val darkCard = Color(0xFF242424)
    val darkCardRaised = Color(0xFF2C2C2C)
    val darkSecondary = Color(0xFF999999)
    val darkTrack = Color(0xFF3A3A3A)
    val lightBackground = Color(0xFFF5F5F5)
    val lightCard = Color(0xFFFFFFFF)
    val lightSecondary = Color(0xFF8A8A8A)
    val lightTrack = Color(0xFFE4E4E4)
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

val HyperPopType = HyperPopTypeScale(
    display = role(40, 48, FontWeight.Medium),
    largeTitle = role(32, 40, FontWeight.Medium),
    title = role(20, 28, FontWeight.Medium),
    section = role(13, 18, FontWeight.Normal),
    body = role(16, 22, FontWeight.Normal),
    secondary = role(14, 20, FontWeight.Normal),
    settingLabel = role(17, 22, FontWeight.Normal),
    settingDescription = role(13, 18, FontWeight.Normal),
    button = role(17, 22, FontWeight.Medium),
    caption = role(12, 16, FontWeight.Normal),
    numeric = role(15, 20, FontWeight.Medium),
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
