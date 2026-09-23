package com.d4viddf.hyperbridge.xposed.mediacard.island.layout.pixel

import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaPixelLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaPixelHeaderLayout.apply(environment)
        IslandExpandedMediaPixelActionLayout.apply(environment)
        IslandExpandedMediaPixelProgressLayout.apply(environment)
    }
}
