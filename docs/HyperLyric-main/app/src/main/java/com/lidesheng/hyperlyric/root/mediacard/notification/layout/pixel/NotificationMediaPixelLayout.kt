package com.lidesheng.hyperlyric.root.mediacard.notification.layout.pixel

import android.view.View
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintBridge
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.clearMargins
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dimenPx
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dp
import kotlin.math.roundToInt

/**
 * Pixel 风格只重排宿主已有控件，进度条仍由 SystemUI 原生控件负责绘制、拖动和动画。
 */
internal object NotificationMediaPixelLayout : NotificationMediaLayoutPreset {
    override fun apply(environment: NotificationMediaLayoutEnvironment) {
        with(environment) {
            val progressBarId = ids.mediaProgressBar
            if (progressBarId == 0) return

            val middleHorizontalMargin = context.dp(
                MediaLayoutSharedMetrics.PIXEL_CONTENT_MARGIN_DP
            )
            val bottomHorizontalMargin = context.dp(
                MediaLayoutSharedMetrics.PIXEL_BOTTOM_MARGIN_DP
            )
            val topMargin = context.dimenPx(
                "media_control_top_margin",
                MediaLayoutSharedMetrics.PIXEL_TOP_MARGIN_DP
            )
            val deviceEndMargin = context.dimenPx(
                "media_control_seamless_end_margin",
                MediaLayoutSharedMetrics.PIXEL_CONTENT_MARGIN_DP
            )
            val bottomMargin = context.dp(MediaLayoutSharedMetrics.PIXEL_BOTTOM_OFFSET_DP)
            val artistTopGap = context.dimenPx(
                "header_artist_margin_top",
                MediaLayoutSharedMetrics.PIXEL_ARTIST_GAP_DP
            )
            val titleEndGap = context.dimenPx(
                "media_header_title_margin_end",
                MediaLayoutSharedMetrics.PIXEL_TITLE_END_GAP_DP
            )
            val middleTopMargin = context.dp(70f)
            val actionWidth = context.dimenPx(
                "media_action_width",
                MediaLayoutSharedMetrics.NATIVE_ACTION_WIDTH_FALLBACK_DP
            )
            val actionHeight = context.dimenPx(
                "media_action_height",
                MediaLayoutSharedMetrics.NATIVE_ACTION_HEIGHT_FALLBACK_DP
            )
            val primaryActionWidth = (
                actionWidth * MediaLayoutSharedMetrics.PIXEL_PRIMARY_ACTION_SCALE
                ).roundToInt()
            val primaryActionHeight = (
                actionHeight * MediaLayoutSharedMetrics.PIXEL_PRIMARY_ACTION_SCALE
                ).roundToInt()
            val bottomActionWidth = (
                actionWidth * MediaLayoutSharedMetrics.PIXEL_SECONDARY_ACTION_SCALE
                ).roundToInt()
            // Keep the whole bottom row aligned with the explicitly sized progress container.
            val progressHeight = context.dp(MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP)
            val bottomActionHeight = progressHeight
            val componentGap = context.dp(MediaLayoutSharedMetrics.PIXEL_COMPONENT_GAP_DP)

            bridge.setVisibility(normalLayout, ids.albumArt, View.GONE)
            hideTimeView(bridge, normalLayout, ids.mediaElapsedTime)
            hideTimeView(bridge, normalLayout, ids.mediaTotalTime)

            if (hideDevice) {
                bridge.setVisibility(normalLayout, ids.mediaSeamless, View.GONE)
            } else {
                bridge.setVisibility(normalLayout, ids.mediaSeamless, View.VISIBLE)
                val deviceSize = context.dimenPx("media_control_seamless", 34f)
                bridge.constrainWidth(normalLayout, ids.mediaSeamless, deviceSize)
                bridge.constrainHeight(normalLayout, ids.mediaSeamless, deviceSize)
                bridge.clearAll(normalLayout, ids.mediaSeamless)
                bridge.clearMargins(normalLayout, ids.mediaSeamless)
                bridge.connect(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.END,
                    0,
                    NotificationMediaConstraintSide.END
                )
                bridge.connect(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.TOP,
                    0,
                    NotificationMediaConstraintSide.TOP
                )
                bridge.setMargin(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.END,
                    deviceEndMargin
                )
                bridge.setMargin(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.TOP,
                    topMargin
                )
            }

            val primaryAction = ids.actionButtons[2]
            bridge.constrainWidth(normalLayout, primaryAction, primaryActionWidth)
            bridge.constrainHeight(normalLayout, primaryAction, primaryActionHeight)
            bridge.clearAll(normalLayout, primaryAction)
            bridge.clearMargins(normalLayout, primaryAction)
            bridge.connect(
                normalLayout,
                primaryAction,
                NotificationMediaConstraintSide.END,
                0,
                NotificationMediaConstraintSide.END
            )
            bridge.connect(
                normalLayout,
                primaryAction,
                NotificationMediaConstraintSide.TOP,
                ids.headerTitle,
                NotificationMediaConstraintSide.TOP
            )
            bridge.connect(
                normalLayout,
                primaryAction,
                NotificationMediaConstraintSide.BOTTOM,
                ids.headerArtist,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                primaryAction,
                NotificationMediaConstraintSide.END,
                middleHorizontalMargin
            )

            bridge.constrainWidth(normalLayout, ids.headerTitle, 0)
            bridge.clearAll(normalLayout, ids.headerTitle)
            bridge.clearMargins(normalLayout, ids.headerTitle)
            bridge.connect(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.START,
                0,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.END,
                primaryAction,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.TOP,
                0,
                NotificationMediaConstraintSide.TOP
            )
            bridge.setMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.START,
                middleHorizontalMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.END,
                titleEndGap
            )
            bridge.setMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.TOP,
                middleTopMargin
            )

