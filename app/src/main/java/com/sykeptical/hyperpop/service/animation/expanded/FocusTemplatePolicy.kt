package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Focus template spacing. Pill mode tightens the root and the lower row.
 * Call buttons are not part of this plan; [CallControlPolicy] owns those.
 */
object FocusTemplatePolicy {
    data class Plan(
        val rootMarginPx: Int,
        val belowPaddingPx: Int,
        val pillMarginVerticalPx: Int,
        val pillGapPx: Int,
        val retune: Boolean,
    )

    fun plan(density: Float, pill: Boolean): Plan {
        if (!pill || density <= 0f) {
            return Plan(
                rootMarginPx = ExpandedVisualTokens.px(ExpandedVisualTokens.FOCUS_ROOT_MARGIN_DP, density),
                belowPaddingPx = ExpandedVisualTokens.px(ExpandedVisualTokens.FOCUS_AREA_D_PADDING_DP, density),
                pillMarginVerticalPx = ExpandedVisualTokens.px(ExpandedVisualTokens.FOCUS_PILL_MARGIN_VERTICAL_DP, density),
                pillGapPx = ExpandedVisualTokens.px(8f, density),
                retune = false,
            )
        }
        val text = ExpandedVisualTokens.px(ExpandedVisualTokens.ACTION_PILL_TEXT_DP, density)
        val visual = ExpandedVisualTokens.px(ExpandedVisualTokens.ACTION_PILL_VISUAL_HEIGHT_DP, density)
        val vertical = ((visual - text) / 2).coerceAtLeast(0)
        return Plan(
            rootMarginPx = ExpandedVisualTokens.px(ExpandedVisualTokens.OUTER_HORIZONTAL_DP, density),
            belowPaddingPx = ExpandedVisualTokens.px(ExpandedVisualTokens.BELOW_BAND_GAP_DP, density),
            pillMarginVerticalPx = vertical,
            pillGapPx = ExpandedVisualTokens.px(ExpandedVisualTokens.ACTION_PILL_GAP_DP, density),
            retune = true,
        )
    }
}
