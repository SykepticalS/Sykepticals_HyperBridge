package com.sykeptical.hyperpop.service.voice

/**
 * A voice note that Android exposes as a media-style playback notification.
 *
 * Telegram's player puts the sender in the title and the voice-message label
 * (English `AttachAudio`, "Voice message") in the content text. Music uses the
 * same MediaStyle notification but puts the track title and artist there, fills
 * the album subtext when artwork metadata exists, and adds skip/shuffle/repeat
 * actions. Duration is intentionally not a signal.
 */
data class MediaBackedVoiceSignals(
    val isMediaTransport: Boolean,
    val title: String,
    val text: String,
    val subText: String = "",
    val ticker: String = "",
    val actionLabels: List<String> = emptyList(),
)

object MediaBackedVoiceClassifier {
    private val voiceLabels = listOf(
        "voice message",
        "voice note",
        "audio message",
        "voice msg",
    )

    private val skipLabels = listOf(
        "next",
        "previous",
        "prev",
        "skip",
        "shuffle",
        "repeat",
    )

    fun isVoiceMessage(signals: MediaBackedVoiceSignals): Boolean {
        if (!signals.isMediaTransport) return false
        if (signals.subText.isNotBlank()) return false
        if (hasSkipTransport(signals.actionLabels)) return false
        return secondaryLines(signals).any(::isVoiceLabel)
    }

    fun countsAsNativeMediaTemplate(template: String?, mediaBackedVoice: Boolean): Boolean {
        if (mediaBackedVoice) return false
        return template == "androidx.media.app.NotificationCompat\$MediaStyle" ||
            template == "android.app.Notification\$MediaStyle"
    }

    fun hasSkipTransport(actionLabels: List<String>): Boolean =
        actionLabels.any { label ->
            val value = label.lowercase()
            skipLabels.any { token -> value.contains(token) }
        }

    private fun secondaryLines(signals: MediaBackedVoiceSignals): List<String> =
        listOf(signals.text, signals.ticker)

    private fun isVoiceLabel(line: String): Boolean {
        val value = line.trim().lowercase()
        if (value.isEmpty()) return false
        return voiceLabels.any { label -> value == label || value.contains(label) }
    }
}
