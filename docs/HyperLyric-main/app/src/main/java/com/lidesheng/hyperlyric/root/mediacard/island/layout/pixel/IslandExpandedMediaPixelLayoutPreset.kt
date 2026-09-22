package com.lidesheng.hyperlyric.root.mediacard.island.layout.pixel

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaPixelLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaPixelHeaderLayout.apply(environment)
        IslandExpandedMediaPixelActionLayout.apply(environment)
        IslandExpandedMediaPixelProgressLayout.apply(environment)
    }
}
