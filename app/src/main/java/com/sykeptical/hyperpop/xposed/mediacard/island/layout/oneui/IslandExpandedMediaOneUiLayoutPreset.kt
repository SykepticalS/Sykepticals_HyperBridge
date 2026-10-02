package com.sykeptical.hyperpop.xposed.mediacard.island.layout.oneui

import com.sykeptical.hyperpop.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

internal object IslandExpandedMediaOneUiLayoutPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        IslandExpandedMediaOneUiHeaderLayout.apply(environment)
        IslandExpandedMediaOneUiProgressLayout.apply(environment)
        IslandExpandedMediaOneUiActionLayout.apply(environment)
    }
}
