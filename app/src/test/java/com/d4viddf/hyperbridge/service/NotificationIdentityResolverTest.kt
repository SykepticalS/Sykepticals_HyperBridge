package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdentityResolverTest {
    @Test
    fun personNameBecomesTitleAndCaptionStaysBody() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "TikTok",
            title = "Shared a video",
            text = "Watch this",
            personNames = listOf("Ada"),
        )
        assertEquals("Ada", title)
        assertEquals("Watch this", text)
    }

    @Test
    fun conversationTitleWinsOverGenericCaptionTitle() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "TikTok",
            title = "Sent you a video",
            text = "Sent you a video",
            conversationTitle = "Skepttral",
            subText = "TikTok",
        )
        assertEquals("Skepttral", title)
        assertEquals("Sent you a video", text)
    }

    @Test
    fun appLabelAndDuplicateSubTextAreNotUsedAsBody() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "TikTok",
            title = "Ada",
            text = "Shared a video",
            subText = "TikTok",
        )
        assertEquals("Ada", title)
        assertEquals("Shared a video", text)
    }

    @Test
    fun messagingStyleCompoundTitleIsNotUsedAsSubtitle() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "Instagram",
            title = "amerikanbebesi: nisa",
            text = "Sent you a post",
            conversationTitle = "amerikanbebesi",
            personNames = listOf("nisa"),
            messageSender = "nisa",
            messageText = "Sent you a post",
            ticker = "Sent you a post",
            selfName = "atahan.",
        )
        assertEquals("amerikanbebesi", title)
        assertEquals("Sent you a post", text)
    }

    @Test
    fun tickerSuppliesSenderWhenExtrasAreOnlyCaptions() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "TikTok",
            title = "Look ->",
            text = "Shared a video",
            ticker = "Ada: Look ->",
        )
        assertEquals("Ada", title)
        assertEquals("Shared a video", text)
    }

    @Test
    fun leadingShareCaptionNameBecomesTitle() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "TikTok",
            title = "Ada shared a video",
            text = "Look ->",
        )
        assertEquals("Ada", title)
        assertEquals("Look ->", text)
    }

    @Test
    fun groupConversationUsesSenderTitleAndRawMessage() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "WhatsApp",
            title = "Soemthing (3 messages): Skeptral Wha",
            text = "hi",
            conversationTitle = "Soemthing (3 messages)",
            personNames = listOf("Skeptral Wha"),
            messageSender = "Skeptral Wha",
            messageText = "hi",
            selfName = "You",
            isGroupConversation = true,
        )
        assertEquals("Skeptral Wha", title)
        assertEquals("hi", text)
    }

    @Test
    fun unreadCountConversationTitleUsesSenderOnTheLeft() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "WhatsApp",
            title = "Family (2 messages)",
            text = "hello there",
            conversationTitle = "Family (2 messages)",
            personNames = listOf("Mom"),
            messageSender = "Mom",
            messageText = "hello there",
            selfName = "You",
        )
        assertEquals("Mom", title)
        assertEquals("hello there", text)
    }

    @Test
    fun oneToOneMessagesStayUnprefixed() {
        val (title, text) = NotificationIdentityResolver.resolve(
            appLabel = "WhatsApp",
            title = "Nisamm",
            text = "Cise gidiombb",
            personNames = listOf("Nisamm"),
            messageSender = "Nisamm",
            messageText = "Cise gidiombb",
            selfName = "You",
            isGroupConversation = false,
        )
        assertEquals("Nisamm", title)
        assertEquals("Cise gidiombb", text)
    }

    @Test
    fun mediaShareCaptionsAreDetected() {
        assertTrue(NotificationIdentityResolver.isMediaShareCaption("Sent you a post"))
        assertTrue(NotificationIdentityResolver.isMediaShareCaption("Shared a video"))
        assertTrue(NotificationIdentityResolver.isMediaShareCaption("nisa sent you a reel"))
    }
}
