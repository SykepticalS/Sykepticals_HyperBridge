package com.sykeptical.hyperpop.service.voice

import com.sykeptical.hyperpop.models.NotificationType
import java.util.concurrent.ConcurrentHashMap

data class VoicePlaybackUpdateSample(
    val progress: Int,
    val progressMax: Int,
    val structureFingerprint: Int,
)

/**
 * Rejects progress-only player notifications without delaying real player state changes.
 * Rebuilding and reposting a complete Focus island, even periodically, stalls SystemUI on the
 * affected device. Progress can be animated locally later; it must not drive notification reposts.
 */
class VoicePlaybackUpdateGate {
    private data class State(
        val lastObservedProgress: Int,
        val lastProgressMax: Int,
        val lastStructureFingerprint: Int,
    )

    private val states = ConcurrentHashMap<String, State>()

    fun shouldRender(key: String, sample: VoicePlaybackUpdateSample): Boolean {
        var render = false
        states.compute(key) { _, previous ->
            render = previous == null ||
                sample.structureFingerprint != previous.lastStructureFingerprint ||
                sample.progressMax != previous.lastProgressMax ||
                sample.progress < previous.lastObservedProgress ||
                isComplete(sample)

            State(
                lastObservedProgress = sample.progress,
                lastProgressMax = sample.progressMax,
                lastStructureFingerprint = sample.structureFingerprint,
            )
        }
        return render
    }

    fun remove(key: String) {
        states.remove(key)
    }

    private fun isComplete(sample: VoicePlaybackUpdateSample): Boolean =
        sample.progressMax > 0 && sample.progress >= sample.progressMax
}

/**
 * A throttled voice update must not rebuild or repost the Focus island.
 * The notification SystemUI is about to snapshot still needs the decoration
 * from the last real player post, or the island disappears with the old object.
 */
object VoicePlaybackDecorationPolicy {
    fun restampCachedDecoration(hasCachedDecoration: Boolean): Boolean = hasCachedDecoration

    fun rebuildIsland(): Boolean = false

    /**
     * A missing island is not proof the voice decoration is stale. The first post
     * caches it before the island record is visible to the next progress tick.
     */
    fun evictCachedDecoration(activeType: NotificationType?): Boolean =
        activeType != null && activeType != NotificationType.VOICE_MESSAGE
}

object VoicePlaybackHotPathPolicy {
    fun usesBoundedRemoteViewsHash(
        packageName: String,
        progressMax: Int,
        channelId: String,
    ): Boolean =
        (packageName == "com.instagram.android" && progressMax > 0) ||
            ((packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") &&
                channelId.contains("media_playback", ignoreCase = true))
}
