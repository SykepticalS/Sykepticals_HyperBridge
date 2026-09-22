package com.d4viddf.hyperbridge.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarqueeMotionTest {
    @Test fun overflowDistanceStopsAtTheLastGlyphWithoutBlankSpace() {
        assertEquals(60f, MarqueeMotion.overflowDistance(260f, 200), 0.001f)
        assertEquals(0f, MarqueeMotion.overflowDistance(200f, 200), 0.001f)
        assertEquals(0f, MarqueeMotion.overflowDistance(180f, 200), 0.001f)
        assertEquals(0f, MarqueeMotion.overflowDistance(260f, 0), 0.001f)
        assertEquals(0f, MarqueeMotion.overflowDistance(200.8f, 200, tolerancePx = 1f), 0.001f)
        assertEquals(2f, MarqueeMotion.overflowDistance(202f, 200, tolerancePx = 1f), 0.001f)
    }

    @Test fun trailingInkGapDoesNotScrollTextThatAlreadyFits() {
        assertEquals(200f, MarqueeMotion.visibleTextWidth(208f, 200f, maxTrimPx = 48f), 0.001f)
        assertEquals(208f, MarqueeMotion.visibleTextWidth(208f, 40f, maxTrimPx = 48f), 0.001f)
        assertEquals(
            0f,
            MarqueeMotion.overflowDistance(
                textWidthPx = MarqueeMotion.visibleTextWidth(208f, 200f, maxTrimPx = 48f),
                availableWidthPx = 200,
                tolerancePx = 1f,
            ),
            0.001f,
        )
        assertEquals(
            40f,
            MarqueeMotion.overflowDistance(
                textWidthPx = MarqueeMotion.visibleTextWidth(260f, 240f, maxTrimPx = 48f),
                availableWidthPx = 200,
            ),
            0.001f,
        )
    }

    @Test fun endPaddingIsVisibleRoomNotABlankStop() {
        assertEquals(
            200,
            MarqueeMotion.visibleSlotWidth(
                textOrigin = 12,
                viewRight = 212,
                rightDrawableInset = 0,
                clipLeft = 0,
                clipRight = 212,
            ),
        )
        assertEquals(
            184,
            MarqueeMotion.visibleSlotWidth(
                textOrigin = 12,
                viewRight = 212,
                rightDrawableInset = 16,
                clipLeft = 0,
                clipRight = 212,
            ),
        )
    }

    @Test fun carouselGeometryMustRemainStableBeforeMarqueeStarts() {
        var signature: Int? = null
        var stableFrames = 0
        listOf(10, 20, 30, 30, 30, 30).forEachIndexed { attempt, current ->
            stableFrames = MarqueeMotion.nextStableFrames(signature, current, stableFrames)
            signature = current
            val expectedReady = attempt == 5
            assertEquals(
                expectedReady,
                MarqueeMotion.geometryReady(
                    attempt = attempt,
                    contentReady = true,
                    stableFrames = stableFrames,
                    minimumFrames = 2,
                    maximumFrames = 60,
                    requiredStableFrames = 3,
                ),
            )
        }
        assertTrue(
            MarqueeMotion.geometryReady(
                attempt = 60,
                contentReady = false,
                stableFrames = 0,
                minimumFrames = 2,
                maximumFrames = 60,
                requiredStableFrames = 3,
            ),
        )
    }

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
