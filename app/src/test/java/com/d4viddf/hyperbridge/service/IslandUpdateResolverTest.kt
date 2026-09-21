package com.d4viddf.hyperbridge.service

import com.d4viddf.hyperbridge.models.MessageEventFingerprint
import com.d4viddf.hyperbridge.models.MessageEventFingerprintSource
import com.d4viddf.hyperbridge.models.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandUpdateResolverTest {
    @Test
    fun newLogicalIdAlwaysCreatesNewIsland() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 42,
            contentHash = 123,
            previous = null
        )

        assertEquals(IslandPresentationKind.NEW, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertFalse(decision.onlyAlertOnce)
        assertEquals(IslandPresentationReason.NEW_EVENT, decision.presentationReason)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun changedContentUpdatesExistingIslandSilently() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 99,
            contentHash = 456,
            previous = PreviousIslandPresentation("conversation-a", 42, 123),
            presentationReason = IslandPresentationReason.CONTENT_UPDATE
        )

        assertEquals(IslandPresentationKind.UPDATE, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertTrue(decision.onlyAlertOnce)
        assertEquals(IslandPresentationReason.CONTENT_UPDATE, decision.presentationReason)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun unchangedContentDoesNotNotify() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 99,
            contentHash = 123,
            previous = PreviousIslandPresentation("conversation-a", 42, 123)
        )

        assertEquals(IslandPresentationKind.UNCHANGED, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertTrue(decision.onlyAlertOnce)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun sourcePromotionAllowsAutoExpand() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 42,
            contentHash = 123,
            previous = null,
            presentationReason = IslandPresentationReason.SOURCE_PROMOTION
        )

        assertEquals(IslandPresentationKind.NEW, decision.kind)
        assertFalse(decision.onlyAlertOnce)
        assertEquals(IslandPresentationReason.SOURCE_PROMOTION, decision.presentationReason)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun restorePreservesOriginalBridgeIdSilently() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 42,
            contentHash = 123,
            previous = null,
            presentationReason = IslandPresentationReason.RESTORE
        )

        assertEquals(IslandPresentationKind.NEW, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertTrue(decision.onlyAlertOnce)
        assertEquals(IslandPresentationReason.RESTORE, decision.presentationReason)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun sameConversationMessageUpdatesExistingIslandInPlace() {
        val first = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 42,
            contentHash = "hello".hashCode(),
            previous = null,
            notificationType = NotificationType.MESSAGE
        )
        val second = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = "where are you?".hashCode(),
            previous = PreviousIslandPresentation("conversation-a", first.bridgeId, "hello".hashCode()),
            notificationType = NotificationType.MESSAGE,
            messageEventFingerprint = messageEvent(200L, 2)
        )
        val third = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 1000,
            contentHash = "HELLO".hashCode(),
            previous = PreviousIslandPresentation(
                "conversation-a",
                second.bridgeId,
                "where are you?".hashCode(),
                messageEventFingerprint = messageEvent(200L, 2)
            ),
            notificationType = NotificationType.MESSAGE,
            messageEventFingerprint = messageEvent(300L, 3)
        )

        assertEquals(first.bridgeId, second.bridgeId)
        assertEquals(second.bridgeId, third.bridgeId)
        assertEquals(IslandPresentationKind.UPDATE, second.kind)
        assertEquals(IslandPresentationKind.UPDATE, third.kind)
        assertFalse(second.presentationReason.mayAutoExpand)
        assertFalse(third.presentationReason.mayAutoExpand)
        assertFalse(second.cancelBeforeNotify)
        assertFalse(third.cancelBeforeNotify)
    }

    @Test
    fun chromeDownloadProgressIsAnInPlaceUpdate() {
        val first = IslandUpdateResolver.decide(
            logicalId = "download:com.android.chrome:file:report.pdf",
            candidateBridgeId = 42,
            contentHash = 10,
            previous = null,
            notificationType = NotificationType.DOWNLOAD
        )
        val second = IslandUpdateResolver.decide(
            logicalId = "download:com.android.chrome:file:report.pdf",
            candidateBridgeId = 99,
            contentHash = 55,
            previous = PreviousIslandPresentation(
                "download:com.android.chrome:file:report.pdf",
                first.bridgeId,
                10
            ),
            notificationType = NotificationType.DOWNLOAD,
            presentationReason = IslandPresentationReason.CONTENT_UPDATE
        )

        assertEquals(IslandPresentationKind.UPDATE, second.kind)
        assertEquals(first.bridgeId, second.bridgeId)
        assertTrue(second.onlyAlertOnce)
        assertFalse(second.cancelBeforeNotify)
        assertFalse(second.presentationReason.mayAutoExpand)
    }

    @Test
    fun identicalMessageRepostRemainsUnchanged() {
        val event = messageEvent(timestamp = 200L, messageCount = 2)
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = "hello".hashCode(),
            previous = PreviousIslandPresentation(
                "conversation-a",
                42,
                "hello".hashCode(),
                messageEventFingerprint = event
            ),
            notificationType = NotificationType.MESSAGE,
            messageEventFingerprint = event
        )

        assertEquals(IslandPresentationKind.UNCHANGED, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun sameMessageRenderingDriftIsASilentInPlaceUpdate() {
        val event = messageEvent(timestamp = 200L, messageCount = 2)
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = "same message, refreshed artwork".hashCode(),
            previous = PreviousIslandPresentation(
                "conversation-a",
                42,
                "same message".hashCode(),
                messageEventFingerprint = event
            ),
            notificationType = NotificationType.MESSAGE,
            messageEventFingerprint = event
        )

        assertEquals(IslandPresentationKind.UPDATE, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertTrue(decision.onlyAlertOnce)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun sameContentAndNotificationWhenIgnoresRefreshedPostTime() {
        val previousEvent = MessageEventFingerprint(
            source = MessageEventFingerprintSource.NOTIFICATION_WHEN,
            primaryValue = 200L,
            secondaryValue = 300L
        )
        val repostEvent = previousEvent.copy(secondaryValue = 400L)
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = "hello".hashCode(),
            previous = PreviousIslandPresentation(
                "conversation-a",
                42,
                "hello".hashCode(),
                messageEventFingerprint = previousEvent
            ),
            notificationType = NotificationType.MESSAGE,
            messageEventFingerprint = repostEvent
        )

        assertEquals(IslandPresentationKind.UNCHANGED, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun identicalTextWithDifferentMessageEventUpdatesInPlace() {
        val firstEvent = messageEvent(timestamp = 100L, messageCount = 1)
        val secondEvent = messageEvent(timestamp = 200L, messageCount = 2)
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = "hello".hashCode(),
            previous = PreviousIslandPresentation(
                "conversation-a",
                42,
                "hello".hashCode(),
                messageEventFingerprint = firstEvent
            ),
            notificationType = NotificationType.MESSAGE,
            messageEventFingerprint = secondEvent
        )

        assertEquals(IslandPresentationKind.UPDATE, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertTrue(decision.onlyAlertOnce)
        assertEquals(IslandPresentationReason.CONTENT_UPDATE, decision.presentationReason)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun messageLikeStandardNotificationUpdatesExistingConversation() {
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 999,
            contentHash = "hello".hashCode(),
            previous = PreviousIslandPresentation(
                "conversation-a",
                42,
                "hello".hashCode(),
                messageEventFingerprint = messageEvent(100L, 1)
            ),
            notificationType = NotificationType.STANDARD,
            isMessagingEvent = true,
            messageEventFingerprint = messageEvent(200L, 2)
        )

        assertEquals(IslandPresentationKind.UPDATE, decision.kind)
        assertEquals(42, decision.bridgeId)
        assertFalse(decision.cancelBeforeNotify)
    }

    @Test
    fun messageBridgeIdsStayStableForTheSameConversation() {
        val first = MessageBridgeIdPolicy.candidate("conversation-a", 2L, 123)
        val second = MessageBridgeIdPolicy.candidate("conversation-a", 3L, 456)

        assertTrue(first < -1_000_000_000)
        assertTrue(second < -1_000_000_000)
        assertEquals(first, second)
        assertTrue(first != PermanentIslandManager.PERMANENT_BRIDGE_ID)
        assertTrue(first != MessageBridgeIdPolicy.candidate("conversation-b", 2L, 123))
    }

    @Test
    fun messageBridgeIdIgnoresEventIdentitySoNotifyCanUpdate() {
        val first = MessageBridgeIdPolicy.candidate(
            logicalId = "conversation-a",
            generation = 2L,
            contentHash = 123,
            messageEventFingerprint = messageEvent(100L, 1)
        )
        val second = MessageBridgeIdPolicy.candidate(
            logicalId = "conversation-a",
            generation = 2L,
            contentHash = 123,
            messageEventFingerprint = messageEvent(200L, 2)
        )

        assertEquals(first, second)
    }

    @Test
    fun internalReplacementMarkerIsConsumedOnceAndExpires() {
        val registry = InternalBridgeReplacementRegistry(ttlMs = 100L, maxEntries = 2)
        registry.mark(42, "conversation-a", generation = 2L, now = 1_000L)

        assertEquals("conversation-a", registry.consume(42, now = 1_050L)?.logicalId)
        assertEquals(null, registry.consume(42, now = 1_050L))

        registry.mark(43, "conversation-a", generation = 3L, now = 2_000L)
        assertEquals(null, registry.consume(43, now = 2_101L))
    }

    @Test
    fun permanentIslandIsOnlyDesiredWhenNoRealOrNativeIslandExists() {
        assertTrue(PermanentIslandVisibilityPolicy.desiredActive(true, 0, false, false, false))
        assertFalse(PermanentIslandVisibilityPolicy.desiredActive(true, 1, false, false, false))
        assertFalse(PermanentIslandVisibilityPolicy.desiredActive(true, 0, true, false, false))
        assertFalse(PermanentIslandVisibilityPolicy.desiredActive(true, 0, false, true, true))
        assertFalse(PermanentIslandVisibilityPolicy.desiredActive(false, 0, false, false, false))
    }

    @Test
    fun reconciliationNeverReapsMessageForMissingRegroupedSource() {
        assertFalse(
            NotificationLifecyclePolicy.shouldReapMissingSource(
                type = NotificationType.MESSAGE,
                removeOriginalNotification = false,
                dismissWithOriginal = true
            )
        )
        assertFalse(
            NotificationLifecyclePolicy.shouldReapMissingSource(
                type = NotificationType.STANDARD,
                removeOriginalNotification = false,
                dismissWithOriginal = true
            )
        )
        assertFalse(
            NotificationLifecyclePolicy.shouldReapMissingSource(
                type = NotificationType.DOWNLOAD,
                removeOriginalNotification = false,
                dismissWithOriginal = true
            )
        )
    }

    @Test
    fun appCancelDoesNotDismissInProgressDownloadIsland() {
        assertFalse(
            NotificationLifecyclePolicy.shouldDismissIslandOnSourceRemoval(
                type = NotificationType.DOWNLOAD,
                dismissWithOriginal = true,
                isAppCancellation = true
            )
        )
        assertFalse(
            NotificationLifecyclePolicy.shouldDismissIslandOnSourceRemoval(
                type = NotificationType.PROGRESS,
                dismissWithOriginal = true,
                isAppCancellation = true
            )
        )
    }

    @Test
    fun messageReplacementCancelDoesNotDismissTheIsland() {
        assertFalse(
            NotificationLifecyclePolicy.shouldDismissIslandOnSourceRemoval(
                type = NotificationType.MESSAGE,
                dismissWithOriginal = true,
                isAppCancellation = true
            )
        )
        assertFalse(
            NotificationLifecyclePolicy.shouldDismissIslandOnSourceRemoval(
                type = NotificationType.STANDARD,
                dismissWithOriginal = true,
                isAppCancellation = true
            )
        )
        assertFalse(
            NotificationLifecyclePolicy.shouldDismissIslandOnSourceRemoval(
                type = NotificationType.MESSAGE,
                dismissWithOriginal = true,
                isAppCancellation = false
            )
        )
    }

    @Test
    fun cacheMissOnTheSameConversationStillReusesTheFocusNotifyId() {
        val logicalId = "conversation:whatsapp:ada"
        val firstId = MessageBridgeIdPolicy.candidate(logicalId, 1L, "hello".hashCode())
        val secondId = MessageBridgeIdPolicy.candidate(logicalId, 99L, "uhh".hashCode())
        assertEquals(firstId, secondId)

        val afterCacheClear = IslandUpdateResolver.decide(
            logicalId = logicalId,
            candidateBridgeId = secondId,
            contentHash = "uhh".hashCode(),
            previous = null,
            notificationType = NotificationType.STANDARD,
            isMessagingEvent = true,
            messageEventFingerprint = messageEvent(200L, 2)
        )
        assertEquals(IslandPresentationKind.NEW, afterCacheClear.kind)
        assertEquals(firstId, afterCacheClear.bridgeId)
        assertFalse(afterCacheClear.cancelBeforeNotify)
    }

    @Test
    fun sameConversationKeepsBridgeIdWhenContentChanges() {
        val first = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 42,
            contentHash = 1,
            previous = null,
            notificationType = NotificationType.MESSAGE
        )
        val update = IslandUpdateResolver.decide(
            logicalId = "conversation-a",
            candidateBridgeId = 99,
            contentHash = 2,
            previous = PreviousIslandPresentation("conversation-a", first.bridgeId, 1),
            notificationType = NotificationType.MESSAGE,
            messageEventFingerprint = messageEvent(200L, 2)
        )
        assertEquals(IslandPresentationKind.UPDATE, update.kind)
        assertEquals(first.bridgeId, update.bridgeId)
        assertFalse(update.cancelBeforeNotify)
    }

    @Test
    fun reconciliationStillReapsSourceBoundIslandTypes() {
        assertTrue(
            NotificationLifecyclePolicy.shouldReapMissingSource(
                type = NotificationType.MEDIA,
                removeOriginalNotification = false,
                dismissWithOriginal = false
            )
        )
    }

    @Test
    fun callProxyStaysPostedEvenWithoutOngoingSourceFlag() {
        assertTrue(NotificationLifecyclePolicy.proxyStaysPosted(NotificationType.CALL))
        assertFalse(NotificationLifecyclePolicy.proxyStaysPosted(NotificationType.STANDARD))
        assertFalse(NotificationLifecyclePolicy.proxyStaysPosted(NotificationType.MESSAGE))
    }

    @Test
    fun callReconciliationLooksAtSourceKeyNotLogicalId() {
        val current = setOf("0|com.android.dialer|42|null|0")
        assertTrue(
            NotificationLifecyclePolicy.isTrackedSourcePresent(
                logicalId = "call:com.android.dialer:1",
                sourceKey = "0|com.android.dialer|42|null|0",
                currentSourceKeys = current
            )
        )
        assertFalse(
            NotificationLifecyclePolicy.isTrackedSourcePresent(
                logicalId = "call:com.android.dialer:1",
                sourceKey = "0|com.android.dialer|42|null|0",
                currentSourceKeys = emptySet()
            )
        )
    }

    private fun messageEvent(timestamp: Long, messageCount: Int): MessageEventFingerprint {
        return MessageEventFingerprint(
            source = MessageEventFingerprintSource.MESSAGING_STYLE,
            primaryValue = timestamp,
            messageCount = messageCount
        )
    }
}
