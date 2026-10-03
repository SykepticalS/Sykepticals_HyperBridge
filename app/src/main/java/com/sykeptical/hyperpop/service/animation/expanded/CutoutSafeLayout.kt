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
    const val SAFETY_DP = 4f
    const val HORIZONTAL_PAD_DP = 4f
    const val EDGE_PAD_DP = 2f
    const val MIN_LIFT_DP = 4f

    /**
     * Per-child lifts pull rewind, forward, and time labels away from the
     * native media layout. The card moves as one piece instead.
     */
    const val SIDE_LIFTS_ENABLED = false

    data class Result(
        val contentOriginY: Int,
        val sideLifts: List<SideLift>,
    )

    fun solve(
        cardLeft: Int,
        cardRight: Int,
        cardTop: Int,
        cutout: IslandRect,
        density: Float,
        radiusPx: Float,
        profile: ExpandedContentProfile,
    ): Result {
        val safety = (SAFETY_DP * density).toInt()
        val edge = (EDGE_PAD_DP * density).toInt()
        val hPad = (HORIZONTAL_PAD_DP * density).toInt()
        val minLift = (MIN_LIFT_DP * density).toInt()
        val exclusionLeft = cutout.left - hPad
        val exclusionRight = cutout.right + hPad
        val contentLeft = contentLeft(cardLeft, cardRight, profile.contentWidthPx)
        val leaves = profile.leaves.filter { it.kind != ContentLeafKind.DECORATIVE && !it.bounds.isEmpty() }
        var origin = cardTop
        for (leaf in leaves) {
            val windowLeft = contentLeft + leaf.bounds.left
            val windowRight = contentLeft + leaf.bounds.right
            val crosses = windowLeft < exclusionRight && windowRight > exclusionLeft
            val minTop = if (crosses) {
                cutout.bottom + safety
            } else {
                cardTop + cornerInsetY(cardLeft, cardRight, radiusPx, windowLeft, windowRight) + edge
            }
            origin = max(origin, minTop - leaf.bounds.top)
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
        return Result(origin, lifts)
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
