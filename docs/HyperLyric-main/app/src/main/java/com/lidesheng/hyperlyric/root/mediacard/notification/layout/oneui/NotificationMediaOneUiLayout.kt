package com.lidesheng.hyperlyric.root.mediacard.notification.layout.oneui

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.anchorActionRowToBottom
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dimenPx
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dp
import kotlin.math.roundToInt

internal object NotificationMediaOneUiLayout : NotificationMediaLayoutPreset {
    override fun apply(environment: NotificationMediaLayoutEnvironment) {
        with(environment) {
            val progressBarId = ids.mediaProgressBar
            if (
                progressBarId == 0 ||
                    ids.mediaElapsedTime == 0 ||
                    ids.mediaTotalTime == 0
            ) return

            val horizontalMargin = context.dp(25f)
            val topMargin = context.dp(17f)
            val progressOverlap = context.dp(
                MediaLayoutSharedMetrics.STANDARD_PROGRESS_OVERLAP_DP
            )
            val actionWidth = (
                context.dimenPx(
                    "media_action_width",
                    MediaLayoutSharedMetrics.NATIVE_ACTION_WIDTH_FALLBACK_DP
                ) *
                    NotificationMediaOneUiStyle.ACTION_BUTTON_SCALE
                ).roundToInt()
            val actionHeight = (
                context.dimenPx(
                    "media_action_height",
                    MediaLayoutSharedMetrics.NATIVE_ACTION_HEIGHT_FALLBACK_DP
                ) *
                    NotificationMediaOneUiStyle.ACTION_BUTTON_SCALE
                ).roundToInt()
            val actionGap = context.dp(4f)
            val actionRowWidth =
                actionWidth * ids.actionButtons.size + actionGap * (ids.actionButtons.size - 1)

            bridge.setVisibility(normalLayout, ids.albumArt, View.GONE)

            bridge.constrainWidth(normalLayout, ids.mediaSeamless, 0)
            bridge.constrainHeight(normalLayout, ids.mediaSeamless, context.dp(18f))
            bridge.clearAll(normalLayout, ids.mediaSeamless)
            bridge.connect(
                normalLayout,
                ids.mediaSeamless,
                NotificationMediaConstraintSide.START,
                0,
                NotificationMediaConstraintSide.START
            )
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
                NotificationMediaConstraintSide.START,
                horizontalMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.mediaSeamless,
                NotificationMediaConstraintSide.END,
                horizontalMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.mediaSeamless,
                NotificationMediaConstraintSide.TOP,
                topMargin
            )

            bridge.constrainWidth(normalLayout, ids.headerTitle, 0)
            bridge.clearAll(normalLayout, ids.headerTitle)
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
                0,
                NotificationMediaConstraintSide.END
            )
            bridge.connect(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.TOP,
                ids.mediaSeamless,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.START,
                horizontalMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.END,
                horizontalMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.TOP,
                topMargin
            )

            bridge.constrainWidth(normalLayout, ids.headerArtist, 0)
            bridge.clearAll(normalLayout, ids.headerArtist)
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
                context.dp(1f)
            )

            bridge.constrainWidth(normalLayout, progressBarId, 0)
            bridge.constrainHeight(
                normalLayout,
                progressBarId,
                context.dp(MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP)
            )
            bridge.clearAll(normalLayout, progressBarId)
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.START,
                ids.headerTitle,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.END,
                ids.headerTitle,
                NotificationMediaConstraintSide.END
            )
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.TOP,
                ids.headerArtist,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.TOP,
                -progressOverlap
            )

            bridge.constrainWidth(
                normalLayout,
                ids.mediaElapsedTime,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            bridge.clearAll(normalLayout, ids.mediaElapsedTime)
            bridge.connect(
                normalLayout,
                ids.mediaElapsedTime,
                NotificationMediaConstraintSide.START,
                progressBarId,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                ids.mediaElapsedTime,
                NotificationMediaConstraintSide.TOP,
                progressBarId,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                ids.mediaElapsedTime,
                NotificationMediaConstraintSide.TOP,
                -progressOverlap
            )

            bridge.constrainWidth(
                normalLayout,
                ids.mediaTotalTime,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            bridge.clearAll(normalLayout, ids.mediaTotalTime)
            bridge.connect(
                normalLayout,
                ids.mediaTotalTime,
                NotificationMediaConstraintSide.END,
                progressBarId,
                NotificationMediaConstraintSide.END
            )
            bridge.connect(
                normalLayout,
                ids.mediaTotalTime,
                NotificationMediaConstraintSide.TOP,
                progressBarId,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                ids.mediaTotalTime,
                NotificationMediaConstraintSide.TOP,
                -progressOverlap
            )

            bridge.constrainWidth(normalLayout, ids.actions, actionRowWidth)
            ids.actionButtons.forEach { actionId ->
                bridge.constrainWidth(normalLayout, actionId, actionWidth)
                bridge.constrainHeight(normalLayout, actionId, actionHeight)
                bridge.setMargin(normalLayout, actionId, NotificationMediaConstraintSide.LEFT, 0)
                bridge.setMargin(normalLayout, actionId, NotificationMediaConstraintSide.RIGHT, 0)
                bridge.setMargin(normalLayout, actionId, NotificationMediaConstraintSide.START, 0)
                bridge.setMargin(normalLayout, actionId, NotificationMediaConstraintSide.END, 0)
            }
            bridge.anchorActionRowToBottom(normalLayout, ids.action0, context, 9f)
        }
    }
}
