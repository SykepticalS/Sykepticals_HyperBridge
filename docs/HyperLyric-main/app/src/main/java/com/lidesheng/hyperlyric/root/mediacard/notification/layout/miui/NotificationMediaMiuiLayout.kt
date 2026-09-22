package com.lidesheng.hyperlyric.root.mediacard.notification.layout.miui

import android.view.View
import android.view.ViewGroup
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dimenPx
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dp
import kotlin.math.roundToInt

internal object NotificationMediaMiuiLayout : NotificationMediaLayoutPreset {
    override fun apply(environment: NotificationMediaLayoutEnvironment) {
        with(environment) {
            val progressBarId = ids.mediaProgressBar
            if (
                progressBarId == 0 ||
                    ids.mediaElapsedTime == 0 ||
                    ids.mediaTotalTime == 0
            ) return

            val horizontalMargin = context.dp(NotificationMediaMiuiStyle.HORIZONTAL_MARGIN_DP)
            val titleTopMargin = context.dimenPx("media_control_title_top_margin", 21f)
            val artistTopGap = context.dimenPx("header_artist_margin_top", 4f)
            val actionTopGap = context.dp(8f)
            val actionStartPadding = context.dp(12f)
            val actionGap = context.dp(2f)
            val progressBottomMargin = context.dimenPx(
                "media_control_seekbar_bottom_margin",
                16f
            )
            val progressOverlap = context.dp(
                MediaLayoutSharedMetrics.STANDARD_PROGRESS_OVERLAP_DP
            )
            val actionWidth = (
                context.dimenPx(
                    "media_action_width",
                    MediaLayoutSharedMetrics.NATIVE_ACTION_WIDTH_FALLBACK_DP
                ) *
                    NotificationMediaMiuiStyle.ACTION_BUTTON_SCALE
                ).roundToInt()
            val actionHeight = (
                context.dimenPx(
                    "media_action_height",
                    MediaLayoutSharedMetrics.NATIVE_ACTION_HEIGHT_FALLBACK_DP
                ) *
                    NotificationMediaMiuiStyle.ACTION_BUTTON_SCALE
                ).roundToInt()
            val deviceSize = context.dimenPx("media_control_seamless", 34f)
            val progressHeight = context.dp(MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP)
            val orderedActionIds = when (actionsOrder) {
                RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_CUSTOM_RIGHT -> listOf(
                    ids.actionButtons[1],
                    ids.actionButtons[2],
                    ids.actionButtons[3],
                    ids.actionButtons[0],
                    ids.actionButtons[4]
                )

                RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_PLAY_LEFT -> listOf(
                    ids.actionButtons[2],
                    ids.actionButtons[1],
                    ids.actionButtons[3],
                    ids.actionButtons[0],
                    ids.actionButtons[4]
                )

                else -> ids.actionButtons
            }

            bridge.setVisibility(normalLayout, ids.albumArt, View.GONE)

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
                0,
                NotificationMediaConstraintSide.TOP
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
                titleTopMargin
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
                artistTopGap
            )

            bridge.constrainWidth(
                normalLayout,
                ids.actions,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orderedActionIds.forEachIndexed { index, actionId ->
                bridge.constrainWidth(normalLayout, actionId, actionWidth)
                bridge.constrainHeight(normalLayout, actionId, actionHeight)
                bridge.clear(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.LEFT
                )
                bridge.clear(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.RIGHT
                )
                bridge.clear(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.START
                )
                bridge.clear(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.END
                )
                val previousId = orderedActionIds.getOrNull(index - 1) ?: ids.actions
                val previousSide = if (index == 0) {
                    NotificationMediaConstraintSide.LEFT
                } else {
                    NotificationMediaConstraintSide.RIGHT
                }
                val nextId = orderedActionIds.getOrNull(index + 1)
                bridge.connect(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.LEFT,
                    previousId,
                    previousSide
                )
                if (nextId != null) {
                    bridge.connect(
                        normalLayout,
                        actionId,
                        NotificationMediaConstraintSide.RIGHT,
                        nextId,
                        NotificationMediaConstraintSide.LEFT
                    )
                }
                bridge.setMargin(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.LEFT,
                    if (index == 0) actionStartPadding else 0
                )
                bridge.setMargin(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.RIGHT,
                    if (index < ids.actionButtons.lastIndex) actionGap else 0
                )
                bridge.setMargin(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.START,
                    0
                )
                bridge.setMargin(
                    normalLayout,
                    actionId,
                    NotificationMediaConstraintSide.END,
                    0
                )
            }
            bridge.setHorizontalChainStyle(
                normalLayout,
                orderedActionIds.first(),
                1
            )

            bridge.clear(
                normalLayout,
                ids.action0,
                NotificationMediaConstraintSide.TOP
            )
            bridge.clear(
                normalLayout,
                ids.action0,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.connect(
                normalLayout,
                ids.action0,
                NotificationMediaConstraintSide.TOP,
                ids.headerArtist,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                ids.action0,
                NotificationMediaConstraintSide.TOP,
                actionTopGap
            )

            if (hideDevice) {
                bridge.setVisibility(normalLayout, ids.mediaSeamless, View.GONE)
            } else {
                bridge.setVisibility(normalLayout, ids.mediaSeamless, View.VISIBLE)
                bridge.constrainWidth(normalLayout, ids.mediaSeamless, deviceSize)
                bridge.constrainHeight(normalLayout, ids.mediaSeamless, deviceSize)
                bridge.clearAll(normalLayout, ids.mediaSeamless)
                bridge.connect(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.RIGHT,
                    0,
                    NotificationMediaConstraintSide.RIGHT
                )
                bridge.connect(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.TOP,
                    ids.action0,
                    NotificationMediaConstraintSide.TOP
                )
                bridge.connect(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.BOTTOM,
                    ids.action0,
                    NotificationMediaConstraintSide.BOTTOM
                )
                bridge.setMargin(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.RIGHT,
                    horizontalMargin
                )
                bridge.setMargin(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.LEFT,
                    0
                )
                bridge.setMargin(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.START,
                    0
                )
                bridge.setMargin(
                    normalLayout,
                    ids.mediaSeamless,
                    NotificationMediaConstraintSide.END,
                    0
                )
            }

            bridge.constrainWidth(normalLayout, progressBarId, 0)
            bridge.constrainHeight(normalLayout, progressBarId, progressHeight)
            bridge.clearAll(normalLayout, progressBarId)
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.START,
                0,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.END,
                0,
                NotificationMediaConstraintSide.END
            )
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.BOTTOM,
                0,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.START,
                horizontalMargin
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.END,
                horizontalMargin
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.BOTTOM,
                progressBottomMargin
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
        }
    }
}
