package com.sykeptical.hyperpop.service.animation.expanded

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Places expanded content against the camera hole.
 *
 * A leaf that crosses the cutout column stays below the hole plus a small
 * density-scaled gap. A leaf that sits fully to one side may start higher,
 * limited by the rounded corner at its own x span. Full-bleed decorative
 * backgrounds are ignored so they do not push real content down.
 */
object CutoutSafeLayout {
    const val SAFETY_DP = ExpandedVisualTokens.TIGHT_SAFETY_DP
    const val HORIZONTAL_PAD_DP = ExpandedVisualTokens.LEGACY_HORIZONTAL_PAD_DP
    const val EDGE_PAD_DP = ExpandedVisualTokens.LEGACY_EDGE_PAD_DP
    const val MIN_LIFT_DP = ExpandedVisualTokens.LEGACY_MIN_LIFT_DP

    /**
     * Per-child lifts pull rewind, forward, and time labels away from the
     * native media layout. The card moves as one piece instead.
     */
    const val SIDE_LIFTS_ENABLED = false

    data class Result(
        val contentOriginY: Int,
        val sideLifts: List<SideLift>,
        val textClips: List<TextClip> = emptyList(),
    )

    fun solve(
        cardLeft: Int,
        cardRight: Int,
        cardTop: Int,
        cutout: IslandRect,
        density: Float,
        radiusPx: Float,
        profile: ExpandedContentProfile,
        pill: Boolean = false,
        rtl: Boolean = false,
        contentLiftPx: Int = 0,
    ): Result {
        val safety = (SAFETY_DP * density).toInt()
        val edge = (EDGE_PAD_DP * density).toInt()
        val hPad = (HORIZONTAL_PAD_DP * density).toInt()
        val minLift = (MIN_LIFT_DP * density).toInt()
        val exclusionLeft = cutout.left - hPad
        val exclusionRight = cutout.right + hPad
        val contentLeft = contentLeft(cardLeft, cardRight, profile.contentWidthPx)
        val leaves = profile.leaves.filter { it.kind != ContentLeafKind.DECORATIVE && !it.bounds.isEmpty() }
        val band = if (pill) CameraBandGeometry.exclusion(cutout, density) else null
        val minBand = ExpandedVisualTokens.px(ExpandedVisualTokens.MIN_BAND_TEXT_DP, density)
        val textGap = ExpandedVisualTokens.px(ExpandedVisualTokens.CAMERA_TEXT_GAP_DP, density)
        var origin = cardTop
        for (leaf in leaves) {
            val windowLeft = contentLeft + leaf.bounds.left
            val windowRight = contentLeft + leaf.bounds.right
            val minTop = if (band != null) {
                bandMinTop(
                    leaf, windowLeft, windowRight, band, cardLeft, cardRight, cardTop,
                    radiusPx, edge, minBand, textGap, rtl,
                )
            } else {
                val crosses = windowLeft < exclusionRight && windowRight > exclusionLeft
                if (crosses) {
                    cutout.bottom + safety
                } else {
                    cardTop + cornerInsetY(cardLeft, cardRight, radiusPx, windowLeft, windowRight) + edge
                }
            }
            origin = max(origin, minTop - leaf.bounds.top)
        }
        if (band != null) {
            origin += titleCutoutDrop(leaves, contentLeft, origin, cutout, density)
            if (contentLiftPx > 0) {
                val lifted = (origin - contentLiftPx).coerceAtLeast(cardTop)
                origin = lifted + titleCutoutDrop(
                    leaves, contentLeft, lifted, cutout, density,
                    gapDp = ExpandedVisualTokens.NO_ACTION_TITLE_GAP_DP,
                )
            }
        }
        val clips = if (band == null) {
            emptyList()
        } else {
            clipsFor(leaves, contentLeft, origin, band, minBand, textGap, rtl, density)
        }
        val lifts = if (!SIDE_LIFTS_ENABLED) {
            emptyList()
        } else {
            profile.clusters.mapNotNull { cluster ->
                if (cluster.decorative || cluster.bounds.isEmpty()) return@mapNotNull null
                val windowLeft = contentLeft + cluster.bounds.left
                val windowRight = contentLeft + cluster.bounds.right
                if (windowLeft < exclusionRight && windowRight > exclusionLeft) return@mapNotNull null
                val desired = cardTop +
                    cornerInsetY(cardLeft, cardRight, radiusPx, windowLeft, windowRight) +
                    edge
                val current = origin + cluster.bounds.top
                val lift = current - desired
                if (lift < minLift) null else SideLift(cluster.index, -lift)
            }
        }
        return Result(origin, lifts, clips)
    }

