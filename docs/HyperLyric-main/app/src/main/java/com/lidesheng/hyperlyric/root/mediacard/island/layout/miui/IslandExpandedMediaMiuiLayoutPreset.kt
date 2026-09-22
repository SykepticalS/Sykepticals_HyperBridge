package com.lidesheng.hyperlyric.root.mediacard.island.layout.miui

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaMiuiLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaMiuiHeaderLayout.apply(environment)
        IslandExpandedMediaMiuiActionLayout.apply(environment)
        IslandExpandedMediaMiuiProgressLayout.apply(environment)
    }
}
