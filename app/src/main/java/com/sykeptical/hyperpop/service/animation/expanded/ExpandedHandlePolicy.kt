package com.sykeptical.hyperpop.service.animation.expanded

import kotlin.math.max

/**
 * Seats Xiaomi's bottom mini-window bar inside the expanded card.
 *
 * The tuned content padding stays above the bar. The bar's own height and
 * the margin Xiaomi keeps under it are added beneath that padding, and the
 * same card bottom is what radius, clip, and background use. Callers pass
 * the handle-unaware bottom. Xiaomi's untrimmed expanded height already
 * contains the bar, so that path does not call [seatBottom].
 */
object ExpandedHandlePolicy {
    const val CONTENT_GAP_DP = 6f

    fun seatBottom(
        contentBottom: Int,
        leafBottom: Int,
        handle: BottomWindowHandle?,
        density: Float,
        displayHeight: Int,
    ): Int {
        val tail = tailPx(handle) ?: return contentBottom
        if (density <= 0f || leafBottom > contentBottom) return contentBottom
        val minimumAir = (CONTENT_GAP_DP * density).toInt().coerceAtLeast(0)
        val tunedAir = contentBottom - leafBottom
        val seated = leafBottom + max(tunedAir, minimumAir) + tail
        if (seated <= contentBottom) return contentBottom
        val clamped = seated.coerceAtMost(displayHeight)
        return if (clamped > contentBottom) clamped else contentBottom
    }

    /** Window rect of the bar plus the margin under it, inside [card]. */
    fun reserved(card: IslandRect, handle: BottomWindowHandle?): IslandRect? {
        val tail = tailPx(handle) ?: return null
        if (card.isEmpty()) return null
        val top = (card.bottom - tail).coerceAtLeast(card.top)
        if (top >= card.bottom) return null
        return IslandRect(card.left, top, card.right, card.bottom)
    }

    private fun tailPx(handle: BottomWindowHandle?): Int? {
        if (handle == null || handle.heightPx <= 0) return null
        val tail = handle.heightPx + handle.bottomMarginPx.coerceAtLeast(0)
        return tail.takeIf { it > 0 }
    }
}
