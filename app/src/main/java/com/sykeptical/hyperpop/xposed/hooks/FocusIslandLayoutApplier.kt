package com.sykeptical.hyperpop.xposed.hooks

import android.content.res.Resources
import android.graphics.Rect
import android.text.TextUtils.TruncateAt
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.sykeptical.hyperpop.service.animation.expanded.ActionButtonPolicy
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedVisualTokens
import com.sykeptical.hyperpop.service.animation.expanded.FocusTemplatePolicy
import com.sykeptical.hyperpop.service.animation.expanded.IslandRect
import com.sykeptical.hyperpop.service.animation.expanded.MediaSystemPolicy
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap

/**
 * Re-applies pill spacing after Xiaomi binds a Focus template. Only the copy
 * that lives under the expanded island is touched. Call buttons keep their
 * native size; text pills get a shorter visual and a larger hit rect.
 */
object FocusIslandLayoutApplier {
    private const val PACKAGE = "miui.systemui.notification.focus.moduleV3."
    private val HOLDERS = listOf(
        "ModuleViewHolder",
        "ModuleImageTextImViewHolder",
        "ModuleNewImageTextViewHolder",
        "ModuleTextViewHolder",
        "ModuleButtonViewHolder",
        "ModuleTextButton4ViewHolder",
        "ModuleTextButtonViewHolder",
        "ModuleTextButton5ViewHolder",
        "ModuleProgressViewHolder",
        "ModuleMarkViewHolder",
        "ModuleMarkTextImageViewHolder",
    )
    private val loaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val titleOriginals = WeakHashMap<TextView, TitleText>()
    private val idsByResources = Collections.synchronizedMap(WeakHashMap<Resources, MutableMap<String, Int>>())

