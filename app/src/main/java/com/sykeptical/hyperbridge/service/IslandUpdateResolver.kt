package com.sykeptical.hyperbridge.service

import com.sykeptical.hyperbridge.models.MessageEventFingerprint
import com.sykeptical.hyperbridge.models.NotificationType
import java.util.LinkedHashMap

enum class IslandPresentationKind {
    NEW,
    UPDATE,
    UNCHANGED
}

enum class IslandPresentationReason {
    NEW_EVENT,
    CONTENT_UPDATE,
    RESTORE,
    RECONCILE;

    val mayAutoExpand: Boolean
        get() = this == NEW_EVENT
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
        messageEventFingerprint: MessageEventFingerprint? = null
    ): IslandUpdateDecision {
        if (previous == null || previous.logicalId != logicalId) {
            return IslandUpdateDecision(
                kind = IslandPresentationKind.NEW,
                bridgeId = candidateBridgeId,
                onlyAlertOnce = !presentationReason.mayAutoExpand,
                presentationReason = presentationReason
            )
        }

        val messageEventChanged = isMessagingEvent &&
                previous.messageEventFingerprint != messageEventFingerprint &&
                (previous.messageEventFingerprint != null || messageEventFingerprint != null)
        val contentChanged = previous.contentHash != contentHash

        if (!contentChanged && !messageEventChanged) {
            return IslandUpdateDecision(
                kind = IslandPresentationKind.UNCHANGED,
                bridgeId = previous.bridgeId,
                onlyAlertOnce = true,
                presentationReason = presentationReason
            )
        }

        if (isMessagingEvent) {
            val newEventReason = when (presentationReason) {
                IslandPresentationReason.RECONCILE,
                IslandPresentationReason.RESTORE -> presentationReason
                else -> IslandPresentationReason.NEW_EVENT
            }
            return IslandUpdateDecision(
                kind = IslandPresentationKind.NEW,
                bridgeId = candidateBridgeId,
                onlyAlertOnce = !newEventReason.mayAutoExpand,
                presentationReason = newEventReason,
                cancelBeforeNotify = true
            )
        }

        return IslandUpdateDecision(
            kind = IslandPresentationKind.UPDATE,
            bridgeId = previous.bridgeId,
            onlyAlertOnce = true,
            presentationReason = presentationReason
        )
    }
}

object MessageBridgeIdPolicy {
    private const val RANGE_START = -1_900_000_000
    private const val RANGE_SIZE = 800_000_000

    /** Message replacements use a private negative band, away from permanent/widget/watch ids. */
    fun candidate(
        logicalId: String,
        generation: Long,
        contentHash: Int,
        attempt: Int = 0,
        messageEventFingerprint: MessageEventFingerprint? = null
    ): Int {
        val mixed = listOf(
            logicalId,
            generation,
            contentHash,
            messageEventFingerprint?.stableHash,
            attempt
        ).hashCode().toLong()
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
