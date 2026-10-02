package com.sykeptical.hyperpop.service.download

import com.sykeptical.hyperpop.models.NotificationType
import com.sykeptical.hyperpop.service.RawNotificationTypeClassifier
import com.sykeptical.hyperpop.service.RawNotificationTypeSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPausePolicyTest {
    @Test
    fun chromePauseBecomesADownloadUpdateInsteadOfAStandardNotification() {
        assertTrue(
            DownloadPausePolicy.isPaused(
                isDownload = true,
                title = "clip.mp4",
                text = "Download paused",
                actionTitles = listOf("Resume", "Cancel"),
            )
        )
        assertEquals(
            NotificationType.DOWNLOAD,
            RawNotificationTypeClassifier.classify(downloadSignals(hasProgress = true)),
        )
    }

    @Test
    fun activePauseButtonIsNotAPausedDownload() {
        assertFalse(
            DownloadPausePolicy.isPaused(
                isDownload = true,
                title = "clip.mp4",
                text = "12 MB / 40 MB",
                actionTitles = listOf("Pause", "Cancel"),
            )
        )
    }

    @Test
    fun finishedDownloadIsNotPaused() {
        assertFalse(
            DownloadPausePolicy.isPaused(
                isDownload = true,
                title = "clip.mp4",
                text = "Download paused",
                actionTitles = listOf("Resume"),
                finished = true,
            )
        )
    }

    @Test
    fun unrelatedContinueActionDoesNotPauseADownload() {
        assertFalse(
            DownloadPausePolicy.isPaused(
                isDownload = true,
                title = "clip.mp4",
                text = "12 MB / 40 MB",
                actionTitles = listOf("Continue"),
            )
        )
        assertEquals(
            NotificationType.STANDARD,
            RawNotificationTypeClassifier.classify(downloadSignals(hasProgress = false)),
        )
    }

    private fun downloadSignals(hasProgress: Boolean) = RawNotificationTypeSignals(
        isScreenRecording = false,
        isCall = false,
        isNavigation = false,
        isTimer = false,
        isMedia = false,
        isMessage = false,
        hasProgress = hasProgress,
        isDownload = true,
    )
}
