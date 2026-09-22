package com.lidesheng.hyperlyric.root.mediacard.island.layout.pixel

import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp
import kotlin.math.roundToInt

internal object IslandExpandedMediaPixelActionLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            val nativeActionWidth = context.islandExpandedMediaDimenPx(
                "media_action_width",
                MediaLayoutSharedMetrics.NATIVE_ACTION_WIDTH_FALLBACK_DP
            )
            val nativeActionHeight = context.islandExpandedMediaDimenPx(
                "media_action_height",
                MediaLayoutSharedMetrics.NATIVE_ACTION_HEIGHT_FALLBACK_DP
            )
            val primaryWidth = (
                nativeActionWidth * IslandExpandedMediaPixelMetrics.PRIMARY_ACTION_SCALE
                ).roundToInt()
            val primaryHeight = (
                nativeActionHeight * IslandExpandedMediaPixelMetrics.PRIMARY_ACTION_SCALE
                ).roundToInt()
            val bottomWidth = (
                nativeActionWidth * IslandExpandedMediaPixelMetrics.BOTTOM_ACTION_SCALE
                ).roundToInt()
            val bottomHeight = context.islandExpandedMediaDp(
                IslandExpandedMediaPixelMetrics.PROGRESS_HEIGHT_DP
            )
            val componentGap = context.islandExpandedMediaDp(
                IslandExpandedMediaPixelMetrics.COMPONENT_GAP_DP
            )

            val primary = ids.actionButtons[2]
            bridge.constrainWidth(layout, primary, primaryWidth)
            bridge.constrainHeight(layout, primary, primaryHeight)
            bridge.clearAll(layout, primary)
            bridge.connect(
                layout,
                primary,
                IslandExpandedMediaConstraintSide.END,
                0,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                primary,
                IslandExpandedMediaConstraintSide.TOP,
                ids.headerTitle,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.connect(
                layout,
                primary,
                IslandExpandedMediaConstraintSide.BOTTOM,
                ids.headerArtist,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                primary,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(
                    IslandExpandedMediaPixelMetrics.MIDDLE_HORIZONTAL_MARGIN_DP
                )
            )

            val bottomActions = listOf(
                ids.actionButtons[1],
                ids.actionButtons[3],
                ids.actionButtons[0],
                ids.actionButtons[4]
            )
            bottomActions.forEach { action ->
                bridge.constrainWidth(layout, action, bottomWidth)
                bridge.constrainHeight(layout, action, bottomHeight)
                bridge.clearAll(layout, action)
                resetMargins(environment, action)
            }

            val action1 = ids.actionButtons[1]
            val action3 = ids.actionButtons[3]
            val action0 = ids.actionButtons[0]
            val action4 = ids.actionButtons[4]
            bridge.connect(
                layout,
                action1,
                IslandExpandedMediaConstraintSide.START,
                0,
                IslandExpandedMediaConstraintSide.START
            )
            bridge.connect(
                layout,
                action1,
                IslandExpandedMediaConstraintSide.BOTTOM,
                0,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                action1,
                IslandExpandedMediaConstraintSide.START,
                context.islandExpandedMediaDp(
                    IslandExpandedMediaPixelMetrics.BOTTOM_HORIZONTAL_MARGIN_DP
                )
            )
            bridge.setMargin(
                layout,
                action1,
                IslandExpandedMediaConstraintSide.BOTTOM,
                context.islandExpandedMediaDp(IslandExpandedMediaPixelMetrics.BOTTOM_MARGIN_DP)
            )

            // The progress container itself sits between Action1 and Action3.
            // Keep all four controls on the same bottom baseline.
            connectRowItem(environment, action3, ids.mediaProgressBar, action0, action1)
            connectRowItem(environment, action0, action3, action4, action1, componentGap)
            connectRowItem(environment, action4, action0, 0, action1, componentGap)
            bridge.setMargin(
                layout,
                action4,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(
                    IslandExpandedMediaPixelMetrics.BOTTOM_HORIZONTAL_MARGIN_DP
                )
            )
        }
    }

    private fun connectRowItem(
        environment: IslandExpandedMediaLayoutEnvironment,
        viewId: Int,
        previousId: Int,
        nextId: Int,
        verticalAnchorId: Int,
        startMarginPx: Int = 0
    ) {
        with(environment) {
            bridge.connect(
                layout,
                viewId,
                IslandExpandedMediaConstraintSide.START,
                previousId,
                IslandExpandedMediaConstraintSide.END
            )
            bridge.connect(
                layout,
                viewId,
                IslandExpandedMediaConstraintSide.END,
                nextId,
                if (nextId == 0) {
                    IslandExpandedMediaConstraintSide.END
                } else {
                    IslandExpandedMediaConstraintSide.START
                }
            )
            bridge.connect(
                layout,
                viewId,
                IslandExpandedMediaConstraintSide.TOP,
                verticalAnchorId,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.connect(
                layout,
                viewId,
                IslandExpandedMediaConstraintSide.BOTTOM,
                verticalAnchorId,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                layout,
                viewId,
                IslandExpandedMediaConstraintSide.START,
                startMarginPx
            )
        }
    }

    private fun resetMargins(
        environment: IslandExpandedMediaLayoutEnvironment,
        viewId: Int
    ) {
        with(environment) {
            listOf(
                IslandExpandedMediaConstraintSide.LEFT,
                IslandExpandedMediaConstraintSide.RIGHT,
                IslandExpandedMediaConstraintSide.START,
                IslandExpandedMediaConstraintSide.END,
                IslandExpandedMediaConstraintSide.TOP,
                IslandExpandedMediaConstraintSide.BOTTOM
            ).forEach { side -> bridge.setMargin(layout, viewId, side, 0) }
        }
    }
}
