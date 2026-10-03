package com.sykeptical.hyperpop.xposed.hooks

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.sykeptical.hyperpop.service.animation.expanded.IslandRect

/**
 * Debug-only geometry dump. Silent unless `debug.hyperpop.layoutprobe` is `1`.
 * One line per expanded content change, capped so a stuck island cannot flood logcat.
 */
object ExpandedLayoutProbe {
    private const val TAG = "HyperPopLayout"
    private const val PROP = "debug.hyperpop.layoutprobe"
    private const val MAX_VIEWS = 80
    private const val MAX_DUMPS = 24

    private var dumps = 0
    private var lastSignature = 0

    fun capture(content: View, card: IslandRect, cutout: IslandRect, displayCutoutWidth: Int) {
        if (dumps >= MAX_DUMPS || !enabled()) return
        val signature = content.width * 31 + content.height + cutout.left + card.bottom
        if (signature == lastSignature) return
        lastSignature = signature
        dumps += 1
        val lines = ArrayList<String>(32)
        lines += "card=$card cutout=$cutout displayCutoutWidth=$displayCutoutWidth " +
            "content=${content.javaClass.simpleName} ${content.width}x${content.height}"
        walk(content, content, 0, lines)
        Log.d(TAG, lines.joinToString("\n"))
    }

    private fun walk(view: View, root: View, depth: Int, lines: MutableList<String>) {
        if (lines.size >= MAX_VIEWS || depth > 8) return
        if (view !== root) lines += describe(view, root, depth)
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            walk(group.getChildAt(index), root, depth + 1, lines)
        }
    }

    private fun describe(view: View, root: View, depth: Int): String {
        val name = if (view.id != View.NO_ID) {
            runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
        } else {
            null
        } ?: view.javaClass.simpleName
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        return "  ".repeat(depth) +
            "$name global=${location[0]},${location[1]},${location[0] + view.width},${location[1] + view.height} " +
            "size=${view.width}x${view.height} pad=${view.paddingLeft},${view.paddingTop},${view.paddingRight},${view.paddingBottom} " +
            "margin=${params?.leftMargin ?: 0},${params?.topMargin ?: 0},${params?.rightMargin ?: 0},${params?.bottomMargin ?: 0} " +
            "trans=${view.translationX},${view.translationY} scale=${view.scaleX},${view.scaleY} " +
            "vis=${view.visibility} parent=${(view.parent as? View)?.javaClass?.simpleName}"
    }

    private fun enabled(): Boolean = runCatching {
        val type = Class.forName("android.os.SystemProperties")
        val get = type.getMethod("get", String::class.java, String::class.java)
        get.invoke(null, PROP, "0") == "1"
    }.getOrDefault(false)
}
