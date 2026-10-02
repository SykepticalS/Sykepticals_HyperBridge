package com.sykeptical.hyperpop.xposed.mediacard.island.layout.pixel

import com.sykeptical.hyperpop.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaPixelLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaPixelHeaderLayout.apply(environment)
        IslandExpandedMediaPixelActionLayout.apply(environment)
        IslandExpandedMediaPixelProgressLayout.apply(environment)
    }
}
