package com.lidesheng.hyperlyric.root.mediacard.island.layout.pixel

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp

internal object IslandExpandedMediaPixelProgressLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val progress = ids.mediaProgressBar
            val action1 = ids.actionButtons[1]
            val action3 = ids.actionButtons[3]
            val progressHeight = context.islandExpandedMediaDp(
                IslandExpandedMediaPixelMetrics.PROGRESS_HEIGHT_DP
            )
            val componentGap = context.islandExpandedMediaDp(
                IslandExpandedMediaPixelMetrics.COMPONENT_GAP_DP
            )
            bridge.constrainWidth(layout, progress, 0)
            bridge.constrainHeight(layout, progress, progressHeight)
            bridge.clearAll(layout, progress)
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.START,
                action1,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.END,
                action3,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.TOP,
                action1,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.connect(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.BOTTOM,
                action1,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.START,
                componentGap
            )
            bridge.setMargin(
                layout,
                progress,
                IslandExpandedMediaConstraintSide.END,
                componentGap
            )
        }
    }
}
