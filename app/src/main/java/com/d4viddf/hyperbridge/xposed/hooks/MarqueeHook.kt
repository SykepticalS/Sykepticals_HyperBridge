package com.d4viddf.hyperbridge.xposed.hooks

import android.app.Notification
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.models.MarqueeDismissMode
import com.d4viddf.hyperbridge.models.MarqueeTimeoutPolicy
import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Real SystemUI marquee adapted from HyperIsland's Choreographer MarqueeHook (MIT).
 * Only owned HyperBridge proxy islands are scrolled; expanded content is never touched.
 */
object MarqueeHook {
    private const val CONTENT_VIEW = "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val controllers = WeakHashMap<TextView, Controller>()
    private val observed = WeakHashMap<TextView, Listeners>()
    private val islandEnabled = WeakHashMap<ViewGroup, Boolean>()
    private val islandSessions = WeakHashMap<ViewGroup, AutoHideSession>()
    private val islandTokens = Collections.synchronizedMap(WeakHashMap<ViewGroup, Any>())
    private val islandNotifications = Collections.synchronizedMap(WeakHashMap<View, StatusBarNotification>())
    private val islandKeys = Collections.synchronizedMap(WeakHashMap<View, String>())
    private val originalMaxLines = WeakHashMap<TextView, Int>()
    private val originalEllipsize = WeakHashMap<TextView, TextUtils.TruncateAt?>()
    private val originalHorizontalScrolling = WeakHashMap<TextView, Boolean>()
    @Volatile private var active = false

    private data class Listeners(
        val layout: View.OnLayoutChangeListener,
        val attach: View.OnAttachStateChangeListener,
        val watcher: android.text.TextWatcher,
    )

