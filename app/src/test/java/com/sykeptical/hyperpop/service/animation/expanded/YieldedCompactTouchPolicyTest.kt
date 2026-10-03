package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Test

class YieldedCompactTouchPolicyTest {
    @Test
    fun hiddenCircleAloneDropsTheWholeCompactRegion() {
        assertEquals(
            YieldedCompactTouch.DROP_ALL,
            YieldedCompactTouchPolicy.decide(
                blockTouch = true,
                hasBig = false,
                bigYielded = false,
                hasSmall = true,
                smallYielded = true,
            ),
        )
    }

    @Test
    fun hiddenPromotedCircleDropsTheWholeCompactRegion() {
        assertEquals(
            YieldedCompactTouch.DROP_ALL,
            YieldedCompactTouchPolicy.decide(
                blockTouch = true,
                hasBig = true,
                bigYielded = true,
                hasSmall = false,
                smallYielded = false,
            ),
        )
    }

    @Test
    fun visibleBigIslandKeepsItsRegionWhenTheCircleIsHidden() {
        assertEquals(
            YieldedCompactTouch.DROP_SMALL,
            YieldedCompactTouchPolicy.decide(
                blockTouch = true,
                hasBig = true,
                bigYielded = false,
                hasSmall = true,
                smallYielded = true,
            ),
        )
    }

    @Test
    fun visibleCircleKeepsItsRegionWhenTheBigIslandIsHidden() {
        assertEquals(
            YieldedCompactTouch.DROP_BIG,
            YieldedCompactTouchPolicy.decide(
                blockTouch = true,
                hasBig = true,
                bigYielded = true,
                hasSmall = true,
                smallYielded = false,
            ),
        )
    }

    @Test
    fun openTouchAndUnrelatedIslandsStayIntact() {
        assertEquals(
            YieldedCompactTouch.KEEP,
            YieldedCompactTouchPolicy.decide(
                blockTouch = false,
                hasBig = true,
                bigYielded = true,
                hasSmall = true,
                smallYielded = true,
            ),
        )
        assertEquals(
            YieldedCompactTouch.KEEP,
            YieldedCompactTouchPolicy.decide(
                blockTouch = true,
                hasBig = true,
                bigYielded = false,
                hasSmall = true,
                smallYielded = false,
            ),
        )
    }
}
