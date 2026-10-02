package com.sykeptical.hyperpop.models

import org.junit.Assert.assertEquals
import org.junit.Test

class PausableTimeoutClockTest {
    @Test
    fun typingTimeDoesNotConsumeAutoHideDuration() {
        val clock = PausableTimeoutClock(durationMs = 30_000L, startedAtMs = 1_000L)
        clock.pause(nowMs = 11_000L)

        assertEquals(20_000L, clock.remainingMs(nowMs = 61_000L))

        clock.resume(nowMs = 61_000L)
        assertEquals(20_000L, clock.remainingMs(nowMs = 61_000L))
        assertEquals(15_000L, clock.remainingMs(nowMs = 66_000L))
    }

    @Test
    fun repeatedPauseIsIdempotent() {
        val clock = PausableTimeoutClock(durationMs = 10_000L, startedAtMs = 0L)
        clock.pause(2_000L)
        clock.pause(7_000L)
        clock.resume(12_000L)
        assertEquals(8_000L, clock.remainingMs(12_000L))
    }
}
