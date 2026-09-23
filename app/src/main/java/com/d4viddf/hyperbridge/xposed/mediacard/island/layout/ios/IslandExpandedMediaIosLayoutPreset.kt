package com.d4viddf.hyperbridge.xposed.mediacard.island.layout.ios

import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaIosLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaIosHeaderLayout.apply(environment)
        IslandExpandedMediaIosProgressLayout.apply(environment)
        IslandExpandedMediaIosActionLayout.apply(environment)
    }
}
