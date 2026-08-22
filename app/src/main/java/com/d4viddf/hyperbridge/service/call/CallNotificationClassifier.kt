package com.d4viddf.hyperbridge.service.call

enum class CallState {
    INCOMING_RINGING,
    OUTGOING_CALLING,
    OUTGOING_RINGING,
    CONNECTING,
    ACTIVE,
    ENDED
}

enum class CallActionRole {
    ANSWER,
    DECLINE_OR_HANG_UP,
    SPEAKER,
    OTHER
}

enum class CallActiveEvidence {
    NONE,
    EXPLICIT_ONGOING_TYPE,
    CHRONOMETER
}

data class CallActionSignal(
    val title: String,
    val semanticAction: Int,
    val hasPendingIntent: Boolean
)

data class CallNotificationSignals(
    val category: String?,
    val template: String?,
    val callType: Int?,
    val showsChronometer: Boolean,
    val whenTime: Long,
    val actions: List<CallActionSignal>,
    val isOngoingEvent: Boolean = false
)

data class CallClassification(
    val isCall: Boolean,
    val state: CallState,
    val reason: String,
    val activeEvidence: CallActiveEvidence = CallActiveEvidence.NONE,
    val hasAnswer: Boolean = false,
    val hasDeclineOrHangUp: Boolean = false
)

class CallNotificationClassifier(
    private val answerKeywords: List<String>,
    private val declineKeywords: List<String>,
    private val hangUpKeywords: List<String>,
    private val speakerKeywords: List<String>
) {
    fun classify(signals: CallNotificationSignals): CallClassification {
        val template = signals.template.orEmpty()
        val roles = signals.actions.map { roleForAction(it) }
        val hasAnswer = roles.any { it == CallActionRole.ANSWER }
        val hasDeclineOrHangUp = roles.any { it == CallActionRole.DECLINE_OR_HANG_UP }
        val hasSemanticCallAction = signals.actions.any { it.semanticAction == SEMANTIC_ACTION_CALL }
        val callType = signals.callType ?: CALL_TYPE_UNKNOWN

        val isMetadataCall = signals.category == CATEGORY_CALL ||
                template == CALL_STYLE_TEMPLATE ||
                callType != CALL_TYPE_UNKNOWN

        val hasValidChronometer = signals.showsChronometer && signals.whenTime > 0L
        val isActionCall = (hasSemanticCallAction && signals.isOngoingEvent) ||
                (hasAnswer && hasDeclineOrHangUp) ||
                (hasValidChronometer && hasDeclineOrHangUp)

        if (!isMetadataCall && !isActionCall) {
            return CallClassification(false, CallState.ENDED, "no-call-signals")
        }

        val activeEvidence = when {
            callType == CALL_TYPE_ONGOING -> CallActiveEvidence.EXPLICIT_ONGOING_TYPE
            hasValidChronometer -> CallActiveEvidence.CHRONOMETER
            else -> CallActiveEvidence.NONE
        }

        val state = when {
            callType == CALL_TYPE_INCOMING || callType == CALL_TYPE_SCREENING -> CallState.INCOMING_RINGING
            callType == CALL_TYPE_ONGOING -> CallState.ACTIVE
            hasAnswer && hasDeclineOrHangUp -> CallState.INCOMING_RINGING
            hasValidChronometer -> CallState.ACTIVE
            hasDeclineOrHangUp -> CallState.OUTGOING_CALLING
            else -> CallState.CONNECTING
        }

        val reason = when {
            callType != CALL_TYPE_UNKNOWN -> "call-type-$callType"
            signals.category == CATEGORY_CALL -> "category-call"
            template == CALL_STYLE_TEMPLATE -> "call-style"
            hasSemanticCallAction -> "semantic-call-action"
            else -> "call-action-fallback"
        }
        return CallClassification(
            isCall = true,
            state = state,
            reason = reason,
            activeEvidence = activeEvidence,
            hasAnswer = hasAnswer,
            hasDeclineOrHangUp = hasDeclineOrHangUp
        )
    }

    fun roleForAction(action: CallActionSignal): CallActionRole {
        val title = action.title.lowercase()
        return when {
            answerKeywords.any { title.contains(it.lowercase()) } -> CallActionRole.ANSWER
            declineKeywords.any { title.contains(it.lowercase()) } -> CallActionRole.DECLINE_OR_HANG_UP
            hangUpKeywords.any { title.contains(it.lowercase()) } -> CallActionRole.DECLINE_OR_HANG_UP
            speakerKeywords.any { title.contains(it.lowercase()) } -> CallActionRole.SPEAKER
            else -> CallActionRole.OTHER
        }
    }

    companion object {
        const val CATEGORY_CALL = "call"
        const val CALL_STYLE_TEMPLATE = "android.app.Notification\$CallStyle"
        const val CALL_TYPE_UNKNOWN = 0
        const val CALL_TYPE_INCOMING = 1
        const val CALL_TYPE_ONGOING = 2
        const val CALL_TYPE_SCREENING = 3
        const val SEMANTIC_ACTION_CALL = 10
    }
}
