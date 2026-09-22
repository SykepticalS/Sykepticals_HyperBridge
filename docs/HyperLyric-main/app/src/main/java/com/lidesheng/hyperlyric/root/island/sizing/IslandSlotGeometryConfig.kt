package com.lidesheng.hyperlyric.root.island.sizing

import android.view.View

/**
 * Immutable geometry snapshot for the two injected Super Island slots.
 *
 * It contains only values derived from preferences and the visible album/rhythm components. The
 * runtime snapshot remains responsible for reading preferences; View-dependent conversions stay
 * here so content and lifecycle code do not need to know the raw left/right fields.
 */
internal data class IslandSlotGeometryConfig(
    val isDynamicWidth: Boolean,
    val showAlbum: Boolean,
    val showRhythm: Boolean,
    val leftPaddingLeftDp: Int,
    val leftPaddingRightDp: Int,
    val rightPaddingLeftDp: Int,
    val rightPaddingRightDp: Int,
    val leftMinWidthDp: Int,
    val rightMinWidthDp: Int,
    val leftMaxWidthDp: Int,
    val rightMaxWidthDp: Int
) {
    fun isLeftParent(parentName: String): Boolean {
        return parentName.contains("1")
    }

    fun maxWidthDp(parentName: String): Int {
        return if (isLeftParent(parentName)) leftMaxWidthDp else rightMaxWidthDp
    }

    fun minWidthDp(parentName: String): Int {
        return if (isLeftParent(parentName)) leftMinWidthDp else rightMinWidthDp
    }

    fun paddingLeftDp(parentName: String): Int {
        return if (isLeftParent(parentName)) leftPaddingLeftDp else rightPaddingLeftDp
    }

    fun paddingRightDp(parentName: String): Int {
        return if (isLeftParent(parentName)) leftPaddingRightDp else rightPaddingRightDp
    }

    fun widthPx(rootView: View, parentName: String): Int? {
        val maxWidthDp = maxWidthDp(parentName)
        if (maxWidthDp <= 0) return null
        return (maxWidthDp * rootView.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    fun paddingLeftPx(rootView: View, parentName: String): Int {
        return (paddingLeftDp(parentName) * rootView.resources.displayMetrics.density).toInt()
            .coerceAtLeast(0)
    }

    fun paddingRightPx(rootView: View, parentName: String): Int {
        return (paddingRightDp(parentName) * rootView.resources.displayMetrics.density).toInt()
            .coerceAtLeast(0)
    }
}
