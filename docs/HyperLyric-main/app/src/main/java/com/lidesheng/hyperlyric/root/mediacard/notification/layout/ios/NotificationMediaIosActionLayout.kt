package com.lidesheng.hyperlyric.root.mediacard.notification.layout.ios

import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.clearVertical
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dimenPx
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dp
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import kotlin.math.min

internal object NotificationMediaIosActionLayout {
    fun apply(environment: NotificationMediaLayoutEnvironment) {
        with(environment) {
            // The native action chain already owns the start/end anchors and all five slots.
            // Anchor only its vertical position to the bottom like the native progress row.
            bridge.clearVertical(normalLayout, ids.action0)
            bridge.connect(
                normalLayout,
                ids.action0,
                NotificationMediaConstraintSide.BOTTOM,
                0,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                ids.action0,
                NotificationMediaConstraintSide.BOTTOM,
                resolveBottomMargin(environment)
            )
        }
    }

    private fun resolveBottomMargin(environment: NotificationMediaLayoutEnvironment): Int {
        with(environment) {
            val expandedHeight = context.dimenPx("qs_media_session_height_expanded", 185f)
            val coverTop = context.dimenPx("media_margin_left", 15f)
            val coverHeight = context.dp(NotificationMediaIosMetrics.COVER_SIZE_DP)
            val progressTop = context.dp(NotificationMediaIosMetrics.PROGRESS_TOP_MARGIN_DP)
            val progressHeight = context.dimenPx(
                "media_hyper_seekbar_height",
                MediaLayoutSharedMetrics.STANDARD_PROGRESS_HEIGHT_DP
            )
            val actionGap = context.dp(
                NotificationMediaIosMetrics.ACTION_MIN_GAP_AFTER_PROGRESS_DP
            )
            val actionHeight = context.dimenPx(
                "media_action_height",
                MediaLayoutSharedMetrics.NATIVE_ACTION_HEIGHT_FALLBACK_DP
            )
            val minimumActionTop =
                coverTop + coverHeight + progressTop + progressHeight + actionGap
            val maximumBottomMargin =
                (expandedHeight - minimumActionTop - actionHeight).coerceAtLeast(0)
            val bottomMarginOffset = context.dp(
                NotificationMediaIosMetrics.ACTION_BOTTOM_MARGIN_OFFSET_DP
            )
            val desiredBottomMargin = (
                context.dimenPx("media_control_seekbar_bottom_margin", 16f) +
                    bottomMarginOffset
            ).coerceAtLeast(0)
            return min(desiredBottomMargin, maximumBottomMargin)
        }
    }
}
