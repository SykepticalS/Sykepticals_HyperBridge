package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCandidatePolicyTest {
    @Test
    fun rawUpstreamTitleAndTextWithoutMessagingStyleAreUsableAndNotJunk() {
        val quality = NotificationCandidatePolicy.quality(
            NotificationCandidateSignals(true, true, false, false, false, false)
        )
        val junk = NotificationAcceptancePolicy.isJunk(
            NotificationAcceptanceSignals(
                packageName = "com.whatsapp",
                title = "Kişi",
                text = "İleti",
                hasMessageContent = false,
                hasProgressOrSpecialState = false,
                containsBlockedTerm = false,
                isGroupSummary = false,
                isMessageType = false
            )
        )

        assertTrue(quality == SourceCandidateQuality.USABLE)
        assertFalse(junk)
    }

    @Test
    fun usefulStandardNotificationIsAccepted() {
        assertFalse(
            NotificationAcceptancePolicy.isJunk(
                NotificationAcceptanceSignals("com.example", "Başlık", "İçerik", false, false, false, false, false)
            )
        )
    }

    @Test
    fun qualityAndAcceptanceDoNotDependOnEnglishOrWhatsappPackageRules() {
        val quality = NotificationCandidatePolicy.quality(
            NotificationCandidateSignals(false, false, true, true, false, false)
        )
        val accepted = !NotificationAcceptancePolicy.isJunk(
            NotificationAcceptanceSignals("com.whatsapp.w4b", "送信者", "新しい通知", false, false, false, false, false)
        )

        assertTrue(quality == SourceCandidateQuality.USABLE)
        assertTrue(accepted)
    }

    @Test
    fun validMessageGroupSummaryIsNotAutomaticallySuppressed() {
        assertFalse(
            NotificationAcceptancePolicy.isJunk(
                NotificationAcceptanceSignals("com.whatsapp", "Sender", "Content", true, false, false, true, true)
            )
        )
    }

    @Test
    fun periodicSyncSkipsClassificationButDiscreteRecoveryRunsIt() {
        assertFalse(OngoingRecoveryPolicy.shouldClassifyShadeNotifications(refresh = false))
        assertTrue(OngoingRecoveryPolicy.shouldClassifyShadeNotifications(refresh = true))
    }
}
