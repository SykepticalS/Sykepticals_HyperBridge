package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandUpdateResolverTest {
    @Test
    fun changedContentForSameLogicalEventKeepsBridgeId() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = 2,
            previous = PreviousIslandPresentation("conversation-a", bridgeId = 42, contentHash = 1)
        )

        assertEquals(IslandPresentationKind.UPDATE, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertTrue(decision.onlyAlertOnce)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun identicalPayloadSkipsRedundantUpdate() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = 7,
            previous = PreviousIslandPresentation("conversation-a", bridgeId = 42, contentHash = 7)
        )

        assertEquals(IslandPresentationKind.UNCHANGED, decision.kind)
        assertEquals(42, decision.bridgeId)
    }

    @Test
    fun differentLogicalEventIsNewAndMayAlert() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-b",
            candidateBridgeId = 99,
            contentHash = 8,
            previous = PreviousIslandPresentation("conversation-a", bridgeId = 42, contentHash = 7)
        )

        assertEquals(IslandPresentationKind.NEW, decision.kind)
        assertEquals(99, decision.bridgeId)
        assertFalse(decision.onlyAlertOnce)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun callStateHashChangeProducesUpdateEvenWithSameIdentity() {
        val callingHash = listOf("Caller", "OUTGOING_CALLING", null).hashCode()
        val activeHash = listOf("Caller", "ACTIVE", 10_000L).hashCode()
        val decision = IslandUpdateResolver.decide(
            logicalId = "call-a",
            candidateBridgeId = 9,
            contentHash = activeHash,
            previous = PreviousIslandPresentation("call-a", bridgeId = 9, contentHash = callingHash)
        )

        assertEquals(IslandPresentationKind.UPDATE, decision.kind)
        assertEquals(9, decision.bridgeId)
    }

    @Test
    fun recoveryPostIsNewStateButCannotAutoExpand() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 42,
            contentHash = 1,
            previous = null,
            presentationReason = IslandPresentationReason.RECONCILE
        )

        assertEquals(IslandPresentationKind.NEW, decision.kind)
        assertEquals(IslandPresentationReason.RECONCILE, decision.presentationReason)
        assertTrue(decision.onlyAlertOnce)
        assertFalse(decision.presentationReason.mayAutoExpand)
    }

    @Test
    fun onlyNewEventMayAutoExpand() {
        assertTrue(IslandPresentationReason.NEW_EVENT.mayAutoExpand)
        assertFalse(IslandPresentationReason.CONTENT_UPDATE.mayAutoExpand)
        assertFalse(IslandPresentationReason.RESTORE.mayAutoExpand)
        assertFalse(IslandPresentationReason.RECONCILE.mayAutoExpand)
    }

    @Test
    fun staleRemovalCannotEndReplacementGeneration() {
        assertFalse(
            NotificationLifecyclePolicy.isCurrentRemoval(
                activeSourceKey = "same-key",
                activeSourcePostTime = 200L,
                removedSourceKey = "same-key",
                removedSourcePostTime = 100L
            )
        )
        assertFalse(
            NotificationLifecyclePolicy.isCurrentRemoval(
                activeSourceKey = "new-key",
                activeSourcePostTime = 200L,
                removedSourceKey = "old-key",
                removedSourcePostTime = 200L
            )
        )
        assertTrue(
            NotificationLifecyclePolicy.isCurrentRemoval(
                activeSourceKey = "same-key",
                activeSourcePostTime = 200L,
                removedSourceKey = "same-key",
                removedSourcePostTime = 200L
            )
        )
    }

    @Test
    fun durablePayloadClearsOneShotFirstFloat() {
        val json = """{"islandFirstFloat":true,"enableFloat":true}"""

        assertEquals(
            """{"islandFirstFloat":false,"enableFloat":true}""",
            NotificationLifecyclePolicy.disableFirstFloat(json)
        )
    }

    @Test
    fun repeatedMessageUpdatesKeepOneBridgeAndNeverReFloat() {
        val first = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 42,
            contentHash = "hello".hashCode(),
            previous = null
        )
        val second = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = "where are you?".hashCode(),
            previous = PreviousIslandPresentation("conversation-a", first.bridgeId, "hello".hashCode())
        )
        val third = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 1000,
            contentHash = "HELLO".hashCode(),
            previous = PreviousIslandPresentation("conversation-a", second.bridgeId, "where are you?".hashCode())
        )

        assertEquals(42, second.bridgeId)
        assertEquals(42, third.bridgeId)
        assertEquals(IslandPresentationKind.UPDATE, second.kind)
        assertEquals(IslandPresentationKind.UPDATE, third.kind)
        assertFalse(second.presentationReason.mayAutoExpand)
        assertFalse(third.presentationReason.mayAutoExpand)
        assertFalse(second.cancelBeforeNotify)
        assertFalse(third.cancelBeforeNotify)
    }
}
