package com.d4viddf.hyperbridge.service

import com.d4viddf.hyperbridge.models.NotificationType

/**
 * Calls, voice messages, downloads/uploads, and generic progress replace the app shade row
 * with the expanded Focus card. The source notification has to stay posted because it carries
 * that card.
 */
object SourceFocusShadePolicy {
    fun replacesShade(type: NotificationType?): Boolean =
        NotificationLifecyclePolicy.carriesVisibleSourceFocus(type)

    fun keepSourceOngoing(type: NotificationType?, finished: Boolean, cancelling: Boolean): Boolean {
        if (cancelling || type == null) return false
        return when (type) {
            NotificationType.VOICE_MESSAGE -> true
            NotificationType.DOWNLOAD,
            NotificationType.PROGRESS -> !finished
            else -> false
        }
    }

    fun clearCustomShadeViews(replacingShade: Boolean, cancelling: Boolean): Boolean =
        replacingShade && !cancelling
}
