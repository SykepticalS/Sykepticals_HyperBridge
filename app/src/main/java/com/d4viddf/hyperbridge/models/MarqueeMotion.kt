package com.d4viddf.hyperbridge.models

import kotlin.math.roundToLong

/** Pure timing/position policy for the compact island marquee's fast return leg. */
object MarqueeMotion {
    private const val RETURN_SPEED_MULTIPLIER = 4f
    private const val MIN_RETURN_MS = 180L
    private const val MAX_RETURN_MS = 520L

    fun overflowDistance(
        textWidthPx: Float,
        availableWidthPx: Int,
        tolerancePx: Float = 0f,
    ): Float {
        if (textWidthPx <= 0f || availableWidthPx <= 0) return 0f
        val overflow = (textWidthPx - availableWidthPx.toFloat()).coerceAtLeast(0f)
        return overflow.takeIf { it > tolerancePx.coerceAtLeast(0f) } ?: 0f
    }

    /** Tracks consecutive identical viewport samples while Xiaomi's carousel spring settles. */
    fun nextStableFrames(
        previousSignature: Int?,
        currentSignature: Int?,
        previousStableFrames: Int,
    ): Int {
        if (currentSignature == null) return 0
        return if (currentSignature == previousSignature) previousStableFrames + 1 else 0
    }

    fun geometryReady(
        attempt: Int,
        contentReady: Boolean,
        stableFrames: Int,
        minimumFrames: Int,
        maximumFrames: Int,
        requiredStableFrames: Int,
    ): Boolean = attempt >= maximumFrames ||
        (attempt >= minimumFrames && contentReady && stableFrames >= requiredStableFrames)

    fun returnDurationMs(distancePx: Float, forwardSpeedPxPerSec: Int): Long {
        if (distancePx <= 0f) return MIN_RETURN_MS
        val speed = forwardSpeedPxPerSec.coerceIn(20, 500)
        return (distancePx / (speed * RETURN_SPEED_MULTIPLIER) * 1000f)
            .roundToLong()
            .coerceIn(MIN_RETURN_MS, MAX_RETURN_MS)
    }

    fun returnOffset(maxScrollPx: Float, elapsedMs: Long, durationMs: Long): Float {
        if (maxScrollPx <= 0f) return 0f
        val progress = (elapsedMs.toFloat() / durationMs.coerceAtLeast(1L))
            .coerceIn(0f, 1f)
        // Smoothstep accelerates quickly, then eases into the exact starting position.
        val eased = progress * progress * (3f - 2f * progress)
        return maxScrollPx * (1f - eased)
    }
}