    private data class TitleText(val ellipsize: TruncateAt?, val maxEms: Int)
    fun install(module: XposedModule, loader: ClassLoader) {
        if (!loaders.add(loader)) return
        runCatching {
            HOLDERS.forEach { name ->
                val type = runCatching { loader.loadClass(PACKAGE + name) }.getOrNull() ?: return@forEach
                type.declaredMethods
                    .filter { it.name == "bind" || it.name == "updatePartial" }
                    .forEach { method ->
                        method.isAccessible = true
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            runCatching { onHolder(chain.thisObject) }
                            result
                        }
                    }
            }
            val content = loader.loadClass(
                "miui.systemui.dynamicisland.window.content.DynamicIslandContentView",
            )
            content.declaredMethods.filter { it.name == "updateExpandedView" }.forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as? View
                    if (view != null) runCatching { onIsland(view) }
                    result
                }
            }
        }.onFailure {
            loaders.remove(loader)
        }
    }

    fun onIsland(view: View) {
        val data = view.call("getCurrentIslandData") ?: return
        (data.call("getView") as? View)?.let { apply(it) }
        (data.call("getFakeView") as? View)?.let { apply(it) }
    }

    fun apply(root: View) {
        if (!ExpandedVisualSession.active) return
        val density = root.resources.displayMetrics.density
        val plan = FocusTemplatePolicy.plan(density, ExpandedVisualSession.pill)
        if (plan.retune) {
            applyRootMargin(root, plan.rootMarginPx)
            find(root, "area_d")?.let { area ->
                if (area.paddingTop != plan.belowPaddingPx) {
                    area.setPadding(area.paddingLeft, plan.belowPaddingPx, area.paddingRight, area.paddingBottom)
                }
            }
            findAll(root, "focus_button_title").forEach { title ->
                val params = title.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
                if (params.topMargin == plan.pillMarginVerticalPx &&
                    params.bottomMargin == plan.pillMarginVerticalPx
                ) {
                    return@forEach
                }
                params.topMargin = plan.pillMarginVerticalPx
                params.bottomMargin = plan.pillMarginVerticalPx
                title.layoutParams = params
            }
            applyPillTouch(root, density)
        }
        if (ExpandedVisualSession.pill) applyMediaBand(root, density)
        showFullTitles(root)
    }

    private fun applyMediaBand(root: View, density: Float) {
        val art = find(root, "album_art") ?: return
        val margins = MediaSystemPolicy.margins(density)
        setGap(art, start = margins.artStartPx, top = margins.artTopPx)
        find(root, "header_title")?.let { setGap(it, top = margins.titleTopPx) }
        find(root, "media_seamless")?.let { setGap(it, top = margins.seamlessTopPx, end = margins.seamlessEndPx) }
        find(root, "action0")?.let { setGap(it, top = margins.actionTopPx) }
        find(root, "media_progress_bar")?.let { setGap(it, bottom = margins.progressBottomPx) }
    }

    private fun setGap(
        view: View,
        start: Int? = null,
        top: Int? = null,
        end: Int? = null,
        bottom: Int? = null,
    ) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        var changed = false
        if (start != null && params.marginStart != start) {
            params.marginStart = start
            changed = true
        }
        if (top != null && params.topMargin != top) {
            params.topMargin = top
            changed = true
        }
        if (end != null && params.marginEnd != end) {
            params.marginEnd = end
            changed = true
        }
        if (bottom != null && params.bottomMargin != bottom) {
            params.bottomMargin = bottom
            changed = true
        }
        if (changed) view.layoutParams = params
    }

    fun restoreTitles() {
        val snapshot = synchronized(titleOriginals) { titleOriginals.entries.toList() }
        snapshot.forEach { (title, saved) ->
            runCatching {
                title.ellipsize = saved.ellipsize
                title.maxEms = saved.maxEms
            }
        }
        synchronized(titleOriginals) { titleOriginals.clear() }
    }

    private fun showFullTitles(root: View) {
        listOf("focus_title", "header_title").forEach { name ->
            findAll(root, name).forEach { view ->
                val title = view as? TextView ?: return@forEach
                synchronized(titleOriginals) {
                    if (title !in titleOriginals) {
                        titleOriginals[title] = TitleText(title.ellipsize, title.maxEms)
                    }
                }
                if (title.ellipsize != null) title.ellipsize = null
                if (title.maxEms < 256) title.maxEms = Int.MAX_VALUE
            }
        }
    }

    private fun onHolder(holder: Any?) {
        if (!ExpandedVisualSession.active || holder == null) return
        val root = holderView(holder)?.let { expandedContent(it) } ?: return
        apply(root)
    }

    private fun applyRootMargin(root: View, margin: Int) {
        val container = find(root, "focus_container") as? ViewGroup ?: return
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            val params = child.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            val uniform = params.leftMargin > 0 &&
                params.leftMargin == params.rightMargin &&
                params.topMargin == params.bottomMargin &&
                params.leftMargin == params.topMargin
            if (!uniform || params.leftMargin == margin) continue
            params.setMargins(margin, margin, margin, margin)
            child.layoutParams = params
        }
    }

    private fun applyPillTouch(root: View, density: Float) {
        val titles = findAll(root, "focus_button_title")
        if (titles.isEmpty()) return
        val host = titles.first().parent as? ViewGroup ?: return
        if (titles.any { it.parent !== host }) return
        val minTouch = ExpandedVisualTokens.px(ExpandedVisualTokens.MIN_TOUCH_DP, density)
        val ordered = titles.sortedBy { it.left }
        val targets = ordered.mapIndexed { index, title ->
            val visual = Rect(0, 0, title.width, title.height)
            runCatching { host.offsetDescendantRectToMyCoords(title, visual) }
            val leftLimit = if (index == 0) 0 else {
                val previous = ordered[index - 1]
                (previous.right + title.left) / 2
            }
            val rightLimit = if (index == ordered.lastIndex) host.width else {
                val next = ordered[index + 1]
                (title.right + next.left) / 2
            }
            val expanded = if (visual.height() >= minTouch) {
                visual
            } else {
                val rect = ActionButtonPolicy.touchRect(
                    IslandRect(visual.left, visual.top, visual.right, visual.bottom),
                    density,
                    leftLimit,
                    rightLimit,
                )
                Rect(rect.left, rect.top, rect.right, rect.bottom)
            }
            CompositeTouchDelegate.Target(expanded, title)
        }
        val existing = host.touchDelegate as? CompositeTouchDelegate
        val delegate = existing ?: CompositeTouchDelegate(host).also { host.touchDelegate = it }
        delegate.replace(targets)
    }

    private fun holderView(holder: Any): View? {
        var type: Class<*>? = holder.javaClass
        while (type != null) {
            for (field in type.declaredFields) {
                if (!View::class.java.isAssignableFrom(field.type)) continue
                val view = runCatching {
                    field.isAccessible = true
                    field.get(holder) as? View
                }.getOrNull() ?: continue
                if (expandedContent(view) != null) return view
            }
            type = type.superclass
        }
        return null
    }

    private fun expandedContent(view: View): View? {
        var current: View? = view
        while (current != null) {
            val parent = current.parent as? View ?: return null
            if (parent.javaClass.name.contains("DynamicIslandExpandedView")) return current
            val fakeExpanded = id(parent, "fake_expanded_view")
            if (fakeExpanded != 0 && parent.id == fakeExpanded) return current
            current = parent
        }
        return null
    }

    private fun find(root: View, name: String): View? {
        val resolved = id(root, name)
        if (resolved == 0) return null
        if (root.id == resolved) return root
        return root.findViewById(resolved)
    }

    private fun findAll(root: View, name: String): List<View> {
        val resolved = id(root, name)
        if (resolved == 0) return emptyList()
        val found = ArrayList<View>(4)
        collect(root, resolved, found)
        return found
    }

    private fun collect(view: View, id: Int, found: MutableList<View>) {
        if (view.id == id) found += view
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) collect(group.getChildAt(index), id, found)
    }

    /** Resolves each name once per Resources. A miss is cached too. */
    private fun id(view: View, name: String): Int {
        val resources = view.resources
        val cache = idsByResources.getOrPut(resources) { HashMap() }
        synchronized(cache) { cache[name] }?.let { return it }
        val resolved = resources.getIdentifier(name, "id", view.context.packageName)
        synchronized(cache) { cache[name] = resolved }
        return resolved
    }

    private fun Any.call(name: String): Any? {
        val method = javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() } ?: return null
        method.isAccessible = true
        return runCatching { method.invoke(this) }.getOrNull()
    }
}
