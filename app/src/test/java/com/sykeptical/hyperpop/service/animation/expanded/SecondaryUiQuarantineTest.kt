package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondaryUiQuarantineTest {
    @Test
    fun bigExpansionQuarantinesThePromotedSecondary() {
        val quarantine = SecondaryUiQuarantine()
        val decision = quarantine.onFlushStart(snapshot(expanded = 7, big = 4))
        val active = decision as QuarantineFlush.Activate
        assertEquals(7, active.ownerId)
        assertEquals(setOf(4), active.sweepIds)
        assertTrue(quarantine.blocks(4))
        assertFalse(quarantine.blocks(7))
        assertEquals(QuarantineRegion.DROP_ALL, quarantine.region())
        assertTrue(quarantine.touchMask().blockBig)
        assertTrue(quarantine.touchMask().blockDefault)
        assertFalse(quarantine.touchMask().blockSmall)
    }

    @Test
    fun smallExpansionQuarantinesTheRemainingBigIsland() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 8, big = 2, small = 3))
        assertTrue(quarantine.blocks(2))
        assertTrue(quarantine.blocks(3))
        assertFalse(quarantine.blocks(8))
        assertEquals(QuarantineRegion.DROP_ALL, quarantine.region())
        assertTrue(quarantine.touchMask().blockBig)
        assertTrue(quarantine.touchMask().blockSmall)
    }

    @Test
    fun aNewCompactArrivalStaysBlockedWithoutReleasing() {
        val quarantine = SecondaryUiQuarantine()
        val first = quarantine.onFlushStart(snapshot(expanded = 7, big = 4)) as QuarantineFlush.Activate
        quarantine.markHidden(4)
        val second = quarantine.onFlushStart(snapshot(expanded = 7, big = 4, small = 9)) as QuarantineFlush.Activate
        assertEquals(first.generation, second.generation)
        assertEquals(setOf(4, 9), second.sweepIds)
        assertTrue(quarantine.blocks(9))
        assertTrue(quarantine.wasHidden(4))
        assertEquals(emptySet<Int>(), quarantine.onFlushEnd(snapshot(expanded = 7, big = 4, small = 9)))
    }

    @Test
    fun anUpdateWhileHiddenDoesNotRelease() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, big = 4))
        quarantine.markHidden(4)
        assertTrue(quarantine.onFlushStart(snapshot(expanded = 7, big = 4)) is QuarantineFlush.Activate)
        assertTrue(quarantine.active)
        assertTrue(quarantine.blocks(4))
    }

    @Test
    fun anExpiredHiddenIslandIsNotRefreshed() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, big = 4))
        quarantine.markHidden(4)
        assertTrue(quarantine.onFlushStart(snapshot(expanded = null, big = null)) is QuarantineFlush.Release)
        assertEquals(emptySet<Int>(), quarantine.onFlushEnd(snapshot(expanded = null)))
    }

    @Test
    fun nativeCollapseSkipsIslandsRenderedInTheSameFlush() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, big = 4, small = 5))
        quarantine.markHidden(4)
        quarantine.markHidden(5)
        val release = quarantine.onFlushStart(snapshot(expanded = null, big = 4, small = 5)) as QuarantineFlush.Release
        quarantine.markRendered(4)
        quarantine.markRendered(5)
        assertEquals(emptySet<Int>(), quarantine.onFlushEnd(snapshot(expanded = null, big = 4, small = 5)))
        assertTrue(quarantine.acceptsRefresh(release.generation))
        assertFalse(quarantine.blocks(4))
    }

    @Test
    fun secondaryDespanKeepsTheMainIslandHiddenUntilTheCardIsCompact() {
        assertTrue(releasesQuarantineForDespan("BigIsland"))
        assertFalse(releasesQuarantineForDespan("SmallIsland"))
        assertFalse(releasesQuarantineForDespan("ShowOnceBigIsland"))
        assertFalse(releasesQuarantineForDespan(null))

        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 8, big = 2))
        quarantine.markHidden(2)
        val hold = quarantine.onFlushStart(
            snapshot(expanded = null, big = 2, small = 8, takeoverHolding = true),
        ) as QuarantineFlush.Hold
        assertEquals(setOf(2), hold.sweepIds)
        assertTrue(quarantine.blocks(2))
        assertFalse(quarantine.blocks(8))
    }

    @Test
    fun takeoverHoldDefersReleaseUntilTheCardIsNative() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, big = 4))
        quarantine.markHidden(4)
        val hold = quarantine.onFlushStart(
            snapshot(expanded = null, big = 7, small = 4, takeoverHolding = true),
        ) as QuarantineFlush.Hold
        assertEquals(setOf(4), hold.sweepIds)
        assertFalse(hold.sweepIds.contains(7))
        assertTrue(quarantine.blocks(4))
        assertFalse(quarantine.blocks(7))
        assertEquals(QuarantineRegion.DROP_SMALL, quarantine.region())
        assertEquals(
            emptySet<Int>(),
            quarantine.onFlushEnd(snapshot(expanded = null, big = 7, small = 4, takeoverHolding = true)),
        )
        val release = quarantine.onFlushStart(snapshot(expanded = null, big = 7, small = 4)) as QuarantineFlush.Release
        assertEquals(setOf(4), quarantine.onFlushEnd(snapshot(expanded = null, big = 7, small = 4)))
        assertTrue(quarantine.acceptsRefresh(release.generation))
    }

    @Test
    fun aReplacementExpandedIslandStartsANewGeneration() {
        val quarantine = SecondaryUiQuarantine()
        val first = quarantine.onFlushStart(snapshot(expanded = 7, big = 4)) as QuarantineFlush.Activate
        quarantine.markHidden(4)
        val second = quarantine.onFlushStart(snapshot(expanded = 8, big = 4, small = 6)) as QuarantineFlush.Activate
        assertTrue(second.generation > first.generation)
        assertFalse(quarantine.wasHidden(4))
        assertEquals(setOf(4, 6), second.sweepIds)
        assertTrue(quarantine.blocks(4))
        assertFalse(quarantine.acceptsRefresh(first.generation))
    }

    @Test
    fun theReplacedOwnerKeepsItsNativeMorphThenFollowsTheQuarantine() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, small = 4))
        quarantine.markHidden(4)
        val replaced = quarantine.onFlushStart(snapshot(expanded = 8, big = 7, small = 4)) as QuarantineFlush.Activate
        assertEquals(8, replaced.ownerId)
        assertEquals(setOf(4), replaced.sweepIds)
        assertTrue(quarantine.inHandoff(7))
        assertFalse(quarantine.blocks(7))
        assertTrue(quarantine.blocks(4))
        assertTrue(quarantine.touchMask().blockBig)
        assertEquals(setOf(4), quarantine.violations(snapshot(expanded = 8, big = 7, small = 4)))

        assertTrue(quarantine.endHandoff(7))
        assertFalse(quarantine.endHandoff(7))
        assertTrue(quarantine.blocks(7))
        val settled = quarantine.onFlushStart(snapshot(expanded = 8, big = 7, small = 4)) as QuarantineFlush.Activate
        assertEquals(setOf(7, 4), settled.sweepIds)
    }

    @Test
    fun aCompactIslandThatTakesTheCardIsNotAHandoff() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, small = 4))
        quarantine.onFlushStart(snapshot(expanded = 4, big = 7))
        assertTrue(quarantine.inHandoff(7))
        assertFalse(quarantine.inHandoff(4))
        quarantine.onFlushStart(snapshot(expanded = 7, big = 4))
        assertFalse(quarantine.inHandoff(7))
        assertTrue(quarantine.inHandoff(4))
    }

    @Test
    fun aHandoffEndsWhenTheIslandLeavesItsSlotOrTheQuarantineReleases() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7))
        quarantine.onFlushStart(snapshot(expanded = 8, big = 7))
        assertTrue(quarantine.inHandoff(7))
        quarantine.onFlushStart(snapshot(expanded = 8))
        assertFalse(quarantine.inHandoff(7))

        quarantine.onFlushStart(snapshot(expanded = 9, big = 8))
        assertTrue(quarantine.inHandoff(8))
        assertTrue(quarantine.onFlushStart(snapshot(big = 8)) is QuarantineFlush.Release)
        assertFalse(quarantine.inHandoff(8))
        quarantine.onFlushStart(snapshot(expanded = 9, big = 8))
        assertTrue(quarantine.blocks(8))
    }

    @Test
    fun aReplacementWithoutAPriorOwnerHasNoHandoff() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 8, big = 7))
        assertFalse(quarantine.inHandoff(7))
        assertTrue(quarantine.blocks(7))
    }

    @Test
    fun aStaleReleaseCannotRefreshAfterANewExpansion() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, big = 4))
        quarantine.markHidden(4)
        val release = quarantine.onFlushStart(snapshot(expanded = null, big = 4)) as QuarantineFlush.Release
        quarantine.onFlushEnd(snapshot(expanded = null, big = 4))
        quarantine.onFlushStart(snapshot(expanded = 9, big = 4))
        assertFalse(quarantine.acceptsRefresh(release.generation))
        assertTrue(quarantine.blocks(4))
    }

    @Test
    fun showOnceAndBigTempAreQuarantinedWithThePair() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, big = 4, showOnce = 11, bigTemp = 12))
        assertTrue(quarantine.blocks(11))
        assertTrue(quarantine.blocks(12))
        val mask = quarantine.touchMask()
        assertTrue(mask.blockShowOnce)
        assertTrue(mask.blockBigTemp)
        assertEquals(QuarantineRegion.DROP_ALL, quarantine.region())
    }

    @Test
    fun openTouchLeavesTheCompactRegionIntact() {
        val quarantine = SecondaryUiQuarantine()
        assertEquals(QuarantineRegion.KEEP, quarantine.region())
        assertFalse(quarantine.touchMask().blockDefault)
        assertFalse(quarantine.blocks(4))
        assertTrue(quarantine.onFlushStart(snapshot()) is QuarantineFlush.None)
    }

    @Test
    fun violationsAreTheUnhiddenCompactCurrents() {
        val quarantine = SecondaryUiQuarantine()
        val current = snapshot(expanded = 7, big = 4, small = 5)
        quarantine.onFlushStart(current)
        assertEquals(setOf(4, 5), quarantine.violations(current))
        quarantine.markHidden(4)
        quarantine.markHidden(5)
        assertEquals(emptySet<Int>(), quarantine.violations(current))
        quarantine.onFlushStart(snapshot())
        assertEquals(emptySet<Int>(), quarantine.violations(snapshot(big = 4)))
    }

    @Test
    fun aRefreshGenerationIsAcceptedOnce() {
        val quarantine = SecondaryUiQuarantine()
        quarantine.onFlushStart(snapshot(expanded = 7, big = 4))
        quarantine.markHidden(4)
        val release = quarantine.onFlushStart(snapshot(expanded = null, big = 4)) as QuarantineFlush.Release
        assertEquals(setOf(4), quarantine.onFlushEnd(snapshot(expanded = null, big = 4)))
        assertTrue(quarantine.acceptsRefresh(release.generation))
        assertFalse(quarantine.acceptsRefresh(release.generation))
    }

    private fun snapshot(
        expanded: Int? = null,
        big: Int? = null,
        small: Int? = null,
        showOnce: Int? = null,
        bigTemp: Int? = null,
        takeoverHolding: Boolean = false,
    ) = QuarantineSnapshot(expanded, big, small, showOnce, bigTemp, takeoverHolding)
}
