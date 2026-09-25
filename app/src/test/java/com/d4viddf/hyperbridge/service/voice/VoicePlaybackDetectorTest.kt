package com.d4viddf.hyperbridge.service.voice

import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.service.NotificationRemoteViewsParser
import com.d4viddf.hyperbridge.service.RawNotificationTypeClassifier
import com.d4viddf.hyperbridge.service.RawNotificationTypeSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlaybackDetectorTest {
    @Test
    fun instagramPlaybackUsesExtrasProgressAndBeatsGenericProgress() {
        val signals = VoicePlaybackSignals(
            isMediaTransport = false,
            isDownload = false,
            isMessage = false,
            progress = 35268,
            progressMax = 40635,
            title = "Audio message from atahan.",
            text = "",
            ticker = "",
            remoteTexts = emptyList(),
            channelId = "ig_direct",
            hasCustomView = false,
        )
        assertTrue(VoicePlaybackDetector.isVoicePlayback(signals))
        assertEquals(86, VoicePlaybackDetector.percent(35268, 40635))
        assertEquals(
            NotificationType.VOICE_MESSAGE,
            RawNotificationTypeClassifier.classify(baseSignals(isVoice = true, hasProgress = true))
        )
    }

    @Test
    fun idleVoiceChatNoteStaysAMessage() {
        val signals = VoicePlaybackSignals(
            isMediaTransport = false,
            isDownload = false,
            isMessage = true,
            progress = 0,
            progressMax = 0,
            title = "sykeptical: atahan.",
            text = "Sent you a voice message",
            ticker = "Sent you a voice message",
            remoteTexts = emptyList(),
            channelId = "ig_direct",
            hasCustomView = false,
        )
        assertFalse(VoicePlaybackDetector.isVoicePlayback(signals))
        assertEquals(
            NotificationType.MESSAGE,
            RawNotificationTypeClassifier.classify(baseSignals(isMessage = true))
        )
    }

    @Test
    fun whatsappCustomViewUsesParsedProgress() {
        val parsed = NotificationRemoteViewsParser.bestProgress(
            listOf(4 to 100, 1200 to 8000)
        )
        val signals = VoicePlaybackSignals(
            isMediaTransport = false,
            isDownload = false,
            isMessage = false,
            progress = parsed.first,
            progressMax = parsed.second,
            title = "",
            text = "",
            ticker = "",
            remoteTexts = emptyList(),
            channelId = "media_playback@1",
            hasCustomView = true,
        )
        assertEquals(1200 to 8000, parsed)
        assertTrue(VoicePlaybackDetector.isVoicePlayback(signals))
        assertEquals(15, VoicePlaybackDetector.percent(parsed.first, parsed.second))
        assertEquals(
            NotificationType.VOICE_MESSAGE,
            RawNotificationTypeClassifier.classify(baseSignals(isVoice = true))
        )
    }

    @Test
    fun mediaTransportStaysMedia() {
        val signals = VoicePlaybackSignals(
            isMediaTransport = true,
            isDownload = false,
            isMessage = false,
            progress = 10,
            progressMax = 100,
            title = "Audio message",
            text = "",
            ticker = "",
            remoteTexts = emptyList(),
            channelId = "media_playback",
            hasCustomView = true,
        )
        assertFalse(VoicePlaybackDetector.isVoicePlayback(signals))
    }

    @Test
    fun islandPlanPutsCircleOnTheRightAndOmitsPlayWithoutARole() {
        val plan = VoiceIslandPlanner.plan(
            title = "Audio message from atahan.",
            percent = 86,
            playbackRole = null,
        )
        assertTrue(plan.circleOnRight)
        assertEquals("Audio message from atahan.", plan.compactLeft)
        assertEquals(86, plan.expandedBarPercent)
        assertNull(plan.playbackRole)
        assertNull(VoicePlaybackDetector.selectControl(emptyList()))
        assertNull(VoicePlaybackDetector.selectControl(listOf("Close", "1:02")))
    }

    @Test
    fun whatsappCaptionBecomesTheIslandTitle() {
        val caption = VoicePlaybackDetector.playbackCaption(
            listOf("Test", "Voice message from Test", "0:04")
        )
        assertEquals("Voice message from Test", caption)
        assertEquals("Test", VoicePlaybackDetector.senderName(caption))
        assertEquals("0:12 / 0:40", VoicePlaybackDetector.playbackClock(12_400, 40_000))
        val plan = VoiceIslandPlanner.plan(VoicePlaybackDetector.senderName(caption)!!, 20, VoicePlaybackRole.PAUSE)
        assertEquals("Test", plan.compactLeft)
        assertEquals(VoicePlaybackRole.PAUSE, plan.playbackRole)
        assertEquals(VoicePlaybackRole.PAUSE, VoicePlaybackDetector.roleFor("Pause voice"))
        assertEquals(VoicePlaybackRole.PLAY, VoicePlaybackDetector.roleFor("Play voice"))
        assertEquals(VoicePlaybackRole.PAUSE, VoicePlaybackDetector.roleFor("wa_ic_pause_filled"))
        assertEquals(VoicePlaybackRole.PLAY, VoicePlaybackDetector.roleFor("vec_ic_play_arrow_filled"))
        assertNull(VoicePlaybackDetector.roleFor("play_pause"))
    }

    @Test
    fun pauseLabelSelectsPause() {
        assertEquals(VoicePlaybackRole.PAUSE, VoicePlaybackDetector.roleFor("Pause"))
        assertEquals(VoicePlaybackRole.PLAY, VoicePlaybackDetector.selectControl(listOf("Reply", "Play")))
    }

    @Test
    fun instagramCaptionShellWithoutProgressIsIgnored() {
        val shell = VoicePlaybackSignals(
            isMediaTransport = false,
            isDownload = false,
            isMessage = false,
            progress = 0,
            progressMax = 0,
            title = "Audio message from atahan.",
            text = "",
            ticker = "",
            remoteTexts = emptyList(),
            channelId = "ig_direct",
            hasCustomView = false,
        )
        assertTrue(VoicePlaybackDetector.isVoicePlaybackShell(shell))
    }

    @Test
    fun liveInstagramPlaybackIsNotAShell() {
        val player = VoicePlaybackSignals(
            isMediaTransport = false,
            isDownload = false,
            isMessage = false,
            progress = 35268,
            progressMax = 40635,
            title = "Audio message from atahan.",
            text = "",
            ticker = "",
            remoteTexts = emptyList(),
            channelId = "ig_direct",
            hasCustomView = false,
        )
        assertFalse(VoicePlaybackDetector.isVoicePlaybackShell(player))
    }

    @Test
    fun unreadVoiceChatNoteIsNotAShell() {
        val message = VoicePlaybackSignals(
            isMediaTransport = false,
            isDownload = false,
            isMessage = true,
            progress = 0,
            progressMax = 0,
            title = "sykeptical: atahan.",
            text = "Sent you a voice message",
            ticker = "Sent you a voice message",
            remoteTexts = emptyList(),
            channelId = "ig_direct",
            hasCustomView = false,
        )
        assertFalse(VoicePlaybackDetector.isVoicePlaybackShell(message))
    }

    private fun baseSignals(
        isMessage: Boolean = false,
        hasProgress: Boolean = false,
        isVoice: Boolean = false,
    ) = RawNotificationTypeSignals(
        isScreenRecording = false,
        isCall = false,
        isNavigation = false,
        isTimer = false,
        isMedia = false,
        isMessage = isMessage,
        hasProgress = hasProgress,
        isDownload = false,
        isVoice = isVoice,
    )
}
