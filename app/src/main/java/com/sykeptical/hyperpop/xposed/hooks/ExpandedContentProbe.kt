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
 * Reads the expanded content view before its margin is changed.
 * Full-bleed backgrounds are decorative so they do not push text down.
 * A missing layout falls open to the conservative gap.
 *
 * Xiaomi lays a freshly bound Focus template out only after the expansion has
 * started. A view with no size is therefore measured here the way Xiaomi will:
 * at the size its layout params already carry, or at its natural height inside
 * the native width when Xiaomi has not resolved one yet. A view that is laid
 * out but still has a visible bound view without a size, such as a late action
 * module, is laid out again so the reading holds the complete template.
 *
 * [baselineHeightPx] is the saved native height. The probe measures with
 * AT_MOST so a short exact height cannot hide a call control, then keeps that
 * baseline when the natural result would shrink a root Xiaomi already sized
 * (the media max height).
 */
object ExpandedContentProbe {
    private const val MAX_VIEWS = 80
    private const val MAX_DEPTH = 8
    private const val FLOW_TAG = "hyperpop.media.island_expanded_media_custom_flow"
    private const val HOLDER_BACKGROUND_TAG = "hyperpop.media.island_media_holder_background"

    /**
     * [widthHintPx] is the native expanded width, used only when the layout params carry none.
     * [baselineHeightPx] `>= 0` remeasures from that saved height instead of an exact height
     * Xiaomi just wrote. Negative keeps the view's current layout.
     */
    fun capture(
        content: View,
        widthHintPx: Int = 0,
        baselineHeightPx: Int = -1,
    ): ExpandedContentProfile? {
        val margin = (content.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        val rewrite = baselineHeightPx >= 0
        val prior = if (content.width > 0 && content.height > 0) shownLeaves(content) else ShownLeaves(emptyList(), false)
        val overflow = prior.leaves.any { it.holdsBottomAction() && it.bounds.bottom > content.height }
        // An already laid-out template is Xiaomi's height. Remeasure only to
        // grow for a control that sticks out or a module that has no size yet.
        if ((content.width <= 0 || content.height <= 0 || (rewrite && (overflow || prior.unlaid))) &&
            !measure(content, widthHintPx, baselineHeightPx)
        ) {
            if (content.width <= 0 || content.height <= 0) return null
        }
        if (content.width <= 0 || content.height <= 0) return null
        var shown = shownLeaves(content).leaves
        if (shown.isEmpty() && content.isLayoutRequested && measure(content, widthHintPx, baselineHeightPx)) {
            shown = shownLeaves(content).leaves
        }
        shown = preserveLowerControls(shown, prior.leaves)
        if (shown.isEmpty()) return null
        val clusters = (content as? ViewGroup)?.let { group ->
            (0 until group.childCount).mapNotNull { index ->
                val child = group.getChildAt(index)
                if (child.visibility != View.VISIBLE) return@mapNotNull null
                val bounds = relativeBounds(child, content) ?: return@mapNotNull null
                ContentCluster(index, bounds, decorative(child, content))
            }
        }.orEmpty()
        val leafBottom = shown
            .filter { it.kind != ContentLeafKind.DECORATIVE && !it.bounds.isEmpty() }
            .maxOfOrNull { it.bounds.bottom }
            ?: content.height
        return ExpandedContentProfile(
            nativeTopMarginPx = margin,
            contentWidthPx = content.width,
            contentHeightPx = maxOf(content.height, leafBottom),
            leaves = shown,
            clusters = clusters,
        )
    }

    private data class ShownLeaves(val leaves: List<ContentLeaf>, val unlaid: Boolean)

    private fun shownLeaves(content: View): ShownLeaves {
        val leaves = ArrayList<ContentLeaf>(16)
        val unlaid = intArrayOf(0)
        walk(content, content, 0, leaves, intArrayOf(0), unlaid)
        if (content.width <= 0 || content.height <= 0) return ShownLeaves(emptyList(), unlaid[0] > 0)
        return ShownLeaves(
            leaves.mapNotNull { it.visibleWithin(content.width, content.height) },
            unlaid[0] > 0,
        )
    }

    /** A remeasure that pulls actions up must not hide the ones already below the root. */
    private fun preserveLowerControls(
        measured: List<ContentLeaf>,
        prior: List<ContentLeaf>,
    ): List<ContentLeaf> {
        val measuredBottom = measured
            .filter { it.kind != ContentLeafKind.DECORATIVE && !it.bounds.isEmpty() }
            .maxOfOrNull { it.bounds.bottom }
            ?: 0
        val extras = prior.filter { leaf ->
            leaf.holdsBottomAction() && leaf.bounds.bottom > measuredBottom
        }
        return if (extras.isEmpty()) measured else measured + extras
    }

    private fun measure(content: View, widthHintPx: Int, baselineHeightPx: Int): Boolean {
        val params = content.layoutParams
        val width = params?.width?.takeIf { it > 0 }
            ?: widthHintPx.takeIf { it > 0 }
            ?: content.width.takeIf { it > 0 }
            ?: return false
        val limit = content.resources.displayMetrics.heightPixels
        if (limit <= 0) return false
        val exactHeight = params?.height?.takeIf { it > 0 }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        return runCatching {
            val heightSpec = if (baselineHeightPx >= 0 || exactHeight == null) {
                View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST)
            } else {
                View.MeasureSpec.makeMeasureSpec(exactHeight, View.MeasureSpec.EXACTLY)
            }
            content.measure(widthSpec, heightSpec)
            if (baselineHeightPx >= 0 || exactHeight == null) {
                val desired = content.measuredHeight
                val floor = maxOf(baselineHeightPx, exactHeight ?: 0, content.height)
                val exploded = desired <= 0 || desired >= (limit * 0.85f).toInt()
                val target = when {
                    exploded -> floor.takeIf { it > 0 } ?: return false
                    desired > floor -> desired
                    floor > 0 -> floor
                    else -> desired
                }
                if (target != desired) {
                    content.measure(
                        widthSpec,
                        View.MeasureSpec.makeMeasureSpec(target, View.MeasureSpec.EXACTLY),
                    )
                }
            }
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
        unlaid: IntArray,
    ) {
        if (view !== root) {
            if (!shownInside(view, root)) return
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
            if (bounds != null && leaf) leaves += ContentLeaf(bounds, kind, role(view, root))
            if (bounds == null && leaf && shownInside(view, root)) unlaid[0] += 1
            if (kind == ContentLeafKind.DECORATIVE || group == null) return
            for (index in 0 until group.childCount) {
                walk(group.getChildAt(index), root, depth + 1, leaves, seen, unlaid)
            }
            return
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            walk(group.getChildAt(index), root, depth + 1, leaves, seen, unlaid)
        }
    }

    /** Visible itself and through every parent up to the content root. */
    private fun shownInside(view: View, root: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            if (current === root) return true
            current = current.parent as? View
        }
        return false
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

    private fun role(view: View, root: View): ContentLeafRole {
        var current: View? = view
        var depth = 0
        while (current != null && depth < 8) {
            val named = entryName(current)?.let(::roleFor)
            if (named != null && named != ContentLeafRole.UNKNOWN) return named
            if (current === root) break
            current = current.parent as? View
            depth += 1
        }
        return ContentLeafRole.UNKNOWN
    }

    private fun roleFor(name: String): ContentLeafRole {
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
