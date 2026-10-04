package com.sykeptical.hyperpop.xposed.hooks

import android.view.View
import android.view.ViewGroup
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedMediaSurfacePolicy
import java.util.Collections
import java.util.WeakHashMap

/**
 * Stretches the real media background to the handle-aware card bottom.
 * The player itself is not resized, so control constraints stay put.
 * Ancestors that end with the player grow so the extended background and
 * the drag handle sit in the same rounded surface.
 *
 * The drag shows a second copy of that tree. [mirror] gives it the extension
 * already resolved for the real copy, with no window measurement of its own.
 */
internal object ExpandedMediaSurfaceApplicator {
    private const val FLOW_TAG = "hyperpop.media.island_expanded_media_custom_flow"
    private const val HOLDER_BACKGROUND_TAG = "hyperpop.media.island_media_holder_background"
    private const val MAX_DEPTH = 8
    private const val EXPANDED_VIEW = "DynamicIslandExpandedView"

    private val realSurfaces = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val shownSurfaces = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val realHosts = Collections.synchronizedMap(WeakHashMap<View, HostState>())
    private val shownHosts = Collections.synchronizedMap(WeakHashMap<View, HostState>())

    @Volatile
    private var recordedExtension = 0

    @Volatile
    private var dragging = false

    fun apply(player: View, cardBottom: Int): Int {
        if (dragging && recordedExtension > 0) return recordedExtension
        if (player.width <= 0 || player.height <= 0) return recordedExtension
        val backgrounds = backgrounds(player)
        if (backgrounds.isEmpty()) return recordedExtension
        val measured = ExpandedMediaSurfacePolicy.extensionPx(cardBottom, hostBottom(backgrounds, player))
        val extension = ExpandedMediaSurfacePolicy.heldExtension(recordedExtension, measured, dragging)
        if (extension <= 0) {
            if (!dragging && (realSurfaces.isNotEmpty() || realHosts.isNotEmpty())) {
                recordedExtension = 0
                restoreMargins(realSurfaces)
                restoreHosts(realHosts)
            }
            return recordedExtension
        }
        recordedExtension = extension
        backgrounds.forEach { extend(it, extension, realSurfaces) }
        unclip(player)
        growHosts(player, cardBottom)
        return extension
    }

    /** Lengthen the drag copy by the extension already resolved for the real one. */
    fun mirror(shownRoot: View) {
        val extension = recordedExtension
        if (extension <= 0) return
        backgrounds(shownRoot).forEach { shown ->
            extend(shown, extension, shownSurfaces)
            unclipInside(shown, shownRoot)
        }
    }

    fun noteDragging(active: Boolean) {
        dragging = active
    }

    fun isDragging(): Boolean = dragging

    fun captureShown(): Set<View> {
        val keys = HashSet<View>(shownSurfaces.size + shownHosts.size)
        keys += shownSurfaces.keys
        keys += shownHosts.keys
        return keys
    }

    fun restoreReal(endDrag: Boolean = true) {
        if (endDrag) dragging = false
        recordedExtension = 0
        restoreMargins(realSurfaces)
        restoreHosts(realHosts)
    }

    fun restoreShown(keys: Set<View>? = null) {
        if (keys == null) {
            restoreMargins(shownSurfaces)
            restoreHosts(shownHosts)
            return
        }
        restoreMargins(shownSurfaces, keys)
        restoreHosts(shownHosts, keys)
    }

    fun restore() {
        restoreReal()
        restoreShown()
    }

    /** Only surfaces under [scopes]. Another owner's extension stays applied. */
    fun restoreWithin(scopes: List<View>) {
        restoreMargins(realSurfaces, realSurfaces.keysWithin(scopes))
        restoreHosts(realHosts, realHosts.keysWithin(scopes))
        restoreMargins(shownSurfaces, shownSurfaces.keysWithin(scopes))
        restoreHosts(shownHosts, shownHosts.keysWithin(scopes))
    }

    private fun extend(surface: View, extension: Int, saved: MutableMap<View, Int>) {
        val params = surface.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val base = saved.getOrPut(surface) { params.bottomMargin }
        val margin = ExpandedMediaSurfacePolicy.bottomMargin(base, extension)
        if (params.bottomMargin == margin) return
        params.bottomMargin = margin
        surface.layoutParams = params
        surface.invalidateOutline()
    }

    private fun unclip(player: View) {
        val group = player as? ViewGroup ?: return
        realHosts.getOrPut(group) {
            HostState(
                height = group.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                clipChildren = group.clipChildren,
                clipToPadding = group.clipToPadding,
            )
        }
        group.clipChildren = false
        group.clipToPadding = false
    }

