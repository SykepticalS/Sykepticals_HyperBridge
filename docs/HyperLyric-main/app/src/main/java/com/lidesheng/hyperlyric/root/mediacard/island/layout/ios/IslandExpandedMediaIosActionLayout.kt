package com.lidesheng.hyperlyric.root.mediacard.island.layout.ios

import android.view.View
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.island.layout.IslandExpandedMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearHorizontal
import com.lidesheng.hyperlyric.root.mediacard.island.layout.clearVertical
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDimenPx
import com.lidesheng.hyperlyric.root.mediacard.island.layout.islandExpandedMediaDp
import kotlin.math.min

internal object IslandExpandedMediaIosActionLayout {
    fun apply(environment: IslandExpandedMediaLayoutEnvironment) {
        with(environment) {
            ids.actionButtons.forEach { action ->
                bridge.setScale(
                    layout,
                    action,
                    IslandExpandedMediaIosMetrics.ACTION_SCALE
                )
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
                resolveBottomMargin(environment)
            )
            bridge.setMargin(
                layout,
                ids.action0,
                IslandExpandedMediaConstraintSide.START,
                context.islandExpandedMediaDp(
                    IslandExpandedMediaIosMetrics.ACTION_OUTER_MARGIN_DP
                )
            )
            bridge.setMargin(
                layout,
                ids.action4,
                IslandExpandedMediaConstraintSide.END,
                context.islandExpandedMediaDp(
                    IslandExpandedMediaIosMetrics.ACTION_OUTER_MARGIN_DP
                )
            )

            if (!hideDeviceSwitch && bridge.supportsDeviceSlotReplacement) {
                replaceAction4WithDeviceSwitch(environment)
            }
        }
    }

    private fun resolveBottomMargin(
        environment: IslandExpandedMediaLayoutEnvironment
    ): Int {
        with(environment) {
            // Super Island has its own 168dp expanded player.  Do not use the
            // notification-center qs_media_session_height_expanded resource.
            val expandedHeight = IslandExpandedMediaIosMetrics.rootHeightPx(context)
            val coverTop = context.islandExpandedMediaDp(
                IslandExpandedMediaIosMetrics.COVER_TOP_DP
            )
            val coverHeight = context.islandExpandedMediaDp(
                IslandExpandedMediaIosMetrics.COVER_SIZE_DP
            )
            val progressTop = context.islandExpandedMediaDp(
                if (coverHidden) {
                    IslandExpandedMediaIosMetrics.PROGRESS_TOP_MARGIN_WITHOUT_COVER_DP
                } else {
                    IslandExpandedMediaIosMetrics.PROGRESS_TOP_MARGIN_DP
                }
            )
            val progressHeight = context.islandExpandedMediaDimenPx(
                "media_hyper_seekbar_height",
                IslandExpandedMediaIosMetrics.PROGRESS_HEIGHT_DP
            )
            val actionGap = context.islandExpandedMediaDp(
                IslandExpandedMediaIosMetrics.ACTION_MIN_GAP_AFTER_PROGRESS_DP
            )
            val actionHeight = context.islandExpandedMediaDimenPx(
                "media_action_height",
                IslandExpandedMediaIosMetrics.ACTION_HEIGHT_DP
            )
            val minimumActionTop = coverTop + coverHeight + progressTop + progressHeight + actionGap
            val maximumBottomMargin =
                (expandedHeight - minimumActionTop - actionHeight).coerceAtLeast(0)
            val desiredBottomMargin = (
                context.islandExpandedMediaDimenPx(
                    "media_control_seekbar_bottom_margin",
                    16f
                ) - context.islandExpandedMediaDp(
                    IslandExpandedMediaIosMetrics.ACTION_BOTTOM_MARGIN_OFFSET_DP
                )
                ).coerceAtLeast(0)
            return min(desiredBottomMargin, maximumBottomMargin)
        }
    }

    private fun replaceAction4WithDeviceSwitch(
        environment: IslandExpandedMediaLayoutEnvironment
    ) {
        with(environment) {
            val deviceSwitch = ids.mediaSeamless
            val action4 = ids.action4

            // Keep Action4 as the native slot and place the real device-switch
            // view over it, so SystemUI keeps its original click/bind lifecycle.
            bridge.setVisibility(layout, action4, View.INVISIBLE)
            bridge.clearHorizontal(layout, deviceSwitch)
            bridge.clearVertical(layout, deviceSwitch)
            bridge.connect(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.LEFT,
                action4,
                IslandExpandedMediaConstraintSide.LEFT
            )
            bridge.connect(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.RIGHT,
                action4,
                IslandExpandedMediaConstraintSide.RIGHT
            )
            bridge.connect(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.TOP,
                action4,
                IslandExpandedMediaConstraintSide.TOP
            )
            bridge.connect(
                layout,
                deviceSwitch,
                IslandExpandedMediaConstraintSide.BOTTOM,
                action4,
                IslandExpandedMediaConstraintSide.BOTTOM
            )
            bridge.setScale(
                layout,
                deviceSwitch,
                IslandExpandedMediaIosMetrics.DEVICE_SWITCH_SCALE
            )
        }
    }
}
