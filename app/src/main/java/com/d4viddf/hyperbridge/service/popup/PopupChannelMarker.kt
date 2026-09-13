package com.d4viddf.hyperbridge.service.popup

data class DecodedPopupMarker(
    val visibleDescription: String,
    val originalImportance: Int
)

/** Invisible, namespaced recovery metadata. Local Room state remains the primary authority. */
object PopupChannelMarker {
    private const val PREFIX = "\u2063\u2060\u2063"
    private const val SUFFIX = "\u2060\u2063\u2060"
    private const val IMPORTANCE_MARK = '\u200F'

    fun encode(description: String?, originalImportance: Int): String {
        require(originalImportance in 0..5)
        val visible = decode(description)?.visibleDescription ?: description.orEmpty()
        return visible + PREFIX + IMPORTANCE_MARK.toString().repeat(originalImportance + 1) + SUFFIX
    }

    fun decode(description: String?): DecodedPopupMarker? {
        val value = description ?: return null
        if (!value.endsWith(SUFFIX)) return null
        val suffixStart = value.length - SUFFIX.length
        val prefixStart = value.lastIndexOf(PREFIX, suffixStart - 1)
        if (prefixStart < 0) return null
        val marks = value.substring(prefixStart + PREFIX.length, suffixStart)
        if (marks.isEmpty() || marks.any { it != IMPORTANCE_MARK }) return null
        val importance = marks.length - 1
        if (importance !in 0..5) return null
        return DecodedPopupMarker(value.substring(0, prefixStart), importance)
    }

    fun strip(description: String?): String? = decode(description)?.visibleDescription ?: description
}
