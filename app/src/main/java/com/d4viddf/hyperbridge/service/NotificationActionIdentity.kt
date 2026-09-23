package com.d4viddf.hyperbridge.service

import android.app.PendingIntent
import android.content.Intent
import android.service.notification.StatusBarNotification

/**
 * Identity of one notification action button, such as WhatsApp Reply or charging Boost.
 * PendingIntent object identity is intentionally excluded: apps rebuild the same button on
 * every text update. Title, icon, semantic action, and the intent target are what change
 * when the button itself is replaced.
 */
data class NotificationActionSnapshot(
    val title: String,
    val semanticAction: Int,
    val iconResourceId: Int,
    val hasIcon: Boolean,
    val remoteInputKey: String,
    val intentAction: String,
    val intentComponent: String,
)

object NotificationActionIdentity {
    fun fingerprint(sbn: StatusBarNotification): Int =
        fingerprint(sbn.notification.actions.orEmpty().map { action ->
            val intent = pendingIntentIntent(action.actionIntent)
            NotificationActionSnapshot(
                title = action.title?.toString().orEmpty(),
                semanticAction = action.semanticAction,
                iconResourceId = action.icon,
                hasIcon = action.icon != 0 || action.getIcon() != null,
                remoteInputKey = action.remoteInputs?.firstOrNull()?.resultKey.orEmpty(),
                intentAction = intent?.action.orEmpty(),
                intentComponent = intent?.component?.flattenToString().orEmpty(),
            )
        })

    fun fingerprint(actions: List<NotificationActionSnapshot>): Int {
        if (actions.isEmpty()) return 0
        return actions.map { action ->
            listOf(
                action.title,
                action.semanticAction,
                action.iconResourceId,
                action.hasIcon,
                action.remoteInputKey,
                action.intentAction,
                action.intentComponent,
            ).hashCode()
        }.hashCode()
    }

    fun changed(previous: Int?, next: Int): Boolean = previous != null && previous != next

    /**
     * A replaced action button must not reuse the Focus notification id. HyperOS patches
     * `textButton` on notify(same id) and freezes the expanded island when that button changes.
     */
    fun refreshBridgeId(logicalId: String, actionFingerprint: Int, previousId: Int?): Int {
        var id = logicalId.hashCode() xor actionFingerprint xor 0x41C10A
        if (previousId != null && id == previousId) id = id xor 0x5A5A5A5A
        if (id == 0) id = 1
        return id
    }

    private fun pendingIntentIntent(pendingIntent: PendingIntent?): Intent? {
        if (pendingIntent == null) return null
        return runCatching {
            PendingIntent::class.java.getDeclaredMethod("getIntent").apply { isAccessible = true }
                .invoke(pendingIntent) as? Intent
        }.getOrNull()
    }
}
