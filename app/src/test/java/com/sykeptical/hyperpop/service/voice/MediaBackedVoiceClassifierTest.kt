package com.sykeptical.hyperpop.service.voice

import com.sykeptical.hyperpop.models.NotificationType
import com.sykeptical.hyperpop.service.NotificationLifecyclePolicy
import com.sykeptical.hyperpop.service.RawNotificationTypeClassifier
import com.sykeptical.hyperpop.service.RawNotificationTypeSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBackedVoiceClassifierTest {
    @Test
    fun telegramVoicePlaybackBecomesAVoiceMessage() {
        val signals = telegramVoice()
        assertTrue(MediaBackedVoiceClassifier.isVoiceMessage(signals))
        assertEquals(NotificationType.VOICE_MESSAGE, classify(signals))
        assertTrue(NotificationLifecyclePolicy.carriesVisibleSourceFocus(NotificationType.VOICE_MESSAGE))
        assertFalse(
            MediaBackedVoiceClassifier.countsAsNativeMediaTemplate(
                template = "android.app.Notification\$MediaStyle",
                mediaBackedVoice = true,
            )
        )
    }

    @Test
    fun ordinaryMusicStaysMedia() {
        val signals = MediaBackedVoiceSignals(
            isMediaTransport = true,
            title = "Night Drive",
            text = "Ada",
            subText = "Album",
            actionLabels = listOf("Shuffle", "Previous", "Pause", "Next", "Repeat"),
        )
        assertFalse(MediaBackedVoiceClassifier.isVoiceMessage(signals))
        assertEquals(NotificationType.MEDIA, classify(signals))
        assertFalse(NotificationLifecyclePolicy.carriesVisibleSourceFocus(NotificationType.MEDIA))
    }

    @Test
    fun musicWithoutAnAlbumStillStaysMedia() {
        val signals = MediaBackedVoiceSignals(
            isMediaTransport = true,
            title = "Night Drive",
            text = "Ada",
            actionLabels = listOf("Previous", "Pause", "Next"),
        )
        assertFalse(MediaBackedVoiceClassifier.isVoiceMessage(signals))
        assertEquals(NotificationType.MEDIA, classify(signals))
    }

    @Test
    fun shortMediaWithoutAVoiceLabelStaysMedia() {
        val signals = MediaBackedVoiceSignals(
            isMediaTransport = true,
            title = "Clip",
            text = "0:04",
        )
        assertFalse(MediaBackedVoiceClassifier.isVoiceMessage(signals))
        assertEquals(NotificationType.MEDIA, classify(signals))
    }

    @Test
    fun videoMessageLabelStaysMedia() {
        val signals = MediaBackedVoiceSignals(
            isMediaTransport = true,
            title = "Ada",
            text = "Video message",
        )
        assertFalse(MediaBackedVoiceClassifier.isVoiceMessage(signals))
        assertEquals(NotificationType.MEDIA, classify(signals))
    }

    @Test
    fun voiceLabelOnlyInTheTitleIsNotEnough() {
        val signals = MediaBackedVoiceSignals(
            isMediaTransport = true,
            title = "Voice message",
            text = "Ada",
        )
        assertFalse(MediaBackedVoiceClassifier.isVoiceMessage(signals))
    }

    @Test
    fun nonMediaNotificationIsNotAMediaBackedVoiceMessage() {
        val signals = MediaBackedVoiceSignals(
            isMediaTransport = false,
            title = "Ada",
            text = "Voice message",
        )
        assertFalse(MediaBackedVoiceClassifier.isVoiceMessage(signals))
        assertEquals(
            NotificationType.MESSAGE,
            RawNotificationTypeClassifier.classify(
                RawNotificationTypeSignals(
                    isScreenRecording = false,
                    isCall = false,
                    isNavigation = false,
                    isTimer = false,
                    isMedia = false,
                    isMessage = true,
                    hasProgress = false,
                    isDownload = false,
                    isVoice = false,
                )
            )
        )
    }

    @Test
    fun callStillOutranksAMessageShapedNotification() {
        assertEquals(
            NotificationType.CALL,
            RawNotificationTypeClassifier.classify(
                RawNotificationTypeSignals(
                    isScreenRecording = false,
                    isCall = true,
                    isNavigation = false,
                    isTimer = false,
                    isMedia = false,
                    isMessage = true,
                    hasProgress = false,
                    isDownload = false,
                )
            )
        )
    }

    private fun telegramVoice() = MediaBackedVoiceSignals(
        isMediaTransport = true,
        title = "Ada",
        text = "Voice message",
        actionLabels = listOf("Pause"),
    )

    private fun classify(signals: MediaBackedVoiceSignals): NotificationType {
        val voice = MediaBackedVoiceClassifier.isVoiceMessage(signals)
        return RawNotificationTypeClassifier.classify(
            RawNotificationTypeSignals(
                isScreenRecording = false,
                isCall = false,
                isNavigation = false,
                isTimer = false,
                isMedia = signals.isMediaTransport && !voice,
                isMessage = false,
                hasProgress = false,
                isDownload = false,
                isVoice = voice,
            )
        )
    }
}
