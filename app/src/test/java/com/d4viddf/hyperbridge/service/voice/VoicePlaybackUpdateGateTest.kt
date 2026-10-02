package com.d4viddf.hyperbridge.service.voice

import com.d4viddf.hyperbridge.models.NotificationType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlaybackUpdateGateTest {
    private val gate = VoicePlaybackUpdateGate()

    @Test
    fun forwardProgressDoesNotRepostTheIsland() {
        assertTrue(gate.shouldRender("voice", sample(progress = 100)))
        assertFalse(gate.shouldRender("voice", sample(progress = 200)))
        assertFalse(gate.shouldRender("voice", sample(progress = 900)))
    }

    @Test
    fun playerControlChangeIsImmediate() {
        assertTrue(gate.shouldRender("voice", sample(progress = 100, structure = 1)))
        assertTrue(gate.shouldRender("voice", sample(progress = 110, structure = 2)))
    }

    @Test
    fun backwardSeekAndCompletionAreImmediate() {
        assertTrue(gate.shouldRender("voice", sample(progress = 900)))
        assertTrue(gate.shouldRender("voice", sample(progress = 300)))
        assertTrue(gate.shouldRender("voice", sample(progress = 1_000)))
    }

    @Test
    fun independentPlayersDoNotThrottleEachOther() {
        assertTrue(gate.shouldRender("instagram", sample(progress = 100)))
        assertTrue(gate.shouldRender("whatsapp", sample(progress = 100)))
        assertFalse(gate.shouldRender("instagram", sample(progress = 200)))
    }

    @Test
    fun removingAPlayerClearsItsThrottleState() {
        assertTrue(gate.shouldRender("voice", sample(progress = 100)))
        assertFalse(gate.shouldRender("voice", sample(progress = 200)))
        gate.remove("voice")
        assertTrue(gate.shouldRender("voice", sample(progress = 300)))
    }

    @Test
    fun throttledProgressRestampDoesNotRebuildTheIsland() {
        assertTrue(VoicePlaybackDecorationPolicy.restampCachedDecoration(hasCachedDecoration = true))
        assertFalse(VoicePlaybackDecorationPolicy.restampCachedDecoration(hasCachedDecoration = false))
        assertFalse(VoicePlaybackDecorationPolicy.rebuildIsland())
    }

    @Test
    fun missingIslandDoesNotEvictTheCachedDecoration() {
        assertFalse(VoicePlaybackDecorationPolicy.evictCachedDecoration(activeType = null))
        assertFalse(VoicePlaybackDecorationPolicy.evictCachedDecoration(NotificationType.VOICE_MESSAGE))
        assertTrue(VoicePlaybackDecorationPolicy.evictCachedDecoration(NotificationType.MESSAGE))
    }

    @Test
    fun onlyKnownVoicePlayersUseTheBoundedSystemUiHash() {
        assertTrue(VoicePlaybackHotPathPolicy.usesBoundedRemoteViewsHash("com.instagram.android", 40_000, "ig_direct"))
        assertTrue(VoicePlaybackHotPathPolicy.usesBoundedRemoteViewsHash("com.whatsapp", 0, "media_playback@1"))
        assertTrue(VoicePlaybackHotPathPolicy.usesBoundedRemoteViewsHash("com.whatsapp.w4b", 0, "MEDIA_PLAYBACK"))
        assertFalse(VoicePlaybackHotPathPolicy.usesBoundedRemoteViewsHash("com.instagram.android", 0, "ig_direct"))
        assertFalse(VoicePlaybackHotPathPolicy.usesBoundedRemoteViewsHash("com.example.download", 100, "downloads"))
    }

    private fun sample(
        progress: Int,
        structure: Int = 1,
    ) = VoicePlaybackUpdateSample(
        progress = progress,
        progressMax = 1_000,
        structureFingerprint = structure,
    )
}
