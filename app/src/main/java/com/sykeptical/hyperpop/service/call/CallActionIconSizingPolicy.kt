package com.sykeptical.hyperpop.service.call

/** Keeps call-control glyphs legible inside HyperOS's already-compact action buttons. */
object CallActionIconSizingPolicy {
    const val MAX_CLASSIC_PADDING_PERCENT = 10
    const val NATIVE_PADDING_PERCENT = 7
    const val ANSWER_REJECT_EXTRA_PADDING_PERCENT = 4

    fun classicPaddingPercent(configuredPaddingPercent: Int): Int =
        configuredPaddingPercent.coerceIn(0, MAX_CLASSIC_PADDING_PERCENT)

    /** Answer and reject only. A few extra points of inset, so the phone glyphs sit slightly smaller. */
    fun answerRejectPaddingPercent(configuredPaddingPercent: Int): Int =
        (classicPaddingPercent(configuredPaddingPercent) + ANSWER_REJECT_EXTRA_PADDING_PERCENT)
            .coerceAtMost(18)
}
