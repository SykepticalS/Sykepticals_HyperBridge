package com.lidesheng.hyperlyric.root.mediacard.island.layout.oneui

import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Replaces the native output-device affordance with the One UI application
 * identity row.  One UI intentionally does not move the output switch into
 * Action4; its compact action row remains five media actions only.
 */
internal object IslandExpandedMediaOneUiAccessoryController {
    private val states = Collections.synchronizedMap(
        WeakHashMap<ViewGroup, State>()
    )

    fun apply(
        views: IslandExpandedMediaOneUiAccessoryViews,
        appIcon: Drawable?,
        appName: CharSequence?,
        textColor: Int?
    ) {
        val state = synchronized(states) {
            states[views.player] ?: State.capture(views).also { states[views.player] = it }
        }
        if (appIcon == null || appName.isNullOrBlank()) {
            state.hideSource()
        } else {
            state.apply(appIcon, appName, textColor)
        }
    }

    fun applyTextColor(views: IslandExpandedMediaOneUiAccessoryViews, color: Int) {
        states[views.player]?.applyTextColor(color)
    }

    fun restore(views: IslandExpandedMediaOneUiAccessoryViews) {
        states.remove(views.player)?.restore()
    }

    fun applyToFakeView(
        fakeExpandedView: View,
        reference: IslandExpandedMediaOneUiAccessoryViews,
        appIcon: Drawable?,
        appName: CharSequence?,
        textColor: Int?
    ) {
        val fakeRoot = fakeExpandedView as? ViewGroup ?: return
        val container = fakeRoot.findViewById<View>(reference.container.id) as? ViewGroup
            ?: return
        val sourceIcon = fakeRoot.findViewById<View>(reference.sourceIcon.id) as? ImageView
            ?: return
        val appIconView = fakeRoot.findViewById<View>(reference.appIcon.id) as? ImageView
            ?: return
        val sourceButton = reference.sourceButton?.id?.takeIf { it != 0 }?.let { id ->
            fakeRoot.findViewById<View>(id)
        }
        apply(
            views = IslandExpandedMediaOneUiAccessoryViews(
                player = fakeRoot,
                container = container,
                sourceIcon = sourceIcon,
                sourceButton = sourceButton,
                appIcon = appIconView
            ),
            appIcon = appIcon,
            appName = appName,
            textColor = textColor
        )
    }

    fun clear() {
        states.values.toList().forEach(State::restore)
        states.clear()
    }

    private data class State(
        val views: IslandExpandedMediaOneUiAccessoryViews,
        val originalSourceVisibility: Int,
        val originalSourceButtonVisibility: Int?,
        val originalContainerVisibility: Int,
        val originalContainerBackground: Drawable?,
        val originalContainerGravity: Int?,
        val originalContainerClickable: Boolean,
        val originalContainerFocusable: Boolean,
        var row: LinearLayout? = null,
        var icon: ImageView? = null,
        var label: TextView? = null
    ) {
        fun apply(drawable: Drawable, appName: CharSequence, textColor: Int?) {
            val identityRow = row ?: createRow().also { row = it }
            val identityIcon = requireNotNull(icon)
            val identityLabel = requireNotNull(label)
            identityIcon.setImageDrawable(copyDrawable(drawable, identityIcon))
            identityIcon.imageTintList = null
            identityLabel.text = appName
            textColor?.let(identityLabel::setTextColor)
            identityLabel.alpha = IslandExpandedMediaOneUiMetrics.APP_NAME_ALPHA
            views.sourceButton?.visibility = View.GONE
            views.sourceIcon.visibility = View.GONE
            views.container.background = null
            views.container.isClickable = false
            views.container.isFocusable = false
            (views.container as? LinearLayout)?.gravity = Gravity.CENTER_VERTICAL
            views.container.visibility = View.VISIBLE
            identityRow.visibility = View.VISIBLE
        }

        fun applyTextColor(textColor: Int) {
            label?.setTextColor(textColor)
            label?.invalidate()
        }

        fun hideSource() {
            row?.visibility = View.GONE
            views.sourceButton?.visibility = View.GONE
            views.sourceIcon.visibility = View.GONE
            views.container.visibility = View.GONE
        }

        fun restore() {
            row?.let { (it.parent as? ViewGroup)?.removeView(it) }
            row = null
            icon = null
            label = null
            views.sourceButton?.let { button ->
                originalSourceButtonVisibility?.let { button.visibility = it }
            }
            views.sourceIcon.visibility = originalSourceVisibility
            views.container.background = originalContainerBackground
            views.container.isClickable = originalContainerClickable
            views.container.isFocusable = originalContainerFocusable
            originalContainerGravity?.let { gravity ->
                (views.container as? LinearLayout)?.gravity = gravity
            }
            views.container.visibility = originalContainerVisibility
        }

        private fun createRow(): LinearLayout {
            val context = views.container.context
            val density = context.resources.displayMetrics.density
            val iconSize = (IslandExpandedMediaOneUiMetrics.APP_ICON_SIZE_DP * density)
                .roundToInt()
            val labelGap = (IslandExpandedMediaOneUiMetrics.APP_NAME_GAP_DP * density)
                .roundToInt()
            val identityRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val identityIcon = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            identityRow.addView(identityIcon, LinearLayout.LayoutParams(iconSize, iconSize))
            val identityLabel = TextView(context).apply {
                textSize = IslandExpandedMediaOneUiMetrics.APP_NAME_TEXT_SIZE_SP
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
            views.container.addView(identityRow)
            return identityRow
        }

        private fun copyDrawable(drawable: Drawable, target: ImageView): Drawable {
            return runCatching {
                drawable.constantState
                    ?.newDrawable(target.resources, target.context.theme)
                    ?.mutate()
            }.getOrNull() ?: drawable
        }

        companion object {
            fun capture(views: IslandExpandedMediaOneUiAccessoryViews): State {
                return State(
                    views = views,
                    originalSourceVisibility = views.sourceIcon.visibility,
                    originalSourceButtonVisibility = views.sourceButton?.visibility,
                    originalContainerVisibility = views.container.visibility,
                    originalContainerBackground = views.container.background,
                    originalContainerGravity = (views.container as? LinearLayout)?.gravity,
                    originalContainerClickable = views.container.isClickable,
                    originalContainerFocusable = views.container.isFocusable
                )
            }
        }
    }
}

internal data class IslandExpandedMediaOneUiAccessoryViews(
    val player: ViewGroup,
    val container: ViewGroup,
    val sourceIcon: ImageView,
    val sourceButton: View?,
    val appIcon: ImageView
)
