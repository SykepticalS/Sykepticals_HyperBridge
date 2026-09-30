package com.d4viddf.hyperbridge.service.animation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetterAnimationsPolicyTest {
    @Test
    fun firstIslandUsesCenteredExit() {
        assertTrue(decide(activeIslandCount = 0))
    }

    @Test
    fun existingIslandPreservesXiaomiTarget() {
        assertFalse(decide(activeIslandCount = 1))
        assertFalse(decide(activeIslandCount = 2))
    }

    @Test
    fun disabledFeaturePreservesXiaomiTarget() {
        assertFalse(decide(enabled = false, activeIslandCount = 0))
    }

    @Test
    fun freeformAndInterruptedExitsStayNative() {
        assertFalse(decide(freeform = true, activeIslandCount = 0))
        assertFalse(decide(interrupted = true, activeIslandCount = 0))
    }

    @Test
    fun missingClosingIslandCannotForceACenterTarget() {
        assertFalse(decide(hasClosingIsland = false, activeIslandCount = 0))
    }

    private fun decide(
        enabled: Boolean = true,
        freeform: Boolean = false,
        interrupted: Boolean = false,
        hasClosingIsland: Boolean = true,
        activeIslandCount: Int,
    ) = BetterAnimationsPolicy.shouldUseCenteredExit(
        enabled = enabled,
        freeform = freeform,
        interrupted = interrupted,
        hasClosingIsland = hasClosingIsland,
        activeIslandCount = activeIslandCount,
    )
}
