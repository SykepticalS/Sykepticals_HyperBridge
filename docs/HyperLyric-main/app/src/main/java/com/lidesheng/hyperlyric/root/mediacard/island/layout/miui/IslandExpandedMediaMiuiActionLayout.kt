package com.lidesheng.hyperlyric.root.mediacard.island.layout.miui

import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearVertical
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import kotlin.math.roundToInt

internal object IslandExpandedMediaMiuiActionLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val actionWidth = (
                context.islandExpandedMediaDimenPx(
                    "media_action_width",
                    MediaLayoutSharedMetrics.NATIVE_ACTION_WIDTH_FALLBACK_DP
                ) *
                    IslandExpandedMediaMiuiMetrics.ACTION_BUTTON_SCALE
                ).roundToInt()
            val actionHeight = (
                context.islandExpandedMediaDimenPx(
                    "media_action_height",
                    MediaLayoutSharedMetrics.NATIVE_ACTION_HEIGHT_FALLBACK_DP
                ) *
                    IslandExpandedMediaMiuiMetrics.ACTION_BUTTON_SCALE
                ).roundToInt()
            val actionGap = context.islandExpandedMediaDp(
                IslandExpandedMediaMiuiMetrics.ACTION_GAP_DP
            )

            bridge.constrainWidth(
                layout,
                ids.actions,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            ids.actionButtons.forEachIndexed { index, action ->
                bridge.constrainWidth(layout, action, actionWidth)
                bridge.constrainHeight(layout, action, actionHeight)
                clearHorizontal(environment, action)
                val previous = ids.actionButtons.getOrNull(index - 1) ?: ids.actions
                val next = ids.actionButtons.getOrNull(index + 1)
                bridge.connect(
                    layout,
                    action,
                    IslandExpandedMediaConstraintSide.LEFT,
                    previous,
                    if (index == 0) {
                        IslandExpandedMediaConstraintSide.LEFT
                    } else {
                        IslandExpandedMediaConstraintSide.RIGHT
                    }
                )
                next?.let {
                    bridge.connect(
                        layout,
                        action,
                        IslandExpandedMediaConstraintSide.RIGHT,
                        it,
                        IslandExpandedMediaConstraintSide.LEFT
                    )
                }
                bridge.setMargin(
                    layout,
                    action,
                    IslandExpandedMediaConstraintSide.LEFT,
                    if (index == 0) {
                        context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.ACTION_START_DP)
                    } else {
                        0
                    }
                )
                bridge.setMargin(
                    layout,
                    action,
                    IslandExpandedMediaConstraintSide.RIGHT,
                    if (index < ids.actionButtons.lastIndex) actionGap else 0
                )
                bridge.setMargin(layout, action, IslandExpandedMediaConstraintSide.START, 0)
                bridge.setMargin(layout, action, IslandExpandedMediaConstraintSide.END, 0)
            }
            bridge.clearVertical(layout, ids.action0)
            bridge.connect(
                layout,
                ids.action0,
                IslandExpandedMediaConstraintSide.TOP,
                ids.headerArtist,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                ids.action0,
                IslandExpandedMediaConstraintSide.TOP,
                context.islandExpandedMediaDp(IslandExpandedMediaMiuiMetrics.ACTION_TOP_DP)
            )
        }
    }

    private fun clearHorizontal(
        environment: IslandExpandedMediaLayoutEnvironment,
        viewId: Int
    ) {
        with(environment) {
            bridge.clear(layout, viewId, IslandExpandedMediaConstraintSide.LEFT)
            bridge.clear(layout, viewId, IslandExpandedMediaConstraintSide.RIGHT)
            bridge.clear(layout, viewId, IslandExpandedMediaConstraintSide.START)
            bridge.clear(layout, viewId, IslandExpandedMediaConstraintSide.END)
        }
    }
}
