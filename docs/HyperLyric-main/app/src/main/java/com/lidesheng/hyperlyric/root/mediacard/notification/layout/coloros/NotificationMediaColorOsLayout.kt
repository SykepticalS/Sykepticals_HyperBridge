package com.lidesheng.hyperlyric.root.mediacard.notification.layout.coloros

import android.view.ViewGroup
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaConstraintSide
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutEnvironment
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.NotificationMediaLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.anchorActionRowToBottom
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.clearAll
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dp

internal object NotificationMediaColorOsLayout : NotificationMediaLayoutPreset {
    override fun apply(environment: NotificationMediaLayoutEnvironment) {
        with(environment) {
            val progressBarId = ids.mediaProgressBar
            val albumLayout = normalAlbumLayout
            if (
                progressBarId == 0 ||
                    ids.mediaElapsedTime == 0 ||
                    ids.mediaTotalTime == 0 ||
                    ids.albumArtImage == 0 ||
                    albumLayout == null
            ) return

            val horizontalOuterMargin = context.dp(17f)
            val topMargin = context.dp(17f)
            val contentGap = context.dp(12f)
            val contentEndMargin = context.dp(17f)
            val appIconEndMargin = context.dp(17f)
            val progressOverlap = context.dp(8f)
            val coverSize = context.dp(83f)
            val appIconWidth = context.dp(22f)

            bridge.constrainWidth(normalLayout, ids.albumArt, coverSize)
            bridge.constrainHeight(normalLayout, ids.albumArt, coverSize)
            bridge.clearAll(normalLayout, ids.albumArt)
            bridge.connect(
                normalLayout,
                ids.albumArt,
                NotificationMediaConstraintSide.START,
                0,
                NotificationMediaConstraintSide.START
            )
            bridge.connect(
                normalLayout,
                ids.albumArt,
                NotificationMediaConstraintSide.TOP,
                0,
                NotificationMediaConstraintSide.TOP
            )
            bridge.setMargin(
                normalLayout,
                ids.albumArt,
                NotificationMediaConstraintSide.START,
                horizontalOuterMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.albumArt,
                NotificationMediaConstraintSide.TOP,
                topMargin
            )
            bridge.constrainWidth(albumLayout, ids.albumArtImage, coverSize)
            bridge.constrainHeight(albumLayout, ids.albumArtImage, coverSize)

            val contentStartTarget = if (coverHidden) 0 else ids.albumArt
            val contentStartSide = if (coverHidden) {
                NotificationMediaConstraintSide.START
            } else {
                NotificationMediaConstraintSide.END
            }
            val contentStartMargin = if (coverHidden) contentEndMargin else contentGap

            bridge.clearAll(normalLayout, ids.headerTitle)
            bridge.connect(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.START,
                contentStartTarget,
                contentStartSide
            )
            bridge.connect(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.END,
                ids.mediaSeamless,
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
                contentStartMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.END,
                context.dp(8f)
            )
            bridge.setMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.TOP,
                topMargin
            )

            bridge.constrainWidth(normalLayout, ids.mediaSeamless, appIconWidth)
            bridge.constrainHeight(normalLayout, ids.mediaSeamless, 0)
            bridge.clearAll(normalLayout, ids.mediaSeamless)
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
                ids.headerTitle,
                NotificationMediaConstraintSide.TOP
            )
            bridge.connect(
                normalLayout,
                ids.mediaSeamless,
                NotificationMediaConstraintSide.BOTTOM,
                ids.headerTitle,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                ids.mediaSeamless,
                NotificationMediaConstraintSide.END,
                appIconEndMargin
            )

            bridge.clearAll(normalLayout, ids.headerArtist)
            bridge.connect(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.START,
                contentStartTarget,
                contentStartSide
            )
            bridge.connect(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.END,
                0,
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
                NotificationMediaConstraintSide.START,
                contentStartMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.END,
                contentEndMargin
            )
            bridge.setMargin(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.TOP,
                context.dp(5f)
            )

            bridge.constrainWidth(normalLayout, progressBarId, 0)
            bridge.constrainHeight(normalLayout, progressBarId, context.dp(38f))
            bridge.clearAll(normalLayout, progressBarId)
            bridge.connect(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.START,
                contentStartTarget,
                contentStartSide
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
                NotificationMediaConstraintSide.TOP,
                ids.headerArtist,
                NotificationMediaConstraintSide.BOTTOM
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.START,
                contentStartMargin
            )
            bridge.setMargin(
                normalLayout,
                progressBarId,
                NotificationMediaConstraintSide.END,
                contentEndMargin
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

            bridge.anchorActionRowToBottom(normalLayout, ids.action0, context, 14f)
        }
    }
}
