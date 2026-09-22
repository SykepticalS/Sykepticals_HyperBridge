package com.lidesheng.hyperlyric.root.mediacard.notification.layout.pixel

import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaHostApi
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal object NotificationMediaPixelStyle {
    private val actionButtonPaddingStates =
        Collections.synchronizedMap(WeakHashMap<ImageButton, ActionButtonPaddingState>())
    private val appIconStates =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, PixelAppIconState>())

    fun apply(
        api: NotificationMediaHostApi,
        controller: Any,
        holder: Any,
        mediaData: Any?
    ) {
        api.getActionButtons(holder).forEach(::applyActionButton)

        val player = api.getPlayer(holder) ?: return
        val currentMediaData = api.getMediaData(controller) ?: mediaData
        val drawable = api.getAppIdentityIcon(controller, currentMediaData, player.context)
        if (drawable == null) {
            appIconStates.remove(player)?.restore()
            return
        }
        val state = appIconStates[player]
            ?: PixelAppIconState.create(player)?.also { appIconStates[player] = it }
            ?: return
        state.apply(drawable)
    }

    fun refreshAppIcon(
        api: NotificationMediaHostApi,
        controller: Any,
        holder: Any
    ) {
        apply(api, controller, holder, api.getMediaData(controller))
    }

    fun applyActionButton(button: ImageButton) {
        val scale = if (button.isPrimaryAction()) {
            MediaLayoutSharedMetrics.PIXEL_PRIMARY_ACTION_SCALE
        } else {
            MediaLayoutSharedMetrics.PIXEL_SECONDARY_ACTION_SCALE
        }
        val state = actionButtonPaddingStates.getOrPut(button) {
            ActionButtonPaddingState.capture(button)
        }
        button.setPadding(
            (state.left * scale).roundToInt(),
            (state.top * scale).roundToInt(),
            (state.right * scale).roundToInt(),
            (state.bottom * scale).roundToInt()
        )
    }

    fun restore(api: NotificationMediaHostApi, holder: Any) {
        api.getActionButtons(holder).forEach(::restoreActionButton)
        api.getPlayer(holder)?.let { player ->
            appIconStates.remove(player)?.restore()
        }
    }

    private fun restoreActionButton(button: ImageButton) {
        actionButtonPaddingStates.remove(button)?.restore(button)
    }

    private fun ImageButton.isPrimaryAction(): Boolean {
        return runCatching { resources.getResourceEntryName(id) == "action2" }
            .getOrDefault(false)
    }

    private data class ActionButtonPaddingState(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        fun restore(button: ImageButton) {
            button.setPadding(left, top, right, bottom)
        }

        companion object {
            fun capture(button: ImageButton): ActionButtonPaddingState {
                return ActionButtonPaddingState(
                    left = button.paddingLeft,
                    top = button.paddingTop,
                    right = button.paddingRight,
                    bottom = button.paddingBottom
                )
            }
        }
    }

    private data class PixelAppIconState(val icon: ImageView) {
        fun apply(drawable: Drawable) {
            val copy = runCatching {
                drawable.constantState
                    ?.newDrawable(icon.resources, icon.context.theme)
                    ?.mutate()
            }.getOrNull() ?: drawable
            icon.setImageDrawable(copy)
            icon.imageTintList = null
            icon.alpha = 1f
            icon.visibility = View.VISIBLE
        }

        fun restore() {
            (icon.parent as? ViewGroup)?.removeView(icon)
        }

        companion object {
            fun create(player: ViewGroup): PixelAppIconState? = runCatching {
                val context = player.context
                val density = context.resources.displayMetrics.density
                val iconSize = (
                    MediaLayoutSharedMetrics.PIXEL_APP_ICON_SIZE_DP * density
                    ).roundToInt()
                val icon = ImageView(context).apply {
                    id = View.generateViewId()
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val layoutParamsClass = Class.forName(
                    "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams",
                    false,
                    player.javaClass.classLoader
                )
                val layoutParams = layoutParamsClass.getConstructor(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                ).newInstance(iconSize, iconSize) as ViewGroup.MarginLayoutParams
                layoutParamsClass.getField("startToStart").setInt(layoutParams, 0)
                layoutParamsClass.getField("topToTop").setInt(layoutParams, 0)
                layoutParams.apply {
                    marginStart = (
                        MediaLayoutSharedMetrics.PIXEL_CONTENT_MARGIN_DP * density
                        ).roundToInt()
                    topMargin = (
                        MediaLayoutSharedMetrics.PIXEL_CONTENT_MARGIN_DP * density
                        ).roundToInt()
                }
                player.addView(icon, layoutParams)
                PixelAppIconState(icon)
            }.getOrNull()
        }
    }
}
