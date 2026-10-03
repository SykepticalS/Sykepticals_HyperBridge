package com.sykeptical.hyperpop.service.animation.expanded

/**
 * How a single line of expanded text meets the camera hole.
 *
 * Fits: the line is already inside the safe span.
 * Container: the line is longer than its view, and the view does not meet the hole.
 * Cutout: the view would run into the hole, so the line scrolls inside the safe span.
 * Fallback: the safe span is too small to read, so the row stays below the hole.
 */
object CutoutTextClipPolicy {
    enum class Fit {
        FITS,
        CONTAINER,
        CUTOUT,
        FALLBACK,
    }

    data class Result(
        val fit: Fit,
        val visiblePx: Int,
        val fadePx: Int,
        val overflowPx: Float,
        val cutoutLimited: Boolean,
    )

    fun fadePx(spanPx: Int, density: Float): Int {
        if (spanPx <= 0 || density <= 0f) return 0
        val minPx = ExpandedVisualTokens.FADE_MIN_DP * density
        val maxPx = ExpandedVisualTokens.FADE_MAX_DP * density
        val raw = spanPx * ExpandedVisualTokens.FADE_SPAN_FRACTION
        return raw.coerceIn(minPx, maxPx).toInt().coerceAtMost(spanPx / 2)
    }

    fun startFadePx(scrollPx: Float, density: Float): Int {
        if (scrollPx <= 0f || density <= 0f) return 0
        val cap = (ExpandedVisualTokens.START_FADE_MAX_DP * density).toInt()
        return scrollPx.toInt().coerceIn(0, cap)
    }

    fun evaluate(
        textWidthPx: Float,
        viewLeft: Int,
        viewRight: Int,
        exclusion: IslandRect,
        density: Float,
        rtl: Boolean,
    ): Result {
        val width = (viewRight - viewLeft).coerceAtLeast(0)
        val gap = ExpandedVisualTokens.px(ExpandedVisualTokens.CAMERA_TEXT_GAP_DP, density)
        val minBand = ExpandedVisualTokens.px(ExpandedVisualTokens.MIN_BAND_TEXT_DP, density)
        val crosses = viewLeft < exclusion.right && viewRight > exclusion.left
        val safe = if (!crosses) {
            width
        } else if (!rtl) {
            (exclusion.left - gap - viewLeft).coerceAtLeast(0)
        } else {
            (viewRight - exclusion.right - gap).coerceAtLeast(0)
        }
        if (crosses && safe < minBand) {
            return Result(Fit.FALLBACK, safe, 0, 0f, cutoutLimited = true)
        }
        val visible = safe.coerceAtMost(width).coerceAtLeast(0)
        val overflow = (textWidthPx - visible).coerceAtLeast(0f)
        val tolerance = density.coerceAtLeast(1f)
        if (overflow <= tolerance) {
            return Result(Fit.FITS, visible, 0, 0f, cutoutLimited = crosses)
        }
        val fade = fadePx(visible, density)
        val fit = if (crosses) Fit.CUTOUT else Fit.CONTAINER
        return Result(fit, visible, fade, overflow, cutoutLimited = crosses)
    }
}
