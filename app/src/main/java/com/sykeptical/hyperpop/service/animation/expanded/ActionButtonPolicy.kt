package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Notification text buttons can be shorter than the minimum touch size.
 * The extra hit area grows into the gap around the visual bounds and stops
 * at [leftLimit] / [rightLimit] so neighbours do not share pixels.
 */
object ActionButtonPolicy {
    fun visualHeightPx(density: Float): Int =
        ExpandedVisualTokens.px(ExpandedVisualTokens.ACTION_PILL_VISUAL_HEIGHT_DP, density)

    fun touchRect(
        visual: IslandRect,
        density: Float,
        leftLimit: Int,
        rightLimit: Int,
    ): IslandRect {
        if (visual.isEmpty()) return visual
        val minTouch = ExpandedVisualTokens.px(ExpandedVisualTokens.MIN_TOUCH_DP, density)
        val extraY = (minTouch - visual.height).coerceAtLeast(0)
        val top = visual.top - extraY / 2
        val bottom = top + visual.height + extraY
        val extraX = (minTouch - visual.width).coerceAtLeast(0)
        val leftRoom = (visual.left - leftLimit).coerceAtLeast(0)
        val rightRoom = (rightLimit - visual.right).coerceAtLeast(0)
        val growLeft = minOf(extraX / 2, leftRoom)
        val growRight = minOf(extraX - growLeft, rightRoom)
        return IslandRect(
            visual.left - growLeft,
            top,
            visual.right + growRight,
            bottom,
        )
    }
}
