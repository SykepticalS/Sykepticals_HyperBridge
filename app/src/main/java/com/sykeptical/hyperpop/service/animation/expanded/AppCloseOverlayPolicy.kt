package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Xiaomi's app-close fake island hands off to the real island only when it
 * goes from VISIBLE to INVISIBLE. GONE skips that handoff, so the app-close
 * window animation keeps running and the fake card is shown again.
 */
object AppCloseOverlayPolicy {
    const val VISIBLE: Int = 0
    const val INVISIBLE: Int = 4

    /** The visibility to give the fake island, or null to leave it alone. */
    fun hideVisibility(current: Int): Int? = if (current == VISIBLE) INVISIBLE else null

    /**
     * The same fake island tracks the swipe that opens the app as a window.
     * Its outline reads one corner radius, set once from island_radius.
     * It takes the pill radius only while HyperPop owns the island with the
     * pill on; otherwise Xiaomi's radius.
     */
    fun trackingRadius(nativeRadius: Float, pillRadius: Float?): Float =
        pillRadius?.takeIf { it > 0f } ?: nativeRadius
}
