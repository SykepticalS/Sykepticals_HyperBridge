package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoverFadePolicyTest {
    private val compact = IslandRect(460, 36, 740, 108)
    private val target = IslandRect(48, 36, 1152, 532)
    private val group = IslandRect(80, 20, 1120, 120)

    @Test
    fun endpointsAreExact() {
        assertEquals(1f, TakeoverFadePolicy.alpha(compact, compact, target, group), 0.001f)
        assertEquals(0f, TakeoverFadePolicy.alpha(compact, target, target, group), 0.001f)
    }

    @Test
    fun progressIsMonotonic() {
        val quarter = IslandRect(357, 36, 843, 214)
        val half = IslandRect(254, 36, 946, 320)
        val early = TakeoverFadePolicy.alpha(compact, quarter, target, group)
        val mid = TakeoverFadePolicy.alpha(compact, half, target, group)
        assertTrue(early < 1f && early > mid)
        assertTrue(mid > 0f)
    }

    @Test
    fun overshootClampsAndMissingGroupFallsBackToWidth() {
        val past = target.copy(left = target.left - 40, right = target.right + 40)
        assertEquals(0f, TakeoverFadePolicy.alpha(compact, past, target, group), 0.001f)
        val undershoot = compact.copy(right = compact.right - 20)
        assertEquals(1f, TakeoverFadePolicy.alpha(compact, undershoot, target, null), 0.001f)
        assertEquals(0f, TakeoverFadePolicy.alpha(compact, target, target, null), 0.001f)
    }

    @Test
    fun zeroSpanStaysVisibleUntilTheTargetWidth() {
        val same = compact
        assertEquals(1f, TakeoverFadePolicy.alpha(same, same, same, IslandRect(0, 0, 0, 0)), 0.001f)
        assertFalse(TakeoverFadePolicy.alpha(compact, target, target, null) > 0f)
    }
}
