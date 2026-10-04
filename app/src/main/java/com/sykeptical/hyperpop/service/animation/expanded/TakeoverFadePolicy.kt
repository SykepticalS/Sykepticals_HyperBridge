package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Status-bar alpha from the live card.
 *
 * Compact already overlaps the status bar vertically, so the fade follows how
 * much of the measured icon group the card has covered beyond the compact
 * pill. The compact frame is exactly 1 and the target frame is exactly 0.
 * Spring overshoot past the target clamps to 0.
 */
object TakeoverFadePolicy {
    fun alpha(
        compact: IslandRect,
        live: IslandRect,
        target: IslandRect,
        statusGroup: IslandRect?,
    ): Float {
        val group = statusGroup?.takeUnless { it.isEmpty() }
        val span = if (group != null) {
            val start = group.overlapWidth(compact)
            val end = group.overlapWidth(target)
            val current = group.overlapWidth(live)
            if (end > start) Triple(start, current, end) else null
        } else {
            null
        }
        val (start, current, end) = span ?: run {
            val startWidth = compact.width
            val endWidth = target.width
            if (endWidth <= startWidth) return if (live.width > endWidth) 0f else 1f
            Triple(startWidth, live.width, endWidth)
        }
        val fraction = (current - start).toFloat() / (end - start).toFloat()
        return (1f - fraction).coerceIn(0f, 1f)
    }
}
