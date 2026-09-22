package com.lidesheng.hyperlyric.root.mediacard.notification

import java.lang.reflect.Field

/**
 * A MediaData package is not a media-session identity. Some players keep more
 * than one MediaSession alive, and a reused native controller can therefore
 * receive two different sessions from the same package in succession.
 *
 * Keep the identity cheap: MediaData copies normally retain the same token and
 * Icon instances, while a new session changes the token. This value is used
 * only to invalidate per-controller artwork/color caches; it is not a public
 * list key.
 */
internal object NotificationMediaDataIdentity {
    fun sessionOf(mediaData: Any?): String {
        if (mediaData == null) return "null"
        val notificationKey = readField(mediaData, "notificationKey")
        val token = readField(mediaData, "token")
        return buildString {
            append(notificationKey ?: "null")
            append('|')
            append(token?.javaClass?.name ?: "null")
            append(':')
            append(token?.hashCode() ?: 0)
        }
    }

    fun of(mediaData: Any?): String {
        if (mediaData == null) return "null"
        val artwork = readField(mediaData, "artwork")
        return buildString {
            append(sessionOf(mediaData))
            append('|')
            append(artwork?.javaClass?.name ?: "null")
            append(':')
            append(artwork?.let(System::identityHashCode) ?: 0)
        }
    }

    private fun readField(receiver: Any, name: String): Any? {
        return findField(receiver.javaClass, name)?.let { field ->
            runCatching { field.get(receiver) }.getOrNull()
        }
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                return field
            }
            current = current.superclass
        }
        return null
    }
}
