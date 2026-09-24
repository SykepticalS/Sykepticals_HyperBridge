package com.d4viddf.hyperbridge.service.voice

/**
 * Playback notifications that should become a voice-message island.
 * Instagram puts milliseconds in [android.app.Notification.EXTRA_PROGRESS].
 * WhatsApp leaves those extras empty and draws the player in a custom view
 * on a `media_playback` channel.
 */
data class VoicePlaybackSignals(
    val isMediaTransport: Boolean,
    val isDownload: Boolean,
    val isMessage: Boolean,
    val progress: Int,
    val progressMax: Int,
    val title: String,
    val text: String,
    val ticker: String,
    val remoteTexts: List<String>,
    val channelId: String,
    val hasCustomView: Boolean,
)

enum class VoicePlaybackRole {
    PLAY,
    PAUSE,
}

object VoicePlaybackDetector {
    private val voicePhrases = listOf(
        "voice message",
        "voice note",
        "audio message",
        "voice msg",
    )

    private val fromSender = Regex(
        """(?:voice|audio) message from\s+(.+?)(?:\s*[.。])?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val pauseKeywords = listOf("pause", "duraklat", "pausa", "pausar")
    private val playKeywords = listOf("play", "resume", "oynat", "devam", "reprendre")

    fun isVoicePlayback(signals: VoicePlaybackSignals): Boolean {
        if (signals.isMediaTransport || signals.isDownload) return false
        // Unread chat notes stay messages. The player is a separate notification.
        if (signals.isMessage && signals.progressMax <= 0) return false
        val wording = hasVoiceWording(signals)
        val instagramLike = signals.progressMax > 0 && wording && !signals.isMessage
        val whatsappLike = signals.hasCustomView && !signals.isMessage && (
            signals.channelId.contains("media_playback", ignoreCase = true) ||
                (wording && signals.progressMax > 0)
            )
        return instagramLike || whatsappLike
    }

    /** The line that should be the island title, such as "Voice message from Test". */
    fun playbackCaption(candidates: List<String>): String? {
        return candidates.map { it.trim() }.firstOrNull { line ->
            line.isNotBlank() && voicePhrases.any { phrase -> line.lowercase().contains(phrase) }
        }
    }

    /** "Voice message from Test" -> "Test". */
    fun senderName(caption: String?): String? {
        val value = caption?.trim().orEmpty()
        if (value.isEmpty()) return null
        return fromSender.find(value)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun formatClock(positionMs: Int): String {
        val totalSeconds = (positionMs / 1000).coerceAtLeast(0)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    /** Elapsed / total, both moving as playback progress changes. */
    fun playbackClock(progress: Int, max: Int): String {
        if (max <= 0) return formatClock(progress)
        return "${formatClock(progress)} / ${formatClock(max)}"
    }

    fun percent(progress: Int, max: Int): Int {
        if (max <= 0) return 0
        return ((progress.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun roleFor(label: String): VoicePlaybackRole? {
        val value = label.lowercase()
        if (pauseKeywords.any { value.contains(it) }) return VoicePlaybackRole.PAUSE
        if (playKeywords.any { value.contains(it) }) return VoicePlaybackRole.PLAY
        return null
    }

    /** First labeled click that is actually play or pause. Unlabeled clicks are ignored. */
    fun selectControl(labels: List<String>): VoicePlaybackRole? {
        for (label in labels) {
            roleFor(label)?.let { return it }
        }
        return null
    }

    private fun hasVoiceWording(signals: VoicePlaybackSignals): Boolean {
        val haystack = buildList {
            add(signals.title)
            add(signals.text)
            add(signals.ticker)
            addAll(signals.remoteTexts)
        }
        return haystack.any { value ->
            val normalized = value.lowercase()
            voicePhrases.any { phrase -> normalized.contains(phrase) }
        }
    }
}

data class VoiceIslandPlan(
    val percent: Int,
    val compactLeft: String,
    val circleOnRight: Boolean,
    val expandedBarPercent: Int?,
    val playbackRole: VoicePlaybackRole?,
)

object VoiceIslandPlanner {
    fun plan(
        title: String,
        percent: Int,
        playbackRole: VoicePlaybackRole?,
    ): VoiceIslandPlan {
        val clamped = percent.coerceIn(0, 100)
        return VoiceIslandPlan(
            percent = clamped,
            compactLeft = title,
            circleOnRight = true,
            expandedBarPercent = clamped,
            playbackRole = playbackRole,
        )
    }
}
