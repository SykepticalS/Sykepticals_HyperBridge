package com.sykeptical.hyperpop.xposed.hooks

import android.view.View
import android.view.ViewGroup
import java.util.Collections
import java.util.WeakHashMap

/**
 * Extends the existing media background down to the expanded card bottom so
 * Ambient Flow continues through the mini-window bar. It does not add a
 * second flow view. The island outline remains the only rounded clip.
 */
internal object ExpandedHandleSurface {
    private const val FLOW_TAG = "hyperpop.media.island_expanded_media_custom_flow"
    private const val HOLDER_TAG = "hyperpop.media.island_media_holder_background"
    private const val MAX_DEPTH = 8

    private class Tracked(
        val clipToOutline: Boolean,
        val bottomMargin: Int,
        var appliedBottom: Int = Int.MIN_VALUE,
    )

    private val tracked = Collections.synchronizedMap(WeakHashMap<View, Tracked>())
    private val unclipped = Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())

    fun align(content: View, island: View, cardBottom: Int) {
        if (cardBottom <= 0) return
        val found = ArrayList<View>(4)
        walk(content, 0, found)
        val live = found.toSet()
        val stale = synchronized(tracked) { tracked.keys.filter { it !in live } }
        stale.forEach { releaseView(it) }
        found.forEach { view -> extend(view, island, cardBottom) }
    }

    fun release() {
        val views = synchronized(tracked) { tracked.keys.toList() }
        views.forEach { releaseView(it) }
        val groups = synchronized(unclipped) { unclipped.entries.toList() }
        groups.forEach { (group, clipChildren) ->
            if (group.parent != null) group.clipChildren = clipChildren
        }
        synchronized(unclipped) { unclipped.clear() }
    }

    private fun extend(view: View, island: View, cardBottom: Int) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val state = tracked.getOrPut(view) { Tracked(view.clipToOutline, params.bottomMargin) }
        if (state.appliedBottom == cardBottom || !view.isAttachedToWindow || view.height <= 0) return
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val already = (state.bottomMargin - params.bottomMargin).coerceAtLeast(0)
        val deficit = cardBottom - (location[1] + view.height - already)
        state.appliedBottom = cardBottom
        if (deficit <= 1) return
        unclipUpTo(view, island)
        view.clipToOutline = false
        params.bottomMargin = state.bottomMargin - deficit
        view.layoutParams = params
    }

    private fun releaseView(view: View) {
        val state = tracked.remove(view) ?: return
        view.clipToOutline = state.clipToOutline
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null && params.bottomMargin != state.bottomMargin) {
            params.bottomMargin = state.bottomMargin
            view.layoutParams = params
        }
    }

    private fun unclipUpTo(view: View, island: View) {
        var parent = view.parent as? ViewGroup
        while (parent != null && parent !== island) {
            if (parent !in unclipped) {
                unclipped[parent] = parent.clipChildren
                parent.clipChildren = false
            }
            parent = parent.parent as? ViewGroup
        }
    }

    private fun walk(view: View, depth: Int, found: MutableList<View>) {
        if (depth > MAX_DEPTH) return
        if (isBackground(view)) found += view
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            walk(group.getChildAt(index), depth + 1, found)
        }
    }

    private fun isBackground(view: View): Boolean {
        val tag = view.tag as? String
        if (tag == FLOW_TAG || tag == HOLDER_TAG) return true
        return view.javaClass.name.contains("MusicBgView")
    }
}
