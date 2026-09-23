package com.d4viddf.hyperbridge.xposed.mediacard.island.layout.coloros

import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaColorOsLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaColorOsHeaderLayout.apply(environment)
        IslandExpandedMediaColorOsProgressLayout.apply(environment)
        IslandExpandedMediaColorOsActionLayout.apply(environment)
    }
}
