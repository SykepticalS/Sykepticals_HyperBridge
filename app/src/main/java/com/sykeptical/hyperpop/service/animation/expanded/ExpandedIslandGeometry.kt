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
     * The part of this leaf that belongs to the active layout.
     *
     * A leaf mostly past the side is parked and returns null. Text parked
     * entirely below the root is the same. A call control or other action
     * whose top is inside a short root, or that sits just below it, keeps its
     * true bottom so the island can grow to fit it.
     */
    fun visibleWithin(contentWidth: Int, contentHeight: Int): ContentLeaf? {
        if (bounds.isEmpty() || contentWidth <= 0 || contentHeight <= 0) return null
        val left = bounds.left.coerceAtLeast(0)
        val right = bounds.right.coerceAtMost(contentWidth)
        if (right <= left) return null
        if ((right - left).toLong() * 2 < bounds.width) return null
        val actionPastRoot = bounds.top >= contentHeight && holdsBottomAction()
        if (bounds.top >= contentHeight && !actionPastRoot) return null
        val top = bounds.top.coerceAtLeast(0)
        if (bounds.top < 0) {
            val shownHeight = (bounds.bottom.coerceAtMost(contentHeight) - top).coerceAtLeast(0)
            if (shownHeight.toLong() * 2 < bounds.height) return null
        }
        val bottom = if (bounds.bottom > contentHeight && (bounds.top < contentHeight || actionPastRoot)) {
            bounds.bottom
        } else {
            bounds.bottom.coerceAtMost(contentHeight)
        }
        if (bottom <= top) return null
        val clipped = IslandRect(left, top, right, bottom)
        return if (clipped == bounds) this else copy(bounds = clipped)
    }

    /** Call, pill, and other real controls. A short root must not throw these away. */
    fun holdsBottomAction(): Boolean = when (role) {
        ContentLeafRole.CALL_CONTROL, ContentLeafRole.ACTION_PILL -> true
        else -> kind == ContentLeafKind.INTERACTIVE
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
