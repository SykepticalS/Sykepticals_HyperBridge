package com.d4viddf.hyperbridge.service.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlaybackUpdateGateTest {
    private val gate = VoicePlaybackUpdateGate(minimumRenderIntervalMs = 500L)

    @Test
    fun forwardProgressIsCappedButPeriodicRefreshesContinue() {
        assertTrue(gate.shouldRender("voice", sample(progress = 100, observedAtMs = 1_000)))
        assertFalse(gate.shouldRender("voice", sample(progress = 200, observedAtMs = 1_100)))
        assertFalse(gate.shouldRender("voice", sample(progress = 300, observedAtMs = 1_499)))
        assertTrue(gate.shouldRender("voice", sample(progress = 400, observedAtMs = 1_500)))
    }

    @Test
    fun playerControlChangeIsImmediate() {
        assertTrue(gate.shouldRender("voice", sample(progress = 100, structure = 1, observedAtMs = 1_000)))
        assertTrue(gate.shouldRender("voice", sample(progress = 110, structure = 2, observedAtMs = 1_050)))
    }

    @Test
    fun backwardSeekAndCompletionAreImmediate() {
        assertTrue(gate.shouldRender("voice", sample(progress = 900, observedAtMs = 1_000)))
        assertTrue(gate.shouldRender("voice", sample(progress = 300, observedAtMs = 1_050)))
        assertTrue(gate.shouldRender("voice", sample(progress = 1_000, observedAtMs = 1_100)))
    }

    @Test
    fun independentPlayersDoNotThrottleEachOther() {
        assertTrue(gate.shouldRender("instagram", sample(progress = 100, observedAtMs = 1_000)))
        assertTrue(gate.shouldRender("whatsapp", sample(progress = 100, observedAtMs = 1_010)))
        assertFalse(gate.shouldRender("instagram", sample(progress = 200, observedAtMs = 1_020)))
    }

    @Test
    fun removingAPlayerClearsItsThrottleState() {
        assertTrue(gate.shouldRender("voice", sample(progress = 100, observedAtMs = 1_000)))
        assertFalse(gate.shouldRender("voice", sample(progress = 200, observedAtMs = 1_100)))
        gate.remove("voice")
        assertTrue(gate.shouldRender("voice", sample(progress = 300, observedAtMs = 1_110)))
    }

    private fun sample(
        progress: Int,
        structure: Int = 1,
        observedAtMs: Long,
    ) = VoicePlaybackUpdateSample(
        progress = progress,
        progressMax = 1_000,
        structureFingerprint = structure,
        observedAtMs = observedAtMs,
    )
}
