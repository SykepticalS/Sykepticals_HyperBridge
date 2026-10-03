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
    fun watchdogAbandonsASilentTakeover() {
        val coordinator = ExpandedTakeoverCoordinator(frameTimeoutMs = 3_000)
        val generation = coordinator.arm(1, 0)
        coordinator.onFrame(1, generation, target, compact, target, group, nowMs = 100)
        assertFalse(coordinator.expireIfStale(2_000))
        assertTrue(coordinator.expireIfStale(3_200))
        assertEquals(TakeoverPhase.NATIVE, coordinator.phase)
        assertEquals(1f, coordinator.statusBarAlpha, 0.001f)
    }

    private fun midway() = IslandRect(254, 36, 946, 320)
}
