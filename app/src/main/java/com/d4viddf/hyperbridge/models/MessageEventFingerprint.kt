package com.d4viddf.hyperbridge.models

/**
 * Stable identity for one received message event.
 *
 * This is deliberately separate from the rendered-content hash: two messages may have the
 * same sender and text while still being distinct events.
 */
data class MessageEventFingerprint(
    val source: MessageEventFingerprintSource,
    val primaryValue: Long,
    val messageCount: Int? = null,
    val secondaryValue: Long? = null,
    val sourceScope: Int? = null
) {
    /** A compact seed for bridge-id generation. Equality must still use the full value. */
    val stableHash: Int
        get() = listOf(source, primaryValue, messageCount, secondaryValue, sourceScope).hashCode()
}

enum class MessageEventFingerprintSource {
    MESSAGING_STYLE,
    NOTIFICATION_WHEN,
    SOURCE_POST_TIME,
    CALLBACK_GENERATION
}
