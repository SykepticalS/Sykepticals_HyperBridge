package com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaOneUiLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaOneUiHeaderLayout.apply(environment)
        IslandExpandedMediaOneUiProgressLayout.apply(environment)
        IslandExpandedMediaOneUiActionLayout.apply(environment)
    }
}
