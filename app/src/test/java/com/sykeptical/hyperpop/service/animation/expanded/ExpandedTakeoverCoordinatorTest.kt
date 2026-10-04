package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedTakeoverCoordinatorTest {
    private val compact = IslandRect(460, 36, 740, 108)
    private val target = IslandRect(48, 36, 1152, 532)
    private val group = IslandRect(80, 20, 1120, 120)

    @Test
    fun fullLifecycleRestoresAlpha() {
        val coordinator = ExpandedTakeoverCoordinator()
        coordinator.onSecondaryPresence(true)
        val generation = coordinator.arm(ownerId = 7, nowMs = 1_000)
        assertEquals(TakeoverPhase.EXPANDING, coordinator.phase)
        assertTrue(
            coordinator.onFrame(7, generation, live = midway(), compact, target, group, nowMs = 1_100),
        )
        assertTrue(coordinator.statusBarAlpha in 0.05f..0.95f)
        assertTrue(coordinator.secondaryActive)
        assertTrue(coordinator.secondarySuppressed)
        assertTrue(coordinator.onFrame(7, generation, target, compact, target, group, nowMs = 1_200))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(0f, coordinator.secondaryAlpha, 0.001f)
        assertTrue(coordinator.beginCollapse(7, generation))
        assertEquals(TakeoverPhase.COLLAPSING, coordinator.phase)
        assertTrue(coordinator.onFrame(7, generation, compact, compact, target, group, nowMs = 1_400))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(1f, coordinator.secondaryAlpha, 0.001f)
        assertFalse(coordinator.secondarySuppressed)
        assertTrue(coordinator.secondaryActive)
    }

    @Test
    fun interruptionAndRotationRestoreAlpha() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(4, 0)
        coordinator.onFrame(4, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.abandon(4, generation))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun staleGenerationAndOtherOwnersAreIgnored() {
        val coordinator = ExpandedTakeoverCoordinator()
        val first = coordinator.arm(1, 0)
        val second = coordinator.arm(2, 20)
        assertFalse(coordinator.onFrame(1, first, target, compact, target, group, nowMs = 30))
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertTrue(coordinator.onFrame(2, second, target, compact, target, group, nowMs = 40))
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
        assertFalse(coordinator.beginCollapse(1, first))
    }

    @Test
    fun onlyTheExpandedOwnerDrivesTheFade() {
        val coordinator = ExpandedTakeoverCoordinator()
        coordinator.onSecondaryPresence(true)
        val generation = coordinator.arm(9, 0)
        coordinator.onFrame(9, generation, target, compact, target, group, nowMs = 5)
        assertFalse(coordinator.onFrame(3, generation, compact, compact, target, group, nowMs = 6))
        assertEquals(0f, coordinator.secondaryAlpha, 0.001f)
        assertTrue(coordinator.secondaryActive)
    }

    @Test
    fun disableIsDeferredUntilTheNativeBoundary() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(5, 0)
        coordinator.requestDisable()
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase.let {
            coordinator.onFrame(5, generation, target, compact, target, group, nowMs = 10)
            coordinator.phase
        })
        assertFalse(coordinator.allowsNewExpansion)
        assertEquals(-1L, coordinator.arm(8, 20))
        assertFalse(coordinator.allowsNewExpansion)
        coordinator.beginCollapse(5, generation)
        coordinator.onFrame(5, generation, compact, compact, target, group, nowMs = 30)
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(-1L, coordinator.arm(8, 40))
    }

    @Test
    fun settledExpandedIgnoresSilentWatchdog() {
        val coordinator = ExpandedTakeoverCoordinator(frameTimeoutMs = 3_000)
        coordinator.onSecondaryPresence(true)
        val generation = coordinator.arm(1, 0)
        assertTrue(coordinator.onFrame(1, generation, target, compact, target, group, nowMs = 100))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertTrue(coordinator.retainsExpandedGeometry())
        assertFalse(coordinator.expireIfStale(20_000))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
        assertTrue(coordinator.suppressesSecondaryVisual())
        assertTrue(coordinator.holdsSecondaryPosition())
        assertTrue(coordinator.onFrame(1, generation, target, compact, target, group, nowMs = 20_100))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun stalledExpansionStillAbandonsAndRestores() {
        val coordinator = ExpandedTakeoverCoordinator(frameTimeoutMs = 3_000)
        coordinator.onSecondaryPresence(true)
        val generation = coordinator.arm(1, 0)
        coordinator.onFrame(1, generation, midway(), compact, target, group, nowMs = 100)
        assertEquals(TakeoverPhase.EXPANDING, coordinator.phase)
        assertFalse(coordinator.expireIfStale(2_000))
        assertTrue(coordinator.expireIfStale(3_200))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(1f, coordinator.secondaryAlpha, 0.001f)
        assertFalse(coordinator.secondarySuppressed)
        assertFalse(coordinator.holdsSecondaryPosition())
    }

    @Test
    fun expiredSecondaryIsNotResurrected() {
        val coordinator = ExpandedTakeoverCoordinator()
        coordinator.onSecondaryPresence(true)
        val generation = coordinator.arm(7, 0)
        coordinator.onFrame(7, generation, target, compact, target, group, nowMs = 50)
        coordinator.onSecondaryPresence(false)
        assertFalse(coordinator.secondaryActive)
        assertFalse(coordinator.suppressesSecondaryVisual())
        assertFalse(coordinator.holdsSecondaryPosition())
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
        assertTrue(coordinator.beginCollapse(7, generation))
        coordinator.onFrame(7, generation, compact, compact, target, group, nowMs = 80)
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertFalse(coordinator.secondaryActive)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun collapseKeepsSecondarySourceAndReleasesSuppression() {
        val coordinator = ExpandedTakeoverCoordinator()
        coordinator.onSecondaryPresence(true)
        val generation = coordinator.arm(4, 0)
        coordinator.onFrame(4, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.beginCollapse(4, generation))
        assertFalse(coordinator.suppressesSecondaryVisual())
        assertTrue(coordinator.holdsSecondaryPosition())
        assertTrue(coordinator.secondaryActive)
        coordinator.onFrame(4, generation, compact, compact, target, group, nowMs = 20)
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertTrue(coordinator.secondaryActive)
        assertFalse(coordinator.holdsSecondaryPosition())
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun interruptedCollapseRestoresSecondaryAndStatusBar() {
        val coordinator = ExpandedTakeoverCoordinator()
        coordinator.onSecondaryPresence(true)
        val generation = coordinator.arm(11, 0)
        coordinator.onFrame(11, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.beginCollapse(11, generation))
        assertEquals(TakeoverPhase.COLLAPSING, coordinator.phase)
        assertFalse(coordinator.suppressesSecondaryVisual())
        assertTrue(coordinator.secondaryActive)
        assertTrue(coordinator.abandon(11, generation))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertFalse(coordinator.secondarySuppressed)
        assertTrue(coordinator.secondaryActive)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(1f, coordinator.secondaryAlpha, 0.001f)
    }

    @Test
    fun duplicateAbandonAndStaleCollapseDoNothing() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(3, 0)
        assertTrue(coordinator.abandon(3, generation))
        assertFalse(coordinator.abandon(3, generation))
        assertFalse(coordinator.beginCollapse(3, generation))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun secondaryExpansionFadesStatusBarAndTheOtherCompactIsland() {
        val coordinator = ExpandedTakeoverCoordinator()
        coordinator.onSecondaryPresence(true, islandId = 2)
        val generation = coordinator.arm(ownerId = 8, nowMs = 0, fromSmallIsland = true)
        coordinator.onSecondaryPresence(true, islandId = 2)
        assertTrue(coordinator.onFrame(8, generation, target, compact, target, group, nowMs = 20))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(0f, coordinator.secondaryAlpha, 0.001f)
        assertTrue(coordinator.fadesUnexpandedCompact())
        assertTrue(coordinator.hidesOwnedBigIslandLayer())
        assertTrue(coordinator.hidesSettledSmallIslandLayer())
        assertFalse(coordinator.suppressesSecondaryVisual())
        assertTrue(coordinator.secondaryActive)
        assertTrue(coordinator.blocksTouchFor(2))
        assertFalse(coordinator.blocksTouchFor(8))
    }

    @Test
    fun bigIslandExpandDoesNotHideItsOwnCompactLayer() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(7, 0, fromSmallIsland = false)
        assertFalse(coordinator.hidesOwnedBigIslandLayer())
        coordinator.onFrame(7, generation, target, compact, target, group, nowMs = 10)
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertFalse(coordinator.hidesOwnedBigIslandLayer())
        assertFalse(coordinator.hidesSettledSmallIslandLayer())
    }

    @Test
    fun smallIslandExpandReleasesItsCompactLayerOnCollapse() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(8, 0, fromSmallIsland = true)
        assertTrue(coordinator.hidesOwnedBigIslandLayer())
        assertFalse(coordinator.hidesSettledSmallIslandLayer())
        assertTrue(coordinator.beginCollapse(8, generation))
        assertEquals(TakeoverPhase.COLLAPSING, coordinator.phase)
        assertFalse(coordinator.hidesOwnedBigIslandLayer())
        assertFalse(coordinator.hidesSettledSmallIslandLayer())
    }

    @Test
    fun hiddenSecondaryIsNotClickableUntilNativeRestore() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(7, 0)
        coordinator.onSecondaryPresence(true, islandId = 4)
        coordinator.onFrame(7, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.suppressesSecondaryVisual())
        assertTrue(coordinator.secondaryActive)
        assertTrue(coordinator.shouldRestoreYieldedIsland())
        assertTrue(coordinator.blocksTouchFor(4))
        assertFalse(coordinator.blocksTouchFor(7))
        assertTrue(coordinator.beginCollapse(7, generation))
        assertTrue(coordinator.blocksTouchFor(4))
        assertTrue(coordinator.onFrame(7, generation, midway(), compact, target, group, nowMs = 20))
        assertEquals(TakeoverPhase.COLLAPSING, coordinator.phase)
        assertTrue(coordinator.blocksTouchFor(4))
        assertTrue(coordinator.secondaryActive)
        coordinator.onFrame(7, generation, compact, compact, target, group, nowMs = 30)
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertFalse(coordinator.blocksYieldedIslandTouch())
        assertTrue(coordinator.shouldRestoreYieldedIsland())
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(1f, coordinator.secondaryAlpha, 0.001f)
    }

    @Test
    fun fadedCompactBecomesInteractiveOnlyAfterItIsVisible() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(8, 0, fromSmallIsland = true)
        coordinator.onSecondaryPresence(true, islandId = 2)
        coordinator.onFrame(8, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.beginCollapse(8, generation))
        assertTrue(coordinator.blocksTouchFor(2))
        assertTrue(coordinator.onFrame(8, generation, midway(), compact, target, group, nowMs = 20))
        assertTrue(coordinator.secondaryAlpha < 0.98f)
        assertTrue(coordinator.blocksTouchFor(2))
        coordinator.onFrame(8, generation, compact, compact, target, group, nowMs = 30)
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertFalse(coordinator.blocksTouchFor(2))
        assertTrue(coordinator.shouldRestoreYieldedIsland())
        assertEquals(1f, coordinator.secondaryAlpha, 0.001f)
    }

    @Test
    fun hiddenSecondaryUpdateKeepsTheNewIslandNonInteractive() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(7, 0)
        coordinator.onSecondaryPresence(true, islandId = 4)
        coordinator.onFrame(7, generation, target, compact, target, group, nowMs = 10)
        coordinator.onSecondaryPresence(true, islandId = 5)
        assertTrue(coordinator.secondaryActive)
        assertTrue(coordinator.blocksTouchFor(5))
        assertFalse(coordinator.blocksTouchFor(4))
        assertTrue(coordinator.suppressesSecondaryVisual())
    }

    @Test
    fun expiredHiddenSecondaryIsNotRestoredOrClickable() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(7, 0)
        coordinator.onSecondaryPresence(true, islandId = 4)
        coordinator.onFrame(7, generation, target, compact, target, group, nowMs = 10)
        coordinator.onSecondaryPresence(false)
        assertFalse(coordinator.secondaryActive)
        assertFalse(coordinator.shouldRestoreYieldedIsland())
        assertFalse(coordinator.blocksYieldedIslandTouch())
        assertFalse(coordinator.suppressesSecondaryVisual())
        coordinator.beginCollapse(7, generation)
        coordinator.onFrame(7, generation, compact, compact, target, group, nowMs = 40)
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertFalse(coordinator.secondaryActive)
        assertFalse(coordinator.shouldRestoreYieldedIsland())
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun interruptedExpansionAndCollapseClearYieldedTouch() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(6, 0, fromSmallIsland = true)
        coordinator.onSecondaryPresence(true, islandId = 3)
        coordinator.onFrame(6, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.blocksTouchFor(3))
        assertTrue(coordinator.abandon(6, generation))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertFalse(coordinator.blocksYieldedIslandTouch())
        assertFalse(coordinator.fadesUnexpandedCompact())
        assertFalse(coordinator.suppressesSecondaryVisual())
        assertTrue(coordinator.secondaryActive)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(1f, coordinator.secondaryAlpha, 0.001f)

        val collapse = coordinator.arm(6, 50)
        coordinator.onSecondaryPresence(true, islandId = 3)
        coordinator.onFrame(6, collapse, target, compact, target, group, nowMs = 60)
        coordinator.beginCollapse(6, collapse)
        assertTrue(coordinator.blocksTouchFor(3))
        assertTrue(coordinator.abandon(6, collapse))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertFalse(coordinator.blocksYieldedIslandTouch())
        assertTrue(coordinator.secondaryActive)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun disableAndInterruptClearSecondarySuppression() {
        val coordinator = ExpandedTakeoverCoordinator()
        coordinator.onSecondaryPresence(true)
        val generation = coordinator.arm(6, 0)
        coordinator.onFrame(6, generation, target, compact, target, group, nowMs = 5)
        coordinator.requestDisable()
        assertTrue(coordinator.suppressesSecondaryVisual())
        assertFalse(coordinator.allowsNewExpansion)
        assertTrue(coordinator.abandon(6, generation))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertFalse(coordinator.suppressesSecondaryVisual())
        assertFalse(coordinator.holdsSecondaryPosition())
        assertTrue(coordinator.secondaryActive)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(-1L, coordinator.arm(6, 30))
    }

    @Test
    fun pendingExpansionWaitsForCollapseAndIsNotRepeated() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(1, 0)
        coordinator.onFrame(1, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.enqueueExpansion(1, "same") is EnqueueExpansion.NotActive)
        val queued = coordinator.enqueueExpansion(2, "b")
        assertTrue(queued is EnqueueExpansion.Queued)
        assertEquals("b", (queued as EnqueueExpansion.Queued).pending.key)
        assertEquals(null, queued.displaced)
        assertEquals(null, coordinator.takePendingExpansion())
        assertTrue(coordinator.beginCollapse(1, generation))
        assertTrue(coordinator.onFrame(1, generation, compact, compact, target, group, nowMs = 40))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals("b", coordinator.takePendingExpansion()?.key)
        assertEquals(null, coordinator.takePendingExpansion())
    }

    @Test
    fun aNewerCandidateReplacesThePendingOne() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(1, 0)
        coordinator.onFrame(1, generation, target, compact, target, group, nowMs = 10)
        coordinator.enqueueExpansion(2, "b")
        val next = coordinator.enqueueExpansion(3, "c") as EnqueueExpansion.Queued
        assertEquals("b", next.displaced?.key)
        assertEquals("c", next.pending.key)
        assertEquals(next.pending.token, coordinator.pendingExpansion?.token)
        assertFalse(next.pending.token == next.displaced?.token)
    }

    @Test
    fun abandonAndDisableClearThePendingExpansion() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(1, 0)
        coordinator.enqueueExpansion(2, "b")
        assertTrue(coordinator.abandon(1, generation))
        assertEquals("b", coordinator.takePendingExpansion()?.key)
        val again = coordinator.arm(4, 50)
        coordinator.enqueueExpansion(5, "c")
        coordinator.requestDisable()
        assertEquals("c", coordinator.clearPendingExpansion()?.key)
        assertEquals(null, coordinator.pendingExpansion)
        coordinator.abandon(4, again)
        assertEquals(null, coordinator.takePendingExpansion())
    }

    @Test
    fun aNativeIslandDoesNotQueue() {
        val coordinator = ExpandedTakeoverCoordinator()
        assertTrue(coordinator.enqueueExpansion(2, "b") is EnqueueExpansion.NotActive)
        assertEquals(null, coordinator.pendingExpansion)
    }

    private fun midway() = IslandRect(254, 36, 946, 320)
}