    private class AutoHideSession(
        val islandKey: String,
        val mode: MarqueeDismissMode,
        val timeoutMs: Long,
        val generation: Long,
        val notification: StatusBarNotification?,
        val ongoing: Boolean,
        val startedAtMs: Long = SystemClock.elapsedRealtime(),
        val scrollingViews: WeakHashMap<TextView, Int> = WeakHashMap(),
        var fallback: Runnable? = null,
        var fallbackGeneration: Long = 0L,
        var dismissed: Boolean = false,
    )

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { hook(module, it) }
    }

    fun isActive(): Boolean = active

    private fun hook(module: XposedModule, loader: ClassLoader) {
        val id = System.identityHashCode(loader)
        if (!hookedLoaders.add(id)) return
        runCatching {
            val clazz = loader.loadClass(CONTENT_VIEW)
            val methods = clazz.declaredMethods.filter {
                val base = it.name.substringBefore('$')
                base == "updateBigIslandView" || base == "updateSmallIslandView"
            }
            check(methods.isNotEmpty()) { "updateBigIslandView missing" }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val island = chain.thisObject as? ViewGroup
                    val token = Any()
                    val visualBefore = island?.let(IslandLiveVisual::capture)
                    if (island != null) {
                        islandTokens[island] = token
                        resetIsland(island)
                        islandKeys[island]?.let(ActiveIslandDismissHook::invalidate)
                    }
                    val result = chain.proceed()
                    if (island == null || islandTokens[island] !== token) return@intercept result
                    val snapshot = IslandOwnedNotification.fromIslandData(chain.args.firstOrNull())
                    val sbn = snapshot?.sbn
                    if (sbn != null) islandNotifications[island] = sbn
                    val islandKey = runCatching {
                        IslandHookReflection.invokeNoArg(chain.args.firstOrNull(), "getKey") as? String
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: sbn?.key
                    if (!islandKey.isNullOrBlank()) islandKeys[island] = islandKey
                    if (snapshot == null || !snapshot.owned) {
                        applyMarquee(island, enabled = false)
                        return@intercept result
                    }
                    val extras = snapshot.extras
                    val userMarquee = extras.getBoolean(IslandProtocol.EXTRA_MARQUEE_ENABLED, snapshot.marqueeEnabled)
                    val sourceOngoing = sbn != null &&
                        sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0
                    val ongoing = extras.getBoolean(IslandProtocol.EXTRA_SOURCE_ONGOING, sourceOngoing)
                    val mode = if (userMarquee) {
                        MarqueeDismissMode.parse(extras.getString(IslandProtocol.EXTRA_MARQUEE_MODE))
                    } else {
                        MarqueeDismissMode.OFF
                    }
                    val originalTimeout = extras.getInt(IslandProtocol.EXTRA_ORIGINAL_TIMEOUT, 10)
                    val generation = snapshot.generation
                    island.post {
                        if (islandTokens[island] !== token) return@post
                        val apply = {
                            if (islandTokens[island] === token) {
                                applyMarquee(
                                    island = island,
                                    enabled = userMarquee,
                                    mode = mode,
                                    originalTimeoutSecs = originalTimeout,
                                    generation = generation,
                                    ongoing = ongoing,
                                    notification = sbn ?: islandNotifications[island],
                                )
                            }
                        }
                        // Progress interpolation stays on existing widgets. Never rewrite
                        // subtitle TextViews — HyperIsland leaves those to Xiaomi.
                        runCatching { IslandLiveVisual.play(island, visualBefore, apply) }
                            .onFailure { apply() }
                    }
                    result
                }
            }
            active = true
        }.onFailure {
            hookedLoaders.remove(id)
            module.log("HyperBridge: marquee unavailable: ${it.message}")
        }
    }

    private fun applyMarquee(
        island: ViewGroup,
        enabled: Boolean,
        mode: MarqueeDismissMode = MarqueeDismissMode.OFF,
        originalTimeoutSecs: Int = 10,
        generation: Long = Long.MIN_VALUE,
        ongoing: Boolean = false,
        notification: StatusBarNotification? = null,
    ) {
        islandEnabled[island] = enabled
        val loops = if (enabled) mode.loops else 0
        val overrideTimeout = enabled && mode.overridesTimeout && loops > 0
        val hasText = enabled && hasMarqueeText(island)
        val sessionLoops = if (loops > 0 && !hasText && !overrideTimeout) 0 else loops
        configureSession(island, sessionLoops, overrideTimeout, originalTimeoutSecs, generation, ongoing, notification, mode)
        traverse(island, enabled)
    }

    private fun configureSession(
        island: ViewGroup,
        loops: Int,
        overrideTimeout: Boolean,
        originalTimeoutSecs: Int,
        generation: Long,
        ongoing: Boolean,
        notification: StatusBarNotification?,
        mode: MarqueeDismissMode,
    ) {
        islandSessions.remove(island)?.let { cancelFallback(island, it) }
        if (loops <= 0) return
        val session = AutoHideSession(
            islandKey = islandKeys[island].orEmpty(),
            mode = mode,
            timeoutMs = originalTimeoutSecs.coerceAtLeast(0) * 1000L,
            generation = generation,
            notification = notification ?: islandNotifications[island],
            ongoing = ongoing,
        )
        islandSessions[island] = session
        scheduleFallback(island, session)
    }

    private fun scheduleFallback(island: ViewGroup, session: AutoHideSession) {
        if (!session.mode.overridesTimeout || session.dismissed || session.scrollingViews.isNotEmpty() ||
            session.fallback != null || session.timeoutMs <= 0L
        ) return
        val elapsed = SystemClock.elapsedRealtime() - session.startedAtMs
        val generation = session.fallbackGeneration
        val islandRef = WeakReference(island)
        val runnable = Runnable {
            if (session.fallbackGeneration != generation) return@Runnable
            session.fallback = null
            val activeIsland = islandRef.get() ?: return@Runnable
            if (islandSessions[activeIsland] !== session) return@Runnable
            if (session.scrollingViews.isEmpty()) dismissIsland(activeIsland, session)
        }
        session.fallback = runnable
        island.postDelayed(runnable, (session.timeoutMs - elapsed).coerceAtLeast(0L))
    }

    private fun cancelFallback(island: ViewGroup, session: AutoHideSession) {
        session.fallback?.let(island::removeCallbacks)
        session.fallback = null
        session.fallbackGeneration++
    }

    private fun registerScrolling(view: TextView) {
        val island = findIsland(view) ?: return
        val session = islandSessions[island] ?: return
        session.scrollingViews[view] = 0
        cancelFallback(island, session)
    }

    private fun unregisterScrolling(view: TextView) {
        val island = findIsland(view)
        if (island != null) {
            val session = islandSessions[island]
            if (session != null && session.scrollingViews.remove(view) != null) {
                scheduleFallback(island, session)
            }
            return
        }
        islandSessions.forEach { (candidate, session) ->
            if (session.scrollingViews.remove(view) != null) scheduleFallback(candidate, session)
        }
    }

    private fun onLoop(view: TextView, completed: Int) {
        val island = findIsland(view) ?: return
        val session = islandSessions[island] ?: return
        if (!session.scrollingViews.containsKey(view)) return
        session.scrollingViews[view] = completed
        val loops = session.scrollingViews.values.toList()
        val minLoops = loops.minOrNull() ?: 0
        if (MarqueeTimeoutPolicy.shouldDismiss(session.mode, minLoops, session.ongoing)) {
            dismissIsland(island, session)
        }
    }

    private fun dismissIsland(island: ViewGroup, session: AutoHideSession) {
        if (session.dismissed || session.ongoing) return
        val notification = session.notification ?: islandNotifications[island] ?: return
        session.dismissed = true
        cancelFallback(island, session)
        ActiveIslandDismissHook.dismiss(notification, session.generation)
    }

    private fun traverse(view: View, enabled: Boolean) {
        if (view is TextView) {
            if (isExpanded(view)) return
            if (enabled) {
                observe(view)
                startMarquee(view)
            } else {
                unobserve(view)
                stopMarquee(view)
            }
        } else if (view is ViewGroup) {
            for (index in 0 until view.childCount) traverse(view.getChildAt(index), enabled)
        }
    }

    private fun startMarquee(view: TextView) {
        val full = view.text?.toString().orEmpty()
        val clean = normalize(full)
        if (clean.isEmpty()) {
            stopMarquee(view)
            return
        }
        if (full != clean) view.text = clean
        val visible = visibleWidth(view)
        if (visible <= 0) return
        val available = visible - view.paddingLeft - view.paddingRight
        val overflow = view.paint.measureText(clean) > available
        if (!overflow) {
            stopMarquee(view)
            return
        }
        if (view.maxLines != 1) {
            originalMaxLines.putIfAbsent(view, view.maxLines)
            view.setSingleLine(true)
        }
        originalEllipsize.putIfAbsent(view, view.ellipsize)
        originalHorizontalScrolling.putIfAbsent(view, false)
        view.setHorizontallyScrolling(true)
        view.ellipsize = null
        view.isHorizontalFadingEdgeEnabled = true
        val controller = controllers.getOrPut(view) { Controller(view, HookConfig.marqueeSpeed()) }
        controller.speedPxPerSec = HookConfig.marqueeSpeed()
        controller.start()
    }

    private fun stopMarquee(view: TextView) {
        controllers.remove(view)?.stop()
        originalMaxLines.remove(view)?.let { maxLines ->
            view.setSingleLine(false)
            view.maxLines = maxLines
        }
        originalEllipsize.remove(view)?.let { view.ellipsize = it }
        originalHorizontalScrolling.remove(view)?.let { view.setHorizontallyScrolling(it) }
        view.isHorizontalFadingEdgeEnabled = false
        val full = view.text?.toString().orEmpty()
        val clean = normalize(full)
        if (full != clean) view.text = clean
        unregisterScrolling(view)
    }

    private fun observe(view: TextView) {
        if (observed.containsKey(view)) return
        val layout = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val textView = v as TextView
            if (isExpanded(textView)) return@OnLayoutChangeListener
            textView.post {
                if (islandEnabled[findIsland(textView)] == true) startMarquee(textView) else stopMarquee(textView)
            }
        }
        view.addOnLayoutChangeListener(layout)
        val watcher = object : android.text.TextWatcher {
            private val ref = WeakReference(view)
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val textView = ref.get() ?: return
                if (isExpanded(textView)) return
                textView.post {
                    val attached = ref.get() ?: return@post
                    if (islandEnabled[findIsland(attached)] == true) startMarquee(attached) else stopMarquee(attached)
                }
            }
        }
        view.addTextChangedListener(watcher)
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                (v as? TextView)?.let {
                    unobserve(it)
                    stopMarquee(it)
                }
            }
        }
        view.addOnAttachStateChangeListener(attach)
        observed[view] = Listeners(layout, attach, watcher)
    }

    private fun unobserve(view: TextView) {
        val listeners = observed.remove(view) ?: return
        view.removeOnLayoutChangeListener(listeners.layout)
        view.removeOnAttachStateChangeListener(listeners.attach)
        view.removeTextChangedListener(listeners.watcher)
    }

    private fun resetIsland(island: ViewGroup) {
        islandSessions.remove(island)?.let { cancelFallback(island, it) }
        controllers.keys.toList().filter { findIsland(it) === island }.forEach { stopMarquee(it) }
    }

    private fun findIsland(view: View): ViewGroup? {
        var parent = view.parent
        while (parent is ViewGroup) {
            if (islandEnabled.containsKey(parent)) return parent
            parent = parent.parent
        }
        return null
    }

    private fun hasMarqueeText(view: View): Boolean {
        if (view is TextView) return !isExpanded(view) && normalize(view.text?.toString().orEmpty()).isNotEmpty()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) if (hasMarqueeText(view.getChildAt(index))) return true
        }
        return false
    }

    private fun isExpanded(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            val name = current.javaClass.simpleName
            if (name.contains("DynamicIslandExpandedView") || name.contains("ExpandedView")) return true
            current = current.parent as? View
        }
        return false
    }

    private fun visibleWidth(view: View): Int {
        var width = if (view.width > 0) view.width else Int.MAX_VALUE
        var parent = view.parent
        while (parent is ViewGroup) {
            if (parent.width > 0 && parent.width < width) width = parent.width
            parent = parent.parent
        }
        return if (width == Int.MAX_VALUE) 0 else width
    }

    private fun normalize(text: String): String = text
        .replace(Regex("[\\n\\r\\t\\u00A0\\u200B\\uFEFF]+"), " ")
        .replace(Regex(" +"), " ")
        .trim()

    private class Controller(
        view: TextView,
        var speedPxPerSec: Int,
    ) : Choreographer.FrameCallback {
        private val viewRef = WeakReference(view)
        private val choreographer = Choreographer.getInstance()
        private var running = false
        private var startNanos = 0L
        private var lastNanos = 0L
        private var scrollX = 0f
        private var state = 0
        private var text = ""
        private var completed = 0

        fun start() {
            val view = viewRef.get() ?: return
            val now = normalize(view.text?.toString().orEmpty())
            registerScrolling(view)
            if (running && text == now) return
            text = now
            running = true
            scrollX = 0f
            state = 0
            completed = 0
            startNanos = 0L
            choreographer.removeFrameCallback(this)
            choreographer.postFrameCallback(this)
        }

        fun stop() {
            running = false
            choreographer.removeFrameCallback(this)
            viewRef.get()?.scrollTo(0, 0)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val view = viewRef.get()
            if (view == null || !view.isAttachedToWindow) {
                if (view != null) unregisterScrolling(view)
                stop()
                return
            }
            if (startNanos == 0L) {
                startNanos = frameTimeNanos
                lastNanos = frameTimeNanos
            }
            val maxScroll = maxOf(0f, view.paint.measureText(text) - (visibleWidth(view) - view.paddingLeft - view.paddingRight).toFloat())
            if (maxScroll <= 0f) {
                unregisterScrolling(view)
                stop()
                return
            }
            val elapsedMs = (frameTimeNanos - startNanos) / 1_000_000
            when (state) {
                0 -> if (elapsedMs >= 1500) {
                    state = 1
                    lastNanos = frameTimeNanos
                }
                1 -> {
                    scrollX += speedPxPerSec.coerceIn(20, 500) * ((frameTimeNanos - lastNanos) / 1_000_000_000f)
                    if (scrollX >= maxScroll) {
                        scrollX = maxScroll
                        state = 2
                        startNanos = frameTimeNanos
                    }
                    view.scrollTo(scrollX.toInt(), 0)
                    view.invalidate()
                }
                2 -> if (elapsedMs > 1000) {
                    completed++
                    onLoop(view, completed)
                    val island = findIsland(view)
                    if (island != null && islandSessions[island]?.dismissed == true) {
                        stop()
                        return
                    }
                    scrollX = 0f
                    view.scrollTo(0, 0)
                    state = 0
                    startNanos = frameTimeNanos
                }
            }
            lastNanos = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }
}
