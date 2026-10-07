package com.sykeptical.hyperpop.service.animation.fingerprint

import kotlin.math.max
import kotlin.math.min

/**
 * Lock pill, square, glyph, and shake amplitude as ratios of the compact
 * island height and the camera hole. No device coordinates.
 */
object FingerprintIslandGeometry {
    const val SQUARE_TO_HEIGHT = 3.2f
    const val RADIUS_TO_SIDE = 0.34f
    const val SHAKE_TO_SIDE = 0.05f
    const val HOLE_MARGIN_DP = 2f
    const val PLATE_STROKE_DP = 1.4f
    const val ISLAND_RADIUS_DP = 30f
    const val PLATE_COLOR = 0xFF000000.toInt()
    const val PLATE_STROKE_COLOR = 0x1FFFFFFF
    const val RIDGE_WHITE = 0xFFFFFFFF.toInt()
    const val WINDOW_PAD_DP = 8f

    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f

        fun contains(other: Rect, margin: Float): Boolean =
            left + margin <= other.left &&
                top + margin <= other.top &&
                right - margin >= other.right &&
                bottom - margin >= other.bottom
    }

    data class Layout(
        val hole: Rect,
        val pill: Rect,
        val square: Rect,
        val pillRadius: Float,
        val squareRadius: Float,
        val glyph: Rect,
        val padlock: Rect,
        val shakeAmplitudePx: Float,
        val holeMarginPx: Float,
        val strokePx: Float,
        val strokeColor: Int,
    )

    fun layout(
        hole: Rect,
        islandHeightPx: Float,
        density: Float,
        islandRadiusPx: Float = ISLAND_RADIUS_DP * density.coerceAtLeast(0.5f),
        strokePx: Float = PLATE_STROKE_DP * density.coerceAtLeast(0.5f),
        strokeColor: Int = PLATE_STROKE_COLOR,
    ): Layout {
        val height = islandHeightPx.coerceAtLeast(1f)
        val densityScale = density.coerceAtLeast(0.5f)
        val margin = HOLE_MARGIN_DP * densityScale
        val pillHeight = height
        val lock = min(pillHeight * 0.46f, (pillHeight - margin * 2f).coerceAtLeast(0f))
        val rightEdge = hole.right + margin + lock
        val leftEdge = hole.left - margin
        val half = max(
            max(hole.centerX - leftEdge, rightEdge - hole.centerX),
            pillHeight / 2f,
        )
        val pill = Rect(
            left = hole.centerX - half,
            top = hole.centerY - pillHeight / 2f,
            right = hole.centerX + half,
            bottom = hole.centerY + pillHeight / 2f,
        )
        val padlock = Rect(
            left = hole.right + margin,
            top = pill.centerY - lock / 2f,
            right = hole.right + margin + lock,
            bottom = pill.centerY + lock / 2f,
        )
        val glyphMin = max(hole.width, height)
        val side = max(SQUARE_TO_HEIGHT * height, hole.height + glyphMin + margin * 3f)
        val square = Rect(
            left = hole.centerX - side / 2f,
            top = hole.top - margin,
            right = hole.centerX + side / 2f,
            bottom = hole.top - margin + side,
        )
        val glyphTop = hole.bottom + margin
        val glyphBottom = square.bottom - margin
        val glyphSize = min(glyphBottom - glyphTop, square.width - margin * 2f).coerceAtLeast(0f)
        val glyphLeft = (hole.centerX - glyphSize / 2f)
            .coerceIn(square.left + margin, square.right - margin - glyphSize)
        val glyph = Rect(glyphLeft, glyphTop, glyphLeft + glyphSize, glyphTop + glyphSize)
        val slack = min(
            hole.left - margin - square.left,
            square.right - hole.right - margin,
        ).coerceAtLeast(0f)
        val amplitude = min(SHAKE_TO_SIDE * side, slack)
        return Layout(
            hole = hole,
            pill = pill,
            square = square,
            pillRadius = min(pillHeight / 2f, islandRadiusPx.coerceAtLeast(0f)),
            squareRadius = RADIUS_TO_SIDE * side,
            glyph = glyph,
            padlock = padlock,
            shakeAmplitudePx = amplitude,
            holeMarginPx = margin,
            strokePx = strokePx.coerceAtLeast(0f),
            strokeColor = strokeColor,
        )
    }

    fun reservedHeightPx(result: Layout, phase: FingerprintPhase, density: Float): Int {
        val pad = WINDOW_PAD_DP * density.coerceAtLeast(0.5f)
        val bottom = when (phase) {
            FingerprintPhase.Hidden -> return 0
            FingerprintPhase.LockPill -> result.pill.bottom
            else -> max(result.square.bottom, result.pill.bottom)
        }
        return (bottom + pad).toInt().coerceAtLeast(0)
    }
}
