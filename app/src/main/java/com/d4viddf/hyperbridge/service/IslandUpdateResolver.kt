package com.d4viddf.hyperbridge.service

import com.d4viddf.hyperbridge.models.MessageEventFingerprint
import com.d4viddf.hyperbridge.models.NotificationType
import java.util.LinkedHashMap

enum class IslandPresentationKind {
    NEW,
    UPDATE,
    UNCHANGED
}

enum class IslandPresentationReason {
    NEW_EVENT,
    /** A richer alias replaced an aggregate source before HyperOS displayed the first payload. */
    SOURCE_PROMOTION,
    CONTENT_UPDATE,
    RESTORE,
    RECONCILE;

    val mayAutoExpand: Boolean
        get() = this == NEW_EVENT || this == SOURCE_PROMOTION
}

data class PreviousIslandPresentation(
    val logicalId: String,
    val bridgeId: Int,
    val contentHash: Int,
    val messageEventFingerprint: MessageEventFingerprint? = null
)

data class IslandUpdateDecision(
    val kind: IslandPresentationKind,
    val bridgeId: Int,
    val onlyAlertOnce: Boolean,
    val presentationReason: IslandPresentationReason,
    val cancelBeforeNotify: Boolean = false
)

object IslandUpdateResolver {
    fun decide(
        logicalId: String,
        candidateBridgeId: Int,
        contentHash: Int,
        previous: PreviousIslandPresentation?,
        notificationType: NotificationType = NotificationType.STANDARD,
        presentationReason: IslandPresentationReason = if (previous == null || previous.logicalId != logicalId) {
            IslandPresentationReason.NEW_EVENT
        } else {
            IslandPresentationReason.CONTENT_UPDATE
        },
        isMessagingEvent: Boolean = notificationType == NotificationType.MESSAGE,
        messageEventFingerprint: MessageEventFingerprint? = null,
        actionsChanged: Boolean = false,
        actionRefreshBridgeId: Int? = null,
    ): IslandUpdateDecision {
        if (previous == null || previous.logicalId != logicalId) {
            return IslandUpdateDecision(
                kind = IslandPresentationKind.NEW,
                bridgeId = candidateBridgeId,
                onlyAlertOnce = !presentationReason.mayAutoExpand,
                presentationReason = presentationReason
            )
        }

        val contentChanged = previous.contentHash != contentHash
        val previousMessageEvent = previous.messageEventFingerprint
        val sameKnownMessageEvent = isMessagingEvent &&
                previousMessageEvent != null &&
                messageEventFingerprint != null &&
                previousMessageEvent.representsSameEventAs(
                    messageEventFingerprint,
                    contentUnchanged = !contentChanged
                )
        val messageEventChanged = isMessagingEvent &&
                !sameKnownMessageEvent &&
                (previousMessageEvent != null || messageEventFingerprint != null)

        if (!contentChanged && !messageEventChanged && !actionsChanged) {
            return IslandUpdateDecision(
                kind = IslandPresentationKind.UNCHANGED,
                bridgeId = previous.bridgeId,
                onlyAlertOnce = true,
                presentationReason = presentationReason
            )
        }

        val updateReason = when {
            isMessagingEvent && !sameKnownMessageEvent -> when (presentationReason) {
                IslandPresentationReason.RECONCILE,
                IslandPresentationReason.RESTORE -> presentationReason
                else -> IslandPresentationReason.NEW_EVENT
            }
            else -> presentationReason
        }

        // Action buttons (Reply, Boost, …) are part of the expanded island. HyperOS crashes
        // if those buttons are patched onto the notification id it is already showing, so a
        // button change retires that id and posts the updated buttons as a new one.
        val refreshedBridgeId = actionRefreshBridgeId?.takeIf { actionsChanged && it != previous.bridgeId }
        return IslandUpdateDecision(
            kind = IslandPresentationKind.UPDATE,
            bridgeId = refreshedBridgeId ?: previous.bridgeId,
            onlyAlertOnce = !updateReason.mayAutoExpand,
            presentationReason = updateReason,
            cancelBeforeNotify = refreshedBridgeId != null,
        )
    }
}

