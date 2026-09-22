package com.lidesheng.hyperlyric.root.mediacard.island.layout.ios

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaIosLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaIosHeaderLayout.apply(environment)
        IslandExpandedMediaIosProgressLayout.apply(environment)
        IslandExpandedMediaIosActionLayout.apply(environment)
    }
}
