package com.d4viddf.hyperbridge.service.translators

import android.os.Bundle
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification

data class IslandFloatingPresentation(
    val enableFloat: Boolean,
    val islandFirstFloat: Boolean,
    val reopen: Boolean,
    /**
     * Show the island in the cutout first. HyperBridge expands it after the native appear
     * animation, so HyperOS must not open the expanded card from these flags.
     */
    val stageAutoExpand: Boolean = false,
) {
    fun expandedTimeMs(configured: Int?): Int = if (enableFloat) configured ?: 0 else 0
}

/**
 * HyperOS evaluates enableFloat / islandFirstFloat / reopen whenever a Focus notification is
 * posted, including in-place updates of an already visible island. Keeping any of those flags
 * enabled on an update can therefore re-expand an island the user already collapsed.
 *
 * Auto-expand posts still set enableFloat so the island appears, but leave islandFirstFloat and
 * reopen off. The SystemUI hook expands them after the cutout appear animation.
 */
object IslandFloatingPresentationPolicy {
    fun resolve(firstFloat: Boolean, floatOnUpdate: Boolean, isUpdate: Boolean): IslandFloatingPresentation {
        val mayAutoExpand = if (isUpdate) floatOnUpdate else firstFloat
        return IslandFloatingPresentation(
            enableFloat = mayAutoExpand,
            islandFirstFloat = false,
            reopen = false,
            stageAutoExpand = mayAutoExpand,
        )
    }
}

fun Bundle.applyStagedAutoExpand(presentation: IslandFloatingPresentation) {
    if (presentation.stageAutoExpand) {
        putBoolean(IslandProtocol.EXTRA_AUTO_EXPAND_ENTRANCE, true)
    } else {
        remove(IslandProtocol.EXTRA_AUTO_EXPAND_ENTRANCE)
    }
}

internal fun HyperIslandNotification.applyFloatingPresentation(
    firstFloat: Boolean,
    floatOnUpdate: Boolean,
    isUpdate: Boolean
) = apply {
    val presentation = IslandFloatingPresentationPolicy.resolve(firstFloat, floatOnUpdate, isUpdate)
    setEnableFloat(presentation.enableFloat)
    setIslandFirstFloat(presentation.islandFirstFloat)
    setReopen(presentation.reopen)
}
