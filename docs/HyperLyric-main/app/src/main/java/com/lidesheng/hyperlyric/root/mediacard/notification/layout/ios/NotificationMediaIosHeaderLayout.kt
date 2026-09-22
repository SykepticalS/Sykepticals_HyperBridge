package com.lidesheng.hyperlyric.root.mediacard.notification.layout.ios

import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.clearVertical
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dimenPx
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dp

internal object NotificationMediaIosHeaderLayout {
    fun apply(environment: NotificationMediaLayoutEnvironment) {
        resizeCover(environment)
        anchorTitleToTop(environment)
    }

    private fun resizeCover(environment: NotificationMediaLayoutEnvironment) {
        with(environment) {
            val coverSize = context.dp(NotificationMediaIosMetrics.COVER_SIZE_DP)
            bridge.constrainWidth(normalLayout, ids.albumArt, coverSize)
            bridge.constrainHeight(normalLayout, ids.albumArt, coverSize)
            bridge.setMargin(
                normalLayout,
                ids.albumArt,
                NotificationMediaConstraintSide.TOP,
                context.dimenPx("media_margin_left", 15f)
            )

            val albumLayout = normalAlbumLayout ?: return
            val albumArtImage = ids.albumArtImage.takeIf { it != 0 } ?: return
            bridge.constrainWidth(albumLayout, albumArtImage, coverSize)
            bridge.constrainHeight(albumLayout, albumArtImage, coverSize)
        }
    }

    private fun anchorTitleToTop(environment: NotificationMediaLayoutEnvironment) {
        with(environment) {
            // Keep the SystemUI start/end constraints so text width follows the available card width.
            bridge.clearVertical(normalLayout, ids.headerTitle)
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
                NotificationMediaConstraintSide.TOP,
                context.dimenPx("media_control_title_top_margin", 21f) +
                        context.dp(NotificationMediaIosMetrics.UPPER_CONTENT_SHIFT_DP)
            )
        }
    }
}
