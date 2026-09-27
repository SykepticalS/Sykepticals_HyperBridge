package com.d4viddf.hyperbridge.xposed.mediacard.island.compact

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.d4viddf.hyperbridge.xposed.hooks.IslandOwnedNotification
import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardLog
import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardRuntimeConfig
import com.d4viddf.hyperbridge.xposed.mediacard.compat.CompactMediaText
import com.d4viddf.hyperbridge.xposed.mediacard.compat.IslandProbeUtils
import com.d4viddf.hyperbridge.xposed.mediacard.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.Optional
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Injects the song title into the left slot of a native compact media island, right after
 * the album cover.
 *
 * Xiaomi rebuilds the left module on every update and re-measures `area_left` inside
 * `calculateBigIslandWidth()`. The slot is restored synchronously right before that
 * measurement and after each module bind, so the island never falls back to its native
 * width between tracks or when it comes back from an app.
 */
internal object CompactMediaIslandHooker {
    private const val TAG = "CompactMediaIsland"
    private const val CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val FAKE_CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val ADAPTER =
        "miui.systemui.dynamicisland.module.IslandModuleViewHolderAdapter"
    private const val ICON_HOLDER =
        "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val MEDIA_CONTROLLER =
        "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaControllerImpl"
    private const val WRAPPER_TAG = "hyperbridge.compact_media_title_wrapper"
    private const val TEXT_TAG = "hyperbridge.compact_media_title"
    private const val GHOST_TAG = "hyperbridge.compact_media_title_ghost"
    private const val SLOT_TAG = "hyperbridge.compact_media_title_slot"
    private const val LEFT_MODULE = "island_container_module_image_text_1"
    private const val TEXT_CONTAINER = "island_container_module_text"
    private const val ICON_CONTAINER = "island_container_module_icon"
    private const val MEDIA_ALBUM = "miui_media_album_icon"
    private const val ALBUM_GAP_DP = 6f
    private const val START_FADE_DP = 8f
    private const val END_PADDING_DP = 6f
    private const val END_FADE_DP = 10f
    private const val ICON_FALLBACK_DP = 22f
    private const val TEXT_SP = 13f
    private const val BOLD_WEIGHT = 700
    private const val MAX_BIND_ATTEMPTS = 45
    private const val FAKE_AREA = "fake_area_left"
    private const val MAX_ANIMATING_MS = 2_500L
    private val RESTING_STATES = setOf("BigIsland", "ShowOnceBigIsland")
    private val AREA_NAMES = arrayOf("area_left", FAKE_AREA)
    private val PACKAGES = arrayOf("miui.systemui.plugin", "com.android.systemui")

