package com.lidesheng.hyperlyric.root.mediacard.island.layout.coloros

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaColorOsLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaColorOsHeaderLayout.apply(environment)
        IslandExpandedMediaColorOsProgressLayout.apply(environment)
        IslandExpandedMediaColorOsActionLayout.apply(environment)
    }
}
