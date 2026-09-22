package com.lidesheng.hyperlyric.root.island.sizing

import com.lidesheng.hyperlyric.common.SuperIslandWidthPolicy
import kotlin.math.ceil

/**
 * Dynamic lyric width calculation that is independent from Android Views and preferences.
 *
 * Padding is supplied in pixels because the caller already owns the resource-density
 * conversion used by the injected wrapper. The returned base width is expressed in the
 * right-side content coordinate system so left-side album/rhythm compensation can be applied
 * consistently for both slots.
 */
internal data class IslandLyricWidthSpec(
    val density: Float,
    val paddingLeftPx: Int,
    val paddingRightPx: Int,
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val isLeft: Boolean,
    val showAlbum: Boolean,
    val showRhythm: Boolean
)

internal object IslandLyricWidthCalculator {

    fun baseWidthDp(contentWidthPx: Float, spec: IslandLyricWidthSpec): Float? {
        if (spec.maxWidthDp <= 0 || spec.density <= 0f) return null

        val paddingPx = spec.paddingLeftPx.coerceAtLeast(0) +
                spec.paddingRightPx.coerceAtLeast(0)
        val requiredWidthPx = (
                ceil(contentWidthPx.coerceAtLeast(0f)).toInt() + paddingPx
                ).coerceAtLeast(1)
        val requiredWidthDp = requiredWidthPx / spec.density
        return if (spec.isLeft) {
            SuperIslandWidthPolicy.baseWidthFromLeftContentWidth(
                leftContentWidthDp = requiredWidthDp,
                showAlbum = spec.showAlbum,
                showRhythm = spec.showRhythm
            )
        } else {
            requiredWidthDp
        }
    }

    fun targetWidthDp(baseWidthDp: Float, spec: IslandLyricWidthSpec): Float? {
        if (spec.maxWidthDp <= 0) return null

        val adjustmentDp = if (spec.isLeft) {
            SuperIslandWidthPolicy.leftContentWidthOffsetDp(
                showAlbum = spec.showAlbum,
                showRhythm = spec.showRhythm
            )
        } else {
            0
        }
        return (baseWidthDp + adjustmentDp).coerceIn(
            spec.minWidthDp.toFloat(),
            spec.maxWidthDp.toFloat()
        )
    }

    fun targetWidthPx(baseWidthDp: Float, spec: IslandLyricWidthSpec): Int? {
        if (spec.density <= 0f) return null
        val targetWidthDp = targetWidthDp(baseWidthDp, spec) ?: return null
        return ceil(targetWidthDp * spec.density).toInt().coerceAtLeast(1)
    }
}
