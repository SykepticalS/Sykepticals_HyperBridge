package com.d4viddf.hyperbridge.service.call

data class SelectedCallAction(
    val index: Int,
    val role: CallActionRole,
    val microphoneState: CallMicrophoneState = CallMicrophoneState.UNKNOWN
)

/**
 * Selects only controls whose PendingIntent is owned by the source calling app. In particular, it
 * never invents a microphone control when the source notification does not expose one.
 */
object CallActionSelectionPolicy {
    fun select(
        actions: List<CallActionSignal>,
        isIncoming: Boolean,
        classifier: CallNotificationClassifier,
        packageName: String? = null,
    ): List<SelectedCallAction> {
        val classified = actions.mapIndexedNotNull { index, action ->
            if (!action.hasPendingIntent) return@mapIndexedNotNull null
            val role = classifier.roleForAction(action)
            SelectedCallAction(
                index = index,
                role = role,
                microphoneState = if (role == CallActionRole.MICROPHONE) {
                    classifier.microphoneStateForAction(action)
                } else {
                    CallMicrophoneState.UNKNOWN
                }
            )
        }

        val allowMicrophone = packageName == null || CallMicrophoneApps.supportsNotificationMute(packageName)
        val preferredRoles = if (isIncoming) {
            listOf(CallActionRole.DECLINE_OR_HANG_UP, CallActionRole.ANSWER)
        } else {
            buildList {
                if (allowMicrophone) add(CallActionRole.MICROPHONE)
                add(CallActionRole.DECLINE_OR_HANG_UP)
            }
        }

        return preferredRoles.mapNotNull { preferred ->
            classified.firstOrNull { it.role == preferred }
        }.distinctBy { it.index }.take(2)
    }
}
