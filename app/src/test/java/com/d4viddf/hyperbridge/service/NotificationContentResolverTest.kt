package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentResolverTest {
    @Test
    fun blankTopLevelMessagingStyleResolvesLatestMessage() {
        val content = NotificationContentResolver.resolve(
            title = "",
            text = "",
            bigTitle = null,
            bigText = null,
            messages = listOf(MessageContentCandidate("Alice", "hello")),
            isMessageStyle = true
        )

        assertEquals("Alice", content.title)
        assertEquals("hello", content.text)
        assertTrue(content.hasMessageContent)
    }

    @Test
    fun messagingContentPreventsEmptyClassification() {
        val content = NotificationContentResolver.resolve(
            title = null,
            text = null,
            bigTitle = null,
            bigText = null,
            messages = listOf(MessageContentCandidate("Alice", "hello")),
            isMessageStyle = true
        )

        assertFalse(content.title.isEmpty() && content.text.isEmpty() && !content.hasMessageContent)
    }

    @Test
    fun validTopLevelContentTakesPrecedence() {
        val content = NotificationContentResolver.resolve(
            title = "Conversation",
            text = "Top-level text",
            bigTitle = "Big title",
            bigText = "Big text",
            messages = listOf(MessageContentCandidate("Alice", "message text")),
            isMessageStyle = true
        )

        assertEquals("Conversation", content.title)
        assertEquals("Top-level text", content.text)
    }
}
