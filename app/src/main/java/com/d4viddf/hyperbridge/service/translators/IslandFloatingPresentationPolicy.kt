package com.d4viddf.hyperbridge.service.translators

import io.github.d4viddf.hyperisland_kit.HyperIslandNotification

data class IslandFloatingPresentation(
    val enableFloat: Boolean,
    val islandFirstFloat: Boolean
)

/**
 * HyperOS evaluates enableFloat again whenever an updatable Focus notification is posted.
 * Keeping it enabled on an in-place update can therefore re-expand an island that the user
 * already collapsed. A logical event may float once; later payload refreshes may not.
 */
object IslandFloatingPresentationPolicy {
    fun resolve(firstFloat: Boolean, floatOnUpdate: Boolean, isUpdate: Boolean): IslandFloatingPresentation {
        val mayAutoExpand = if (isUpdate) floatOnUpdate else firstFloat
        return IslandFloatingPresentation(
            enableFloat = mayAutoExpand,
            islandFirstFloat = mayAutoExpand
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
}
