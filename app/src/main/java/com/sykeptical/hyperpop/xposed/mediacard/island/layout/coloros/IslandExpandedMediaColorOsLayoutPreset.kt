package com.sykeptical.hyperpop.xposed.mediacard.island.layout.coloros

import com.sykeptical.hyperpop.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaColorOsLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaColorOsHeaderLayout.apply(environment)
        IslandExpandedMediaColorOsProgressLayout.apply(environment)
        IslandExpandedMediaColorOsActionLayout.apply(environment)
    }
}
