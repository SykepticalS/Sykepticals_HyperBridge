package com.sykeptical.hyperpop.xposed.mediacard.island.layout.coloros

import com.sykeptical.hyperpop.xposed.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.sykeptical.hyperpop.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.sykeptical.hyperpop.xposed.mediacard.island.layout.clearVertical
import com.sykeptical.hyperpop.xposed.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaColorOsActionLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            bridge.clearVertical(layout, ids.action0)
            bridge.connect(
                layout,
                ids.action0,
                IslandExpandedMediaConstraintSide.BOTTOM,
                0,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                ids.action0,
                IslandExpandedMediaConstraintSide.BOTTOM,
                context.islandExpandedMediaDp(IslandExpandedMediaColorOsMetrics.ACTION_BOTTOM_DP)
            )
        }
    }
}
