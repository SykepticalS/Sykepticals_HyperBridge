package com.lidesheng.hyperlyric.root.mediacard.notification.layout

import android.content.Context
import android.view.View
import com.lidesheng.hyperlyric.common.RootConstants
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.coloros.NotificationMediaColorOsLayout
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.ios.NotificationMediaIosLayoutPreset
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.miui.NotificationMediaMiuiLayout
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.oneui.NotificationMediaOneUiLayout
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.pixel.NotificationMediaPixelLayout


internal object NotificationMediaLayoutController {
    fun apply(
        bridge: NotificationMediaConstraintBridge,
        normalLayout: Any,
        normalAlbumLayout: Any?,
        ids: NotificationMediaLayoutResourceIds,
        context: Context,
        layoutStyle: Int,
        coverStyle: Int,
        hideSource: Boolean,
        hideDevice: Boolean,
        hideCustomActions: Boolean,
        hideTime: Boolean,
        actionsLeftAligned: Boolean,
        actionsOrder: Int
    ) {
        val coverHidden = coverStyle == RootConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN
        if (bridge.supportsFullLayout) {
            presetFor(layoutStyle)?.apply(
                NotificationMediaLayoutEnvironment(
                    bridge = bridge,
                    normalLayout = normalLayout,
                    normalAlbumLayout = normalAlbumLayout,
                    ids = ids,
                    context = context,
                    coverHidden = coverHidden,
                    hideDevice = hideDevice,
                    actionsOrder = actionsOrder
                )
            )
        }

        if (coverHidden) {
            bridge.setGoneMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.START,
                context.dp(26f)
            )
            bridge.setGoneMargin(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.START,
                context.dp(26f)
            )
            if (layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM) {
                bridge.setGoneMargin(
                    normalLayout,
                    ids.actions,
                    NotificationMediaConstraintSide.TOP,
                    context.dp(67.5f)
                )
                bridge.setGoneMargin(
                    normalLayout,
                    ids.action0,
                    NotificationMediaConstraintSide.TOP,
                    context.dp(78.5f)
                )
            }
            bridge.setVisibility(normalLayout, ids.albumArt, View.GONE)
        }
        if (hideSource && normalAlbumLayout != null) {
            bridge.setVisibility(normalAlbumLayout, ids.coverSource, View.GONE)
        }
        if (
            layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS ||
                layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI
        ) {
            bridge.setVisibility(normalLayout, ids.mediaSeamless, View.VISIBLE)
        } else if (hideDevice) {
            bridge.setVisibility(normalLayout, ids.mediaSeamless, View.GONE)
        }
        if (hideDevice) {
            bridge.setGoneMargin(
                normalLayout,
                ids.headerTitle,
                NotificationMediaConstraintSide.END,
                context.dp(26f)
            )
            bridge.setGoneMargin(
                normalLayout,
                ids.headerArtist,
                NotificationMediaConstraintSide.END,
                context.dp(26f)
            )
        }
        if (layoutStyle != RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL) {
            applyActionOverrides(
                bridge = bridge,
                layout = normalLayout,
                ids = ids,
                context = context,
                hideCustomActions = hideCustomActions,
                keepAction4 = layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS &&
                        !hideDevice,
                actionsLeftAligned = actionsLeftAligned,
                actionsOrder = if (
                    layoutStyle == RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI
                ) {
                    RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT
                } else {
                    actionsOrder
                }
            )
        }
        if (hideTime && layoutStyle != RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL) {
            if (ids.mediaProgressBar != 0) {
                bridge.connect(
                    normalLayout,
                    ids.mediaProgressBar,
                    NotificationMediaConstraintSide.LEFT,
                    0,
                    NotificationMediaConstraintSide.LEFT
                )
                bridge.connect(
                    normalLayout,
                    ids.mediaProgressBar,
                    NotificationMediaConstraintSide.RIGHT,
                    0,
                    NotificationMediaConstraintSide.RIGHT
                )
                bridge.setMargin(
                    normalLayout,
                    ids.mediaProgressBar,
                    NotificationMediaConstraintSide.LEFT,
                    context.dp(26f)
                )
                bridge.setMargin(
                    normalLayout,
                    ids.mediaProgressBar,
                    NotificationMediaConstraintSide.RIGHT,
                    context.dp(26f)
                )
            }
            if (ids.mediaElapsedTime != 0) {
                bridge.setVisibility(normalLayout, ids.mediaElapsedTime, View.GONE)
            }
            if (ids.mediaTotalTime != 0) {
                bridge.setVisibility(normalLayout, ids.mediaTotalTime, View.GONE)
            }
        }
    }

    private fun presetFor(style: Int): NotificationMediaLayoutPreset? {
        return when (style) {
            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_IOS ->
                NotificationMediaIosLayoutPreset

            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS ->
                NotificationMediaColorOsLayout

            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI ->
                NotificationMediaOneUiLayout

            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI ->
                NotificationMediaMiuiLayout

            RootConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL ->
                NotificationMediaPixelLayout

            else -> null
        }
    }

    private fun applyActionOverrides(
        bridge: NotificationMediaConstraintBridge,
        layout: Any,
        ids: NotificationMediaLayoutResourceIds,
        context: Context,
        hideCustomActions: Boolean,
        keepAction4: Boolean,
        actionsLeftAligned: Boolean,
        actionsOrder: Int
    ) {
        val action0 = ids.actionButtons[0]
        val action1 = ids.actionButtons[1]
        val action2 = ids.actionButtons[2]
        val action3 = ids.actionButtons[3]
        val action4 = ids.actionButtons[4]
        val ordered = when (actionsOrder) {
            RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_CUSTOM_RIGHT ->
                listOf(action1, action2, action3, action0, action4)

            RootConstants.NOTIFICATION_MEDIA_ACTION_ORDER_PLAY_LEFT ->
                listOf(action2, action1, action3, action0, action4)

            else -> emptyList()
        }
        if (ordered.isNotEmpty()) {
            ordered.forEachIndexed { index, viewId ->
                val leftTarget = ordered.getOrNull(index - 1) ?: ids.actions
                val rightTarget = ordered.getOrNull(index + 1) ?: ids.actions
                val leftSide = if (index == 0) {
                    NotificationMediaConstraintSide.LEFT
                } else {
                    NotificationMediaConstraintSide.RIGHT
                }
                val rightSide = if (index == ordered.lastIndex) {
                    NotificationMediaConstraintSide.RIGHT
                } else {
                    NotificationMediaConstraintSide.LEFT
                }
                bridge.connect(
                    layout,
                    viewId,
                    NotificationMediaConstraintSide.LEFT,
                    leftTarget,
                    leftSide
                )
                bridge.connect(
                    layout,
                    viewId,
                    NotificationMediaConstraintSide.RIGHT,
                    rightTarget,
                    rightSide
                )
            }
            bridge.setMargin(
                layout,
                action0,
                NotificationMediaConstraintSide.START,
                0
            )
            bridge.setMargin(
                layout,
                ordered.first(),
                NotificationMediaConstraintSide.START,
                context.dp(6f)
            )
        }
        if (actionsLeftAligned) {
            bridge.clear(layout, action4, NotificationMediaConstraintSide.RIGHT)
        }
        if (hideCustomActions) {
            bridge.setVisibility(layout, action0, View.INVISIBLE)
            if (!keepAction4) {
                bridge.setVisibility(layout, action4, View.INVISIBLE)
            }
        }
    }

}
