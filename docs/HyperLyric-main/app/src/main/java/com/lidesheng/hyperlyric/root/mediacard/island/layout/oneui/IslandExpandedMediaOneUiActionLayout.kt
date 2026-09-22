package com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearVertical
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import kotlin.math.roundToInt

internal object IslandExpandedMediaOneUiActionLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val actionWidth = (
                context.islandExpandedMediaDimenPx(
                    "media_action_width",
                    MediaLayoutSharedMetrics.NATIVE_ACTION_WIDTH_FALLBACK_DP
                ) *
                    IslandExpandedMediaOneUiMetrics.ACTION_BUTTON_SCALE
                ).roundToInt()
            val actionHeight = (
                context.islandExpandedMediaDimenPx(
                    "media_action_height",
                    MediaLayoutSharedMetrics.NATIVE_ACTION_HEIGHT_FALLBACK_DP
                ) *
                    IslandExpandedMediaOneUiMetrics.ACTION_BUTTON_SCALE
                ).roundToInt()
            val actionGap = context.islandExpandedMediaDp(
                IslandExpandedMediaOneUiMetrics.ACTION_GAP_DP
            )
            val rowWidth = actionWidth * ids.actionButtons.size +
                actionGap * (ids.actionButtons.size - 1)

            bridge.constrainWidth(layout, ids.actions, rowWidth)
            ids.actionButtons.forEach { action ->
                bridge.constrainWidth(layout, action, actionWidth)
                bridge.constrainHeight(layout, action, actionHeight)
                bridge.setMargin(layout, action, IslandExpandedMediaConstraintSide.LEFT, 0)
                bridge.setMargin(layout, action, IslandExpandedMediaConstraintSide.RIGHT, 0)
                bridge.setMargin(layout, action, IslandExpandedMediaConstraintSide.START, 0)
                bridge.setMargin(layout, action, IslandExpandedMediaConstraintSide.END, 0)
            }

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
                context.islandExpandedMediaDp(IslandExpandedMediaOneUiMetrics.ACTION_BOTTOM_DP)
            )
        }
    }
}
