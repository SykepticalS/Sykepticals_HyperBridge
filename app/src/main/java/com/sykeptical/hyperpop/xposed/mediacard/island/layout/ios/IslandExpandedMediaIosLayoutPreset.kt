package com.sykeptical.hyperpop.xposed.mediacard.island.layout.ios

import com.sykeptical.hyperpop.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaIosLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaIosHeaderLayout.apply(environment)
        IslandExpandedMediaIosProgressLayout.apply(environment)
        IslandExpandedMediaIosActionLayout.apply(environment)
    }
}
