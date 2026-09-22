package com.lidesheng.hyperlyric.ui.anim

/**
 * Applies a 3D flip animation effect to the album art, mimicking MIUI's SystemUI behavior.
 * 
 * @param rotationYValue The current rotation degree along the Y-axis.
 * @param enableBlur Whether to apply dynamic motion blur during the flip (requires Android 12+ usually, but Compose handles the fallback).
 */
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

fun Modifier.albumArtFlip(
    rotationYValue: Float,
    enableBlur: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    shadowElevation: Dp = 8.dp
): Modifier = this.graphicsLayer {
    clip = true
    this.shape = shape
    this.shadowElevation = shadowElevation.toPx()

    // Normalize rotation to [0, 360) to determine if we are looking at the "back" of the view
    val normalizedRotation = (rotationYValue % 360).let { if (it < 0) it + 360 else it }

    // When the rotation is between 90 and 270 degrees, the view is facing backwards.
    val isBack = normalizedRotation in 90f..270f

    // Apply 3D Y-axis rotation
    rotationY = rotationYValue

    // Mirror the view when looking at its back, so the image content isn't reversed (fixes text reading backwards)
    scaleX = if (isBack) -1f else 1f

    // Enhance 3D perspective distance for a deeper flip effect
    cameraDistance = 3 * density

    // Apply dynamic motion blur based on Folme's sin-wave calculation.
    if (enableBlur && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val rawSin = abs(sin(Math.toRadians(normalizedRotation.toDouble()))).toFloat()
        val blurIntensity = (rawSin - 0.15f).coerceAtLeast(0f) * 40f

        if (blurIntensity > 0.5f) {
            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                blurIntensity, blurIntensity, android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    }
}
