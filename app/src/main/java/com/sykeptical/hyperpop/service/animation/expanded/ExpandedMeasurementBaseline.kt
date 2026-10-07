package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Native expanded geometry, kept apart from HyperPop's own margin and from the
 * height Xiaomi writes back after measuring that margin.
 *
 * The expanded view's measured height includes the content top margin. Each
 * later update stores that taller number and the next decision would add the
 * body offset again. A saved baseline plus the clean required height stays
 * put across those updates. Once a body offset is applied, the Xiaomi field
 * cannot raise the saved height; the required content height still can.
 */
object ExpandedMeasurementBaseline {
    /**
     * The margin Xiaomi used before HyperPop shifted the content.
     * A known saved margin wins. Otherwise a live margin that already includes
     * the applied body offset is unwrapped once.
     */
    fun nativeMargin(
        savedNativeMarginPx: Int?,
        liveMarginPx: Int,
        appliedBodyOffsetPx: Int,
    ): Int {
        if (savedNativeMarginPx != null) return savedNativeMarginPx
        return (liveMarginPx - appliedBodyOffsetPx.coerceAtLeast(0)).coerceAtLeast(0)
    }

    /**
     * Height the layout policy should treat as native.
     *
     * Until a body offset is applied, Xiaomi's own field is still trustworthy and
     * can raise a short early reading. After the offset is applied, that field
     * also contains the margin, so only [requiredHeightPx] from the content
     * can raise the saved height. The result never shrinks.
     *
     * [appliedBodyOffsetPx] is the offset HyperPop has written into the content
     * and is still baked into Xiaomi's field. A new session on a view that an
     * earlier session offset passes that carried offset too: Xiaomi measures
     * after the margin and does not rewrite the field when the island collapses,
     * so the field read at the next arm is still the taller number.
     */
    fun mergeHeight(
        savedNativeHeightPx: Int,
        requiredHeightPx: Int,
        xiaomiFieldHeightPx: Int,
        appliedBodyOffsetPx: Int,
    ): Int {
        val saved = savedNativeHeightPx.coerceAtLeast(0)
        val required = requiredHeightPx.coerceAtLeast(0)
        val field = xiaomiFieldHeightPx.coerceAtLeast(0)
        val offset = appliedBodyOffsetPx.coerceAtLeast(0)
        if (offset <= 0 || saved <= 0) return maxOf(saved, required, field)
        return maxOf(saved, required)
    }

    /** How tall the content must be to contain its real leaves. */
    fun requiredHeight(profile: ExpandedContentProfile): Int {
        val leafBottom = profile.leaves
            .asSequence()
            .filter { it.kind != ContentLeafKind.DECORATIVE && !it.bounds.isEmpty() }
            .maxOfOrNull { it.bounds.bottom }
            ?: 0
        return maxOf(profile.contentHeightPx, leafBottom)
    }
}
