package com.sykeptical.hyperpop.service.download

import com.sykeptical.hyperpop.models.NotificationType
import com.sykeptical.hyperpop.service.RawNotificationTypeClassifier
import com.sykeptical.hyperpop.service.RawNotificationTypeSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeNotificationPolicyTest {
    @Test
    fun shadePausedDownloadStaysADownload() {
        assertTrue(ChromeNotificationPolicy.isDownloadChannel("downloads"))
        assertTrue(
            DownloadPausePolicy.isPaused(
                isDownload = true,
                title = "5GB.zip",
                text = "Download paused",
                actionTitles = listOf("Resume", "Cancel"),
            )
        )
        assertEquals(NotificationType.DOWNLOAD, classify(hasProgress = true, isDownload = true))
    }

    @Test
    fun chromeIncognitoStaysARegularNotification() {
        assertTrue(ChromeNotificationPolicy.isIncognito("com.android.chrome", "incognito"))
        assertFalse(ChromeNotificationPolicy.isIncognito("com.android.chrome", "downloads"))
        assertFalse(ChromeNotificationPolicy.isIncognito("org.mozilla.firefox", "incognito"))
        assertEquals(NotificationType.STANDARD, classify(hasProgress = false, isDownload = false))
    }

    @Test
    fun chromeBetaIncognitoIsChromeSpecific() {
        assertTrue(ChromeNotificationPolicy.isChrome("com.chrome.beta"))
        assertTrue(ChromeNotificationPolicy.isIncognito("com.chrome.dev", "incognito"))
        assertFalse(ChromeNotificationPolicy.isChrome("com.google.android.apps.chromecast.app"))
    }

    private fun classify(hasProgress: Boolean, isDownload: Boolean) = RawNotificationTypeClassifier.classify(
        RawNotificationTypeSignals(
            isScreenRecording = false,
            isCall = false,
            isNavigation = false,
            isTimer = false,
            isMedia = false,
            isMessage = false,
            hasProgress = hasProgress,
            isDownload = isDownload,
        )
    )
}
