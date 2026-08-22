package com.d4viddf.hyperbridge.service.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionTrackerTest {
    @Test
    fun chronometerTransitionStartsActiveCallAtSourceBase() {
        val tracker = CallSessionTracker()
        val calling = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), now = 10_000L))
        val active = tracker.resolve(
            input(
                classification(CallState.ACTIVE, CallActiveEvidence.CHRONOMETER),
                now = 12_000L,
                showsChronometer = true,
                base = 11_500L
            )
        )

        assertEquals(calling.logicalCallId, active.logicalCallId)
        assertEquals(CallState.ACTIVE, active.state)
        assertEquals(11_500L, active.connectedAt)
        assertEquals(ConnectedAtSource.SOURCE_CHRONOMETER, active.connectedAtSource)
    }

    @Test
    fun repeatedActiveUpdatesDoNotResetConnectedAt() {
        val tracker = CallSessionTracker()
        tracker.resolve(input(classification(CallState.OUTGOING_CALLING), now = 10_000L))
        val active = tracker.resolve(
            input(
                classification(CallState.ACTIVE, CallActiveEvidence.CHRONOMETER),
                now = 12_000L,
                showsChronometer = true,
                base = 11_500L
            )
        )
        val repeated = tracker.resolve(
            input(
                classification(CallState.ACTIVE, CallActiveEvidence.CHRONOMETER),
                now = 15_000L,
                showsChronometer = true,
                base = 14_900L
            )
        )

        assertEquals(active.connectedAt, repeated.connectedAt)
        assertEquals(active.connectedAtSource, repeated.connectedAtSource)
    }

    @Test
    fun answeredIncomingUsesLocalTransitionWhenOldBaseIsUnchanged() {
        val tracker = CallSessionTracker()
        tracker.resolve(
            input(
                classification(CallState.INCOMING_RINGING, CallActiveEvidence.CHRONOMETER, hasAnswer = true),
                now = 20_000L,
                showsChronometer = true,
                base = 19_000L
            )
        )
        val answered = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, hasHangUp = true),
                now = 23_000L,
                showsChronometer = true,
                base = 19_000L
            )
        )

        assertEquals(CallState.ACTIVE, answered.state)
        assertEquals(23_000L, answered.connectedAt)
        assertEquals(ConnectedAtSource.LOCAL_STATE_TRANSITION, answered.connectedAtSource)
    }

    @Test
    fun removedSourceReplacementKeepsLogicalCallAndConnectedAt() {
        val tracker = CallSessionTracker(replacementGraceMs = 1_000L)
        val active = tracker.resolve(
            input(
                classification(CallState.ACTIVE, CallActiveEvidence.EXPLICIT_ONGOING_TYPE),
                now = 30_000L,
                sourceKey = "old",
                showsChronometer = true,
                base = 29_500L
            )
        )
        tracker.markSourceRemoved("old", 31_000L)
        val replacement = tracker.resolve(
            input(
                classification(CallState.ACTIVE, CallActiveEvidence.EXPLICIT_ONGOING_TYPE),
                now = 31_500L,
                sourceKey = "new",
                showsChronometer = true,
                base = 31_400L
            )
        )

        assertEquals(active.logicalCallId, replacement.logicalCallId)
        assertEquals(active.connectedAt, replacement.connectedAt)
    }

    @Test
    fun differentParticipantDoesNotMergeDuringReplacementWindow() {
        val tracker = CallSessionTracker(replacementGraceMs = 1_000L)
        val first = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 40_000L, sourceKey = "one"))
        tracker.markSourceRemoved("one", 40_100L)
        val second = tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING), 40_200L, sourceKey = "two", participant = "person-b")
        )

        assertNotEquals(first.logicalCallId, second.logicalCallId)
    }

    @Test
    fun endingSessionCleansState() {
        val tracker = CallSessionTracker()
        val session = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 50_000L))

        tracker.end(session.logicalCallId)

        assertEquals(0, tracker.size())
        assertNull(tracker.logicalIdForSource("source-1"))
    }

    @Test
    fun staleSessionAndSourceAliasArePruned() {
        val tracker = CallSessionTracker(staleSessionMs = 100L)
        tracker.resolve(input(classification(CallState.OUTGOING_CALLING), now = 1_000L))

        tracker.pruneStale(1_101L)

        assertEquals(0, tracker.size())
        assertNull(tracker.logicalIdForSource("source-1"))
    }

    private fun classification(
        state: CallState,
        evidence: CallActiveEvidence = CallActiveEvidence.NONE,
        hasAnswer: Boolean = false,
        hasHangUp: Boolean = state == CallState.OUTGOING_CALLING
    ) = CallClassification(
        isCall = true,
        state = state,
        reason = "test",
        activeEvidence = evidence,
        hasAnswer = hasAnswer,
        hasDeclineOrHangUp = hasHangUp
    )

    private fun input(
        classification: CallClassification,
        now: Long,
        sourceKey: String = "source-1",
        participant: String = "person-a",
        showsChronometer: Boolean = false,
        base: Long = 0L
    ) = CallSessionInput(
        sourceKey = sourceKey,
        packageName = "example.calls",
        notificationId = 7,
        notificationTag = null,
        groupKey = "calls",
        participantId = participant,
        classification = classification,
        showsChronometer = showsChronometer,
        chronometerBase = base,
        observedAt = now
    )
}
