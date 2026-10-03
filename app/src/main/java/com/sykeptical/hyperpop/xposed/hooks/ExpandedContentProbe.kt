package com.sykeptical.hyperpop.xposed.hooks

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.sykeptical.hyperpop.service.animation.expanded.ContentCluster
import com.sykeptical.hyperpop.service.animation.expanded.ContentLeaf
import com.sykeptical.hyperpop.service.animation.expanded.ContentLeafKind
import com.sykeptical.hyperpop.service.animation.expanded.ContentLeafRole
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedContentProfile
import com.sykeptical.hyperpop.service.animation.expanded.IslandRect

/**
 * Reads the expanded content view once, before its margin is changed.
 * Full-bleed backgrounds are decorative so they do not push text down.
 * A missing layout falls open to the conservative gap.
 */
object ExpandedContentProbe {
    private const val MAX_VIEWS = 80
    private const val MAX_DEPTH = 8
    private const val FLOW_TAG = "hyperpop.media.island_expanded_media_custom_flow"
    private const val HOLDER_BACKGROUND_TAG = "hyperpop.media.island_media_holder_background"

    fun capture(content: View): ExpandedContentProfile? {
        val margin = (content.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        if ((content.width <= 0 || content.height <= 0) && !measure(content)) return null
        if (content.width <= 0 || content.height <= 0) return null
        val leaves = ArrayList<ContentLeaf>(16)
        val seen = intArrayOf(0)
        walk(content, content, 0, leaves, seen)
        val shown = leaves.mapNotNull { it.visibleWithin(content.width, content.height) }
        if (shown.isEmpty()) return null
        val clusters = (content as? ViewGroup)?.let { group ->
            (0 until group.childCount).mapNotNull { index ->
                val child = group.getChildAt(index)
                val bounds = relativeBounds(child, content) ?: return@mapNotNull null
                ContentCluster(index, bounds, decorative(child, content))
            }
        }.orEmpty()
        return ExpandedContentProfile(
            nativeTopMarginPx = margin,
            contentWidthPx = content.width,
            contentHeightPx = content.height,
            leaves = shown,
            clusters = clusters,
        )
    }

    private fun measure(content: View): Boolean {
        val width = content.layoutParams?.width ?: return false
        val height = content.layoutParams?.height ?: return false
        if (width <= 0 || height <= 0) return false
        return runCatching {
            content.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            content.layout(
                content.left,
                content.top,
                content.left + content.measuredWidth,
                content.top + content.measuredHeight,
            )
            content.width > 0 && content.height > 0
        }.getOrDefault(false)
    }

    private fun walk(
        view: View,
        root: View,
        depth: Int,
        leaves: MutableList<ContentLeaf>,
        seen: IntArray,
    ) {
        if (view !== root) {
            if (seen[0] >= MAX_VIEWS || depth > MAX_DEPTH) return
            seen[0] += 1
            val kind = kind(view, root)
            val bounds = relativeBounds(view, root)
            val group = view as? ViewGroup
            val leaf = kind == ContentLeafKind.DECORATIVE ||
                kind == ContentLeafKind.TEXT ||
                kind == ContentLeafKind.INTERACTIVE ||
                group == null ||
                group.childCount == 0
            if (bounds != null && leaf) leaves += ContentLeaf(bounds, kind, role(view))
            if (kind == ContentLeafKind.DECORATIVE || group == null) return
            for (index in 0 until group.childCount) {
                walk(group.getChildAt(index), root, depth + 1, leaves, seen)
            }
            return
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            walk(group.getChildAt(index), root, depth + 1, leaves, seen)
        }
    }

    private fun kind(view: View, root: View): ContentLeafKind {
        if (decorative(view, root)) return ContentLeafKind.DECORATIVE
        val name = view.javaClass.name
        if (view is TextView) return ContentLeafKind.TEXT
        if (view.isClickable || name.endsWith("Button") || name.contains("ImageButton")) {
            return ContentLeafKind.INTERACTIVE
        }
        return ContentLeafKind.PLAIN
    }

    private fun role(view: View): ContentLeafRole {
        val name = entryName(view) ?: return ContentLeafRole.UNKNOWN
        return when (name) {
            "focus_title", "header_title" -> ContentLeafRole.PRIMARY_TITLE
            "focus_content", "header_artist", "chronometer", "focus_sub_content" -> ContentLeafRole.SECONDARY_TEXT
            "focus_profile", "focus_icon_container", "album_art", "album_art_image" -> ContentLeafRole.AVATAR
            "focus_button_icon1", "focus_button_icon2", "focus_button_icon3" -> ContentLeafRole.CALL_CONTROL
            "focus_button_title" -> ContentLeafRole.ACTION_PILL
            "media_progress_bar" -> ContentLeafRole.PROGRESS
            else -> ContentLeafRole.UNKNOWN
        }
    }

    private fun entryName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
    }

    private fun decorative(view: View, root: View): Boolean {
        if (view === root) return false
        val name = view.javaClass.name
        val tag = view.tag as? String
        if (name.contains("MusicBgView") || name.contains("MediaFlowBackgroundView")) return true
        if (tag == FLOW_TAG || tag == HOLDER_BACKGROUND_TAG) return true
        if (root.width <= 0 || root.height <= 0) return false
        val covers = view.width >= root.width * 0.9f && view.height >= root.height * 0.9f
        return covers && view !is TextView && !view.isClickable
    }

    private fun relativeBounds(view: View, root: View): IslandRect? {
        if (view.width <= 0 || view.height <= 0) return null
        var x = 0
        var y = 0
        var current: View? = view
        while (current != null && current !== root) {
            x += current.left
            y += current.top
            current = current.parent as? View
        }
        if (current !== root) return null
        return IslandRect(x, y, x + view.width, y + view.height)
    }
}