    private fun growHosts(player: View, cardBottom: Int) {
        var current = player.parent as? View
        var depth = 0
        // Hosts up to Xiaomi's expanded view are the island's own; beyond it they are shared ancestors.
        var owned = hasExpandedViewAncestor(player)
        while (current != null && depth < MAX_DEPTH) {
            depth += 1
            if (!owned && windowBottom(current) > cardBottom) break
            val group = current as? ViewGroup
            if (group != null) {
                realHosts.getOrPut(group) {
                    HostState(
                        height = group.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                        clipChildren = group.clipChildren,
                        clipToPadding = group.clipToPadding,
                    )
                }
                if (group.clipChildren || group.clipToPadding) {
                    group.clipChildren = false
                    group.clipToPadding = false
                }
                val target = ExpandedMediaSurfacePolicy.hostHeight(
                    windowTop(group),
                    cardBottom,
                    group.height,
                    group.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                    owned,
                )
                val params = group.layoutParams
                if (target != null && params != null) {
                    params.height = target
                    group.layoutParams = params
                    group.invalidateOutline()
                }
            }
            val name = current.javaClass.name
            if (name.contains(EXPANDED_VIEW)) owned = false
            if (name.contains("DynamicIslandBackgroundView") || name.contains("DynamicIslandWindow")) break
            current = current.parent as? View
        }
    }

    private fun hasExpandedViewAncestor(player: View): Boolean {
        var current = player.parent as? View
        var depth = 0
        while (current != null && depth < MAX_DEPTH) {
            if (current.javaClass.name.contains(EXPANDED_VIEW)) return true
            current = current.parent as? View
            depth += 1
        }
        return false
    }

    private fun backgrounds(root: View): List<View> {
        val found = ArrayList<View>(4)
        fun walk(view: View, depth: Int) {
            if (depth > MAX_DEPTH) return
            if (view !== root && isBackground(view)) found += view
            val group = view as? ViewGroup ?: return
            for (index in 0 until group.childCount) walk(group.getChildAt(index), depth + 1)
        }
        walk(root, 0)
        return found
    }

    private fun isBackground(view: View): Boolean {
        val tag = view.tag as? String
        if (tag == FLOW_TAG || tag == HOLDER_BACKGROUND_TAG) return true
        val name = view.javaClass.name
        return name.contains("MusicBgView") || name.contains("MediaFlowBackgroundView")
    }

    /**
     * The constraint parent of the background, not the content root. That parent
     * is not resized, so the gap does not collapse after the background grows.
     */
    private fun hostBottom(backgrounds: List<View>, player: View): Int {
        val parentBottom = backgrounds.mapNotNull { (it.parent as? View)?.let(::windowBottom) }.minOrNull()
        return parentBottom ?: windowBottom(player)
    }

    /** Lets the tail draw out of the player without opening the fake view's own clip. */
    private fun unclipInside(surface: View, limit: View) {
        var current = surface.parent as? View
        while (current != null && current !== limit) {
            val group = current as? ViewGroup
            if (group != null) {
                shownHosts.getOrPut(group) {
                    HostState(
                        height = group.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                        clipChildren = group.clipChildren,
                        clipToPadding = group.clipToPadding,
                    )
                }
                group.clipChildren = false
                group.clipToPadding = false
            }
            current = current.parent as? View
        }
    }

    private fun restoreMargins(saved: MutableMap<View, Int>, keys: Set<View>? = null) {
        val entries = saved.entries.filter { keys == null || it.key in keys }
        entries.forEach { (view, baseMargin) ->
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null && params.bottomMargin != baseMargin) {
                params.bottomMargin = baseMargin
                view.layoutParams = params
            }
            view.invalidateOutline()
            saved.remove(view)
        }
        if (keys == null) saved.clear()
    }

    private fun restoreHosts(saved: MutableMap<View, HostState>, keys: Set<View>? = null) {
        val entries = saved.entries.filter { keys == null || it.key in keys }
        entries.forEach { (view, state) ->
            val params = view.layoutParams
            if (params != null && params.height != state.height) {
                params.height = state.height
                view.layoutParams = params
            }
            if (view is ViewGroup) {
                view.clipChildren = state.clipChildren
                view.clipToPadding = state.clipToPadding
            }
            view.invalidateOutline()
            saved.remove(view)
        }
        if (keys == null) saved.clear()
    }

    private fun windowTop(view: View): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[1]
    }

    private fun windowBottom(view: View): Int = windowTop(view) + view.height

    private class HostState(
        val height: Int,
        val clipChildren: Boolean,
        val clipToPadding: Boolean,
    )
}
