package com.d4viddf.hyperbridge.service.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessageNotificationResolverTest {
    private val resolver = MessageNotificationResolver()

    @Test
    fun twoConversationsSharingGroupKeyDoNotMerge() {
        val first = resolver.resolve(signals(shortcutId = "chat-a"))
        val second = resolver.resolve(signals(shortcutId = "chat-b"))

        assertNotEquals(first.logicalId, second.logicalId)
    }

    @Test
    fun sameConversationReusesLogicalIdentityAcrossNotificationSlots() {
        val first = resolver.resolve(signals(notificationId = 9, shortcutId = "chat-a"))
        val second = resolver.resolve(signals(notificationId = 10, shortcutId = "chat-a"))

        assertEquals(first.logicalId, second.logicalId)
    }

    @Test
    fun sameSenderTitleIsConversationIdentityWhenShortcutIsMissing() {
        val first = resolver.resolve(signals(shortcutId = null, conversationTitle = "Ada", notificationId = 1))
        val second = resolver.resolve(signals(shortcutId = null, conversationTitle = "Ada", notificationId = 2))
        val other = resolver.resolve(signals(shortcutId = null, conversationTitle = "Sam", notificationId = 3))

        assertEquals(first.logicalId, second.logicalId)
        assertNotEquals(first.logicalId, other.logicalId)
        assertEquals("conversation", first.source)
    }

    @Test
    fun stableNotificationSlotIgnoresMutableDisplayTitle() {
        val first = resolver.resolve(signals(shortcutId = null))
        val second = resolver.resolve(signals(shortcutId = null))

        assertEquals(first.logicalId, second.logicalId)
        assertEquals("notification-slot", first.source)
    }

    @Test
    fun groupSummaryUsesItsOwnAndroidNotificationSlotAndIsNotSuppressed() {
        val summary = resolver.resolve(signals(isSummary = true, shortcutId = "aggregate", notificationId = 10))
        val child = resolver.resolve(signals(isSummary = false, shortcutId = "chat-a"))

        assertNotEquals(summary.logicalId, child.logicalId)
        assertEquals("notification-slot", summary.source)
    }

    private fun signals(
        shortcutId: String? = "chat",
        isSummary: Boolean = false,
        notificationId: Int = 9,
        conversationTitle: String? = null,
    ) = MessageNotificationSignals(
        packageName = "example.messages",
        notificationId = notificationId,
        notificationTag = null,
        shortcutId = shortcutId,
        locusId = null,
        conversationTitle = conversationTitle,
        isGroupSummary = isSummary
    )
}
