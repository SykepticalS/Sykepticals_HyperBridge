package com.lidesheng.hyperlyric.common

object SuperIslandWidthPolicy {
    const val SIDE_COMPONENT_WIDTH_DP = 22
    const val MIN_ISLAND_WIDTH_DP = 22
    const val MAX_SIDE_WIDTH_DP = 170
    const val SYSTEM_LIMITED_MAX_SIDE_WIDTH_DP = 114

    fun minIslandWidth(showAlbum: Boolean, showRhythm: Boolean): Int =
        (componentWidth(showAlbum) - componentWidth(showRhythm))
            .coerceAtLeast(MIN_ISLAND_WIDTH_DP)

    fun maxIslandWidth(showRhythm: Boolean, disableWidthLimit: Boolean): Int =
        (if (disableWidthLimit) MAX_SIDE_WIDTH_DP else SYSTEM_LIMITED_MAX_SIDE_WIDTH_DP) -
                componentWidth(showRhythm)

    fun normalizeIslandWidth(
        islandWidth: Int,
        showAlbum: Boolean,
        showRhythm: Boolean,
        disableWidthLimit: Boolean = false
    ): Int = islandWidth.coerceIn(
        minIslandWidth(showAlbum, showRhythm),
        maxIslandWidth(showRhythm, disableWidthLimit)
    )

    fun leftContentWidth(
        islandWidth: Int,
        showAlbum: Boolean,
        showRhythm: Boolean,
        disableWidthLimit: Boolean = false
    ): Int {
        val normalizedWidth = normalizeIslandWidth(
            islandWidth = islandWidth,
            showAlbum = showAlbum,
            showRhythm = showRhythm,
            disableWidthLimit = disableWidthLimit
        )
        return normalizedWidth + leftContentWidthOffsetDp(showAlbum, showRhythm)
    }

    fun leftContentWidthOffsetDp(showAlbum: Boolean, showRhythm: Boolean): Int =
        componentWidth(showRhythm) - componentWidth(showAlbum)

    fun baseWidthFromLeftContentWidth(
        leftContentWidthDp: Float,
        showAlbum: Boolean,
        showRhythm: Boolean
    ): Float = leftContentWidthDp - leftContentWidthOffsetDp(showAlbum, showRhythm)

    private fun componentWidth(visible: Boolean): Int =
        if (visible) SIDE_COMPONENT_WIDTH_DP else 0
}
