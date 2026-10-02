package com.sykeptical.hyperpop.service.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSessionTrackerTest {
    @Test
    fun chromeProgressWithNewNotificationKeyStaysOnTheSameIsland() {
        val tracker = DownloadSessionTracker()
        val first = tracker.resolve(
            input(
                sourceKey = "0|com.android.chrome|11|null|0",
                notificationId = 11,
                title = "report.pdf",
                text = "1.2 MB / 10 MB",
                now = 1_000L,
            )
        )
        val second = tracker.resolve(
            input(
                sourceKey = "0|com.android.chrome|12|null|0",
                notificationId = 12,
                title = "report.pdf",
                text = "4.8 MB / 10 MB",
                now = 1_200L,
            )
        )

        assertEquals(first.logicalId, second.logicalId)
        assertTrue(second.sourceReplacement)
        assertEquals("report.pdf", DownloadIdentity.stableLabel("report.pdf", "4.8 MB / 10 MB"))
    }

    @Test
    fun playStoreInPlaceSlotKeepsTheSameIdentity() {
        val tracker = DownloadSessionTracker()
        val first = tracker.resolve(
            input(
                sourceKey = "0|com.android.vending|7|null|0",
                packageName = "com.android.vending",
                notificationId = 7,
                title = "Maps",
                text = "12 MB / 50 MB",
                now = 1_000L,
            )
        )
        val second = tracker.resolve(
            input(
                sourceKey = "0|com.android.vending|7|null|0",
                packageName = "com.android.vending",
                notificationId = 7,
                title = "Maps",
                text = "24 MB / 50 MB",
                now = 1_400L,
            )
        )

        assertEquals(first.logicalId, second.logicalId)
        assertFalse(second.sourceReplacement)
    }

    @Test
    fun differentFilesDoNotShareAnIsland() {
        val tracker = DownloadSessionTracker()
        val first = tracker.resolve(input(sourceKey = "chrome:1", title = "one.pdf", notificationId = 1, now = 1_000L))
        val second = tracker.resolve(input(sourceKey = "chrome:2", title = "two.pdf", notificationId = 2, now = 1_100L))
        assertNotEquals(first.logicalId, second.logicalId)
    }

    @Test
    fun chromeCancelAndRepostRebindsDuringGraceWindow() {
        val tracker = DownloadSessionTracker()
        val first = tracker.resolve(input(sourceKey = "chrome:1", notificationId = 1, title = "clip.mp4", now = 1_000L))
        tracker.markSourceRemoved("chrome:1", 1_050L)
        val second = tracker.resolve(input(sourceKey = "chrome:2", notificationId = 2, title = "clip.mp4", now = 1_200L))
        assertEquals(first.logicalId, second.logicalId)
    }

    @Test
    fun stableLabelStripsProgressNoise() {
        assertEquals("holiday.mp4", DownloadIdentity.stableLabel("Downloading holiday.mp4", "12% • 3 MB/s"))
    }

    @Test
    fun finishedNoticeDoesNotCountAsALiveDownload() {
        val tracker = DownloadSessionTracker()
        val first = tracker.resolve(input(sourceKey = "chrome:1", notificationId = 1, title = "clip.mp4", now = 1_000L))
        tracker.markSourceRemoved("chrome:1", 1_050L)
        val finished = tracker.resolve(
            input(sourceKey = "chrome:2", notificationId = 2, title = "clip.mp4", now = 1_200L).copy(finished = true)
        )

        assertEquals(first.logicalId, finished.logicalId)
        assertFalse(tracker.hasLiveProgress(first.logicalId, "chrome:1"))
        assertTrue(tracker.isFinished(first.logicalId))
    }

    @Test
    fun pausedRepostKeepsTheFocusDownloadAndFrozenProgress() {
        val tracker = DownloadSessionTracker()
        val active = tracker.resolve(
            input(sourceKey = "chrome:1", notificationId = 1, title = "clip.mp4", now = 1_000L, progressPercent = 42),
        )
        tracker.markSourceRemoved("chrome:1", 1_050L)
        val paused = tracker.resolve(
            input(
                sourceKey = "chrome:2",
                notificationId = 2,
                title = "clip.mp4",
                text = "Download paused",
                now = 1_200L,
                paused = true,
            ),
        )

        assertEquals(active.logicalId, paused.logicalId)
        assertTrue(paused.paused)
        assertEquals(42, paused.progressPercent)
        assertTrue(tracker.matchesLiveDownload("com.android.chrome", "other", "clip.mp4", "Download paused"))
        assertFalse(tracker.matchesLiveDownload("com.android.chrome", "other", "notes.txt", "Download paused"))
    }

    @Test
    fun unfinishedRepostKeepsTheDownloadAlive() {
        val tracker = DownloadSessionTracker()
        val first = tracker.resolve(input(sourceKey = "chrome:1", notificationId = 1, title = "clip.mp4", now = 1_000L))
        tracker.markSourceRemoved("chrome:1", 1_050L)
        tracker.resolve(input(sourceKey = "chrome:2", notificationId = 2, title = "clip.mp4", now = 1_200L))

        assertTrue(tracker.hasLiveProgress(first.logicalId, "chrome:1"))
    }

    private fun input(
        sourceKey: String = "0|com.android.chrome|1|null|0",
        packageName: String = "com.android.chrome",
        notificationId: Int = 1,
        title: String = "file.bin",
        text: String = "1 MB / 2 MB",
        now: Long = 1_000L,
        progressPercent: Int? = null,
        paused: Boolean = false,
    ) = DownloadSessionInput(
        sourceKey = sourceKey,
        packageName = packageName,
        notificationId = notificationId,
        notificationTag = null,
        title = title,
        text = text,
        observedAt = now,
        progressPercent = progressPercent,
        paused = paused,
    )
}
