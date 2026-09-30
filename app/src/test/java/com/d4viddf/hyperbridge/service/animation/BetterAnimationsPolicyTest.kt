package com.d4viddf.hyperbridge.service.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BetterAnimationsPolicyTest {
    @Test
    fun firstIslandUsesCenteredExit() {
        assertTrue(decide(activeIslandCount = 0, hasCurrentBigIsland = false))
    }

    @Test
    fun higherPriorityHandoffUsesCenteredExit() {
        assertTrue(
            decide(
                activeIslandCount = 1,
                hasCurrentBigIsland = true,
                nativeTargetSlot = AppExitTargetSlot.PRIMARY,
            ),
        )
    }

    @Test
    fun lowerPriorityClosingIslandPreservesNativeSecondaryTarget() {
        assertFalse(
            decide(
                activeIslandCount = 1,
                hasCurrentBigIsland = true,
                nativeTargetSlot = AppExitTargetSlot.SECONDARY,
            ),
        )
    }

    @Test
    fun showOnceCutoutTargetPreservesNativeBehavior() {
        assertFalse(
            decide(
                activeIslandCount = 1,
                hasCurrentBigIsland = true,
                nativeTargetSlot = AppExitTargetSlot.CUTOUT,
            ),
        )
    }

    @Test
    fun higherPriorityHandoffWithTwoExistingIslandsUsesCenteredExit() {
        assertTrue(
            decide(
                activeIslandCount = 2,
                hasCurrentBigIsland = true,
                nativeTargetSlot = AppExitTargetSlot.PRIMARY,
            ),
        )
    }

    @Test
    fun higherPriorityHandoffWithThreeExistingIslandsUsesCenteredExit() {
        assertTrue(
            decide(
                activeIslandCount = 3,
                hasCurrentBigIsland = true,
                nativeTargetSlot = AppExitTargetSlot.PRIMARY,
            ),
        )
    }

    @Test
    fun lowerPriorityHandoffWithMultipleExistingIslandsStaysNative() {
        assertFalse(
            decide(
                activeIslandCount = 3,
                hasCurrentBigIsland = true,
                nativeTargetSlot = AppExitTargetSlot.SECONDARY,
            ),
        )
    }

    @Test
    fun missingCurrentBigIslandPreservesNativeTarget() {
        assertFalse(
            decide(
                activeIslandCount = 1,
                hasCurrentBigIsland = false,
                nativeTargetSlot = AppExitTargetSlot.PRIMARY,
            ),
        )
    }

    @Test
    fun disabledFeaturePreservesXiaomiTarget() {
        assertFalse(decide(enabled = false, activeIslandCount = 0, hasCurrentBigIsland = false))
    }

    @Test
    fun freeformAndInterruptedExitsStayNative() {
        assertFalse(decide(freeform = true, activeIslandCount = 0, hasCurrentBigIsland = false))
        assertFalse(decide(interrupted = true, activeIslandCount = 0, hasCurrentBigIsland = false))
    }

    @Test
    fun missingClosingIslandCannotForceACenterTarget() {
        assertFalse(
            decide(
                hasClosingIsland = false,
                activeIslandCount = 0,
                hasCurrentBigIsland = false,
            ),
        )
    }

    @Test
    fun classifiesBigPillAsPrimaryTarget() {
        val cutout = AppExitTargetBounds(566, 47, 634, 115)
        val big = AppExitTargetBounds(370, 30, 830, 132)

        assertEquals(
            AppExitTargetSlot.PRIMARY,
            BetterAnimationsPolicy.classifyNativeTarget(big, cutout),
        )
    }

    @Test
    fun classifiesRightHandCircleAsSecondaryTarget() {
        val cutout = AppExitTargetBounds(566, 47, 634, 115)
        val small = AppExitTargetBounds(650, 47, 718, 115)

        assertEquals(
            AppExitTargetSlot.SECONDARY,
            BetterAnimationsPolicy.classifyNativeTarget(small, cutout),
        )
    }

    @Test
    fun classifiesPhysicalCutoutSeparately() {
        val cutout = AppExitTargetBounds(566, 47, 634, 115)

        assertEquals(
            AppExitTargetSlot.CUTOUT,
            BetterAnimationsPolicy.classifyNativeTarget(cutout, cutout),
        )
    }

    @Test
    fun invalidGeometryFailsOpen() {
        val cutout = AppExitTargetBounds(566, 47, 634, 115)
        val invalid = AppExitTargetBounds(650, 47, 650, 115)

        assertEquals(
            AppExitTargetSlot.UNKNOWN,
            BetterAnimationsPolicy.classifyNativeTarget(invalid, cutout),
        )
    }

    private fun decide(
        enabled: Boolean = true,
        freeform: Boolean = false,
        interrupted: Boolean = false,
        hasClosingIsland: Boolean = true,
        activeIslandCount: Int,
        hasCurrentBigIsland: Boolean,
        nativeTargetSlot: AppExitTargetSlot = AppExitTargetSlot.UNKNOWN,
    ) = BetterAnimationsPolicy.shouldUseCenteredExit(
        enabled = enabled,
        freeform = freeform,
        interrupted = interrupted,
        hasClosingIsland = hasClosingIsland,
        activeIslandCount = activeIslandCount,
        hasCurrentBigIsland = hasCurrentBigIsland,
        nativeTargetSlot = nativeTargetSlot,
    )
}
