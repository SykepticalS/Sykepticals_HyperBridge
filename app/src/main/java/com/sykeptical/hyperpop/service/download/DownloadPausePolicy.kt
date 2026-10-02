package com.sykeptical.hyperpop.service.download

/**
 * A paused Chrome download drops its progress bar and ongoing flag. The notification is still
 * that download: Resume replaces Pause, or the text says the download is paused.
 */
object DownloadPausePolicy {
    fun isPaused(
        isDownload: Boolean,
        title: String,
        text: String,
        actionTitles: List<String>,
        extraText: String = "",
        finished: Boolean = false,
        groupSummary: Boolean = false,
    ): Boolean {
        if (!isDownload || finished || groupSummary) return false
        val hasResume = actionTitles.any(::isResumeControl)
        val hasPause = actionTitles.any {
            DownloadTransportControls.glyph(it) == DownloadTransportControls.Glyph.PAUSE
        }
        if (hasResume && !hasPause) return true
        val blob = "$title\n$text\n$extraText"
        return PAUSED_HINTS.any { hint -> blob.contains(hint, ignoreCase = true) }
    }

    private fun isResumeControl(title: String): Boolean {
        if (title.isBlank()) return false
        val value = title.lowercase()
        return RESUME_CONTROLS.any { it in value }
    }

    private val RESUME_CONTROLS = listOf(
        "resume", "reanud", "reprendre", "fortsetz", "继续", "繼續", "恢复", "恢復",
    )

    private val PAUSED_HINTS = listOf(
        "paused", "pausad", "en pausa", "duraklat", "pausiert", "일시중지", "已暂停", "已暫停",
    )
}
