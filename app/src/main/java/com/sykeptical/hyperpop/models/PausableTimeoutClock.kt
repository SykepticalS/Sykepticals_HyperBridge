package com.sykeptical.hyperpop.models

/** Monotonic timeout clock whose remaining duration does not advance while paused. */
class PausableTimeoutClock(
    private val durationMs: Long,
    private val startedAtMs: Long,
) {
    private var pausedAtMs: Long? = null
    private var accumulatedPauseMs = 0L

    fun pause(nowMs: Long) {
        if (pausedAtMs == null) pausedAtMs = nowMs
    }

    fun resume(nowMs: Long) {
        val pausedAt = pausedAtMs ?: return
        accumulatedPauseMs += (nowMs - pausedAt).coerceAtLeast(0L)
        pausedAtMs = null
    }

    fun remainingMs(nowMs: Long): Long {
        val effectiveNow = pausedAtMs ?: nowMs
        val elapsed = (effectiveNow - startedAtMs - accumulatedPauseMs).coerceAtLeast(0L)
        return (durationMs - elapsed).coerceAtLeast(0L)
    }
}
