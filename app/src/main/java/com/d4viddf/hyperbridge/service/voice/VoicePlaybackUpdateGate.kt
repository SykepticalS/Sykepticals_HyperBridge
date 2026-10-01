package com.d4viddf.hyperbridge.service.voice

import java.util.concurrent.ConcurrentHashMap

data class VoicePlaybackUpdateSample(
    val progress: Int,
    val progressMax: Int,
    val structureFingerprint: Int,
    val observedAtMs: Long,
)

/**
 * Caps high-frequency player progress notifications without delaying real player state changes.
 * Instagram can post progress many times per second; rebuilding and reposting a complete Focus
 * island for every tick makes SystemUI contend with audio playback work.
 */
class VoicePlaybackUpdateGate(
    private val minimumRenderIntervalMs: Long = DEFAULT_RENDER_INTERVAL_MS,
) {
    private data class State(
        val lastObservedProgress: Int,
        val lastProgressMax: Int,
        val lastStructureFingerprint: Int,
        val lastRenderedAtMs: Long,
    )

    private val states = ConcurrentHashMap<String, State>()

    fun shouldRender(key: String, sample: VoicePlaybackUpdateSample): Boolean {
        var render = false
        states.compute(key) { _, previous ->
            render = previous == null ||
                sample.structureFingerprint != previous.lastStructureFingerprint ||
                sample.progressMax != previous.lastProgressMax ||
                sample.progress < previous.lastObservedProgress ||
                isComplete(sample) ||
                sample.observedAtMs - previous.lastRenderedAtMs >= minimumRenderIntervalMs

            State(
                lastObservedProgress = sample.progress,
                lastProgressMax = sample.progressMax,
                lastStructureFingerprint = sample.structureFingerprint,
                lastRenderedAtMs = if (render) {
                    sample.observedAtMs
                } else {
                    requireNotNull(previous).lastRenderedAtMs
                },
            )
        }
        return render
    }

    fun remove(key: String) {
        states.remove(key)
    }

    private fun isComplete(sample: VoicePlaybackUpdateSample): Boolean =
        sample.progressMax > 0 && sample.progress >= sample.progressMax

    companion object {
        const val DEFAULT_RENDER_INTERVAL_MS = 500L
    }
}
