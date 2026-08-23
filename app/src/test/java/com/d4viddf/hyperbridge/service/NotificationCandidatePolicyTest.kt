package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCandidatePolicyTest {
    @Test
    fun rawUpstreamTitleAndTextWithoutMessagingStyleAreAcceptedWithoutRefresh() {
        val shouldRefresh = NotificationRefreshPolicy.shouldRefresh(
            NotificationRefreshSignals("com.whatsapp", "Kişi", "İleti", false, false)
        )
        val junk = NotificationAcceptancePolicy.isJunk(
            NotificationAcceptanceSignals(
                packageName = "com.whatsapp",
                title = "Kişi",
                text = "İleti",
                hasMessageContent = false,
                hasProgressOrSpecialState = false,
                containsBlockedTerm = false
            )
        )

        assertFalse(shouldRefresh)
        assertFalse(junk)
    }

    @Test
    fun sparseCallbackRequiresRefreshBeforeEmptyAcceptanceDecision() {
        assertTrue(
            NotificationRefreshPolicy.shouldRefresh(
                NotificationRefreshSignals("com.whatsapp", "", "", false, false)
            )
        )
    }

    @Test
    fun usefulRefreshedContentStopsRetrying() {
        assertFalse(
            NotificationRefreshPolicy.shouldRefresh(
                NotificationRefreshSignals("com.whatsapp", "Alice", "Hello", false, false)
            )
        )
    }

    @Test
    fun refreshIsShortAndBounded() {
        assertTrue(NotificationRefreshPolicy.REFRESH_DELAY_MS in 100L..150L)
        assertTrue(NotificationRefreshPolicy.MAX_REFRESH_ATTEMPTS == 2)
    }

    @Test
    fun persistentStateDoesNotWaitForContent() {
        assertFalse(
            NotificationRefreshPolicy.shouldRefresh(
                NotificationRefreshSignals("com.example", "", "", false, true)
            )
        )
    }

    @Test
    fun usefulStandardContentIsAcceptedWithoutGroupSummaryGate() {
        assertFalse(
            NotificationAcceptancePolicy.isJunk(
                NotificationAcceptanceSignals("com.whatsapp", "Sender", "Content", false, false, false)
            )
        )
    }

    @Test
    fun genuinelyEmptyContentIsJunkAfterRefreshAttempts() {
        assertTrue(
            NotificationAcceptancePolicy.isJunk(
                NotificationAcceptanceSignals("com.whatsapp", "", "", false, false, false)
            )
        )
    }

    @Test
    fun periodicSyncSkipsClassificationButDiscreteRecoveryRunsIt() {
        assertFalse(OngoingRecoveryPolicy.shouldClassifyShadeNotifications(refresh = false))
        assertTrue(OngoingRecoveryPolicy.shouldClassifyShadeNotifications(refresh = true))
    }
}
