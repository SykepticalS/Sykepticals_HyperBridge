/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import android.graphics.LinearGradient
import android.graphics.Shader

internal object LyricGradientShader {
    // Keep the dominant cover color present a little longer without turning the gradient into
    // separate color blocks.
    private const val PRIMARY_COLOR_HOLD_FRACTION = 0.18f

    fun create(
        requestedStartX: Float,
        requestedEndX: Float,
        colors: IntArray
    ): LinearGradient {
        require(colors.size >= 2)

        val shaderColors = IntArray(colors.size + 1)
        val positions = FloatArray(colors.size + 1)
        shaderColors[0] = colors[0]
        shaderColors[1] = colors[0]
        positions[0] = 0f
        positions[1] = PRIMARY_COLOR_HOLD_FRACTION

        val remainingSpan = 1f - PRIMARY_COLOR_HOLD_FRACTION
        for (index in 1 until colors.size) {
            shaderColors[index + 1] = colors[index]
            positions[index + 1] = PRIMARY_COLOR_HOLD_FRACTION +
                remainingSpan * index / (colors.size - 1)
        }

        val startX = requestedStartX.takeIf { it.isFinite() } ?: 0f
        val finiteEndX = requestedEndX.takeIf { it.isFinite() } ?: startX
        val endX = if (finiteEndX > startX) finiteEndX else startX + 1f

        return LinearGradient(
            startX,
            0f,
            endX,
            0f,
            shaderColors,
            positions,
            Shader.TileMode.CLAMP
        )
    }
}
