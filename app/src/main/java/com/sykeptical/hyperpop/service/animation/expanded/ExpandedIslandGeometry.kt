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

data class ExpandedVisualStyle(
    val blackBackground: Boolean = false,
    val roundedPill: Boolean = false,
)

enum class ContentLeafKind {
    TEXT,
    INTERACTIVE,
    DECORATIVE,
    PLAIN,
}

enum class ContentLeafRole {
    UNKNOWN,
    PRIMARY_TITLE,
    SECONDARY_TEXT,
    AVATAR,
    CALL_CONTROL,
    ACTION_PILL,
    MEDIA_ART,
    PROGRESS,
}

/** Leaf bounds are relative to the expanded content view's top-left. */
data class ContentLeaf(
    val bounds: IslandRect,
    val kind: ContentLeafKind,
    val role: ContentLeafRole = ContentLeafRole.UNKNOWN,
) {
    /**
     * The part the content view can draw. A leaf mostly outside the content,
     * such as a call button parked off the edge during a state change, is
     * not on screen and returns null.
     */
    fun visibleWithin(contentWidth: Int, contentHeight: Int): ContentLeaf? {
        val clipped = IslandRect(
            left = bounds.left.coerceAtLeast(0),
            top = bounds.top.coerceAtLeast(0),
            right = bounds.right.coerceAtMost(contentWidth),
            bottom = bounds.bottom.coerceAtMost(contentHeight),
        )
        if (clipped.isEmpty() || bounds.isEmpty()) return null
        val shown = clipped.width.toLong() * clipped.height
        val whole = bounds.width.toLong() * bounds.height
        if (shown * 2 < whole) return null
        return if (clipped == bounds) this else copy(bounds = clipped)
    }
}

/** Direct child of the expanded content view, in child index order. */
data class ContentCluster(
    val index: Int,
    val bounds: IslandRect,
    val decorative: Boolean,
)

data class ExpandedContentProfile(
    val nativeTopMarginPx: Int,
    val contentWidthPx: Int,
    val contentHeightPx: Int,
    val leaves: List<ContentLeaf>,
    val clusters: List<ContentCluster> = emptyList(),
)

data class SideLift(
    val clusterIndex: Int,
    val translationY: Int,
)

/** Safe horizontal span for one expanded text line, in window coordinates. */
data class TextClip(
    val safeLeft: Int,
    val safeRight: Int,
    val fadePx: Int,
    val cutoutLimited: Boolean,
)

fun ContentLeaf.sharesCameraBand(): Boolean = when (role) {
    ContentLeafRole.PRIMARY_TITLE,
    ContentLeafRole.SECONDARY_TEXT,
    -> true
    else -> false
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
    val style: ExpandedVisualStyle = ExpandedVisualStyle(),
    val nativeRadiusPx: Float = 0f,
    val content: ExpandedContentProfile? = null,
    val rtl: Boolean = false,
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
        /** Absolute top margin of the content view. Equals [bodyOffsetPx] when no profile was measured. */
        val contentOffsetPx: Int = bodyOffsetPx,
        val nativeRadiusPx: Float = 0f,
        val radiusPx: Float = nativeRadiusPx,
        val contentScale: Float = 1f,
        val flowMask: FlowMask? = null,
        val sideLifts: List<SideLift> = emptyList(),
        val pillEnabled: Boolean = false,
        val blackBackground: Boolean = false,
        /** True when leaf geometry tightened the cutout gap. False keeps today's margin math. */
        val tightLayout: Boolean = false,
        val textClips: List<TextClip> = emptyList(),
        /** Pixels the media card bottom was raised. Other families stay at 0. */
        val mediaBottomTrimPx: Int = 0,
    ) : ExpandedLayoutDecision() {
        fun ownsTouch(x: Int, y: Int): Boolean = touchRegions.any { it.contains(x, y) }

        fun acceptsShadePull(x: Int, y: Int, statusBarHeight: Int): Boolean =
            y in 0 until statusBarHeight && !ownsTouch(x, y)
    }
}
