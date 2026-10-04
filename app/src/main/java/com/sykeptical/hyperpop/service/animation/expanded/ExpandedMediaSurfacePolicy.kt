package com.sykeptical.hyperpop.service.animation.expanded

/**
 * The media player stays at Xiaomi's measured height so controls do not move.
 * The island clip and the window-drag handle continue below that player.
 * The background has to cover that same tail; the host that draws it has to
 * be tall enough for those pixels to exist inside the island.
 */
object ExpandedMediaSurfacePolicy {
    fun extensionPx(cardBottom: Int, surfaceBottom: Int): Int =
        (cardBottom - surfaceBottom).coerceAtLeast(0)

    /**
     * Drag hides the real copy, so a fresh measurement can come back short.
     * Keep the tail that was resolved before the gesture, and still allow it
     * to grow. Once the drag is over, the live measurement wins.
     */
    fun heldExtension(recorded: Int, measured: Int, dragging: Boolean): Int =
        if (dragging && recorded > measured) recorded else measured

    fun bottomMargin(baseMargin: Int, extensionPx: Int): Int = baseMargin - extensionPx

    /**
     * Height that puts this host's bottom on [cardBottom]. A host outside the
     * expanded view that is already taller than the card is unchanged, so a
     * full-screen ancestor is left alone.
     *
     * [currentHeight] is the last laid-out size, [layoutHeight] what the host
     * will be measured with. A media refresh restores the host's params and
     * rebuilds the session before Xiaomi lays out again, so the laid-out size
     * is stale: it may equal or exceed the card while the params already say
     * wrap content, and the next layout shrinks the host under the extended
     * background. For hosts that belong to the expanded view ([owned]) the
     * params alone decide, and the height is exactly the card.
     */
    fun hostHeight(
        windowTop: Int,
        cardBottom: Int,
        currentHeight: Int,
        layoutHeight: Int,
        owned: Boolean,
    ): Int? {
        if (currentHeight <= 0 || cardBottom <= windowTop) return null
        val target = cardBottom - windowTop
        if (!owned && currentHeight > target) return null
        return target.takeIf { it != layoutHeight }
    }
}
