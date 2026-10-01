package com.d4viddf.hyperbridge.screenrecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRecorderLifecyclePolicyTest {
    @Test
    fun islandOwnershipFollowsOnlyTheMasterIntegrationToggle() {
        assertTrue(ScreenRecorderLifecyclePolicy.ownsIsland(true))
        assertFalse(ScreenRecorderLifecyclePolicy.ownsIsland(false))
    }

    @Test
    fun countdownIncludesZeroBeforeStartingRecorder() {
        assertEquals(2, ScreenRecorderLifecyclePolicy.nextCountdown(3))
        assertEquals(1, ScreenRecorderLifecyclePolicy.nextCountdown(2))
        assertEquals(0, ScreenRecorderLifecyclePolicy.nextCountdown(1))
        assertEquals(0, ScreenRecorderLifecyclePolicy.nextCountdown(0))
        assertEquals(
            4 * ScreenRecorderContract.COUNTDOWN_TICK_MS,
            ScreenRecorderLifecyclePolicy.startDelayMillis(countdownEnabled = true),
        )
        assertEquals(0L, ScreenRecorderLifecyclePolicy.startDelayMillis(countdownEnabled = false))
    }

    @Test
    fun stopDismissesStartingSessionImmediately() {
        assertTrue(
            ScreenRecorderLifecyclePolicy.stopDismissesImmediately(
                ScreenRecorderContract.STATE_STARTING,
            ),
        )
        assertFalse(
            ScreenRecorderLifecyclePolicy.stopDismissesImmediately(
                ScreenRecorderContract.STATE_RECORDING,
            ),
        )
    }
}
