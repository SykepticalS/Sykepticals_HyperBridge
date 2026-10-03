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

    private fun midway() = IslandRect(254, 36, 946, 320)
}
