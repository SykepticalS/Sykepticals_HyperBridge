package com.sykeptical.hyperpop.xposed.hooks

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup

/**
 * Hit rects that are larger than the visuals they belong to. A press is
 * delivered to the child whose expanded rect contains the down event.
 */
class CompositeTouchDelegate(
    private val host: ViewGroup,
) : TouchDelegate(Rect(), host) {
    data class Target(val bounds: Rect, val child: View)

    private val targets = ArrayList<Target>(4)
    private var active: View? = null

    fun replace(next: List<Target>) {
        targets.clear()
        next.filter { !it.bounds.isEmpty }.forEach { targets += it }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.toInt()
        val y = event.y.toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = targets.firstOrNull { it.bounds.contains(x, y) }?.child
            }
            MotionEvent.ACTION_CANCEL -> active = null
        }
        val child = active ?: return false
        val childBounds = Rect(0, 0, child.width, child.height)
        val shifted = runCatching {
            host.offsetDescendantRectToMyCoords(child, childBounds)
            true
        }.getOrDefault(false)
        if (!shifted) return false
        val copy = MotionEvent.obtain(event)
        copy.offsetLocation(-childBounds.left.toFloat(), -childBounds.top.toFloat())
        val handled = child.dispatchTouchEvent(copy)
        copy.recycle()
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            active = null
        }
        return handled
    }
}