    /**
     * Lowest extra drop that puts a crossing primary title under the camera
     * hole. The pill then follows that new top gap below the content.
     */
    private fun titleCutoutDrop(
        leaves: List<ContentLeaf>,
        contentLeft: Int,
        origin: Int,
        cutout: IslandRect,
        density: Float,
        gapDp: Float = ExpandedVisualTokens.CAMERA_VERTICAL_GAP_DP,
    ): Int {
        val gap = ExpandedVisualTokens.px(gapDp, density)
        var drop = 0
        for (leaf in leaves) {
            if (leaf.role != ContentLeafRole.PRIMARY_TITLE) continue
            val windowLeft = contentLeft + leaf.bounds.left
            val windowRight = contentLeft + leaf.bounds.right
            if (windowRight <= cutout.left || windowLeft >= cutout.right) continue
            val needed = cutout.bottom + gap - (origin + leaf.bounds.top)
            if (needed > drop) drop = needed
        }
        return drop.coerceAtLeast(0)
    }

    private fun bandMinTop(
        leaf: ContentLeaf,
        windowLeft: Int,
        windowRight: Int,
        band: IslandRect,
        cardLeft: Int,
        cardRight: Int,
        cardTop: Int,
        radiusPx: Float,
        edge: Int,
        minBand: Int,
        textGap: Int,
        rtl: Boolean,
    ): Int {
        val corner = cardTop + cornerInsetY(cardLeft, cardRight, radiusPx, windowLeft, windowRight) + edge
        val crosses = windowLeft < band.right && windowRight > band.left
        if (!crosses) return corner
        if (leaf.sharesCameraBand()) {
            val span = safeSpan(windowLeft, windowRight, band, textGap, rtl)
            if (span >= minBand) return corner
        }
        return band.bottom
    }

    private fun clipsFor(
        leaves: List<ContentLeaf>,
        contentLeft: Int,
        origin: Int,
        band: IslandRect,
        minBand: Int,
        textGap: Int,
        rtl: Boolean,
        density: Float,
    ): List<TextClip> {
        val clips = ArrayList<TextClip>(2)
        for (leaf in leaves) {
            if (!leaf.sharesCameraBand()) continue
            val windowLeft = contentLeft + leaf.bounds.left
            val windowRight = contentLeft + leaf.bounds.right
            val top = origin + leaf.bounds.top
            val bottom = origin + leaf.bounds.bottom
            val crosses = windowLeft < band.right && windowRight > band.left &&
                top < band.bottom && bottom > band.top
            if (!crosses) continue
            val span = safeSpan(windowLeft, windowRight, band, textGap, rtl)
            if (span < minBand) continue
            val safeLeft = if (!rtl) windowLeft else band.right + textGap
            val safeRight = if (!rtl) band.left - textGap else windowRight
            if (safeRight <= safeLeft) continue
            clips += TextClip(
                safeLeft = safeLeft,
                safeRight = safeRight,
                fadePx = CutoutTextClipPolicy.fadePx(safeRight - safeLeft, density),
                cutoutLimited = true,
            )
        }
        return clips
    }

    private fun safeSpan(
        windowLeft: Int,
        windowRight: Int,
        band: IslandRect,
        textGap: Int,
        rtl: Boolean,
    ): Int = if (!rtl) {
        (band.left - textGap - windowLeft).coerceAtLeast(0)
    } else {
        (windowRight - band.right - textGap).coerceAtLeast(0)
    }

    fun contentLeft(cardLeft: Int, cardRight: Int, contentWidth: Int): Int {
        val slack = (cardRight - cardLeft - contentWidth).coerceAtLeast(0)
        return cardLeft + slack / 2
    }

    /** How far below [cardTop] a span has to start so its top corners clear the curve. */
    fun cornerInsetY(
        cardLeft: Int,
        cardRight: Int,
        radiusPx: Float,
        x0: Int,
        x1: Int,
    ): Int {
        if (radiusPx <= 1f || x1 <= x0) return 0
        val right = (x1 - 1).coerceAtLeast(x0)
        return ceil(max(insetAt(cardLeft, cardRight, radiusPx, x0.toFloat()), insetAt(cardLeft, cardRight, radiusPx, right.toFloat()))).toInt()
    }

    private fun insetAt(cardLeft: Int, cardRight: Int, radiusPx: Float, x: Float): Float {
        val dx = when {
            x < cardLeft + radiusPx -> cardLeft + radiusPx - x
            x > cardRight - radiusPx -> x - (cardRight - radiusPx)
            else -> return 0f
        }.coerceAtLeast(0f)
        if (dx >= radiusPx) return radiusPx
        val inside = radiusPx * radiusPx - dx * dx
        return radiusPx - sqrt(inside.coerceAtLeast(0f))
    }
}
