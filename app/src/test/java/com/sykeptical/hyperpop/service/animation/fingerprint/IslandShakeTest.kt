package com.sykeptical.hyperpop.service.animation.fingerprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IslandShakeTest {
    @Test
    fun shakeEndsAtZeroAndStaysInsideTheClampedAmplitude() {
        val hole = FingerprintIslandGeometry.Rect(400f, 30f, 470f, 100f)
        val layout = FingerprintIslandGeometry.layout(hole, 102f, 3f)
        val amplitude = layout.shakeAmplitudePx
        assertEquals(0f, IslandShake.offsets(0L, amplitude, true).platePx, 0.001f)
        assertEquals(0f, IslandShake.offsets(IslandShake.DURATION_MS, amplitude, true).platePx, 0.001f)
        assertEquals(0f, IslandShake.offsets(100L, amplitude, animationsEnabled = false).platePx, 0.001f)
        var peak = 0f
        var elapsed = 0L
        while (elapsed <= IslandShake.DURATION_MS) {
            val offsets = IslandShake.offsets(elapsed, amplitude, true)
            assertTrue(abs(offsets.platePx) <= amplitude + 0.001f)
            assertEquals(offsets.platePx * IslandShake.GLYPH_FACTOR, offsets.glyphPx, 0.001f)
            val plate = FingerprintIslandGeometry.Rect(
                layout.square.left + offsets.platePx,
                layout.square.top,
                layout.square.right + offsets.platePx,
                layout.square.bottom,
            )
            assertTrue(plate.contains(hole, layout.holeMarginPx))
            peak = maxOf(peak, abs(offsets.platePx))
            elapsed += 15L
        }
        assertTrue(peak > 0f)
    }
}
