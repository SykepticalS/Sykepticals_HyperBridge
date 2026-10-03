package com.sykeptical.hyperpop.service.call

import org.junit.Assert.assertEquals
import org.junit.Test

class CallActionIconSizingPolicyTest {
    @Test
    fun defaultThemePaddingIsReducedForCallControls() {
        assertEquals(10, CallActionIconSizingPolicy.classicPaddingPercent(15))
    }

    @Test
    fun smallerCustomPaddingIsPreserved() {
        assertEquals(4, CallActionIconSizingPolicy.classicPaddingPercent(4))
    }

    @Test
    fun invalidNegativePaddingIsClamped() {
        assertEquals(0, CallActionIconSizingPolicy.classicPaddingPercent(-5))
    }

    @Test
    fun answerAndRejectGlyphsGetASmallExtraInset() {
        assertEquals(14, CallActionIconSizingPolicy.answerRejectPaddingPercent(15))
    }

    @Test
    fun endCallLabelIsHiddenOnceTheHangUpIconExists() {
        assertEquals(
            "",
            CallActionIconSizingPolicy.islandActionTitle(
                CallActionRole.DECLINE_OR_HANG_UP,
                "End call",
                hasIcon = true,
            )
        )
    }

    @Test
    fun hangUpKeepsItsLabelWhenNoIconCanBeDrawn() {
        assertEquals(
            "End call",
            CallActionIconSizingPolicy.islandActionTitle(
                CallActionRole.DECLINE_OR_HANG_UP,
                "End call",
                hasIcon = false,
            )
        )
    }
}
