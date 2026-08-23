package com.sykeptical.hyperbridge.service.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionTrackerTest {
    @Test
    fun ongoingPresentationImmediatelyKeepsOutgoingCallPreConnected() {
        val session = CallSessionTracker().resolve(
            input(classification(CallState.OUTGOING_CALLING, presentation = CallPresentationType.ONGOING), 10_000L)
        )

        assertEquals(CallState.OUTGOING_CALLING, session.state)
        assertNull(session.connectedAt)
        assertNull(CallTimerPolicy.connectedAtForTimer(session))
    }

    @Test
    fun initialOngoingChronometerDoesNotStartTimer() {
        val session = CallSessionTracker().resolve(
            input(
                classification(
                    CallState.OUTGOING_CALLING,
                    CallActiveEvidence.CHRONOMETER_PRESENT,
                    presentation = CallPresentationType.ONGOING
                ),
                now = 10_000L,
                showsChronometer = true,
                base = 9_000L
            )
        )

        assertEquals(CallState.OUTGOING_CALLING, session.state)
        assertNull(session.connectedAt)
        assertNull(CallTimerPolicy.connectedAtForTimer(session))
    }

    @Test
    fun repeatedIdenticalDialingChronometerDoesNotBecomeActive() {
        val tracker = CallSessionTracker()
        tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                10_000L,
                showsChronometer = true,
                base = 9_000L
            )
        )
        val repeated = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                12_000L,
                showsChronometer = true,
                base = 9_000L
            )
        )

        assertEquals(CallState.OUTGOING_CALLING, repeated.state)
        assertNull(repeated.connectedAt)
    }

    @Test
    fun chronometerAppearanceStartsActiveCallAtSourceBase() {
        val tracker = CallSessionTracker()
        val calling = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 10_000L))
        val active = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                12_000L,
                showsChronometer = true,
                base = 11_500L
            )
        )

        assertEquals(calling.logicalCallId, active.logicalCallId)
        assertEquals(CallState.ACTIVE, active.state)
        assertEquals(11_500L, active.connectedAt)
        assertEquals(ConnectedAtSource.SOURCE_CHRONOMETER, active.connectedAtSource)
        assertEquals(CallActiveEvidence.CHRONOMETER_STARTED, active.activeEvidence)
        assertEquals(11_500L, CallTimerPolicy.connectedAtForTimer(active))
    }

    @Test
    fun materialChronometerBaseResetStartsActiveCallAtNewBase() {
        val tracker = CallSessionTracker()
        tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                10_000L,
                showsChronometer = true,
                base = 9_000L
            )
        )
        val active = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                15_000L,
                showsChronometer = true,
                base = 14_000L
            )
        )

        assertEquals(CallState.ACTIVE, active.state)
        assertEquals(14_000L, active.connectedAt)
        assertEquals(CallActiveEvidence.CHRONOMETER_BASE_RESET, active.activeEvidence)
    }

    @Test
    fun smallChronometerBaseNoiseDoesNotStartTimer() {
        val tracker = CallSessionTracker()
        tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                10_000L,
                showsChronometer = true,
                base = 9_000L
            )
        )
        val noisy = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                11_000L,
                showsChronometer = true,
                base = 9_500L
            )
        )

        assertEquals(CallState.OUTGOING_CALLING, noisy.state)
        assertNull(noisy.connectedAt)
    }

    @Test
    fun incomingAnswerDisappearanceStartsActiveAtObservation() {
        val tracker = CallSessionTracker()
        tracker.resolve(
            input(classification(CallState.INCOMING_RINGING, hasAnswer = true, hasHangUp = true), 20_000L)
        )
        val answered = tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING, hasHangUp = true), 23_000L)
        )

        assertEquals(CallState.ACTIVE, answered.state)
        assertEquals(23_000L, answered.connectedAt)
        assertEquals(ConnectedAtSource.OBSERVED_CONNECTION_TRANSITION, answered.connectedAtSource)
        assertEquals(CallActiveEvidence.INCOMING_ANSWERED, answered.activeEvidence)
    }

    @Test
    fun connectedControlAppearanceIsSupportingActiveEvidence() {
        val tracker = CallSessionTracker()
        tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 20_000L))
        val connected = tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING, hasConnectedControl = true), 21_000L)
        )

        assertEquals(CallState.ACTIVE, connected.state)
        assertEquals(21_000L, connected.connectedAt)
        assertEquals(CallActiveEvidence.CONNECTED_ACTIONS_APPEARED, connected.activeEvidence)
    }

    @Test
    fun connectedControlPresentFromFirstCallbackDoesNotStartTimer() {
        val tracker = CallSessionTracker()
        tracker.resolve(input(classification(CallState.OUTGOING_CALLING, hasConnectedControl = true), 20_000L))
        val repeated = tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING, hasConnectedControl = true), 21_000L)
        )

        assertEquals(CallState.OUTGOING_CALLING, repeated.state)
        assertNull(repeated.connectedAt)
    }

    @Test
    fun activeNoisyCallbacksNeverRegressOrResetConnectedAt() {
        val tracker = CallSessionTracker()
        tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 10_000L))
        val active = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                12_000L,
                showsChronometer = true,
                base = 11_500L
            )
        )
        val noisy = tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING, presentation = CallPresentationType.ONGOING), 15_000L)
        )

        assertEquals(CallState.ACTIVE, noisy.state)
        assertEquals(active.connectedAt, noisy.connectedAt)
        assertEquals(active.connectedAtSource, noisy.connectedAtSource)
    }

    @Test
    fun ongoingPresentationDoesNotRegressConnectingState() {
        val tracker = CallSessionTracker()
        tracker.resolve(input(classification(CallState.CONNECTING), 10_000L))
        val repeated = tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING, presentation = CallPresentationType.ONGOING), 11_000L)
        )

        assertEquals(CallState.CONNECTING, repeated.state)
        assertNull(repeated.connectedAt)
    }

    @Test
    fun activeCandidateWithoutBoundaryEvidenceRemainsPreConnected() {
        val first = CallSessionTracker().resolve(input(classification(CallState.ACTIVE), 16_000L))
        assertEquals(CallState.CONNECTING, first.state)
        assertNull(first.connectedAt)

        val tracker = CallSessionTracker()
        val previous = tracker.resolve(input(classification(CallState.OUTGOING_RINGING), 17_000L))
        val candidate = tracker.resolve(input(classification(CallState.ACTIVE), 18_000L))
        assertEquals(CallState.OUTGOING_RINGING, previous.state)
        assertEquals(CallState.OUTGOING_RINGING, candidate.state)
        assertNull(candidate.connectedAt)
    }

    @Test
    fun sourceReplacementWhileCallingKeepsLogicalCallAndBridgeIdentity() {
        val tracker = CallSessionTracker()
        val first = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 30_000L, sourceKey = "old"))
        tracker.markSourceRemoved("old", 30_100L)
        val replacement = tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING), 30_600L, sourceKey = "new", notificationId = 99)
        )

        assertEquals(first.logicalCallId, replacement.logicalCallId)
        assertEquals(first.logicalCallId.hashCode(), replacement.logicalCallId.hashCode())
        assertTrue(replacement.sourceReplacement)
        assertEquals(CallState.OUTGOING_CALLING, replacement.state)
    }

    @Test
    fun replacementPostedBeforeRemovalRebindsOnUniqueParticipantIdentity() {
        val tracker = CallSessionTracker()
        val first = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 35_000L, sourceKey = "old"))
        val replacement = tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING), 35_500L, sourceKey = "new", notificationId = 99)
        )

        assertEquals(first.logicalCallId, replacement.logicalCallId)
        assertTrue(replacement.sourceReplacement)
        assertNull(tracker.markSourceRemoved("old", 35_600L))
    }

    @Test
    fun replacementsNearOldOneSecondBoundaryKeepLogicalCall() {
        listOf(900L, 1_000L, 1_100L, 1_500L).forEach { elapsed ->
            val tracker = CallSessionTracker()
            val first = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 40_000L, sourceKey = "old"))
            tracker.markSourceRemoved("old", 40_000L)
            val replacement = tracker.resolve(
                input(
                    classification(CallState.OUTGOING_CALLING),
                    40_000L + elapsed,
                    sourceKey = "new",
                    notificationId = elapsed.toInt()
                )
            )

            assertEquals("elapsed=$elapsed", first.logicalCallId, replacement.logicalCallId)
        }
        assertTrue(CallReplacementPolicy.REMOVAL_DELAY_MS > CallReplacementPolicy.MATCH_GRACE_MS)
    }

    @Test
    fun answerEvidenceAfterSourceReplacementUpdatesSameLogicalCall() {
        val tracker = CallSessionTracker()
        val first = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 50_000L, sourceKey = "old"))
        tracker.markSourceRemoved("old", 50_100L)
        tracker.resolve(
            input(classification(CallState.OUTGOING_CALLING), 50_600L, sourceKey = "new", notificationId = 99)
        )
        val active = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                52_000L,
                sourceKey = "new",
                notificationId = 99,
                showsChronometer = true,
                base = 51_900L
            )
        )

        assertEquals(first.logicalCallId, active.logicalCallId)
        assertEquals(CallState.ACTIVE, active.state)
        assertEquals(51_900L, active.connectedAt)
    }

    @Test
    fun unansweredOutgoingCallNeverGainsTimerAsTimePasses() {
        val tracker = CallSessionTracker()
        var session = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                60_000L,
                showsChronometer = true,
                base = 59_000L
            )
        )
        repeat(20) { second ->
            session = tracker.resolve(
                input(
                    classification(CallState.OUTGOING_CALLING, CallActiveEvidence.CHRONOMETER_PRESENT),
                    61_000L + second * 1_000L,
                    showsChronometer = true,
                    base = 59_000L
                )
            )
            assertEquals(CallState.OUTGOING_CALLING, session.state)
            assertNull(session.connectedAt)
            assertNull(CallTimerPolicy.connectedAtForTimer(session))
        }

        tracker.end(session.logicalCallId)
        assertEquals(0, tracker.size())
    }

    @Test
    fun differentParticipantDoesNotMergeDuringReplacementWindow() {
        val tracker = CallSessionTracker()
        val first = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 70_000L, sourceKey = "one"))
        tracker.markSourceRemoved("one", 70_100L)
        val second = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING),
                70_200L,
                sourceKey = "two",
                participant = "person-b",
                notificationId = 8
            )
        )

        assertNotEquals(first.logicalCallId, second.logicalCallId)
    }

    @Test
    fun replacementAfterGraceDoesNotMerge() {
        val tracker = CallSessionTracker()
        val first = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 80_000L, sourceKey = "one"))
        tracker.markSourceRemoved("one", 80_000L)
        val second = tracker.resolve(
            input(
                classification(CallState.OUTGOING_CALLING),
                80_000L + CallReplacementPolicy.MATCH_GRACE_MS + 1L,
                sourceKey = "two",
                notificationId = 8
            )
        )

        assertNotEquals(first.logicalCallId, second.logicalCallId)
    }

    @Test
    fun preConnectedStatesCannotExposeTimerAndInvalidSessionFailsFast() {
        val tracker = CallSessionTracker()
        listOf(
            CallState.INCOMING_RINGING,
            CallState.OUTGOING_CALLING,
            CallState.OUTGOING_RINGING,
            CallState.CONNECTING
        ).forEachIndexed { index, state ->
            val session = tracker.resolve(
                input(
                    classification(state, hasAnswer = state == CallState.INCOMING_RINGING),
                    90_000L + index,
                    sourceKey = "source-$index",
                    notificationId = index,
                    participant = "person-$index"
                )
            )
            assertNull(CallTimerPolicy.connectedAtForTimer(session))
        }

        val invalid = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 100_000L))
            .copy(connectedAt = 99_000L)
        assertThrows(IllegalStateException::class.java) {
            CallTimerPolicy.connectedAtForTimer(invalid)
        }
    }

    @Test
    fun endingAndPruningSessionsCleanSourceAliases() {
        val tracker = CallSessionTracker(staleSessionMs = 100L)
        val session = tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 1_000L))
        tracker.end(session.logicalCallId)
        assertEquals(0, tracker.size())
        assertNull(tracker.logicalIdForSource("source-1"))

        tracker.resolve(input(classification(CallState.OUTGOING_CALLING), 2_000L))
        tracker.pruneStale(2_101L)
        assertEquals(0, tracker.size())
        assertNull(tracker.logicalIdForSource("source-1"))
    }

    private fun classification(
        state: CallState,
        evidence: CallActiveEvidence = CallActiveEvidence.NONE,
        hasAnswer: Boolean = false,
        hasHangUp: Boolean = state == CallState.OUTGOING_CALLING,
        hasConnectedControl: Boolean = false,
        presentation: CallPresentationType = CallPresentationType.UNKNOWN
    ) = CallClassification(
        isCall = true,
        state = state,
        reason = "test",
        activeEvidence = evidence,
        presentationType = presentation,
        hasAnswer = hasAnswer,
        hasDeclineOrHangUp = hasHangUp,
        hasConnectedControl = hasConnectedControl
    )

    private fun input(
        classification: CallClassification,
        now: Long,
        sourceKey: String = "source-1",
        participant: String = "person-a",
        notificationId: Int = 7,
        showsChronometer: Boolean = false,
        base: Long = 0L
    ) = CallSessionInput(
        sourceKey = sourceKey,
        packageName = "example.calls",
        notificationId = notificationId,
        notificationTag = null,
        groupKey = "calls",
        participantId = participant,
        classification = classification,
        showsChronometer = showsChronometer,
        chronometerBase = base,
        observedAt = now
    )
}
