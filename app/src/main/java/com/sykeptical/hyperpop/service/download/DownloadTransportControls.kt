package com.sykeptical.hyperpop.service.download

/**
 * Pause, resume, and cancel labels used by Play Store, Chrome, and other download notifications.
 * These controls are drawn as finished focus buttons, so the source app's bitmap is not reused.
 */
object DownloadTransportControls {
    enum class Glyph {
        PAUSE,
        RESUME,
        CANCEL,
    }

    fun glyph(title: String, isDelete: Boolean = false): Glyph? {
        if (isDelete) return Glyph.CANCEL
        val value = title.lowercase()
        if (value.isBlank()) return null
        return when {
            PAUSE.any { it in value } -> Glyph.PAUSE
            RESUME.any { it in value } -> Glyph.RESUME
            CANCEL.any { it in value } -> Glyph.CANCEL
            else -> null
        }
    }

    private val PAUSE = listOf(
        "pause", "pausar", "pausa", "pausier", "暂停", "暫停",
    )

    private val RESUME = listOf(
        "resume", "play", "contin", "reanud", "reprendre", "fortsetz", "继续", "繼續", "恢复", "恢復",
    )

    private val CANCEL = listOf(
        "cancel", "stop", "remove", "delete", "cancelar", "detener", "eliminar",
        "abbrechen", "annuler", "取消", "停止",
    )
}
