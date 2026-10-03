package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Pixel rect in the island window, whose origin matches the screen while the
 * window is gravity-top at y = 0. Kept free of Android types so layout policy
 * can be tested on the JVM.
 */
data class IslandRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun isEmpty(): Boolean = width <= 0 || height <= 0

    fun translate(dx: Int, dy: Int): IslandRect = IslandRect(left + dx, top + dy, right + dx, bottom + dy)

    fun inflate(px: Int): IslandRect = IslandRect(left - px, top - px, right + px, bottom + px)

    fun intersects(other: IslandRect): Boolean {
        if (isEmpty() || other.isEmpty()) return false
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    fun contains(other: IslandRect): Boolean =
        !other.isEmpty() && other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    fun overlapWidth(other: IslandRect): Int {
        val start = maxOf(left, other.left)
        val end = minOf(right, other.right)
        return (end - start).coerceAtLeast(0)
    }
}

enum class TakeoverPhase {
    NATIVE,
    EXPANDING,
    EXPANDED,
    COLLAPSING,
}

data class ExpandedLayoutRequest(
    val enabled: Boolean,
    val portrait: Boolean,
    val keyguard: Boolean,
    val tablet: Boolean,
    val displayWidth: Int,
    val displayHeight: Int,
    val cutout: IslandRect,
    val compact: IslandRect,
    val nativeExpanded: IslandRect,
    val statusBarHeight: Int,
    val density: Float,
    val leadingEar: IslandRect? = null,
    val trailingEar: IslandRect? = null,
)

sealed class ExpandedLayoutDecision {
    data object Native : ExpandedLayoutDecision()

    data class Takeover(
        val card: IslandRect,
        val bodyOffsetPx: Int,
        val bodyTop: Int,
        val leadingEar: IslandRect?,
        val trailingEar: IslandRect?,
        val leadingSafe: IslandRect,
        val trailingSafe: IslandRect,
        val belowCutout: IslandRect,
        val touchRegions: List<IslandRect>,
    ) : ExpandedLayoutDecision() {
        fun ownsTouch(x: Int, y: Int): Boolean = touchRegions.any { it.contains(x, y) }

        fun acceptsShadePull(x: Int, y: Int, statusBarHeight: Int): Boolean =
            y in 0 until statusBarHeight && !ownsTouch(x, y)
    }
}
