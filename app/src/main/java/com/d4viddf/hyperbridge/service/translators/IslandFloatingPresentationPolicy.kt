package com.d4viddf.hyperbridge.service.translators

import io.github.d4viddf.hyperisland_kit.HyperIslandNotification

data class IslandFloatingPresentation(
    val enableFloat: Boolean,
    val islandFirstFloat: Boolean,
    val reopen: Boolean,
) {
    fun expandedTimeMs(configured: Int?): Int = if (enableFloat) configured ?: 0 else 0
}

/**
 * HyperOS evaluates enableFloat / islandFirstFloat / reopen whenever a Focus notification is
 * posted, including in-place updates of an already visible island. Keeping any of those flags
 * enabled on an update can therefore re-expand an island the user already collapsed.
 */
object IslandFloatingPresentationPolicy {
    fun resolve(firstFloat: Boolean, floatOnUpdate: Boolean, isUpdate: Boolean): IslandFloatingPresentation {
        val mayAutoExpand = if (isUpdate) floatOnUpdate else firstFloat
        return IslandFloatingPresentation(
            enableFloat = mayAutoExpand,
            islandFirstFloat = mayAutoExpand,
            reopen = mayAutoExpand,
        )
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
