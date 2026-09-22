package com.lidesheng.hyperlyric.root.mediacard.notification.layout

import android.content.Context
import kotlin.math.roundToInt

internal fun NotificationMediaConstraintBridge.anchorActionRowToBottom(
    layout: Any,
    action0: Int,
    context: Context,
    bottomMarginDp: Float
) {
    clear(layout, action0, NotificationMediaConstraintSide.TOP)
    clear(layout, action0, NotificationMediaConstraintSide.BOTTOM)
    connect(
        layout,
        action0,
        NotificationMediaConstraintSide.BOTTOM,
        0,
        NotificationMediaConstraintSide.BOTTOM
    )
    setMargin(
        layout,
        action0,
        NotificationMediaConstraintSide.BOTTOM,
        context.dp(bottomMarginDp)
    )
}

internal fun NotificationMediaConstraintBridge.clearAll(layout: Any, viewId: Int) {
    clear(layout, viewId, NotificationMediaConstraintSide.LEFT)
    clear(layout, viewId, NotificationMediaConstraintSide.RIGHT)
    clear(layout, viewId, NotificationMediaConstraintSide.START)
    clear(layout, viewId, NotificationMediaConstraintSide.END)
    clear(layout, viewId, NotificationMediaConstraintSide.TOP)
    clear(layout, viewId, NotificationMediaConstraintSide.BOTTOM)
}

internal fun NotificationMediaConstraintBridge.clearVertical(layout: Any, viewId: Int) {
    clear(layout, viewId, NotificationMediaConstraintSide.TOP)
    clear(layout, viewId, NotificationMediaConstraintSide.BOTTOM)
}

internal fun NotificationMediaConstraintBridge.clearHorizontal(layout: Any, viewId: Int) {
    clear(layout, viewId, NotificationMediaConstraintSide.LEFT)
    clear(layout, viewId, NotificationMediaConstraintSide.RIGHT)
    clear(layout, viewId, NotificationMediaConstraintSide.START)
    clear(layout, viewId, NotificationMediaConstraintSide.END)
}

internal fun NotificationMediaConstraintBridge.clearMargins(layout: Any, viewId: Int) {
    setMargin(layout, viewId, NotificationMediaConstraintSide.LEFT, 0)
    setMargin(layout, viewId, NotificationMediaConstraintSide.RIGHT, 0)
    setMargin(layout, viewId, NotificationMediaConstraintSide.START, 0)
    setMargin(layout, viewId, NotificationMediaConstraintSide.END, 0)
    setMargin(layout, viewId, NotificationMediaConstraintSide.TOP, 0)
    setMargin(layout, viewId, NotificationMediaConstraintSide.BOTTOM, 0)
}

internal fun Context.dp(value: Float): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}

@Suppress("DiscouragedApi")
internal fun Context.dimenPx(name: String, fallbackDp: Float): Int {
    val id = resources.getIdentifier(name, "dimen", packageName)
    return if (id != 0) resources.getDimensionPixelSize(id) else dp(fallbackDp)
}
