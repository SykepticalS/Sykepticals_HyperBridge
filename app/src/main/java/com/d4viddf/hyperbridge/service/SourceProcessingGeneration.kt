package com.d4viddf.hyperbridge.service

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class SourceCandidateQuality {
    USABLE,
    EMPTY_AUXILIARY
}

/** Lightweight latest-wins generation gate; running jobs are never cancelled mid-commit. */
class SourceProcessingGeneration {
    private val sequence = AtomicLong()
    private val latestBySource = ConcurrentHashMap<String, Long>()

    /**
     * Claims ownership only for a usable callback. Empty auxiliary updates are deliberately
     * invisible to the generation gate so they cannot make queued useful work stale.
     */
    fun next(sourceKey: String, quality: SourceCandidateQuality = SourceCandidateQuality.USABLE): Long? {
        if (quality == SourceCandidateQuality.EMPTY_AUXILIARY) return null
        return sequence.incrementAndGet().also {
            latestBySource[sourceKey] = it
        }
    }

    fun isCurrent(sourceKey: String, generation: Long): Boolean =
        latestBySource[sourceKey] == generation

    fun current(sourceKey: String): Long? = latestBySource[sourceKey]

    fun remove(sourceKey: String) {
        latestBySource.remove(sourceKey)
    }

    fun clear() = latestBySource.clear()
}
