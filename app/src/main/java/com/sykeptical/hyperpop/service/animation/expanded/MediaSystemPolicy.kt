package com.sykeptical.hyperpop.service.animation.expanded

/**
 * System (native) expanded media layout only. Other presets keep their own
 * metrics. Values are margins in pixels, applied through the ConstraintSet
 * Xiaomi reloads on attach.
 */
object MediaSystemPolicy {
    data class Margins(
        val artStartPx: Int,
        val artTopPx: Int,
        val titleTopPx: Int,
        val seamlessTopPx: Int,
        val seamlessEndPx: Int,
        val actionTopPx: Int,
        val progressBottomPx: Int,
    )

    fun margins(density: Float): Margins {
        val artTop = ExpandedVisualTokens.px(ExpandedVisualTokens.MEDIA_ART_TOP_DP, density)
        val artSize = ExpandedVisualTokens.MEDIA_ART_SIZE_DP * density
        val seamless = ExpandedVisualTokens.MEDIA_SEAMLESS_SIZE_DP * density
        val seamlessTop = artTop + ((artSize - seamless) / 2f).toInt().coerceAtLeast(0)
        return Margins(
            artStartPx = ExpandedVisualTokens.px(ExpandedVisualTokens.MEDIA_ART_INSET_DP, density),
            artTopPx = artTop,
            titleTopPx = artTop + ExpandedVisualTokens.px(ExpandedVisualTokens.MEDIA_TITLE_BELOW_ART_DP, density),
            seamlessTopPx = seamlessTop,
            seamlessEndPx = ExpandedVisualTokens.px(ExpandedVisualTokens.OUTER_HORIZONTAL_DP, density),
            actionTopPx = ExpandedVisualTokens.px(ExpandedVisualTokens.MEDIA_ACTION_TOP_DP, density),
            progressBottomPx = ExpandedVisualTokens.px(ExpandedVisualTokens.MEDIA_PROGRESS_BOTTOM_DP, density),
        )
    }
}
