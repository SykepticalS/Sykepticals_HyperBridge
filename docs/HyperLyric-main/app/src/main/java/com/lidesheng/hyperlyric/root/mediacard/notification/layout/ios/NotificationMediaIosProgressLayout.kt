package com.lidesheng.hyperlyric.root.mediacard.notification.layout.ios

import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.clearVertical
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dp

internal object NotificationMediaIosProgressLayout {
    fun apply(
        environment: NotificationMediaLayoutEnvironment,
        progressBarId: Int
    ) {
        with(environment) {
            val progressAnchor = if (coverHidden) ids.headerArtist else ids.albumArt
            val progressTopMargin = context.dp(
                if (coverHidden) {
                    NotificationMediaIosMetrics.PROGRESS_TOP_MARGIN_WITHOUT_COVER_DP
                } else {
                    NotificationMediaIosMetrics.PROGRESS_TOP_MARGIN_DP
                }
            )

            bridge.clearVertical(normalLayout, progressBarId)
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.TOP,
                progressAnchor,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.TOP,
                progressTopMargin
            )
            applyTimeBounds(environment, progressBarId)
        }
    }

    private fun applyTimeBounds(
        environment: NotificationMediaLayoutEnvironment,
        progressBarId: Int
    ) {
        with(environment) {
            ids.mediaElapsedTime.takeIf { it != 0 }?.let { elapsedTime ->
                bridge.constrainWidth(
                    normalLayout,
                    elapsedTime,
                    context.dp(NotificationMediaIosMetrics.TIME_WIDTH_DP)
                )
                bridge.setMargin(
                    normalLayout,
                    elapsedTime,
                    NotificationMediaConstraintSide.LEFT,
                    context.dp(NotificationMediaIosMetrics.TIME_OUTER_MARGIN_DP)
                )
                bridge.setMargin(
                    normalLayout,
                    progressBarId,
                    NotificationMediaConstraintSide.LEFT,
                    0
                )
            }
            ids.mediaTotalTime.takeIf { it != 0 }?.let { totalTime ->
                bridge.constrainWidth(
                    normalLayout,
                    totalTime,
                    context.dp(NotificationMediaIosMetrics.TIME_WIDTH_DP)
                )
                bridge.setMargin(
                    normalLayout,
                    totalTime,
                    NotificationMediaConstraintSide.LEFT,
                    0
                )
                bridge.setMargin(
                    normalLayout,
                    totalTime,
                    NotificationMediaConstraintSide.RIGHT,
                    context.dp(NotificationMediaIosMetrics.TIME_OUTER_MARGIN_DP)
                )
            }
        }
    }
}
