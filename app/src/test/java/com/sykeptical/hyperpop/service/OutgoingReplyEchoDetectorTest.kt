package com.sykeptical.hyperpop.service

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
    fun contactNamedMeIsAnInboundMessage() {
        assertFalse(
            OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = listOf(MessageContentCandidate("Me", "Helo")),
                selfName = "You",
                title = "Me",
                text = "Helo",
                conversationTitle = "Me",
            )
        )
    }

    @Test
    fun prefixedContactNamedMeIsAnInboundMessage() {
        assertFalse(
            OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = listOf(MessageContentCandidate("Me", "Helo")),
                selfName = "You",
                title = "Me",
                text = "Me: Helo",
                extras = listOf("Me: Helo"),
                conversationTitle = "Me",
            )
        )
    }

    @Test
    fun missedCallLineWithEmptySenderIsNotAnEcho() {
        assertFalse(
            OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = listOf(MessageContentCandidate("", "Missed Call")),
                selfName = "",
                title = "",
                text = "Missed Call",
                conversationTitle = "",
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