object MessageBridgeIdPolicy {
    private const val RANGE_START = -1_900_000_000
    private const val RANGE_SIZE = 800_000_000

    /**
     * HyperOS Focus identity is `(tag, id)`. Mixing generation/content into the id made every
     * WhatsApp follow-up a new island, so Xiaomi ran add+checkError and deleted the view.
     * Keep the notify id on the conversation only; payload changes go through notify(same id).
     */
    @Suppress("UNUSED_PARAMETER")
    fun candidate(
        logicalId: String,
        generation: Long = 0L,
        contentHash: Int = 0,
        attempt: Int = 0,
        messageEventFingerprint: MessageEventFingerprint? = null
    ): Int {
        val mixed = logicalId.hashCode().toLong()
        return RANGE_START + Math.floorMod(mixed, RANGE_SIZE.toLong()).toInt()
    }
}

data class InternalBridgeReplacement(
    val logicalId: String,
    val generation: Long,
    val markedAt: Long
)

class InternalBridgeReplacementRegistry(
    private val ttlMs: Long = 10_000L,
    private val maxEntries: Int = 64
) {
    private val entries = LinkedHashMap<Int, InternalBridgeReplacement>()

    @Synchronized
    fun mark(bridgeId: Int, logicalId: String, generation: Long, now: Long) {
        prune(now)
        entries[bridgeId] = InternalBridgeReplacement(logicalId, generation, now)
        while (entries.size > maxEntries) {
            entries.remove(entries.entries.first().key)
        }
    }

    @Synchronized
    fun consume(bridgeId: Int, now: Long): InternalBridgeReplacement? {
        prune(now)
        return entries.remove(bridgeId)
    }

    @Synchronized
    fun prune(now: Long) {
        entries.entries.removeIf { now - it.value.markedAt > ttlMs }
    }

    @Synchronized
    fun clear() = entries.clear()
}

object PermanentIslandVisibilityPolicy {
    fun desiredActive(
        enabled: Boolean,
        realNotificationCount: Int,
        hasNativeIsland: Boolean,
        hideInLandscape: Boolean,
        isLandscape: Boolean
    ): Boolean = enabled &&
            realNotificationCount == 0 &&
            !hasNativeIsland &&
            !(hideInLandscape && isLandscape)
}

object NotificationLifecyclePolicy {
    const val REASON_CLICK = 1
    const val REASON_CANCEL = 2
    const val REASON_CANCEL_ALL = 3
    const val REASON_PACKAGE_CHANGED = 5
    const val REASON_APP_CANCEL = 8
    const val REASON_APP_CANCEL_ALL = 9
    const val REASON_LISTENER_CANCEL = 10
    const val REASON_LISTENER_CANCEL_ALL = 11

    fun dismissesWithSource(type: NotificationType?): Boolean = when (type) {
        NotificationType.CALL,
        NotificationType.MEDIA,
        NotificationType.NAVIGATION,
        NotificationType.SCREEN_RECORDING -> true
        else -> false
    }

    fun isProgressLifecycle(type: NotificationType?): Boolean =
        type == NotificationType.DOWNLOAD ||
            type == NotificationType.PROGRESS ||
            type == NotificationType.VOICE_MESSAGE

    /**
     * Call/media/nav/recording islands outlive the source FLAG_ONGOING_EVENT. Outgoing
     * "calling" notifications often omit that flag, and mirroring auto-cancel lets HyperOS
     * retire the proxy while the session is still live.
     */
    fun proxyStaysPosted(type: NotificationType?): Boolean = dismissesWithSource(type)

