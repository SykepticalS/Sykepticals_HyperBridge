package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingReplyEchoDetectorTest {
    @Test
    fun youPrefixOnLatestMessageIsEcho() {
        assertTrue(
            OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = listOf(
                    MessageContentCandidate("Ada", "hello"),
                    MessageContentCandidate("You", "on my way"),
                ),
                selfName = "Atahan",
                title = "Ada",
                text = "You: on my way",
            )
        )
    }

    @Test
    fun messagingStyleSelfFlagIsEcho() {
        assertTrue(
            OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = listOf(MessageContentCandidate("Atahan", "ok", isSelf = true)),
                selfName = "Atahan",
                title = "Ada",
                text = "ok",
            )
        )
    }

    @Test
    fun inboundMessageIsNotEcho() {
        assertFalse(
            OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = listOf(MessageContentCandidate("Ada", "hello")),
                selfName = "Atahan",
                title = "Ada",
                text = "hello",
            )
        )
    }

    @Test
    fun rawYouPrefixIsEchoEvenIfLatestLooksInbound() {
        assertTrue(
            OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = listOf(MessageContentCandidate("Ada", "hello")),
                selfName = "Atahan",
                title = "Ada",
                text = "hello",
                extras = listOf("You: on my way"),
            )
        )
    }

    @Test
    fun remoteInputHistoryMatchIsEcho() {
        assertTrue(
            OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = listOf(MessageContentCandidate(null, "on my way")),
                selfName = null,
                title = "Ada",
                text = "on my way",
                remoteInputHistory = arrayOf("on my way"),
            )
        )
    }
}
