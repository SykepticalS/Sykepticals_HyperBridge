package com.d4viddf.hyperbridge.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarqueeMotionTest {
    @Test fun returnMotionRunsBackwardsAndLandsExactlyAtStart() {
        val duration = MarqueeMotion.returnDurationMs(distancePx = 240f, forwardSpeedPxPerSec = 100)
        val samples = (0..10).map { step ->
            MarqueeMotion.returnOffset(240f, duration * step / 10, duration)
        }

        assertEquals(240f, samples.first(), 0.001f)
        assertEquals(0f, samples.last(), 0.001f)
        assertTrue(samples.zipWithNext().all { (first, second) -> second <= first })
    }

    @Test fun returnDurationIsFastAndBounded() {
        assertEquals(180L, MarqueeMotion.returnDurationMs(distancePx = 1f, forwardSpeedPxPerSec = 100))
        assertEquals(520L, MarqueeMotion.returnDurationMs(distancePx = 10_000f, forwardSpeedPxPerSec = 20))
        assertTrue(MarqueeMotion.returnDurationMs(distancePx = 240f, forwardSpeedPxPerSec = 100) < 2_400L)
    }
}
