package com.d4viddf.hyperbridge.service

import com.d4viddf.hyperbridge.models.MessageEventFingerprint

data class ExpiredIslandRecord(
    val logicalId: String,
    val sourceKey: String,
    val sourceFingerprint: Int,
    val expiredAt: Long,
    val messageEventFingerprint: MessageEventFingerprint? = null
)

enum class ExpiredSourceDecision {
    NOT_EXPIRED,
    SUPPRESS_IDENTICAL,
    NEW_GENERATION
}

/** Recovery is a snapshot of notifications already on screen, not a new user event. */
enum class RecoveryExpiry {
    ABSENT,
    SAME_EVENT,
    NEW_EVENT
}

/** Bounded memory of ephemeral source generations that have intentionally reached their TTL. */
class ExpiredIslandRegistry(
    private val maxEntries: Int = 256,
    private val retentionMs: Long = 6 * 60 * 60 * 1000L
) {
    private val records = LinkedHashMap<String, ExpiredIslandRecord>(16, 0.75f, true)

    @Synchronized
    fun record(record: ExpiredIslandRecord) {
        prune(record.expiredAt)
        records[record.sourceKey] = record
        while (records.size > maxEntries) {
            records.entries.iterator().run {
                if (hasNext()) {
                    next()
                    remove()
                }
            }
        }
    }

    @Synchronized
    fun evaluate(
        sourceKey: String,
        sourceFingerprint: Int,
        now: Long,
        messageEventFingerprint: MessageEventFingerprint? = null,
        logicalId: String? = null
    ): ExpiredSourceDecision {
        prune(now)
        val record = findRecord(sourceKey, logicalId) ?: return ExpiredSourceDecision.NOT_EXPIRED
        if (record.isSameGeneration(sourceFingerprint, messageEventFingerprint)) {
            return ExpiredSourceDecision.SUPPRESS_IDENTICAL
        }
        return ExpiredSourceDecision.NEW_GENERATION
    }

    /**
     * A shade refresh often rewrites postTime and the rendered fingerprint of an island that
     * already expired. That drift is not a new message. A different message event is.
     */
    @Synchronized
    fun recoveryExpiry(
        sourceKey: String,
        now: Long,
        messageEventFingerprint: MessageEventFingerprint? = null,
        logicalId: String? = null
    ): RecoveryExpiry {
        prune(now)
        val record = findRecord(sourceKey, logicalId) ?: return RecoveryExpiry.ABSENT
        val recorded = record.messageEventFingerprint
        if (recorded != null && messageEventFingerprint != null && recorded != messageEventFingerprint) {
            return RecoveryExpiry.NEW_EVENT
        }
        return RecoveryExpiry.SAME_EVENT
    }

    /** Clear only after the changed source generation was successfully posted. */
    @Synchronized
    fun acceptNewGeneration(
        sourceKey: String,
        sourceFingerprint: Int,
        messageEventFingerprint: MessageEventFingerprint? = null,
        logicalId: String? = null
    ) {
        val record = findRecord(sourceKey, logicalId) ?: return
        if (!record.isSameGeneration(sourceFingerprint, messageEventFingerprint)) {
            records.remove(record.sourceKey)
        }
    }

    private fun findRecord(sourceKey: String, logicalId: String?): ExpiredIslandRecord? =
        records[sourceKey] ?: logicalId?.let { id -> records.values.lastOrNull { it.logicalId == id } }

    @Synchronized
    fun removeSource(sourceKey: String) {
        records.remove(sourceKey)
    }

    @Synchronized
    fun clear() = records.clear()

    @Synchronized
    fun size(): Int = records.size

    @Synchronized
    fun prune(now: Long) {
        records.entries.removeIf { now - it.value.expiredAt > retentionMs }
    }
}

private fun ExpiredIslandRecord.isSameGeneration(
    candidateSourceFingerprint: Int,
    candidateMessageEventFingerprint: MessageEventFingerprint?
): Boolean {
    // When both sides know the event identity it is authoritative. The legacy source fingerprint
    // remains a conservative fallback when metadata is absent on either snapshot.
    if (messageEventFingerprint != null && candidateMessageEventFingerprint != null) {
        return messageEventFingerprint == candidateMessageEventFingerprint
    }
    return sourceFingerprint == candidateSourceFingerprint
}

fun sourceGenerationFingerprint(contentHash: Int, sourcePostTime: Long): Int =
    listOf(contentHash, sourcePostTime).hashCode()

object IslandTimeoutPolicy {
    fun durationMillis(timeoutSeconds: Int?): Long? =
        timeoutSeconds?.takeIf { it > 0 }?.toLong()?.times(1_000L)

    fun isCurrent(
        activeGeneration: Long?,
        activeBridgeId: Int?,
        scheduledGeneration: Long,
        scheduledBridgeId: Int
    ): Boolean = activeGeneration == scheduledGeneration && activeBridgeId == scheduledBridgeId
}
