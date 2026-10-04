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
        val generation = coordinator.arm(ownerId = 7, nowMs = 1_000)
        assertEquals(TakeoverPhase.EXPANDING, coordinator.phase)
        assertTrue(
            coordinator.onFrame(7, generation, live = midway(), compact, target, group, nowMs = 1_100),
        )
        assertTrue(coordinator.statusBarAlpha in 0.05f..0.95f)
        assertTrue(coordinator.onFrame(7, generation, target, compact, target, group, nowMs = 1_200))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
        assertTrue(coordinator.beginCollapse(7, generation))
        assertEquals(TakeoverPhase.COLLAPSING, coordinator.phase)
        assertTrue(coordinator.onFrame(7, generation, compact, compact, target, group, nowMs = 1_400))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
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
        val generation = coordinator.arm(9, 0)
        coordinator.onFrame(9, generation, target, compact, target, group, nowMs = 5)
        assertFalse(coordinator.onFrame(3, generation, compact, compact, target, group, nowMs = 6))
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
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
        val generation = coordinator.arm(1, 0)
        assertTrue(coordinator.onFrame(1, generation, target, compact, target, group, nowMs = 100))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertTrue(coordinator.retainsExpandedGeometry())
        assertFalse(coordinator.expireIfStale(20_000))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
        assertTrue(coordinator.onFrame(1, generation, target, compact, target, group, nowMs = 20_100))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun stalledExpansionStillAbandonsAndRestores() {
        val coordinator = ExpandedTakeoverCoordinator(frameTimeoutMs = 3_000)
        val generation = coordinator.arm(1, 0)
        coordinator.onFrame(1, generation, midway(), compact, target, group, nowMs = 100)
        assertEquals(TakeoverPhase.EXPANDING, coordinator.phase)
        assertFalse(coordinator.expireIfStale(2_000))
        assertTrue(coordinator.expireIfStale(3_200))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun interruptedCollapseRestoresTheStatusBar() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(11, 0)
        coordinator.onFrame(11, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.beginCollapse(11, generation))
        assertEquals(TakeoverPhase.COLLAPSING, coordinator.phase)
        assertTrue(coordinator.abandon(11, generation))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
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
    fun secondaryExpansionStillFadesTheStatusBar() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(ownerId = 8, nowMs = 0)
        assertTrue(coordinator.onFrame(8, generation, target, compact, target, group, nowMs = 20))
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
    }

    @Test
    fun disableAndInterruptRestoreTheStatusBar() {
        val coordinator = ExpandedTakeoverCoordinator()
        val generation = coordinator.arm(6, 0)
        coordinator.onFrame(6, generation, target, compact, target, group, nowMs = 5)
        coordinator.requestDisable()
        assertFalse(coordinator.allowsNewExpansion)
        assertTrue(coordinator.abandon(6, generation))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
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

    @Test
    fun nativeReplacementIsAdmittedOnlyWhileTheOwnerIsXiaomisExpandedCurrent() {
        val coordinator = ExpandedTakeoverCoordinator()
        assertFalse(coordinator.admitsNativeReplacement(2, xiaomiExpandedIsOwner = true, ownerTempShow = false))
        val generation = coordinator.arm(1, 0)
        assertTrue(coordinator.admitsNativeReplacement(2, xiaomiExpandedIsOwner = true, ownerTempShow = false))
        assertFalse(coordinator.admitsNativeReplacement(1, xiaomiExpandedIsOwner = true, ownerTempShow = false))
        assertFalse(coordinator.admitsNativeReplacement(2, xiaomiExpandedIsOwner = false, ownerTempShow = false))
        assertFalse(coordinator.admitsNativeReplacement(2, xiaomiExpandedIsOwner = true, ownerTempShow = true))
        coordinator.onFrame(1, generation, target, compact, target, group, nowMs = 10)
        assertTrue(coordinator.admitsNativeReplacement(2, xiaomiExpandedIsOwner = true, ownerTempShow = false))
        coordinator.beginCollapse(1, generation)
        assertFalse(coordinator.admitsNativeReplacement(2, xiaomiExpandedIsOwner = true, ownerTempShow = false))
        assertTrue(coordinator.enqueueExpansion(2, "b") is EnqueueExpansion.Queued)
        assertFalse(coordinator.admitsNativeReplacement(3, xiaomiExpandedIsOwner = true, ownerTempShow = false))
    }

    @Test
    fun aReplacedOwnerRetiresAndKeepsTheStatusBarCoveredUntilItIsCompact() {
        val coordinator = ExpandedTakeoverCoordinator()
        val first = coordinator.arm(1, 0)
        coordinator.onFrame(1, first, target, compact, target, group, nowMs = 10)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)

        val replacement = coordinator.replace(2, nowMs = 20)!!
        assertEquals(RetiringOwner(1, first), replacement.retired)
        assertEquals(2, coordinator.ownerId)
        assertEquals(TakeoverPhase.EXPANDING, coordinator.phase)
        assertTrue(coordinator.retires(1, first))
        assertFalse(coordinator.accepts(1, first))
        assertTrue(coordinator.accepts(2, replacement.generation))
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)

        assertTrue(coordinator.onFrame(2, replacement.generation, compact, compact, target, group, nowMs = 30))
        assertEquals(TakeoverPhase.EXPANDING, coordinator.phase)
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)

        assertEquals(
            RetiringFrame.MOVING,
            coordinator.onRetiringFrame(1, first, midway(), compact, target, group),
        )
        assertTrue(coordinator.statusBarAlpha in 0.05f..0.95f)
        assertEquals(
            RetiringFrame.SETTLED,
            coordinator.onRetiringFrame(1, first, compact, compact, target, group),
        )
        assertFalse(coordinator.hasRetiring())
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
        assertEquals(
            RetiringFrame.IGNORED,
            coordinator.onRetiringFrame(1, first, midway(), compact, target, group),
        )
    }

    @Test
    fun theIncomingOwnerSettlesOnItsOwnFadeNotTheRetiringOne() {
        val coordinator = ExpandedTakeoverCoordinator()
        val first = coordinator.arm(1, 0)
        coordinator.onFrame(1, first, target, compact, target, group, nowMs = 10)
        val replacement = coordinator.replace(2, nowMs = 20)!!
        coordinator.onFrame(2, replacement.generation, midway(), compact, target, group, nowMs = 30)
        assertEquals(TakeoverPhase.EXPANDING, coordinator.phase)
        assertFalse(coordinator.releaseRetiringWhenCovered())
        assertTrue(coordinator.retires(1, first))
        coordinator.onFrame(2, replacement.generation, target, compact, target, group, nowMs = 40)
        assertEquals(TakeoverPhase.EXPANDED, coordinator.phase)
        assertTrue(coordinator.releaseRetiringWhenCovered())
        assertFalse(coordinator.hasRetiring())
        assertEquals(0f, coordinator.statusBarAlpha, 0.001f)
        assertFalse(coordinator.releaseRetiringWhenCovered())
    }

    @Test
    fun rapidReplacementsLeaveOneOwnerAndRetireEveryPreviousOne() {
        val coordinator = ExpandedTakeoverCoordinator()
        val a = coordinator.arm(1, 0)
        val b = coordinator.replace(2, nowMs = 10)!!
        val c = coordinator.replace(3, nowMs = 20)!!
        assertEquals(3, coordinator.ownerId)
        assertTrue(coordinator.retires(1, a))
        assertTrue(coordinator.retires(2, b.generation))
        assertEquals(RetiringOwner(2, b.generation), c.retired)
        assertFalse(coordinator.accepts(2, b.generation))
        assertEquals(null, coordinator.replace(3, nowMs = 30))
    }

    @Test
    fun replacementNeedsAnOwnerOnScreen() {
        val coordinator = ExpandedTakeoverCoordinator()
        assertEquals(null, coordinator.replace(2, nowMs = 0))
        val generation = coordinator.arm(1, 0)
        assertTrue(coordinator.abandon(1, generation))
        assertEquals(null, coordinator.replace(2, nowMs = 10))
        val again = coordinator.arm(4, 20)
        coordinator.requestDisable()
        assertEquals(null, coordinator.replace(5, nowMs = 30))
        assertTrue(coordinator.accepts(4, again))
    }

    @Test
    fun finishingARetiringIslandIsGenerationScopedAndKeepsTheOwner() {
        val coordinator = ExpandedTakeoverCoordinator()
        val a = coordinator.arm(1, 0)
        coordinator.onFrame(1, a, target, compact, target, group, nowMs = 10)
        val b = coordinator.replace(2, nowMs = 20)!!
        assertFalse(coordinator.finishRetiring(1, a + 99))
        assertTrue(coordinator.finishRetiring(1, a))
        assertFalse(coordinator.finishRetiring(1, a))
        assertFalse(coordinator.hasRetiring())
        assertTrue(coordinator.accepts(2, b.generation))
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)

        val again = coordinator.arm(1, 30)
        assertFalse(coordinator.retires(1, a))
        assertTrue(coordinator.accepts(1, again))
    }

    private fun midway() = IslandRect(254, 36, 946, 320)
}
