package com.lidesheng.hyperlyric.root.mediacard.notification.layout.miui

import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.lidesheng.hyperlyric.root.mediacard.layout.common.MediaLayoutSharedMetrics
import com.lidesheng.hyperlyric.root.mediacard.notification.NotificationMediaHostApi
import com.lidesheng.hyperlyric.root.mediacard.notification.layout.dimenPx
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

internal object NotificationMediaMiuiStyle {
    const val ACTION_BUTTON_SCALE = MediaLayoutSharedMetrics.COMPACT_ACTION_SCALE
    const val HORIZONTAL_MARGIN_DP = 21f

    private const val TIME_TEXT_SIZE_SP = 8f
    private const val APP_NAME_TEXT_SIZE_SP = 10f

    private val actionButtonPaddingStates =
        Collections.synchronizedMap(WeakHashMap<ImageButton, ActionButtonPaddingState>())
    private val timeTextStates =
        Collections.synchronizedMap(WeakHashMap<TextView, TimeTextState>())
    private val appNameStates =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, AppNameState>())

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

        val title = api.getTitleText(holder) ?: return
        val player = api.getPlayer(holder) ?: title.parent as? ViewGroup ?: return
        // Prefer the controller's post-bind field. A reused native controller
        // can receive a late bind callback whose argument is no longer the
        // data that HyperOS kept as its active MediaData.
        val currentMediaData = api.getMediaData(controller) ?: mediaData
        val appName = api.getApplicationName(currentMediaData, player.context)
        if (appName.isNullOrBlank()) {
            appNameStates.remove(player)?.restore()
            return
        }
        val reference = api.getArtistText(holder) ?: title
        val state = appNameStates[player]
            ?: AppNameState.create(player, title)?.also { appNameStates[player] = it }
            ?: return
        state.apply(appName, reference)
    }

    fun refreshAppNameColor(api: NotificationMediaHostApi, holder: Any) {
        val title = api.getTitleText(holder) ?: return
        val player = api.getPlayer(holder) ?: title.parent as? ViewGroup ?: return
        appNameStates[player]?.applyColor(api.getArtistText(holder) ?: title)
    }

    fun applyActionButton(button: ImageButton) {
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
        val title = api.getTitleText(holder)
        val player = api.getPlayer(holder) ?: title?.parent as? ViewGroup
        player?.let { appNameStates.remove(it)?.restore() }
    }

    private fun restoreTimeText(view: TextView) {
        timeTextStates.remove(view)?.restore()
    }

    private fun restoreActionButton(button: ImageButton) {
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
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, TIME_TEXT_SIZE_SP)
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

    private data class AppNameState(
        val label: TextView,
        val titleConstraintState: TitleVerticalConstraintState,
        val titleGap: Int
    ) {
        fun apply(appName: CharSequence, reference: TextView) {
            label.text = appName
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, APP_NAME_TEXT_SIZE_SP)
            applyColor(reference)
            label.visibility = View.VISIBLE
            titleConstraintState.connectBelow(label.id, titleGap)
        }

        fun applyColor(reference: TextView) {
            label.setTextColor(reference.currentTextColor)
            label.alpha = reference.alpha
            label.typeface = reference.typeface
            label.includeFontPadding = reference.includeFontPadding
            label.letterSpacing = reference.letterSpacing
            label.fontFeatureSettings = reference.fontFeatureSettings
            label.invalidate()
        }

        fun restore() {
            titleConstraintState.restore()
            (label.parent as? ViewGroup)?.removeView(label)
        }

        companion object {
            fun create(player: ViewGroup, title: TextView): AppNameState? = runCatching {
                val context = player.context
                val horizontalMargin =
                    (HORIZONTAL_MARGIN_DP * context.resources.displayMetrics.density).roundToInt()
                val topMargin = context.dimenPx("media_control_title_top_margin", 21f)
                val titleGap = context.dimenPx("header_artist_margin_top", 4f)
                val label = TextView(context).apply {
                    id = View.generateViewId()
                    textSize = APP_NAME_TEXT_SIZE_SP
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    isSingleLine = true
                    includeFontPadding = false
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val layoutParams = createLayoutParams(player, title)
                layoutParams.javaClass.getField("startToStart").setInt(layoutParams, 0)
                layoutParams.javaClass.getField("endToEnd").setInt(layoutParams, 0)
                layoutParams.javaClass.getField("topToTop").setInt(layoutParams, 0)
                layoutParams.apply {
                    marginStart = horizontalMargin
                    marginEnd = horizontalMargin
                    this.topMargin = topMargin
                }
                val titleConstraintState =
                    requireNotNull(TitleVerticalConstraintState.capture(title))
                player.addView(label, layoutParams)
                titleConstraintState.connectBelow(label.id, titleGap)
                AppNameState(label, titleConstraintState, titleGap)
            }.getOrNull()
        }
    }

    private fun createLayoutParams(
        player: ViewGroup,
        title: TextView
    ): ViewGroup.MarginLayoutParams {
        val paramsClass = title.layoutParams?.javaClass
            ?: Class.forName(
                "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams",
                false,
                player.javaClass.classLoader
            )
        return paramsClass.getConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).newInstance(0, ViewGroup.LayoutParams.WRAP_CONTENT)
            as ViewGroup.MarginLayoutParams
    }

    private data class TitleVerticalConstraintState(
        val title: TextView,
        val originalValues: Map<String, Int>,
        val originalTopMargin: Int
    ) {
        fun connectBelow(anchorId: Int, topMargin: Int) {
            updateLayoutParams { params, fields ->
                fields.forEach { (name, field) ->
                    field.setInt(params, if (name == "topToBottom") anchorId else -1)
                }
                params.topMargin = topMargin
            }
        }

        fun restore() {
            updateLayoutParams { params, fields ->
                fields.forEach { (name, field) ->
                    originalValues[name]?.let { value -> field.setInt(params, value) }
                }
                params.topMargin = originalTopMargin
            }
        }

        private fun updateLayoutParams(
            block: (ViewGroup.MarginLayoutParams, Map<String, Field>) -> Unit
        ) {
            val params = title.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            val fields = constraintFields(params)
            block(params, fields)
            title.layoutParams = params
            title.requestLayout()
        }

        companion object {
            private val VERTICAL_FIELDS = listOf(
                "topToTop",
                "topToBottom",
                "bottomToTop",
                "bottomToBottom",
                "baselineToBaseline",
                "baselineToTop",
                "baselineToBottom"
            )

            fun capture(title: TextView): TitleVerticalConstraintState? = runCatching {
                val params = title.layoutParams as ViewGroup.MarginLayoutParams
                val fields = constraintFields(params)
                require(fields.containsKey("topToTop") && fields.containsKey("topToBottom"))
                TitleVerticalConstraintState(
                    title = title,
                    originalValues = fields.mapValues { (_, field) -> field.getInt(params) },
                    originalTopMargin = params.topMargin
                )
            }.getOrNull()

            private fun constraintFields(params: ViewGroup.MarginLayoutParams): Map<String, Field> {
                return VERTICAL_FIELDS.mapNotNull { name ->
                    runCatching { params.javaClass.getField(name) }
                        .getOrNull()
                        ?.let { field -> name to field }
                }.toMap()
            }
        }
    }
}
