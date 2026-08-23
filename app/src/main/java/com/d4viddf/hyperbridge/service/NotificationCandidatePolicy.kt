package com.d4viddf.hyperbridge.service

data class NotificationRefreshSignals(
    val packageName: String,
    val title: String,
    val text: String,
    val hasMessageContent: Boolean,
    val hasProgressOrSpecialState: Boolean
)

/** Bounded refresh policy used after an allowed callback has already claimed a generation. */
object NotificationRefreshPolicy {
    const val MAX_REFRESH_ATTEMPTS = 2
    const val REFRESH_DELAY_MS = 125L

    fun shouldRefresh(signals: NotificationRefreshSignals): Boolean {
        if (signals.hasProgressOrSpecialState) return false
        if (signals.title.equals(signals.packageName, ignoreCase = true) ||
            signals.text.equals(signals.packageName, ignoreCase = true)
        ) return true
        return signals.title.isBlank() && signals.text.isBlank() && !signals.hasMessageContent
    }
}

data class NotificationAcceptanceSignals(
    val packageName: String,
    val title: String,
    val text: String,
    val hasMessageContent: Boolean,
    val hasProgressOrSpecialState: Boolean,
    val containsBlockedTerm: Boolean
)

/** Upstream-compatible junk policy using already resolved title/text content. */
object NotificationAcceptancePolicy {
    fun isJunk(signals: NotificationAcceptanceSignals): Boolean {
        if (signals.hasProgressOrSpecialState) return false
        if (signals.title.isEmpty() && signals.text.isEmpty() && !signals.hasMessageContent) return true
        if (signals.title.equals(signals.packageName, ignoreCase = true) ||
            signals.text.equals(signals.packageName, ignoreCase = true)
        ) return true
        if (signals.containsBlockedTerm) return true
        return false
    }
}

/** Full shade classification is reserved for connect/unlock/screen-on recovery passes. */
object OngoingRecoveryPolicy {
    fun shouldClassifyShadeNotifications(refresh: Boolean): Boolean = refresh
}
