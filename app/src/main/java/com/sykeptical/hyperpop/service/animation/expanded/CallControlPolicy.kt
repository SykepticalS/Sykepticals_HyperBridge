package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Answer, reject, and hang-up stay at their native visual size. The touch
 * target is never smaller than the visual size or the minimum touch token.
 */
object CallControlPolicy {
    fun visualPx(nativePx: Int): Int = nativePx.coerceAtLeast(0)

    fun touchPx(nativePx: Int, density: Float): Int {
        val visual = visualPx(nativePx)
        val floor = ExpandedVisualTokens.px(ExpandedVisualTokens.MIN_TOUCH_DP, density)
        return maxOf(visual, floor)
    }
}