    /** Logical ids such as `call:pkg:1` are never present in the live source snapshot. */
    fun isTrackedSourcePresent(
        logicalId: String,
        sourceKey: String,
        currentSourceKeys: Set<String>
    ): Boolean {
        val trackedSourceKey = sourceKey.ifBlank { logicalId }
        return trackedSourceKey in currentSourceKeys || logicalId in currentSourceKeys
    }

    fun canIntentionallyMirrorSource(type: NotificationType): Boolean =
        type == NotificationType.MESSAGE || type == NotificationType.STANDARD

    fun shouldDismissSourceAfterBridgeRemoval(
        dismissSourceOnContentClick: Boolean,
        wasContentClick: Boolean
    ): Boolean = dismissSourceOnContentClick && wasContentClick

    fun isUserInitiatedRemoval(reason: Int): Boolean = when (reason) {
        REASON_CLICK,
        REASON_CANCEL,
        REASON_CANCEL_ALL,
        REASON_LISTENER_CANCEL,
        REASON_LISTENER_CANCEL_ALL -> true
        else -> false
    }

    fun isAppCancellationReason(reason: Int): Boolean =
        reason == REASON_APP_CANCEL || reason == REASON_APP_CANCEL_ALL

    /**
     * Recents cleanup force-stops apps and cancels their notifications with
     * [REASON_PACKAGE_CHANGED]. That is not the user dismissing an island.
     *
     * Shade and lock-screen "clear all" ([REASON_CANCEL_ALL], [REASON_LISTENER_CANCEL_ALL])
     * are a user dismissal of every clearable notification, same as a shade swipe
     * ([REASON_CANCEL]). Those must retire the island with the source.
     */
    fun preservesActiveIsland(reason: Int): Boolean = reason == REASON_PACKAGE_CHANGED

    /**
     * Shade swipe/clear is a user dismissal. An app that clears its own notification ends
     * the island too, once that source does not come back. [isAppCancellation] does not
     * change the answer: the caller waits out the replacement window and drops the
     * dismissal if a follow-up is posted. A still-visible conversation alias is handled
     * before this check and must not reach it. [regroupingProtected] records that case.
     */
    fun shouldDismissIslandOnSourceRemoval(
        type: NotificationType?,
        dismissWithOriginal: Boolean,
        @Suppress("UNUSED_PARAMETER") isAppCancellation: Boolean,
        @Suppress("UNUSED_PARAMETER") regroupingProtected: Boolean =
            type == NotificationType.MESSAGE || type == NotificationType.STANDARD,
    ): Boolean {
        if (dismissesWithSource(type) || isProgressLifecycle(type)) return true
        return dismissWithOriginal
    }

    /** A periodic snapshot cannot distinguish app regrouping from a user dismissal. */
    fun shouldReapMissingSource(
        type: NotificationType?,
        removeOriginalNotification: Boolean,
        dismissWithOriginal: Boolean,
        retainedWithoutSource: Boolean = false,
        replacementGraceExpired: Boolean = false,
    ): Boolean {
        if (retainedWithoutSource) return false
        if (isProgressLifecycle(type)) return replacementGraceExpired
        if (type == NotificationType.MESSAGE || type == NotificationType.STANDARD) return false
        if (removeOriginalNotification) return false
        return dismissesWithSource(type) || dismissWithOriginal
    }

    fun presentationReason(hasPrevious: Boolean, recovery: Boolean): IslandPresentationReason = when {
        recovery -> IslandPresentationReason.RECONCILE
        hasPrevious -> IslandPresentationReason.CONTENT_UPDATE
        else -> IslandPresentationReason.NEW_EVENT
    }

    fun isCurrentRemoval(
        activeSourceKey: String,
        activeSourcePostTime: Long,
        removedSourceKey: String,
        removedSourcePostTime: Long
    ): Boolean {
        return activeSourceKey == removedSourceKey && activeSourcePostTime <= removedSourcePostTime
    }
}
