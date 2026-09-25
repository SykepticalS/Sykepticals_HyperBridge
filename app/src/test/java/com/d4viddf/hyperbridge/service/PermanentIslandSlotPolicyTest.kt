package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermanentIslandSlotPolicyTest {
    @Test
    fun firstIncomingIslandUpdatesThePermanentSlot() {
        val plan = PermanentIslandSlotPolicy.postPlan(
            slotAvailable = true,
            occupyingLogicalId = null,
            occupantStillActive = false,
            expansionLocked = true,
            permanentPosted = true,
            logicalId = "island-a",
            alreadyPosted = false,
            previousBridgeId = null,
            eligible = true,
        )
        assertTrue(plan.usePermanentSlot)
        assertTrue(plan.notifyInPlace)
        assertFalse(plan.lockExpansion)
        assertTrue(plan.deferAutoExpand)
    }

    @Test
    fun secondIslandPostsSeparatelyWhileOccupantIsStillActive() {
        val plan = PermanentIslandSlotPolicy.postPlan(
            slotAvailable = true,
            occupyingLogicalId = "island-a",
            occupantStillActive = true,
            expansionLocked = false,
            permanentPosted = true,
            logicalId = "island-b",
            alreadyPosted = false,
            previousBridgeId = null,
            eligible = true,
        )
        assertFalse(plan.usePermanentSlot)
        assertFalse(plan.lockExpansion)
        assertFalse(plan.deferAutoExpand)
    }

    @Test
    fun occupantContentUpdateStaysOnPermanentAndKeepsExpansionLock() {
        val plan = PermanentIslandSlotPolicy.postPlan(
            slotAvailable = true,
            occupyingLogicalId = "island-a",
            occupantStillActive = true,
            expansionLocked = true,
            permanentPosted = true,
            logicalId = "island-a",
            alreadyPosted = true,
            previousBridgeId = PermanentIslandManager.PERMANENT_BRIDGE_ID,
            eligible = true,
        )
        assertTrue(plan.usePermanentSlot)
        assertTrue(plan.notifyInPlace)
        assertTrue(plan.lockExpansion)
        assertFalse(plan.deferAutoExpand)
    }

    @Test
    fun lastRemainingExtraIslandRestoresTheEmptyStub() {
        val plan = PermanentIslandSlotPolicy.afterRemoval(
            slotAvailable = true,
            occupyingLogicalId = "island-a",
            remaining = listOf(
                PermanentSlotIslandRef("island-b", bridgeId = 42, postTime = 200L),
            ),
        )
        assertEquals(PermanentSlotAfterRemoval.RESTORE_STUB, plan.action)
    }

    @Test
    fun lastRemainingOccupantIsNotCopiedOntoTheStub() {
        val plan = PermanentIslandSlotPolicy.afterRemoval(
            slotAvailable = true,
            occupyingLogicalId = "island-a",
            remaining = listOf(
                PermanentSlotIslandRef(
                    "island-a",
                    PermanentIslandManager.PERMANENT_BRIDGE_ID,
                    postTime = 100L,
                ),
            ),
        )
        assertEquals(PermanentSlotAfterRemoval.COLLAPSE_LAST_ON_PERMANENT, plan.action)
        assertEquals("island-a", plan.lastLogicalId)
    }

    @Test
    fun occupantStillAliveIsNotReplacedByANewerExtra() {
        val plan = PermanentIslandSlotPolicy.afterRemoval(
            slotAvailable = true,
            occupyingLogicalId = "island-a",
            remaining = listOf(
                PermanentSlotIslandRef(
                    "island-a",
                    PermanentIslandManager.PERMANENT_BRIDGE_ID,
                    postTime = 100L,
                ),
                PermanentSlotIslandRef("island-c", bridgeId = 7, postTime = 300L),
            ),
        )
        assertEquals(PermanentSlotAfterRemoval.COLLAPSE_LAST_ON_PERMANENT, plan.action)
        assertEquals("island-a", plan.lastLogicalId)
    }

    @Test
    fun emptySlotRestoresTheStub() {
        val plan = PermanentIslandSlotPolicy.afterRemoval(
            slotAvailable = true,
            occupyingLogicalId = "island-a",
            remaining = emptyList(),
        )
        assertEquals(PermanentSlotAfterRemoval.RESTORE_STUB, plan.action)
    }

    @Test
    fun nativeSystemOccupantDoesNotRestoreTheStubWhenARealIslandLeaves() {
        val plan = PermanentIslandSlotPolicy.afterRemoval(
            slotAvailable = true,
            occupyingLogicalId = NativeSystemIslandPolicy.logicalId("charge"),
            remaining = emptyList(),
        )
        assertEquals(PermanentSlotAfterRemoval.NONE, plan.action)
    }

    @Test
    fun ineligibleIslandsDoNotUseThePermanentSlot() {
        val plan = PermanentIslandSlotPolicy.postPlan(
            slotAvailable = true,
            occupyingLogicalId = null,
            occupantStillActive = false,
            expansionLocked = true,
            permanentPosted = true,
            logicalId = "call-a",
            alreadyPosted = false,
            previousBridgeId = null,
            eligible = false,
        )
        assertFalse(plan.usePermanentSlot)
    }

    @Test
    fun callIslandsOccupyThePermanentSlotWhenItIsAvailable() {
        assertTrue(
            PermanentIslandSlotPolicy.eligibleForPermanentSlot(
                preferSourceFocus = true,
                slotAvailable = true,
            )
        )
        assertFalse(
            PermanentIslandSlotPolicy.useSourceFocus(
                preferSourceFocus = true,
                usePermanentSlot = true,
                slotAvailable = true,
            )
        )
        val plan = PermanentIslandSlotPolicy.postPlan(
            slotAvailable = true,
            occupyingLogicalId = null,
            occupantStillActive = false,
            expansionLocked = true,
            permanentPosted = true,
            logicalId = "call-a",
            alreadyPosted = false,
            previousBridgeId = null,
            eligible = true,
        )
        assertTrue(plan.usePermanentSlot)
        assertTrue(plan.notifyInPlace)
        assertTrue(plan.deferAutoExpand)
    }

    @Test
    fun sourceFocusIsKeptOnlyWhenThePermanentSlotIsUnavailable() {
        assertFalse(
            PermanentIslandSlotPolicy.eligibleForPermanentSlot(
                preferSourceFocus = true,
                slotAvailable = false,
            )
        )
        assertTrue(
            PermanentIslandSlotPolicy.useSourceFocus(
                preferSourceFocus = true,
                usePermanentSlot = false,
                slotAvailable = false,
            )
        )
        assertFalse(
            PermanentIslandSlotPolicy.useSourceFocus(
                preferSourceFocus = true,
                usePermanentSlot = false,
                slotAvailable = true,
            )
        )
    }

    @Test
    fun foldingLastIslandBackForcesLockEvenWhenExpansionWasUnlocked() {
        val plan = PermanentIslandSlotPolicy.postPlan(
            slotAvailable = true,
            occupyingLogicalId = "island-a",
            occupantStillActive = false,
            expansionLocked = false,
            permanentPosted = true,
            logicalId = "island-b",
            alreadyPosted = true,
            previousBridgeId = 42,
            eligible = true,
            forcePermanent = true,
            forceLockExpansion = true,
        )
        assertTrue(plan.usePermanentSlot)
        assertTrue(plan.lockExpansion)
        assertFalse(plan.deferAutoExpand)
    }

    @Test
    fun delayedAutoExpandUnlocksThePermanentSlotWithoutDeferringAgain() {
        val plan = PermanentIslandSlotPolicy.postPlan(
            slotAvailable = true,
            occupyingLogicalId = "island-a",
            occupantStillActive = true,
            expansionLocked = true,
            permanentPosted = true,
            logicalId = "island-a",
            alreadyPosted = true,
            previousBridgeId = PermanentIslandManager.PERMANENT_BRIDGE_ID,
            eligible = true,
            forcePermanent = true,
            forceUnlockExpansion = true,
        )
        assertTrue(plan.usePermanentSlot)
        assertFalse(plan.lockExpansion)
        assertFalse(plan.deferAutoExpand)
    }
}