    private val hookedLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val tokens = Collections.synchronizedMap(WeakHashMap<ViewGroup, Any>())
    private val hosts = Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())
    private val lastText = Collections.synchronizedMap(WeakHashMap<ViewGroup, CompactMediaText>())
    private val controllers = Collections.synchronizedMap(WeakHashMap<CompactTitleView, CompactMediaTitleController>())
    private val containerSnapshots = Collections.synchronizedMap(WeakHashMap<View, SlotSnapshot>())
    private val areaWidths = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val leadings = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val hiddenNative = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val pendingRelayout = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ViewGroup, Boolean>())
    )
    private val ids = ConcurrentHashMap<String, Int>()
    private val relayoutMethods = ConcurrentHashMap<Class<*>, Method>()
    private val holderFields = ConcurrentHashMap<Class<*>, Field>()
    private val rootViewMethods = ConcurrentHashMap<Class<*>, Optional<Method>>()
    private val stateGetters = ConcurrentHashMap<Class<*>, Optional<Method>>()
    private val animatingGetters = ConcurrentHashMap<Class<*>, Optional<Method>>()
    private val stateNames = Collections.synchronizedMap(WeakHashMap<View, String?>())
    private val animatingSince = Collections.synchronizedMap(WeakHashMap<View, Long>())
    private val reports = HashMap<String, String>()
    private val resizing = ThreadLocal<Boolean>()
    @Volatile private var liveTitle: String = ""
    @Volatile private var liveArtist: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    fun hook(module: XposedModule, loader: ClassLoader) {
        if (!hookedLoaders.add(loader)) return
        CompactMediaSessions.onTrackChanged = { refresh() }
        var installed = 0
        fun install(method: Method, capability: String, hooker: Hooker) {
            val hooked = runCatching {
                module.managedHook(executable = method, capability = capability, hooker = hooker)
            }.isSuccess
            if (hooked) installed++
        }
        listOf(CONTENT_VIEW, FAKE_CONTENT_VIEW).forEach { name ->
            val clazz = runCatching { loader.loadClass(name) }.getOrNull() ?: return@forEach
            (clazz.methods.asList() + clazz.declaredMethods.asList())
                .distinct()
                .filter { it.name.substringBefore('$') == "updateBigIslandView" }
                .forEach { install(it, "media.island.compact.${clazz.simpleName}.${it.name}", UpdateHook()) }
            if (name == CONTENT_VIEW) {
                clazz.methods.firstOrNull {
                    it.name == "calculateBigIslandWidth" && it.parameterCount == 0 && it.returnType == Void.TYPE
                }?.let { install(it, "media.island.compact.measure", MeasureHook()) }
            } else {
                clazz.declaredMethods
                    .filter { it.name == "setVisibility" && it.parameterCount == 1 }
                    .forEach { install(it, "media.island.compact.fake.visibility", FakeVisibilityHook()) }
            }
        }
        runCatching { loader.loadClass(ADAPTER) }.getOrNull()?.declaredMethods
            ?.filter { method ->
                (method.name == "bindData" && method.parameterCount == 2) ||
                    (method.name == "updateView" && method.parameterCount == 3)
            }
            ?.forEach { method ->
                method.isAccessible = true
                install(method, "media.island.compact.adapter.${method.name}", AdapterHook())
            }
        runCatching { loader.loadClass(ICON_HOLDER) }.getOrNull()?.declaredMethods
            ?.filter { it.name == "setFixIcon" }
            ?.forEach { install(it, "media.island.compact.album.${it.parameterCount}", AlbumHook()) }
        runCatching { loader.loadClass(MEDIA_CONTROLLER) }.getOrNull()?.declaredMethods
            ?.filter { it.name == "addDynamicIslandView" || it.name.startsWith("removeDynamicIsland") }
            ?.forEach { install(it, "media.island.compact.controller.${it.name}.${it.parameterCount}", AddIslandHook()) }
        if (installed == 0) {
            hookedLoaders.remove(loader)
            MediaCardLog.w(TAG, "Compact media title hook was not installed")
        } else {
            Log.i("HyperBridge", "CompactMediaIsland: hook installed methods=$installed loader=${loader.javaClass.simpleName}")
            scanExistingIslands()
        }
    }

    /** The island can already be on screen by the time the hook is installed. */
    private fun scanExistingIslands() {
        val guarded = Runnable {
            runCatching { rescanAttachedIslands() }
                .onFailure { error -> Log.i("HyperBridge", "CompactMediaIsland: scan failed ${error.message}") }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) guarded.run() else mainHandler.post(guarded)
        mainHandler.postDelayed(guarded, 1500)
        mainHandler.postDelayed(guarded, 4000)
    }

    private fun rescanAttachedIslands() {
        val islands = ArrayList<ViewGroup>()
        windowRoots().forEach { root -> collectIslandViews(root, islands) }
        islands.distinct().forEach { schedule(it) }
    }

    private fun windowRoots(): List<View> {
        return runCatching {
            val manager = Class.forName("android.view.WindowManagerGlobal")
                .getDeclaredMethod("getInstance")
                .invoke(null)
            val field = manager.javaClass.declaredFields.firstOrNull { it.name == "mViews" }
                ?: return@runCatching emptyList()
            field.isAccessible = true
            when (val value = field.get(manager)) {
                is List<*> -> value.filterIsInstance<View>()
                is Array<*> -> value.filterIsInstance<View>()
                else -> emptyList()
            }
        }.getOrElse { emptyList() }
    }

    private fun collectIslandViews(view: View, islands: MutableList<ViewGroup>) {
        if (view !is ViewGroup) return
        val name = view.javaClass.name
        if (name == CONTENT_VIEW || name == FAKE_CONTENT_VIEW) {
            islands.add(view)
            return
        }
        for (index in 0 until view.childCount) collectIslandViews(view.getChildAt(index), islands)
    }

    fun refresh() {
        val run = Runnable {
            if (hosts.isEmpty()) {
                runCatching { rescanAttachedIslands() }
                return@Runnable
            }
            hosts.keys.toList().forEach { host -> if (host.isAttachedToWindow) schedule(host) }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else mainHandler.post(run)
    }

    private class UpdateHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val host = chain.thisObject as? ViewGroup ?: return result
            val name = host.javaClass.name
            if (name == CONTENT_VIEW || name == FAKE_CONTENT_VIEW) schedule(host)
            return result
        }
    }

    /** Runs before Xiaomi measures `area_left`, so the width always includes the title. */
    private class MeasureHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val host = chain.thisObject as? ViewGroup
            if (host != null && Looper.myLooper() == Looper.getMainLooper()) {
                runCatching { prepareForMeasure(host) }
                    .onFailure { error -> MediaCardLog.w(TAG, "Compact media pre-measure failed", error) }
            }
            return chain.proceed()
        }
    }

    /** The transition copy is shown when the island returns from an app. */
    private class FakeVisibilityHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val host = chain.thisObject as? ViewGroup
            if (host != null && (chain.args.firstOrNull() as? Number)?.toInt() == View.VISIBLE &&
                Looper.myLooper() == Looper.getMainLooper()
            ) {
                runCatching { prepareForMeasure(host) }
            }
            return chain.proceed()
        }
    }

    /** First synchronous point after Xiaomi builds or rebinds a module holder. */
    private class AdapterHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            if (Looper.myLooper() != Looper.getMainLooper()) return result
            runCatching { restoreModule(chain.thisObject, chain.args) }
                .onFailure { error -> MediaCardLog.w(TAG, "Compact media module restore failed", error) }
            return result
        }
    }

    /** Media island content is attached here once the screen is awake. */
    private class AddIslandHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            runCatching {
                chain.args.forEach { rememberMedia(it) }
                refresh()
            }
            return result
        }
    }

    /** A new album can change the cover width, which moves the title. */
    private class AlbumHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val result = chain.proceed()
            val holder = chain.thisObject ?: return result
            runCatching {
                if (!isMediaAlbum(holder)) return@runCatching
                val host = holderRoot(holder)?.let(::contentHost) ?: return@runCatching
                schedule(host)
            }
            return result
        }
    }

    private fun prepareForMeasure(host: ViewGroup) {
        if (!injects(MediaCardRuntimeConfig.current.compactIsland) &&
            host.findViewWithTag<View>(WRAPPER_TAG) == null
        ) return
        hosts[host] = true
        bind(host, measuring = true)
    }

    /** The title, or with the title off, empty room when the length slider is raised. */
    private fun injects(settings: CompactMediaIslandSettings): Boolean =
        settings.showTitle || settings.widthPercent > 0

    private fun restoreModule(adapter: Any?, args: List<*>) {
        val settings = MediaCardRuntimeConfig.current.compactIsland
        if (!injects(settings)) return
        val moduleType = args.firstOrNull() as? String ?: return
        val holder = moduleHolder(adapter, moduleType) ?: return
        val area = holderRoot(holder) as? ViewGroup ?: return
        if (resourceName(area) !in AREA_NAMES) return
        val data = args.lastOrNull()
        if (!isNativeMedia(data)) return
        val host = contentHost(area)
        val text = if (settings.showTitle) {
            resolveText(area, data) ?: host?.let { lastText[it] } ?: return
        } else {
            null
        }
        if (host != null) {
            hosts[host] = true
            if (text != null) lastText[host] = text
        }
        if (bindArea(area, text, settings) > 0 && host != null) requestIslandWidth(host)
    }

    private fun schedule(host: ViewGroup, attempt: Int = 0, token: Any = Any()) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { schedule(host, attempt, token) }
            return
        }
        if (attempt == 0) {
            tokens[host] = token
            hosts[host] = true
        }
        if (tokens[host] !== token) return
        if (!host.isAttachedToWindow) {
            if (attempt < MAX_BIND_ATTEMPTS) {
                Choreographer.getInstance().postFrameCallback { schedule(host, attempt + 1, token) }
            }
            return
        }
        val ready = runCatching { bind(host, measuring = false) }
            .onFailure { error -> MediaCardLog.w(TAG, "Compact media title bind failed", error) }
            .getOrDefault(false)
        if (!ready && attempt < MAX_BIND_ATTEMPTS) {
            Choreographer.getInstance().postFrameCallback { schedule(host, attempt + 1, token) }
        }
    }

    /**
     * Returns false only when the island is not ready yet. Missing island data (Xiaomi resumes
     * its suspend update with no data) never clears the title, so the width holds between tracks.
     */
    private fun bind(host: ViewGroup, measuring: Boolean): Boolean {
        val settings = MediaCardRuntimeConfig.current.compactIsland
        if (!injects(settings)) {
            clear(host, relayout = !measuring)
            return true
        }
        val data = islandData(host)
        if (data != null && !isNativeMedia(data)) {
            clear(host, relayout = !measuring)
            report(host, "skip not-media")
            return true
        }
        if (!settings.showTitle && data == null && host.findViewWithTag<View>(WRAPPER_TAG) == null) {
            report(host, "waiting for island data")
            return false
        }
        val text = if (settings.showTitle) data?.let { resolveText(host, it) } ?: lastText[host] else null
        if (settings.showTitle && text == null) {
            report(host, if (data == null) "waiting for island data" else "waiting for title")
            return false
        }
        val previous = if (text != null) lastText.put(host, text) else lastText.remove(host)
        val areas = AREA_NAMES.mapNotNull { name -> findNamed(host, name) }
        if (areas.isEmpty()) {
            report(host, "waiting for area_left")
            return false
        }
        var changed = false
        var attached = true
        areas.forEach { area ->
            when (bindArea(area, text, settings)) {
                -1 -> attached = false
                1 -> changed = true
            }
        }
        if (changed && !measuring) requestIslandWidth(host)
        if (text != null && previous?.identity != text.identity) {
            Log.i("HyperBridge", "CompactMediaIsland: track '${text.title}' by '${text.artist}'")
        }
        val shown = if (text != null) "showing '${text.title}'" else "extending length ${settings.widthPercent}"
        report(host, if (attached) shown else "waiting for left slot")
        return attached
    }

    /**
     * -1 when the slot is not inflated yet, 1 when the island width has to be recalculated.
     * A null [text] leaves an empty slot sized by the length slider.
     */
    private fun bindArea(area: View, text: CompactMediaText?, settings: CompactMediaIslandSettings): Int {
        val group = area as? ViewGroup ?: return -1
        val module = findNamed(area, LEFT_MODULE)
        val native = findNamed(module ?: area, TEXT_CONTAINER) as? ViewGroup
        var changed = false
        val container: ViewGroup
        val leading: Int
        if (native != null) {
            group.findViewWithTag<View>(SLOT_TAG)?.let { slot ->
                (slot.parent as? ViewGroup)?.removeView(slot)
                changed = true
            }
            if (module != null && module !== native && module.visibility != View.VISIBLE) {
                module.visibility = View.VISIBLE
                changed = true
            }
            container = native
            leading = leadingPx(area, module?.let { findNamed(it, ICON_CONTAINER) })
            changed = revealContainer(container, leading) || changed
        } else {
            container = ensureSlot(group) ?: return -1
            leading = leadingPx(area, group.getChildAt(0)?.takeIf { it !== container })
            changed = setMarginStart(container, leading) || changed
            changed = relaxArea(area) || changed
        }
        val wrapper = ensureWrapper(container)
        changed = hideNativeChildren(container, wrapper) || changed
        val primary = wrapper.findViewWithTag<CompactTitleView>(TEXT_TAG) ?: return -1
        val ghost = wrapper.findViewWithTag<CompactTitleView>(GHOST_TAG) ?: return -1
        primary.clipTo(area)
        ghost.clipTo(area)
        val density = area.resources.displayMetrics.density
        if (text != null) {
            val controller = controllers[primary] ?: createController(area, wrapper, primary, ghost)
                .also { controllers[primary] = it }
            controller.render(settings, text.title, text.artist, text.identity)
        }
        val width = if (text == null) {
            CompactMediaIslandPolicy.spacerWidthPx(settings.widthPercent, density, leading)
        } else {
            val lines = buildList {
                add(text.title)
                if (settings.cycleActive) {
                    CompactMediaIslandPolicy.artistLine(text.artist).takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
            CompactMediaIslandPolicy.displayedSlotWidthPx(
                percent = settings.widthPercent,
                displayedLineWidthPx = primary.paint.measureText(primary.text.ifBlank { text.title }),
                lineWidthsPx = lines.map { primary.paint.measureText(it) },
                horizontalPaddingPx = primary.insetPx + primary.endPaddingPx +
                    CompactMediaIslandPolicy.defaultEndClearancePx(settings.widthPercent, density),
                viewportPx = CompactMediaIslandPolicy.textViewportPx(settings.widthPercent, density, leading),
            )
        }
        val params = wrapper.layoutParams
        if (params != null && params.width != width) {
            params.width = width
            wrapper.layoutParams = params
            changed = true
        }
        if (wrapper.visibility != View.VISIBLE) {
            wrapper.visibility = View.VISIBLE
            changed = true
        }
        if (text == null) {
            controllers.remove(primary)?.stop()
            primary.text = ""
            ghost.text = ""
            return if (changed) 1 else 0
        }
        return if (changed) 1 else 0
    }

    private fun createController(
        area: View,
        wrapper: View,
        primary: CompactTitleView,
        ghost: CompactTitleView,
    ): CompactMediaTitleController {
        val areaRef = WeakReference(area)
        val wrapperRef = WeakReference(wrapper)
        val primaryRef = WeakReference(primary)
        return CompactMediaTitleController(
            primary = primary,
            ghost = ghost,
            passive = resourceName(area) == FAKE_AREA,
            islandAtRest = ::islandAtRest,
            onLineChanged = { line ->
                val liveArea = areaRef.get()
                val liveWrapper = wrapperRef.get()
                val livePrimary = primaryRef.get()
                if (liveArea != null && liveWrapper != null && livePrimary != null) {
                    resizeDefaultLength(liveArea, liveWrapper, livePrimary, line)
                }
            },
        )
    }

    private fun resizeDefaultLength(
        area: View,
        wrapper: View,
        primary: CompactTitleView,
        line: String,
    ) {
        val settings = MediaCardRuntimeConfig.current.compactIsland
        if (!settings.showTitle || settings.widthPercent != 0 || line.isBlank()) return
        val density = area.resources.displayMetrics.density
        val leading = leadings[area] ?: 0
        val width = CompactMediaIslandPolicy.displayedSlotWidthPx(
            percent = 0,
            displayedLineWidthPx = primary.paint.measureText(line),
            lineWidthsPx = emptyList(),
            horizontalPaddingPx = primary.insetPx + primary.endPaddingPx +
                CompactMediaIslandPolicy.defaultEndClearancePx(0, density),
            viewportPx = CompactMediaIslandPolicy.textViewportPx(0, density, leading),
        )
        val params = wrapper.layoutParams ?: return
        if (params.width == width) return
        params.width = width
        wrapper.layoutParams = params
        wrapper.requestLayout()
        contentHost(area)?.let(::requestIslandWidth)
    }

    /**
     * Album cover width. The gap after it lives inside the title view so the cover-side shade
     * can cover it. Kept per area so a cover mid-load does not collapse it.
     */
    private fun leadingPx(area: View, icon: View?): Int {
        val cover = when {
            icon == null || icon.visibility == View.GONE -> 0
            else -> {
                val width = icon.width.takeIf { it > 0 } ?: icon.measuredWidth
                val params = icon.layoutParams as? ViewGroup.MarginLayoutParams
                if (width > 0) width + (params?.marginStart ?: 0) + (params?.marginEnd ?: 0) else -1
            }
        }
        if (cover >= 0) {
            leadings[area] = cover
            return cover
        }
        return leadings[area] ?: (ICON_FALLBACK_DP * area.resources.displayMetrics.density).toInt()
    }

    private fun revealContainer(container: ViewGroup, leading: Int): Boolean {
        if (!containerSnapshots.containsKey(container)) {
            containerSnapshots[container] = SlotSnapshot(
                visibility = container.visibility,
                width = container.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                marginStart = (container.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0,
            )
        }
        var changed = false
        if (container.visibility != View.VISIBLE) {
            container.visibility = View.VISIBLE
            changed = true
        }
        if (!container.clipChildren) container.clipChildren = true
        val params = container.layoutParams
        if (params != null && params.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            container.layoutParams = params
            changed = true
        }
        return setMarginStart(container, leading) || changed
    }

    /** Xiaomi's width pass can flip its empty title views back on next to ours. */
    private fun hideNativeChildren(container: ViewGroup, keep: View): Boolean {
        var changed = false
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            if (child === keep || child.visibility == View.GONE) continue
            hiddenNative.putIfAbsent(child, child.visibility)
            child.visibility = View.GONE
            changed = true
        }
        return changed
    }

    private fun setMarginStart(view: View, margin: Int): Boolean {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return false
        if (params.marginStart == margin) return false
        params.marginStart = margin
        view.layoutParams = params
        return true
    }

    private fun ensureWrapper(container: ViewGroup): FrameLayout {
        container.findViewWithTag<FrameLayout>(WRAPPER_TAG)?.let { return it }
        val context = container.context
        val native = nativeTextStyle(container)
        val wrapper = FrameLayout(context).apply {
            tag = WRAPPER_TAG
            clipChildren = true
            clipToPadding = true
        }
        val ghost = titleView(container, native).apply {
            tag = GHOST_TAG
            visibility = View.GONE
        }
        val primary = titleView(container, native).apply { tag = TEXT_TAG }
        val fill = ViewGroup.LayoutParams.MATCH_PARENT
        wrapper.addView(ghost, FrameLayout.LayoutParams(fill, fill))
        wrapper.addView(primary, FrameLayout.LayoutParams(fill, fill))
        val params = if (container is FrameLayout) {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, fill).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
        } else {
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, fill)
        }
        container.addView(wrapper, params)
        return wrapper
    }

    private fun titleView(container: ViewGroup, native: NativeTextStyle): CompactTitleView {
        val density = container.resources.displayMetrics.density
        return CompactTitleView(container.context).apply {
            paint.textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                TEXT_SP,
                container.resources.displayMetrics,
            )
            paint.color = native.color
            paint.typeface = Typeface.create(native.typeface ?: Typeface.DEFAULT, BOLD_WEIGHT, false)
            insetPx = (ALBUM_GAP_DP * density).toInt()
            startFadePx = (START_FADE_DP * density).toInt()
            endPaddingPx = (END_PADDING_DP * density).toInt()
            endFadePx = (END_FADE_DP * density).toInt()
            cameraDistance = 8000f * density
        }
    }

    private fun ensureSlot(area: ViewGroup): ViewGroup? {
        area.findViewWithTag<ViewGroup>(SLOT_TAG)?.let { return it }
        val slot = FrameLayout(area.context).apply {
            tag = SLOT_TAG
            clipChildren = true
        }
        val fill = ViewGroup.LayoutParams.MATCH_PARENT
        val params: ViewGroup.LayoutParams = if (area is LinearLayout) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, fill).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        } else {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, fill).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
            }
        }
        area.addView(slot, (if (area.childCount > 0) 1 else 0).coerceAtMost(area.childCount), params)
        area.clipChildren = false
        return slot
    }

    private fun relaxArea(area: View): Boolean {
        val params = area.layoutParams
        if (params == null || params.width <= 0 || areaWidths.containsKey(area)) return false
        areaWidths[area] = params.width
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        area.layoutParams = params
        return true
    }

    private fun clear(host: ViewGroup, relayout: Boolean) {
        lastText.remove(host)
        val wrappers = ArrayList<View>()
        collectTagged(host, WRAPPER_TAG, wrappers)
        var changed = false
        wrappers.forEach { wrapper ->
            wrapper.findViewWithTag<CompactTitleView>(TEXT_TAG)?.let { controllers.remove(it)?.stop() }
            val container = wrapper.parent as? ViewGroup ?: return@forEach
            container.removeView(wrapper)
            if (container.tag == SLOT_TAG) {
                (container.parent as? ViewGroup)?.removeView(container)
            } else {
                restoreContainer(container)
            }
            changed = true
        }
        val relaxed = synchronized(areaWidths) {
            areaWidths.keys.filter { it === host || it.isDescendantOf(host) }
        }
        relaxed.forEach { area ->
            val width = areaWidths.remove(area) ?: return@forEach
            val params = area.layoutParams ?: return@forEach
            params.width = width
            area.layoutParams = params
            changed = true
        }
        if (changed && relayout) requestIslandWidth(host)
    }

    private fun restoreContainer(container: ViewGroup) {
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            hiddenNative.remove(child)?.let { child.visibility = it }
        }
        val snapshot = containerSnapshots.remove(container) ?: return
        container.visibility = snapshot.visibility
        val params = container.layoutParams ?: return
        params.width = snapshot.width
        (params as? ViewGroup.MarginLayoutParams)?.marginStart = snapshot.marginStart
        container.layoutParams = params
    }

    /** Only the real island recalculates; the transition copy follows its width. */
    private fun requestIslandWidth(host: ViewGroup) {
        if (host.javaClass.name != CONTENT_VIEW) {
            host.requestLayout()
            return
        }
        if (!pendingRelayout.add(host)) return
        host.post {
            pendingRelayout.remove(host)
            if (host.isAttachedToWindow) relayout(host)
        }
    }

    private fun relayout(host: ViewGroup) {
        if (resizing.get() == true) return
        resizing.set(true)
        try {
            host.requestLayout()
            val method = relayoutMethods.getOrPut(host.javaClass) {
                host.javaClass.methods.firstOrNull { it.name == "updateBigIslandViewWidth" && it.parameterCount == 0 }
                    ?: host.javaClass.methods.first { it.name == "calculateBigIslandWidth" && it.parameterCount == 0 }
            }
            method.invoke(host)
        } catch (error: Throwable) {
            MediaCardLog.w(TAG, "Compact media island relayout failed", error)
        } finally {
            resizing.set(false)
        }
    }

    private fun resolveText(anchor: View, data: Any?): CompactMediaText? {
        CompactMediaSessions.ensureStarted(anchor.context.applicationContext ?: anchor.context)
        IslandProbeUtils.packageName(data)
            ?.let { CompactMediaSessions.track(it) }
            ?.let { CompactMediaText.of(it.title, it.artist) }
            ?.let { return it }
        return IslandProbeUtils.readCompactMediaText(data) ?: liveCompactText()
    }

    private fun rememberMedia(value: Any?) {
        if (value == null || !value.javaClass.name.endsWith("MediaData")) return
        val song = stringField(value, "song").ifBlank { stringField(value, "title") }
        val artist = stringField(value, "artist").ifBlank { stringField(value, "subtitle") }
        if (song.isBlank() && artist.isBlank()) return
        liveTitle = song
        liveArtist = artist
    }

    private fun liveCompactText(): CompactMediaText? = CompactMediaText.of(liveTitle, liveArtist)

    private fun stringField(target: Any, name: String): String {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            val field = runCatching { clazz.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return field.get(target)?.toString()?.trim().orEmpty()
            }
            clazz = clazz.superclass
        }
        return ""
    }

    private fun isNativeMedia(data: Any?): Boolean {
        if (!IslandProbeUtils.isMediaIsland(data)) return false
        return IslandOwnedNotification.fromIslandData(data)?.owned != true
    }

    private fun islandData(view: View): Any? {
        var current: View? = view
        while (current != null) {
            IslandProbeUtils.getCurrentIslandData(current)?.let { return it }
            current = current.parent as? View
        }
        return null
    }

    private fun moduleHolder(adapter: Any?, moduleType: String): Any? {
        if (adapter == null) return null
        val field = holderFields.getOrPut(adapter.javaClass) {
            adapter.javaClass.getDeclaredField("holders").apply { isAccessible = true }
        }
        return (field.get(adapter) as? Map<*, *>)?.get(moduleType)
    }

    private fun holderRoot(holder: Any): View? {
        val method = rootViewMethods.getOrPut(holder.javaClass) {
            Optional.ofNullable(holder.javaClass.methods.firstOrNull { it.name == "getRootView" && it.parameterCount == 0 })
        }.orElse(null)
        (method?.invoke(holder) as? View)?.let { return it }
        val field = holder.javaClass.declaredFields.firstOrNull {
            it.name == "iconContainer" || it.name == "rootView"
        } ?: return null
        field.isAccessible = true
        return field.get(holder) as? View
    }

    private fun isMediaAlbum(holder: Any): Boolean {
        val field = holder.javaClass.declaredFields.firstOrNull { it.name == "picInfo" } ?: return false
        field.isAccessible = true
        val picInfo = field.get(holder) ?: return false
        val pic = picInfo.javaClass.methods.firstOrNull {
            it.name == "getPic" && it.parameterCount == 0
        }?.invoke(picInfo) as? String ?: return false
        return pic == MEDIA_ALBUM || pic.contains("album")
    }

    /**
     * The compact island is only really on show in its big-island state with no transition
     * running. Inside the media app the state is AppExpanded while every view still reports
     * itself visible. A transition flag stuck on for too long is ignored.
     */
    private fun islandAtRest(view: View): Boolean {
        val host = contentHost(view) ?: return true
        if (host.javaClass.name != CONTENT_VIEW) return true
        val state = runCatching { stateGetter(host)?.invoke(host) }.getOrNull()
        val stateName = state?.javaClass?.simpleName
        if (stateName != stateNames[host]) {
            stateNames[host] = stateName
            Log.i("HyperBridge", "CompactMediaIsland: island state $stateName")
        }
        if (stateName != null && stateName !in RESTING_STATES) {
            animatingSince.remove(host)
            return false
        }
        val animating = runCatching { animatingGetter(host)?.invoke(host) as? Boolean }.getOrNull() == true
        if (!animating) {
            animatingSince.remove(host)
            return true
        }
        val now = SystemClock.uptimeMillis()
        val since = animatingSince.getOrPut(host) { now }
        return now - since > MAX_ANIMATING_MS
    }

    private fun stateGetter(host: View): Method? = stateGetters.getOrPut(host.javaClass) {
        Optional.ofNullable(host.javaClass.methods.firstOrNull { it.name == "getState" && it.parameterCount == 0 })
    }.orElse(null)

    private fun animatingGetter(host: View): Method? = animatingGetters.getOrPut(host.javaClass) {
        Optional.ofNullable(host.javaClass.methods.firstOrNull { it.name == "isAnimating" && it.parameterCount == 0 })
    }.orElse(null)

    private fun contentHost(view: View): ViewGroup? {
        var current: View? = view
        while (current != null) {
            val name = current.javaClass.name
            if (name == CONTENT_VIEW || name == FAKE_CONTENT_VIEW) return current as? ViewGroup
            current = current.parent as? View
        }
        return null
    }

    /** Colour and font family of the native right-side text, so the title matches it. */
    private fun nativeTextStyle(anchor: View): NativeTextStyle {
        val right = findNamed(anchor.rootView, "area_right") ?: return NativeTextStyle(Color.WHITE, null)
        var found: TextView? = null
        fun walk(view: View) {
            if (found != null) return
            if (view is TextView && view.visibility == View.VISIBLE && Color.alpha(view.currentTextColor) > 0) {
                found = view
                return
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(right)
        return NativeTextStyle(found?.currentTextColor ?: Color.WHITE, found?.typeface)
    }

    private data class NativeTextStyle(val color: Int, val typeface: Typeface?)

    /** Resolves the id once; the tree walk is only a fallback for unknown resource tables. */
    private fun findNamed(root: View, name: String): View? {
        val id = ids.getOrPut(name) {
            PACKAGES.firstNotNullOfOrNull { pkg ->
                runCatching { root.resources.getIdentifier(name, "id", pkg) }.getOrNull()?.takeIf { it != 0 }
            } ?: 0
        }
        if (id != 0) {
            if (root.id == id) return root
            return (root as? ViewGroup)?.findViewById(id)
        }
        return findByEntry(root, name)
    }

    private fun findByEntry(root: View, name: String): View? {
        if (resourceName(root) == name) return root
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                findByEntry(root.getChildAt(index), name)?.let { return it }
            }
        }
        return null
    }

    private fun resourceName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
    }

    private fun collectTagged(root: View, tag: String, out: MutableList<View>) {
        if (root.tag == tag) {
            out.add(root)
            return
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) collectTagged(root.getChildAt(index), tag, out)
        }
    }

    private fun report(host: View, state: String) {
        val id = System.identityHashCode(host).toString()
        val changed = synchronized(reports) { reports.put(id, state) != state }
        if (changed) Log.i("HyperBridge", "CompactMediaIsland: $state")
    }

    private fun View.isDescendantOf(ancestor: View): Boolean {
        var current: View? = this
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }

    private data class SlotSnapshot(val visibility: Int, val width: Int, val marginStart: Int)
}
