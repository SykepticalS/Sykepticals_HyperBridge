package com.d4viddf.hyperbridge.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallBannerPolicyTest {
    @Test
    fun `incoming call banner is suppressed only when the island will replace it`() {
        assertTrue(
            IncomingCallBannerPolicy.shouldSuppress(
                suppressSourceEnabled = true,
                engineReady = true,
                packageAllowed = true,
                callIslandsEnabled = true,
                incomingStageEnabled = true,
                hasFullScreenIntent = true,
                isIncomingCall = true,
            )
        )
    }

    @Test
    fun `full screen intent alone does not suppress a non incoming notification`() {
        assertFalse(
            IncomingCallBannerPolicy.shouldSuppress(
                suppressSourceEnabled = true,
                engineReady = true,
                packageAllowed = true,
                callIslandsEnabled = true,
                incomingStageEnabled = true,
                hasFullScreenIntent = true,
                isIncomingCall = false,
            )
        )
    }

    @Test
    fun `disabled incoming stage keeps the system banner`() {
        assertFalse(
            IncomingCallBannerPolicy.shouldSuppress(
                suppressSourceEnabled = true,
                engineReady = true,
                packageAllowed = true,
                callIslandsEnabled = true,
                incomingStageEnabled = false,
                hasFullScreenIntent = true,
                isIncomingCall = true,
            )
        )
    }

    @Test
    fun `banner suppression requires the global suppress switch`() {
        assertFalse(
            IncomingCallBannerPolicy.shouldSuppress(
                suppressSourceEnabled = false,
                engineReady = true,
                packageAllowed = true,
                callIslandsEnabled = true,
                incomingStageEnabled = true,
                hasFullScreenIntent = true,
                isIncomingCall = true,
            )
        )
    }

    @Test
    fun `unlocked interactive use is the home screen or an app`() {
        assertTrue(IncomingCallBannerPolicy.inUnlockedApp(interactive = true, keyguardLocked = false))
        assertFalse(IncomingCallBannerPolicy.inUnlockedApp(interactive = true, keyguardLocked = true))
        assertFalse(IncomingCallBannerPolicy.inUnlockedApp(interactive = false, keyguardLocked = false))
        assertFalse(IncomingCallBannerPolicy.inUnlockedApp(interactive = null, keyguardLocked = false))
    }
}
