package com.d4viddf.hyperbridge.service.translators

import android.app.PendingIntent

/**
 * HyperOS focus buttons dispatch with `actionIntentType`.
 * 1 starts an activity, 2 sends a broadcast, and 3 starts a service.
 * Play Store and Chrome download controls are service or broadcast intents. Marking them as
 * activities makes the tap fall through to the source notification channel settings.
 */
object FocusActionIntentTypes {
    const val ACTIVITY = 1
    const val BROADCAST = 2
    const val SERVICE = 3

    fun of(pendingIntent: PendingIntent?): Int {
        if (pendingIntent == null) return ACTIVITY
        return runCatching {
            resolve(
                isBroadcast = pendingIntent.isBroadcast,
                isForegroundService = pendingIntent.isForegroundService,
                isService = pendingIntent.isService,
            )
        }.getOrDefault(ACTIVITY)
    }

    fun resolve(isBroadcast: Boolean, isForegroundService: Boolean, isService: Boolean): Int {
        return when {
            isBroadcast -> BROADCAST
            isForegroundService || isService -> SERVICE
            else -> ACTIVITY
        }
    }
}
