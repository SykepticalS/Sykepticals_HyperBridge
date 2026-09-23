package com.d4viddf.hyperbridge.xposed.mediacard.island.layout.miui

import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaMiuiLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaMiuiHeaderLayout.apply(environment)
        IslandExpandedMediaMiuiActionLayout.apply(environment)
        IslandExpandedMediaMiuiProgressLayout.apply(environment)
    }
}
