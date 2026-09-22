package com.lidesheng.hyperlyric.root.mediacard.notification.layout.oneui

import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaActionIconScaler
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaHostApi
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal object NotificationMediaOneUiStyle {
    const val ACTION_BUTTON_SCALE = MediaLayoutSharedMetrics.COMPACT_ACTION_SCALE

    private const val APP_ICON_SIZE_DP = 16f
    private const val APP_NAME_GAP_DP = 6f
    private const val APP_NAME_TEXT_SIZE_SP = 12f
    private const val APP_NAME_ALPHA = 0.65f

    private val actionButtonPaddingStates =
        Collections.synchronizedMap(WeakHashMap<ImageButton, ActionButtonPaddingState>())
    private val timeTextStates =
        Collections.synchronizedMap(WeakHashMap<TextView, TimeTextState>())
    private val appIdentityStates =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, AppIdentityState>())

    fun apply(
        api: NotificationMediaHostApi,
        controller: Any,
        holder: Any,
        mediaData: Any?
    ) {
        api.getElapsedTimeView(holder)?.let { view ->
            timeTextStates.getOrPut(view) { TimeTextState.capture(view) }
                .apply(Gravity.START or Gravity.CENTER_VERTICAL)
        }
        api.getTotalTimeView(holder)?.let { view ->
            timeTextStates.getOrPut(view) { TimeTextState.capture(view) }
                .apply(Gravity.END or Gravity.CENTER_VERTICAL)
        }
        api.getActionButtons(holder).forEach(::applyActionButton)

        val container = api.getSeamlessContainer(holder) ?: return
        val sourceIcon = api.getSeamlessIcon(holder) ?: return
        val currentMediaData = api.getMediaData(controller) ?: mediaData
        val appName = api.getApplicationName(currentMediaData, container.context)
        val appIcon = api.getAppIdentityIcon(controller, currentMediaData, container.context)
        val textColor = api.getMediaForegroundColor(holder)
        if (appName.isNullOrBlank() || appIcon == null || textColor == null) {
            appIdentityStates.getOrPut(container) {
                AppIdentityState(container, sourceIcon)
            }.hideSource()
            return
        }

        appIdentityStates.getOrPut(container) {
            AppIdentityState(container, sourceIcon)
        }.apply(appIcon, appName, textColor)
    }

    fun refreshAppNameColor(api: NotificationMediaHostApi, holder: Any) {
        val container = api.getSeamlessContainer(holder) ?: return
        val textColor = api.getMediaForegroundColor(holder) ?: return
        appIdentityStates[container]?.applyTextColor(textColor)
    }

    fun applyActionButton(button: ImageButton) {
        MediaActionIconScaler.apply(button, ACTION_BUTTON_SCALE)
        val state = actionButtonPaddingStates.getOrPut(button) {
            ActionButtonPaddingState.capture(button)
        }
        button.setPadding(
            (state.left * ACTION_BUTTON_SCALE).roundToInt(),
            (state.top * ACTION_BUTTON_SCALE).roundToInt(),
            (state.right * ACTION_BUTTON_SCALE).roundToInt(),
            (state.bottom * ACTION_BUTTON_SCALE).roundToInt()
        )
    }

    fun restore(api: NotificationMediaHostApi, holder: Any) {
        api.getElapsedTimeView(holder)?.let(::restoreTimeText)
        api.getTotalTimeView(holder)?.let(::restoreTimeText)
        api.getActionButtons(holder).forEach(::restoreActionButton)
        api.getSeamlessContainer(holder)?.let { container ->
            appIdentityStates.remove(container)?.restore()
        }
    }

    private fun restoreTimeText(view: TextView) {
        timeTextStates.remove(view)?.restore()
    }

    private fun restoreActionButton(button: ImageButton) {
        MediaActionIconScaler.restore(button)
        actionButtonPaddingStates.remove(button)?.restore(button)
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

    private data class TimeTextState(
        val view: TextView,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int,
        val minEms: Int,
        val gravity: Int,
        val textSizePx: Float
    ) {
        fun apply(horizontalGravity: Int) {
            view.setPadding(0, paddingTop, 0, paddingBottom)
            view.minEms = 0
            view.gravity = horizontalGravity
        }

        fun restore() {
            view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
            view.minEms = minEms
            view.gravity = gravity
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
        }

        companion object {
            fun capture(view: TextView): TimeTextState {
                return TimeTextState(
                    view = view,
                    paddingLeft = view.paddingLeft,
                    paddingTop = view.paddingTop,
                    paddingRight = view.paddingRight,
                    paddingBottom = view.paddingBottom,
                    minEms = view.minEms,
                    gravity = view.gravity,
                    textSizePx = view.textSize
                )
            }
        }
    }

    private data class AppIdentityState(
        val container: ViewGroup,
        val sourceIcon: ImageView,
        val sourceControl: View? = (sourceIcon.parent as? View)?.takeIf {
            it !== container
        },
        val originalSourceIconVisibility: Int = sourceIcon.visibility,
        val originalSourceControlVisibility: Int? = sourceControl?.visibility,
        val originalContainerVisibility: Int = container.visibility,
        val originalContainerBackground: Drawable? = container.background,
        val originalContainerGravity: Int? = (container as? LinearLayout)?.gravity,
        var row: LinearLayout? = null,
        var icon: ImageView? = null,
        var label: TextView? = null
    ) {
        fun apply(drawable: Drawable, appName: CharSequence, textColor: Int) {
            val identityRow = row ?: createRow().also { created ->
                row = created
            }
            val identityIcon = requireNotNull(icon)
            val identityLabel = requireNotNull(label)
            val copy = runCatching {
                drawable.constantState
                    ?.newDrawable(identityIcon.resources, identityIcon.context.theme)
                    ?.mutate()
            }.getOrNull() ?: drawable
            identityIcon.setImageDrawable(copy)
            identityIcon.imageTintList = null
            identityLabel.text = appName
            identityLabel.setTextColor(textColor)
            identityLabel.alpha = APP_NAME_ALPHA
            sourceControl?.visibility = View.GONE
            sourceIcon.visibility = View.GONE
            container.background = null
            (container as? LinearLayout)?.gravity = Gravity.CENTER_VERTICAL
            container.visibility = View.VISIBLE
            identityRow.visibility = View.VISIBLE
        }

        fun applyTextColor(textColor: Int) {
            label?.setTextColor(textColor)
            label?.invalidate()
        }

        fun restore() {
            row?.let { identityRow ->
                (identityRow.parent as? ViewGroup)?.removeView(identityRow)
            }
            row = null
            icon = null
            label = null
            sourceControl?.let { control ->
                originalSourceControlVisibility?.let { visibility ->
                    control.visibility = visibility
                }
            }
            sourceIcon.visibility = originalSourceIconVisibility
            container.background = originalContainerBackground
            originalContainerGravity?.let { gravity ->
                (container as? LinearLayout)?.gravity = gravity
            }
            container.visibility = originalContainerVisibility
        }

        fun hideSource() {
            row?.visibility = View.GONE
            sourceControl?.visibility = View.GONE
            sourceIcon.visibility = View.GONE
            container.background = originalContainerBackground
            container.visibility = View.GONE
        }

        private fun createRow(): LinearLayout {
            val context = container.context
            val iconSize = (APP_ICON_SIZE_DP * context.resources.displayMetrics.density)
                .roundToInt()
            val labelGap = (APP_NAME_GAP_DP * context.resources.displayMetrics.density)
                .roundToInt()
            val identityRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = if (container is LinearLayout) {
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                } else {
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
            val identityIcon = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            identityRow.addView(identityIcon, LinearLayout.LayoutParams(iconSize, iconSize))
            val identityLabel = TextView(context).apply {
                textSize = APP_NAME_TEXT_SIZE_SP
                gravity = Gravity.CENTER_VERTICAL
                isSingleLine = true
                includeFontPadding = false
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            identityRow.addView(
                identityLabel,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                ).apply { marginStart = labelGap }
            )
            icon = identityIcon
            label = identityLabel
            container.addView(identityRow)
            return identityRow
        }
    }
}
