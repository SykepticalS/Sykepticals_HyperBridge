package com.d4viddf.hyperbridge.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedProgressAnimationPolicyTest {
    @Test fun animatesForwardProgress() {
        assertTrue(
            ExpandedProgressAnimationPolicy.shouldAnimate(
                fromProgress = 20,
                targetProgress = 35,
                maxProgress = 100,
            )
        )
    }

    @Test fun rejectsFirstBindResetsAndInvalidRanges() {
        assertFalse(ExpandedProgressAnimationPolicy.shouldAnimate(null, 20, 100))
        assertFalse(ExpandedProgressAnimationPolicy.shouldAnimate(40, 10, 100))
        assertFalse(ExpandedProgressAnimationPolicy.shouldAnimate(10, 20, 0))
        assertFalse(ExpandedProgressAnimationPolicy.shouldAnimate(101, 110, 100))
    }

    @Test fun convertsProgressToDrawableLevelWithoutOverflow() {
        assertEquals(0, ExpandedProgressAnimationPolicy.drawableLevel(-5, 100))
        assertEquals(2_500, ExpandedProgressAnimationPolicy.drawableLevel(25, 100))
        assertEquals(10_000, ExpandedProgressAnimationPolicy.drawableLevel(150, 100))
        assertEquals(0, ExpandedProgressAnimationPolicy.drawableLevel(50, 0))
    }

    @Test fun historySharesOnePreviousValueAcrossRealAndFakeBinds() {
        val history = ExpandedProgressHistory()
        assertNull(history.observe("download:1", updateId = 1L, targetProgress = 10))
        assertNull(history.observe("download:1", updateId = 1L, targetProgress = 10))
        assertEquals(10, history.observe("download:1", updateId = 2L, targetProgress = 30))
        assertEquals(10, history.observe("download:1", updateId = 2L, targetProgress = 30))
        assertEquals(30, history.observe("download:1", updateId = 3L, targetProgress = 55))
    }

    @Test fun historyDoesNotCrossSourceOwnership() {
        val history = ExpandedProgressHistory()
        assertNull(history.observe("download:a", 1L, 25))
        assertNull(history.observe("download:b", 1L, 70))
        assertEquals(25, history.observe("download:a", 2L, 40))
        assertEquals(70, history.observe("download:b", 2L, 80))
    }
}
