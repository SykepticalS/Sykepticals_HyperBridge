package com.sykeptical.hyperpop.service.animation.expanded

import kotlin.math.abs

/**
 * Camera hole used to seat expanded content. The reported rect is Xiaomi's
 * `getCutoutRect`. A hole whose center misses the compact island, or whose
 * width disagrees with the framework cutout, is rebuilt from the compact
 * island center and the framework width. No device coordinates.
 */
object CameraBandGeometry {
    private const val WIDTH_TOLERANCE = 0.25f

    data class Bands(
        val exclusion: IslandRect,
        val cameraLineY: Int,
        val leading: IslandRect,
        val trailing: IslandRect,
        val belowTop: Int,
    )

    fun resolve(reported: IslandRect?, compact: IslandRect, displayCutoutWidth: Int): IslandRect? {
        if (reported != null && valid(reported, compact, displayCutoutWidth)) return reported
        val fallback = fallback(compact, displayCutoutWidth)
        if (fallback != null) return fallback
        return reported?.takeIf { !it.isEmpty() }
    }

    fun valid(cutout: IslandRect, compact: IslandRect, displayCutoutWidth: Int): Boolean {
        if (cutout.isEmpty() || compact.isEmpty()) return false
        if (!compact.contains(cutout.centerX, cutout.centerY)) return false
        if (displayCutoutWidth <= 0) return true
        val delta = abs(cutout.width - displayCutoutWidth).toFloat() / displayCutoutWidth
        return delta <= WIDTH_TOLERANCE
    }

    fun fallback(compact: IslandRect, displayCutoutWidth: Int): IslandRect? {
        if (compact.isEmpty() || displayCutoutWidth <= 0) return null
        val width = displayCutoutWidth.coerceAtMost((compact.height * 0.9f).toInt()).coerceAtLeast(1)
        val left = compact.centerX - width / 2
        val top = compact.centerY - width / 2
        return IslandRect(left, top, left + width, top + width)
    }

    fun exclusion(cutout: IslandRect, density: Float): IslandRect {
        val side = ExpandedVisualTokens.px(ExpandedVisualTokens.CAMERA_SIDE_GAP_DP, density)
        val vertical = ExpandedVisualTokens.px(ExpandedVisualTokens.CAMERA_VERTICAL_GAP_DP, density)
        return IslandRect(
            cutout.left - side,
            cutout.top - vertical,
            cutout.right + side,
            cutout.bottom + vertical,
        )
    }

    fun bands(
        cardLeft: Int,
        cardRight: Int,
        cardTop: Int,
        radiusPx: Float,
        cutout: IslandRect,
        density: Float,
    ): Bands {
        val exclusion = exclusion(cutout, density)
        val edge = ExpandedVisualTokens.px(ExpandedVisualTokens.PILL_EDGE_INSET_DP, density)
        val leadingInset = CutoutSafeLayout.cornerInsetY(
            cardLeft, cardRight, radiusPx, cardLeft, (cardLeft + cardRight) / 2,
        )
        val trailingInset = CutoutSafeLayout.cornerInsetY(
            cardLeft, cardRight, radiusPx, (cardLeft + cardRight) / 2, cardRight,
        )
        val leading = IslandRect(
            cardLeft + leadingInset.coerceAtLeast(edge),
            cardTop,
            exclusion.left,
            exclusion.bottom,
        ).let { if (it.isEmpty()) IslandRect(0, 0, 0, 0) else it }
        val trailing = IslandRect(
            exclusion.right,
            cardTop,
            cardRight - trailingInset.coerceAtLeast(edge),
            exclusion.bottom,
        ).let { if (it.isEmpty()) IslandRect(0, 0, 0, 0) else it }
        return Bands(
            exclusion = exclusion,
            cameraLineY = cutout.centerY,
            leading = leading,
            trailing = trailing,
            belowTop = exclusion.bottom,
        )
    }
}
