package com.d4viddf.hyperbridge.xposed.mediacard.island.layout.oneui

import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaOneUiLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaOneUiHeaderLayout.apply(environment)
        IslandExpandedMediaOneUiProgressLayout.apply(environment)
        IslandExpandedMediaOneUiActionLayout.apply(environment)
    }
}
