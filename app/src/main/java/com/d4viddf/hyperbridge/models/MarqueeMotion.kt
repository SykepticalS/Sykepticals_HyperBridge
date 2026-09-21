package com.d4viddf.hyperbridge.models

import kotlin.math.roundToLong

/** Pure timing/position policy for the compact island marquee's fast return leg. */
object MarqueeMotion {
    private const val RETURN_SPEED_MULTIPLIER = 4f
    private const val MIN_RETURN_MS = 180L
    private const val MAX_RETURN_MS = 520L

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
