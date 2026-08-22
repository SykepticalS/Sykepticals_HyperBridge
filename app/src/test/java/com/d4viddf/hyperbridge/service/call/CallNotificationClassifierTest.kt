package com.d4viddf.hyperbridge.service.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallNotificationClassifierTest {
    private val classifier = CallNotificationClassifier(
        answerKeywords = listOf("answer", "accept"),
        declineKeywords = listOf("decline", "reject"),
        hangUpKeywords = listOf("hang", "end"),
        speakerKeywords = listOf("speaker")
    )

    @Test
    fun incomingCallWithAnswerActionIsRinging() {
        val result = classifier.classify(
            baseSignals(
                category = CallNotificationClassifier.CATEGORY_CALL,
                actions = listOf(action("Decline"), action("Answer"))
            )
        )

        assertTrue(result.isCall)
        assertEquals(CallState.INCOMING_RINGING, result.state)
    }

    @Test
    fun outgoingHangUpWithoutChronometerIsNotActive() {
        val result = classifier.classify(
            baseSignals(
                category = CallNotificationClassifier.CATEGORY_CALL,
                actions = listOf(action("Hang up"))
            )
        )

        assertEquals(CallState.OUTGOING_CALLING, result.state)
        assertEquals(CallActiveEvidence.NONE, result.activeEvidence)
    }

    @Test
    fun reliableChronometerIsActiveEvidence() {
        val result = classifier.classify(
            baseSignals(
                category = CallNotificationClassifier.CATEGORY_CALL,
                showsChronometer = true,
                whenTime = 1_000L,
                actions = listOf(action("Hang up"))
            )
        )

        assertEquals(CallState.ACTIVE, result.state)
        assertEquals(CallActiveEvidence.CHRONOMETER, result.activeEvidence)
    }

    @Test
    fun malformedStandardNotificationIsNotCall() {
        val result = classifier.classify(baseSignals())

        assertFalse(result.isCall)
        assertEquals(CallState.ENDED, result.state)
    }

    @Test
    fun firstChronometerOnlyObservationDoesNotStartTimer() {
        val now = 50_000L
        val tracker = CallSessionTracker()
        val classification = classifier.classify(
            baseSignals(
                category = CallNotificationClassifier.CATEGORY_CALL,
                showsChronometer = true,
                whenTime = now,
                actions = listOf(action("Hang up"))
            )
        )

        val session = tracker.resolve(input(classification, now, showsChronometer = true, base = now))

        assertEquals(CallState.OUTGOING_CALLING, session.state)
        assertNull(session.connectedAt)
    }

    private fun action(title: String) = CallActionSignal(title, 0, true)

    private fun input(
        classification: CallClassification,
        now: Long,
        sourceKey: String = "source-1",
        notificationId: Int = 7,
        showsChronometer: Boolean = false,
        base: Long = 0L
    ) = CallSessionInput(
        sourceKey = sourceKey,
        packageName = "example.calls",
        notificationId = notificationId,
        notificationTag = null,
        groupKey = "calls",
        participantId = "person-a",
        classification = classification,
        showsChronometer = showsChronometer,
        chronometerBase = base,
        observedAt = now
    )

    private fun baseSignals(
        category: String? = null,
        template: String? = null,
        callType: Int? = null,
        showsChronometer: Boolean = false,
        whenTime: Long = 0L,
        actions: List<CallActionSignal> = emptyList()
    ) = CallNotificationSignals(
        category = category,
        template = template,
        callType = callType,
        showsChronometer = showsChronometer,
        whenTime = whenTime,
        actions = actions
    )
}
