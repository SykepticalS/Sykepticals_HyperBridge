package com.d4viddf.hyperbridge.service

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Lightweight latest-wins generation gate; running jobs are never cancelled mid-commit. */
class SourceProcessingGeneration {
    private val sequence = AtomicLong()
    private val latestBySource = ConcurrentHashMap<String, Long>()

    /** Claims latest-wins ownership. Content validity is decided only after source refresh. */
    fun next(sourceKey: String): Long =
        sequence.incrementAndGet().also {
            latestBySource[sourceKey] = it
        }

    fun isCurrent(sourceKey: String, generation: Long): Boolean =
        latestBySource[sourceKey] == generation

    fun remove(sourceKey: String) {
        latestBySource.remove(sourceKey)
    }

    fun clear() = latestBySource.clear()
}
