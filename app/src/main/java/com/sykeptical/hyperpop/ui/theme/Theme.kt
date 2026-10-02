package com.sykeptical.hyperpop.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.sykeptical.hyperpop.ui.system.HyperPopColor
import com.sykeptical.hyperpop.ui.system.HyperPopSize
import com.sykeptical.hyperpop.ui.system.LocalReducedMotion
import com.sykeptical.hyperpop.ui.system.hyperPopReducedMotion
import com.sykeptical.hyperpop.ui.system.hyperPopTypography

private val DarkColors = darkColorScheme(
    primary = HyperPopColor.accent,
    onPrimary = HyperPopColor.onAccent,
    secondary = HyperPopColor.darkSecondary,
    background = HyperPopColor.darkBackground,
    onBackground = Color.White,
    surface = HyperPopColor.darkBackground,
    onSurface = Color.White,
    surfaceContainer = HyperPopColor.darkCard,
    surfaceContainerHigh = HyperPopColor.darkCardRaised,
    surfaceContainerLow = Color(0xFF161616),
    surfaceVariant = HyperPopColor.darkCard,
    onSurfaceVariant = HyperPopColor.darkSecondary,
    outline = HyperPopColor.darkTrack,
    outlineVariant = Color(0xFF2E2E2E),
    error = HyperPopColor.danger,
    onError = Color.White,
)

private val LightColors = lightColorScheme(
    primary = HyperPopColor.accent,
    onPrimary = HyperPopColor.onAccent,
    secondary = HyperPopColor.lightSecondary,
    background = HyperPopColor.lightBackground,
    onBackground = Color.Black,
    surface = HyperPopColor.lightBackground,
    onSurface = Color.Black,
    surfaceContainer = HyperPopColor.lightCard,
    surfaceContainerHigh = Color.White,
    surfaceContainerLow = Color(0xFFEEEEEE),
    surfaceVariant = HyperPopColor.lightCard,
    onSurfaceVariant = HyperPopColor.lightSecondary,
    outline = HyperPopColor.lightTrack,
    outlineVariant = Color(0xFFE6E6E6),
    error = HyperPopColor.danger,
    onError = Color.White,
)

private val HyperPopShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(HyperPopSize.radius),
    large = RoundedCornerShape(HyperPopSize.radius),
    extraLarge = RoundedCornerShape(HyperPopSize.sheetRadius),
)

@Composable
fun HyperPopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val reduced = hyperPopReducedMotion()
    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = hyperPopTypography(),
            shapes = HyperPopShapes,
            content = content
        )
    }
}
