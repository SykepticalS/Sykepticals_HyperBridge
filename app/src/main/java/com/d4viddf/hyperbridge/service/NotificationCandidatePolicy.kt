package com.d4viddf.hyperbridge.service

data class NotificationCandidateSignals(
    val hasTitle: Boolean,
    val hasText: Boolean,
    val hasBigTitle: Boolean,
    val hasBigText: Boolean,
    val hasMessagingStyleMessage: Boolean,
    val hasSupportedPersistentState: Boolean
)

/** Cheap callback-time quality check; it intentionally contains no app or language rules. */
object NotificationCandidatePolicy {
    fun quality(signals: NotificationCandidateSignals): SourceCandidateQuality =
        if (
            signals.hasTitle || signals.hasText || signals.hasBigTitle || signals.hasBigText ||
            signals.hasMessagingStyleMessage || signals.hasSupportedPersistentState
        ) {
            SourceCandidateQuality.USABLE
        } else {
            SourceCandidateQuality.EMPTY_AUXILIARY
        }
}

data class NotificationAcceptanceSignals(
    val packageName: String,
    val title: String,
    val text: String,
    val hasMessageContent: Boolean,
    val hasProgressOrSpecialState: Boolean,
    val containsBlockedTerm: Boolean,
    val isGroupSummary: Boolean,
    val isMessageType: Boolean
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
        if (signals.isGroupSummary) {
            if (!signals.isMessageType) return true
            if ((signals.text.isEmpty() || signals.title.isEmpty()) && !signals.hasMessageContent) return true
        }
        return false
    }
}

/** Full shade classification is reserved for connect/unlock/screen-on recovery passes. */
object OngoingRecoveryPolicy {
    fun shouldClassifyShadeNotifications(refresh: Boolean): Boolean = refresh
}
