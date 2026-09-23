package com.d4viddf.hyperbridge.xposed.mediacard.island.layout.coloros

import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.clearVertical
import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.islandExpandedMediaDp

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
