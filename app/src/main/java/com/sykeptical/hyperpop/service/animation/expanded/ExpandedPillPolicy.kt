package com.sykeptical.hyperpop.service.animation.expanded

import kotlin.math.max
import kotlin.math.min

/**
 * Rounded-pill compactness. Empty space below the last real leaf matches the
 * empty space above the first one, and never goes under the minimum pad.
 * The radius follows the resulting height and never drops below Xiaomi's
 * native cap. Content scale is a last resort and stays inside the touch floor.
 */
object ExpandedPillPolicy {
    const val CAP_DP = ExpandedVisualTokens.PILL_CAP_DP
    const val BOTTOM_PAD_DP = ExpandedVisualTokens.PILL_BOTTOM_PAD_DP
    const val MIN_SCALE = ExpandedVisualTokens.PILL_MIN_SCALE
    const val MIN_TOUCH_DP = ExpandedVisualTokens.MIN_TOUCH_DP

    data class Result(
        val cardBottom: Int,
        val radiusPx: Float,
        val contentScale: Float,
    )

    fun radiusCap(cardHeight: Int, nativeRadiusPx: Float, density: Float): Float {
        val floor = nativeRadiusPx.coerceAtLeast(0f)
        val cap = max(floor, CAP_DP * density)
        return (cardHeight / 2f).coerceIn(floor, cap)
    }

    fun apply(
        enabled: Boolean,
        cardLeft: Int,
        cardRight: Int,
        cardTop: Int,
        provisionalBottom: Int,
        contentOriginY: Int,
        contentLeft: Int,
        nativeRadiusPx: Float,
        density: Float,
        profile: ExpandedContentProfile?,
        displayHeight: Int,
        handle: BottomWindowHandle? = null,
    ): Result {
        val nativeBottom = provisionalBottom.coerceAtMost(displayHeight)
        if (!enabled || profile == null) {
            val radius = if (enabled) radiusCap(nativeBottom - cardTop, nativeRadiusPx, density) else nativeRadiusPx
            return Result(nativeBottom, radius, 1f)
        }
        val pad = (BOTTOM_PAD_DP * density).toInt()
        val real = profile.leaves.filter { it.kind != ContentLeafKind.DECORATIVE && !it.bounds.isEmpty() }
        val highest = real.minOfOrNull { it.bounds.top } ?: 0
        val lowest = real.maxOfOrNull { it.bounds.bottom } ?: profile.contentHeightPx
        val topGap = (contentOriginY + highest - cardTop).coerceAtLeast(0)
        val bottomGap = max(pad, topGap)
        val desired = (contentOriginY + lowest + bottomGap).coerceAtMost(displayHeight)
        var bottom = max(desired, contentOriginY + lowest)
        if (bottom <= cardTop) bottom = nativeBottom
        bottom = ExpandedHandlePolicy.seatBottom(
            bottom,
            contentOriginY + lowest,
            handle,
            density,
            displayHeight,
        )
        var radius = radiusCap(bottom - cardTop, nativeRadiusPx, density)
        radius = relaxRadius(
            cardLeft, cardRight, cardTop, bottom, contentOriginY, contentLeft, radius, nativeRadiusPx, real,
        )
        return Result(bottom, radius, 1f)
    }

    /**
     * Scale only when even the native radius still clips, and never enough to
     * push a previously usable control under [MIN_TOUCH_DP].
     */
    fun boundedScale(leaves: List<ContentLeaf>, density: Float): Float {
        val floor = MIN_TOUCH_DP * density
        val interactive = leaves.filter { it.kind == ContentLeafKind.INTERACTIVE && !it.bounds.isEmpty() }
        val shortest = interactive.minOfOrNull { min(it.bounds.width, it.bounds.height) }
        if (shortest != null && shortest >= floor && shortest * MIN_SCALE < floor) return 1f
        return MIN_SCALE
    }

    private fun relaxRadius(
        cardLeft: Int,
        cardRight: Int,
        cardTop: Int,
        cardBottom: Int,
        contentOriginY: Int,
        contentLeft: Int,
        radiusPx: Float,
        nativeRadiusPx: Float,
        leaves: List<ContentLeaf>,
    ): Float {
        if (fits(cardLeft, cardRight, cardTop, cardBottom, contentOriginY, contentLeft, radiusPx, leaves)) {
            return radiusPx
        }
        var high = radiusPx
        var low = nativeRadiusPx.coerceAtLeast(0f)
        if (high <= low) return low
        repeat(8) {
            val mid = (low + high) / 2f
            if (fits(cardLeft, cardRight, cardTop, cardBottom, contentOriginY, contentLeft, mid, leaves)) {
                low = mid
            } else {
                high = mid
            }
        }
        return low
    }

    private fun fits(
        cardLeft: Int,
        cardRight: Int,
        cardTop: Int,
        cardBottom: Int,
        contentOriginY: Int,
        contentLeft: Int,
        radiusPx: Float,
        leaves: List<ContentLeaf>,
    ): Boolean {
        for (leaf in leaves) {
            val left = contentLeft + leaf.bounds.left
            val right = contentLeft + leaf.bounds.right
            val inset = CutoutSafeLayout.cornerInsetY(cardLeft, cardRight, radiusPx, left, right)
            val top = contentOriginY + leaf.bounds.top
            val bottom = contentOriginY + leaf.bounds.bottom
            if (top < cardTop + inset) return false
            if (bottom > cardBottom - inset) return false
        }
        return true
    }
}
