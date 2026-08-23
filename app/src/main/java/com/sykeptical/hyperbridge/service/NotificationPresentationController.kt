package com.sykeptical.hyperbridge.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sykeptical.hyperbridge.models.IslandConfig
import com.sykeptical.hyperbridge.models.NotificationType

data class NotificationPresentationRequest(
    val originalKey: String,
    val bridgeId: Int,
    val config: IslandConfig,
    val type: NotificationType,
    val isLiveUpdate: Boolean,
    val sbn: StatusBarNotification?,
    val title: String,
    val text: String
)

class NotificationPresentationController(
    private val markIntentionallyRemoved: (String) -> Unit,
    private val cancelOriginal: (String) -> Unit,
    private val postWatchRelay: (StatusBarNotification, String, String) -> Unit
) {
    fun applyPostEffects(request: NotificationPresentationRequest) {
        if (!shouldCancelOriginal(request)) return

        val sbn = request.sbn ?: return
        if (!request.isLiveUpdate && (request.type == NotificationType.MESSAGE || request.type == NotificationType.STANDARD)) {
            postWatchRelay(sbn, request.title, request.text)
        }

        markIntentionallyRemoved(request.originalKey)
        cancelOriginal(request.originalKey)
    }

    private fun shouldCancelOriginal(request: NotificationPresentationRequest): Boolean {
        if (request.config.removeOriginalNotification != true) return false

        val sbn = request.sbn ?: return false
        val flags = sbn.notification.flags
        val isOngoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0
        val isForegroundService = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0

        // Public NotificationListenerService APIs can cancel a notification, but they cannot
        // suppress only the source app's heads-up presentation while preserving the exact
        // original shade entry. Keep the fallback narrow so calls, navigation, media, downloads,
        // foreground services, and other persistent state are not destabilized.
        val canSafelyMirror = request.type == NotificationType.MESSAGE || request.type == NotificationType.STANDARD
        val safe = canSafelyMirror && !isOngoing && !isForegroundService

        if (!safe) {
            Log.d("HyperBridgePresentation", "Keeping original notification ${request.originalKey}; unsafe to cancel type=${request.type}")
        }
        return safe
    }
}
