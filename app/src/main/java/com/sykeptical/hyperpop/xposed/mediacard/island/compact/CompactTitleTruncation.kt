package com.sykeptical.hyperpop.xposed.mediacard.island.compact

/**
 * End-ellipsis for a single-line media title, matching Xiaomi's
 * `android:ellipsize="end"` and `android:singleLine="true"` on the island title.
 */
internal object CompactTitleTruncation {
    const val ELLIPSIS = "…"

    fun ellipsize(text: String, availablePx: Float, measure: (String) -> Float): String {
        if (text.isEmpty()) return ""
        if (availablePx <= 0f) return text
        if (measure(text) <= availablePx) return text
        if (measure(ELLIPSIS) > availablePx) return ""
        var low = 0
        var high = text.length
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            val candidate = cut(text, mid) + ELLIPSIS
            if (measure(candidate) <= availablePx) low = mid else high = mid - 1
        }
        if (low == 0) return ELLIPSIS
        return cut(text, low) + ELLIPSIS
    }

    /** Avoid splitting a surrogate pair at the cut. */
    private fun cut(text: String, end: Int): String {
        var index = end.coerceIn(0, text.length)
        if (index in 1 until text.length && text[index - 1].isHighSurrogate()) index -= 1
        return text.take(index).trimEnd()
    }
}
