package com.sykeptical.hyperpop.service.animation.expanded

import kotlin.math.min

/**
 * Progress-driven fill, corner radius, and the Ambient Flow black fade.
 * Progress 0 matches Xiaomi. Progress 1 is the selected expanded style.
 */
data class FlowMask(
    val blackUntilY: Int,
    val fadeEndY: Int,
) {
    fun alphaAt(y: Float): Float = ExpandedSurfaceStyle.smoothstep(y, blackUntilY.toFloat(), fadeEndY.toFloat())
}

object ExpandedSurfaceStyle {
    const val BLACK: Int = 0xFF000000.toInt()
    private const val FADE_FRACTION = 0.35f
    private const val MIN_FADE_DP = 20f
    private const val MAX_FADE_DP = 56f

    fun fill(nativeFill: Int, black: Boolean, progress: Float): Int {
        if (!black || progress <= 0f) return nativeFill
        if (progress >= 1f) return BLACK
        return lerpArgb(nativeFill, BLACK, progress)
    }

    /** The owned card plate. It does not lerp Xiaomi's fill. */
    fun ownedPlateFill(): Int = BLACK

    /**
     * The outline draws the card into [actualHeight] before the view's layout
     * height catches up. The tallest measurement is the card on screen.
     */
    fun drawnHeight(clipPx: Int, expandedHeight: Int, backgroundHeight: Int, actualHeight: Int): Int =
        maxOf(clipPx, expandedHeight, backgroundHeight, actualHeight).coerceAtLeast(0)

    /**
     * Xiaomi's own rule is min(clipHeight / 2, island radius). Pill mode
     * raises that cap along the morph instead of replacing the radius outright.
     */
    fun clipRadius(
        liveClipHeight: Float,
        nativeCap: Float,
        pillCap: Float,
        pill: Boolean,
        progress: Float,
    ): Float {
        if (liveClipHeight <= 0f) return 0f
        val cap = if (!pill) {
            nativeCap
        } else {
            val fraction = progress.coerceIn(0f, 1f)
            nativeCap + (pillCap - nativeCap) * fraction
        }
        return min(liveClipHeight / 2f, cap.coerceAtLeast(0f))
    }

    /**
     * Corner radius applied to Xiaomi's own plate so its stroke follows the
     * pill. The stroke width, stroke color, and `stokeWidth` outset stay
     * Xiaomi's; HyperPop does not draw or clear them.
     */
    fun plateCornerRadius(clipRadiusPx: Float): Float = clipRadiusPx.coerceAtLeast(0f)

    fun morphProgress(liveHeight: Int, compactHeight: Int, cardHeight: Int): Float {
        val span = cardHeight - compactHeight
        if (span <= 1) return 1f
        return ((liveHeight - compactHeight).toFloat() / span).coerceIn(0f, 1f)
    }

    /**
     * Xiaomi's spring overshoots the clip bottom past the card. The media
     * background stops at the card bottom, so the overshoot shows only the
     * black plate as a band under the card. The clip target is the card
     * bottom ([cardBottom], from getExpandedBottom), in the same coordinates.
     */
    fun cappedClipBottom(clipBottom: Float, cardBottom: Int): Float = minOf(clipBottom, cardBottom.toFloat())

    /** The same fade, measured from a card top that may move, such as the fake island's. */
    fun flowMaskFromCardTop(mask: FlowMask, cardTop: Int): FlowMask =
        FlowMask(mask.blackUntilY - cardTop, mask.fadeEndY - cardTop)

    fun flowMask(
        cutout: IslandRect,
        cardBottom: Int,
        density: Float,
        contentBottom: Int = Int.MIN_VALUE,
    ): FlowMask? {
        if (cutout.isEmpty() || density <= 0f) return null
        val safety = (CutoutSafeLayout.SAFETY_DP * density).toInt()
        var blackUntil = cutout.bottom + safety
        if (contentBottom > blackUntil) blackUntil = contentBottom.coerceAtMost(cardBottom - 1)
        if (cardBottom <= blackUntil) return null
        val remaining = (cardBottom - blackUntil).toFloat()
        val fade = (remaining * FADE_FRACTION).coerceIn(MIN_FADE_DP * density, MAX_FADE_DP * density)
        val fadeEnd = min(cardBottom, blackUntil + fade.toInt()).coerceAtLeast(blackUntil + 1)
        return FlowMask(blackUntil, fadeEnd)
    }

    /** 0 at and above [start], 1 at and below [end], with zero slope at both ends. */
    fun smoothstep(y: Float, start: Float, end: Float): Float {
        if (y <= start) return 0f
        if (end <= start || y >= end) return if (y >= end) 1f else 0f
        val t = ((y - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun lerpArgb(from: Int, to: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val start = (from ushr shift) and 0xFF
            val end = (to ushr shift) and 0xFF
            return (start + (end - start) * t).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
