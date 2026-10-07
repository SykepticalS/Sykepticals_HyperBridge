package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedReacquirePolicyTest {
    private fun reacquire(
        released: Boolean = true,
        expanded: Boolean = true,
        session: Boolean = false,
        blocked: Boolean = false,
        hidden: Boolean = false,
        enabled: Boolean = true,
    ) = ExpandedReacquirePolicy.shouldReacquire(released, expanded, session, blocked, hidden, enabled)

    @Test
    fun aReleasedIslandThatIsStillExpandedGetsTheTakeoverBack() {
        assertTrue(reacquire())
    }

    @Test
    fun nothingIsReacquiredWithoutARelease() {
        assertFalse(reacquire(released = false))
    }

    @Test
    fun anIslandXiaomiCollapsedStaysNative() {
        assertFalse(reacquire(expanded = false))
        assertFalse(ExpandedReacquirePolicy.keepsRelease(stillExpanded = false))
        assertTrue(ExpandedReacquirePolicy.keepsRelease(stillExpanded = true))
    }

    @Test
    fun anArmedSessionIsNeverArmedTwice() {
        assertFalse(reacquire(session = true))
    }

    @Test
    fun aBlockedOrHiddenIslandWaitsForXiaomi() {
        assertFalse(reacquire(blocked = true))
        assertFalse(reacquire(hidden = true))
    }

    @Test
    fun aDisabledTakeoverStaysReleased() {
        assertFalse(reacquire(enabled = false))
    }
}
