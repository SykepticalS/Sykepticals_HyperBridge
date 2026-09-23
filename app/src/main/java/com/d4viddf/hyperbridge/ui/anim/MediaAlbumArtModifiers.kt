package com.d4viddf.hyperbridge.ui.anim

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/** 3D album flip used by HyperOS media cards, including the back-face correction. */
fun Modifier.albumArtFlip(
    rotationYValue: Float,
    enableBlur: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    shadowElevation: Dp = 8.dp,
): Modifier = this.graphicsLayer {
    clip = true
    this.shape = shape
    this.shadowElevation = shadowElevation.toPx()
    val normalizedRotation = (rotationYValue % 360f).let { if (it < 0f) it + 360f else it }
    val isBack = normalizedRotation in 90f..270f
    rotationY = rotationYValue
    scaleX = if (isBack) -1f else 1f
    cameraDistance = 3f * density
    if (enableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val rawSin = abs(sin(Math.toRadians(normalizedRotation.toDouble()))).toFloat()
        val blurIntensity = (rawSin - 0.15f).coerceAtLeast(0f) * 40f
        if (blurIntensity > 0.5f) {
            renderEffect = RenderEffect.createBlurEffect(
                blurIntensity,
                blurIntensity,
                Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        }
    }
}
