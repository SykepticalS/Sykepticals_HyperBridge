package com.sykeptical.hyperpop.xposed.mediacard.island.layout.system

import com.sykeptical.hyperpop.service.animation.expanded.MediaSystemPolicy
import com.sykeptical.hyperpop.xposed.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.sykeptical.hyperpop.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment

/**
 * Camera-band margins for the native System media layout. Action order and
 * the spread chain are left to the existing element overrides.
 */
internal object IslandExpandedMediaCameraBandPreset {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        val margins = MediaSystemPolicy.margins(environment.context.resources.displayMetrics.density)
        val bridge = environment.bridge
        val layout = environment.layout
        val ids = environment.ids
        bridge.setMargin(layout, ids.albumArt, IslandExpandedMediaConstraintSide.START, margins.artStartPx)
        bridge.setMargin(layout, ids.albumArt, IslandExpandedMediaConstraintSide.TOP, margins.artTopPx)
        bridge.setMargin(layout, ids.headerTitle, IslandExpandedMediaConstraintSide.TOP, margins.titleTopPx)
        bridge.setMargin(layout, ids.mediaSeamless, IslandExpandedMediaConstraintSide.TOP, margins.seamlessTopPx)
        bridge.setMargin(layout, ids.mediaSeamless, IslandExpandedMediaConstraintSide.END, margins.seamlessEndPx)
        bridge.setMargin(layout, ids.action0, IslandExpandedMediaConstraintSide.TOP, margins.actionTopPx)
        bridge.setMargin(layout, ids.mediaProgressBar, IslandExpandedMediaConstraintSide.BOTTOM, margins.progressBottomPx)
    }
}