            bridge.constrainWidth(normalLayout, ids.headerArtist, 0)
            bridge.clearAll(normalLayout, ids.headerArtist)
            bridge.clearMargins(normalLayout, ids.headerArtist)
            bridge.connect(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.START,
                ids.headerTitle,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.END,
                ids.headerTitle,
                NotificationMediaConstraintSide.END
            )
            bridge.connect(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.TOP,
                ids.headerTitle,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.TOP,
                artistTopGap
            )

            val action1 = ids.actionButtons[1]
            val action3 = ids.actionButtons[3]
            val action0 = ids.actionButtons[0]
            val action4 = ids.actionButtons[4]
            listOf(action1, action3, action0, action4).forEach { actionId ->
                bridge.constrainWidth(normalLayout, actionId, bottomActionWidth)
                bridge.constrainHeight(normalLayout, actionId, bottomActionHeight)
                bridge.clearAll(normalLayout, actionId)
                bridge.clearMargins(normalLayout, actionId)
            }

            bridge.connect(
                normalLayout,
                action1,
                NotificationMediaConstraintSide.START,
                0,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                action1,
                NotificationMediaConstraintSide.BOTTOM,
                0,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                action1,
                NotificationMediaConstraintSide.START,
                bottomHorizontalMargin
            )
            bridge.setMargin(
                normalLayout,
                action1,
                NotificationMediaConstraintSide.BOTTOM,
                bottomMargin
            )

            bridge.constrainWidth(normalLayout, progressBarId, 0)
            bridge.constrainHeight(normalLayout, progressBarId, progressHeight)
            bridge.clearAll(normalLayout, progressBarId)
            bridge.clearMargins(normalLayout, progressBarId)
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.START,
                action1,
                NotificationMediaConstraintSide.END
            )
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.END,
                action3,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.TOP,
                action1,
                NotificationMediaConstraintSide.TOP
            )
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.BOTTOM,
                action1,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.START,
                componentGap
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.END,
                componentGap
            )

            bridge.connectRowItem(normalLayout, action3, progressBarId, action0, action1)
            bridge.connectRowItem(
                normalLayout,
                action0,
                action3,
                action4,
                action1,
                componentGap
            )
            bridge.connectRowItem(
                normalLayout,
                action4,
                action0,
                0,
                action1,
                componentGap
            )
            bridge.setMargin(
                normalLayout,
                action4,
                NotificationMediaConstraintSide.END,
                bottomHorizontalMargin
            )
        }
    }

    private fun hideTimeView(
        bridge: NotificationMediaConstraintBridge,
        layout: Any,
        viewId: Int
    ) {
        if (viewId == 0) return
        bridge.setVisibility(layout, viewId, View.GONE)
        bridge.constrainWidth(layout, viewId, 0)
        bridge.constrainHeight(layout, viewId, 0)
    }

    private fun NotificationMediaConstraintBridge.connectRowItem(
        layout: Any,
        viewId: Int,
        previousId: Int,
        nextId: Int,
        verticalAnchorId: Int,
        startMargin: Int = 0
    ) {
        connect(
            layout,
            viewId,
            NotificationMediaConstraintSide.START,
            previousId,
            NotificationMediaConstraintSide.END
        )
        connect(
            layout,
            viewId,
            NotificationMediaConstraintSide.END,
            nextId,
            if (nextId == 0) NotificationMediaConstraintSide.END
            else NotificationMediaConstraintSide.START
        )
        connect(
            layout,
            viewId,
            NotificationMediaConstraintSide.TOP,
            verticalAnchorId,
            NotificationMediaConstraintSide.TOP
        )
        connect(
            layout,
            viewId,
            NotificationMediaConstraintSide.BOTTOM,
            verticalAnchorId,
            NotificationMediaConstraintSide.BOTTOM
        )
        setMargin(layout, viewId, NotificationMediaConstraintSide.START, startMargin)
    }
}
