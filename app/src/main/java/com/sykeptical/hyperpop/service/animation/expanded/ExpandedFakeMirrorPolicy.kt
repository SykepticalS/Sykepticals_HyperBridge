package com.sykeptical.hyperpop.service.animation.expanded

/**
 * The bottom-handle drag shows a second copy of the expanded content.
 * HyperPop tunes that copy from the same decision as the real one.
 * A generation, stamp, and layout token say whether the copy is already
 * in sync, so a drag that did not reset it costs one comparison.
 * Originals on that copy stay applied while Xiaomi is still drawing it.
 */
object ExpandedFakeMirrorPolicy {
    data class Marker(val generation: Long, val stamp: Int, val token: Int)

    fun stamp(bodyOffsetPx: Int, cardBottom: Int, contentScale: Float): Int =
        bodyOffsetPx * 31 + cardBottom + (contentScale * 1000f).toInt()

    fun layoutToken(childTopMargins: IntArray): Int {
        var token = childTopMargins.size
        for (margin in childTopMargins) token = token * 31 + margin
        return token
    }

    fun needsSync(recorded: Marker?, generation: Long, stamp: Int, token: Int): Boolean =
        recorded == null ||
            recorded.generation != generation ||
            recorded.stamp != stamp ||
            recorded.token != token

    fun marker(generation: Long, stamp: Int, token: Int): Marker =
        Marker(generation, stamp, token)

    /** The fake copy is what the user sees only while Xiaomi has it visible. */
    fun deferRestore(fakeVisible: Boolean): Boolean = fakeVisible
}
