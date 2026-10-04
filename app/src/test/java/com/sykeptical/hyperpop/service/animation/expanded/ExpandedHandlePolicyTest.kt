package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpandedHandlePolicyTest {
    private val handle = BottomWindowHandle(heightPx = 11, bottomMarginPx = 22)

    @Test
    fun noHandleLeavesTheTunedBottom() {
        assertEquals(400, ExpandedHandlePolicy.seatBottom(400, 300, null, 3f, 2608))
        assertNull(ExpandedHandlePolicy.reserved(IslandRect(0, 0, 100, 400), null))
    }

    @Test
    fun handleAddsItsOwnHeightAndMarginBelowTheTunedAir() {
        assertEquals(433, ExpandedHandlePolicy.seatBottom(400, 300, handle, 3f, 2608))
        assertEquals(
            IslandRect(10, 400, 110, 433),
            ExpandedHandlePolicy.reserved(IslandRect(10, 20, 110, 433), handle),
        )
    }

    @Test
    fun aShortBottomGapStillClearsTheBar() {
        assertEquals(351, ExpandedHandlePolicy.seatBottom(310, 300, handle, 3f, 2608))
    }
}
