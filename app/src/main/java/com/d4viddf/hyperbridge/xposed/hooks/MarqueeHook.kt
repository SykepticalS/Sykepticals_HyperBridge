package com.d4viddf.hyperbridge.xposed.hooks

import android.app.Notification
import android.graphics.Rect
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.models.MarqueeDismissMode
import com.d4viddf.hyperbridge.models.MarqueeMotion
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
    private const val MIN_CONTENT_READY_FRAMES = 8
    private const val MAX_CONTENT_READY_FRAMES = 90
    private const val REQUIRED_STABLE_GEOMETRY_FRAMES = 3
    // TimerTextEffectView posts its native stop runnable at 600 ms. Keep our TextWatchers and
    // scroll controller away until that transition has committed its final Spannable.
    private const val NATIVE_TEXT_EFFECT_SETTLE_MS = 650L
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
    private val compactAreas = WeakHashMap<TextView, WeakReference<ViewGroup>>()
    private val visibilityRetries = WeakHashMap<ViewGroup, ViewTreeObserver.OnPreDrawListener>()
    @Volatile private var active = false
    @Volatile private var autoHideHeld = false

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

    fun holdAutoHide() {
        autoHideHeld = true
        islandSessions.forEach { (island, session) -> cancelFallback(island, session) }
    }

    fun releaseAutoHide() {
        autoHideHeld = false
        islandSessions.forEach { (island, session) -> scheduleFallback(island, session) }
    }

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
                    val incoming = IslandOwnedNotification.fromIslandData(chain.args.firstOrNull())
                    val nativeTextUpdate = incoming?.owned == true &&
                        incoming.extras.getBoolean("miui.island.updateNoFloat", false) &&
                        incoming.extras.getBoolean(
                            IslandProtocol.EXTRA_TEXT_UPDATE_ANIMATION,
                            false,
                        )
                    if (island != null) {
                        islandTokens[island] = token
                        if (nativeTextUpdate) pauseIsland(island) else resetIsland(island)
                        islandKeys[island]?.let(ActiveIslandDismissHook::invalidate)
                    }
                    val result = chain.proceed()
                    if (island == null || islandTokens[island] !== token) return@intercept result
                    val snapshot = IslandOwnedNotification.fromIslandData(chain.args.firstOrNull())
                        ?: incoming
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
                    if (nativeTextUpdate) {
                        module.log(
                            "HyperBridge: native compact text update protected " +
                                "key=${snapshot.islandKey.orEmpty()} generation=$generation",
                        )
                    }
                    scheduleMarquee(
                        island = island,
                        token = token,
                        enabled = userMarquee,
                        mode = mode,
                        originalTimeoutSecs = originalTimeout,
                        generation = generation,
                        ongoing = ongoing,
                        notification = sbn ?: islandNotifications[island],
                        visualBefore = visualBefore,
                        settleUntilMs = if (nativeTextUpdate) {
                            SystemClock.uptimeMillis() + NATIVE_TEXT_EFFECT_SETTLE_MS
                        } else {
                            0L
                        },
                    )
                    result
                }
            }
            active = true
            module.log("HyperBridge: hooked native compact marquee loader=$id")
        }.onFailure { error ->
            hookedLoaders.remove(id)
            if (error !is ClassNotFoundException) {
                module.log("HyperBridge: marquee unavailable loader=$id: ${error.message}")
            }
        }
    }

    /**
     * Xiaomi's update methods are suspend functions. Their Java method can return before the
     * compact holder has inflated and measured its text, so a single View.post races the real
     * content and permanently misses every TextView. Follow animation frames until the current
     * generation has usable compact text, then attach the controllers exactly once.
     */
    private fun scheduleMarquee(
        island: ViewGroup,
        token: Any,
        enabled: Boolean,
        mode: MarqueeDismissMode,
        originalTimeoutSecs: Int,
        generation: Long,
        ongoing: Boolean,
        notification: StatusBarNotification?,
        visualBefore: IslandLiveVisual.Snapshot?,
        settleUntilMs: Long,
        attempt: Int = 0,
        previousGeometrySignature: Int? = null,
        stableGeometryFrames: Int = 0,
    ) {
        if (islandTokens[island] !== token) return
        val settleRemainingMs = settleUntilMs - SystemClock.uptimeMillis()
        if (settleRemainingMs > 0L) {
            island.postDelayed(
                {
                    scheduleMarquee(
                        island,
                        token,
                        enabled,
                        mode,
                        originalTimeoutSecs,
                        generation,
                        ongoing,
                        notification,
                        visualBefore,
                        settleUntilMs,
                        attempt,
                        previousGeometrySignature,
                        stableGeometryFrames,
                    )
                },
                settleRemainingMs,
            )
            return
        }
        val compactText = compactTextViews(island)
        val contentReady = compactText.isNotEmpty() && compactText.all {
            it.isAttachedToWindow && availableTextWidth(it) > 0
        }
        val geometrySignature = compactText.takeIf { contentReady }?.let(::geometrySignature)
        val nextStableGeometryFrames = MarqueeMotion.nextStableFrames(
            previousSignature = previousGeometrySignature,
            currentSignature = geometrySignature,
            previousStableFrames = stableGeometryFrames,
        )
        val geometryReady = MarqueeMotion.geometryReady(
            attempt = attempt,
            contentReady = contentReady,
            stableFrames = nextStableGeometryFrames,
            minimumFrames = MIN_CONTENT_READY_FRAMES,
            maximumFrames = MAX_CONTENT_READY_FRAMES,
            requiredStableFrames = REQUIRED_STABLE_GEOMETRY_FRAMES,
        )
        if (enabled && attempt >= MAX_CONTENT_READY_FRAMES && !contentReady) {
            armVisibilityRetry(
                island = island,
                token = token,
                mode = mode,
                originalTimeoutSecs = originalTimeoutSecs,
                generation = generation,
                ongoing = ongoing,
                notification = notification,
            )
            return
        }
        val shouldWait = enabled && !geometryReady
        if (shouldWait) {
            island.postOnAnimation {
                scheduleMarquee(
                    island,
                    token,
                    enabled,
                    mode,
                    originalTimeoutSecs,
                    generation,
                    ongoing,
                    notification,
                    visualBefore,
                    settleUntilMs,
                    attempt + 1,
                    geometrySignature,
                    nextStableGeometryFrames,
                )
            }
            return
        }
        if (enabled) {
            val viewports = compactText.joinToString(",") { view ->
                "${compactAreaName(view)}:${availableTextWidth(view)}/${laidOutTextWidth(view, view.text?.toString().orEmpty()).toInt()}"
            }
            Log.i(
                "HyperBridge",
                "HyperBridge: compact marquee geometry settled " +
                    "key=${islandKeys[island].orEmpty()} attempt=$attempt views=[$viewports]",
            )
        }
        val apply = {
            if (islandTokens[island] === token) {
                applyMarquee(
                    island = island,
                    enabled = enabled,
                    mode = mode,
                    originalTimeoutSecs = originalTimeoutSecs,
                    generation = generation,
                    ongoing = ongoing,
                    notification = notification,
                )
            }
        }
        // Progress interpolation stays on existing widgets. Never rewrite subtitle TextViews.
        runCatching { IslandLiveVisual.play(island, visualBefore, apply) }
            .onFailure { apply() }
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
        removeVisibilityRetry(island)
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
        if (autoHideHeld || IslandReplyComposer.shouldStayExpanded() || !session.mode.overridesTimeout || session.dismissed || session.scrollingViews.isNotEmpty() ||
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
        if (MarqueeTimeoutPolicy.shouldDismiss(session.mode, minLoops, session.ongoing) && !autoHideHeld) {
            dismissIsland(island, session)
        }
    }

    private fun dismissIsland(island: ViewGroup, session: AutoHideSession) {
        if (session.dismissed || session.ongoing || autoHideHeld) return
        val notification = session.notification ?: islandNotifications[island] ?: return
        session.dismissed = true
        cancelFallback(island, session)
        ActiveIslandDismissHook.dismiss(notification, session.generation)
    }

    private fun traverse(view: View, enabled: Boolean) {
        if (view is TextView) {
            if (isExpanded(view)) return
            if (enabled && isUsableCompactText(view)) {
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
        val available = availableTextWidth(view)
        if (available <= 0) return
        val overflow = MarqueeMotion.overflowDistance(
            textWidthPx = scrollingTextWidth(view, clean),
            availableWidthPx = available,
            tolerancePx = overflowTolerancePx(view),
        ) > 0f
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
        removeVisibilityRetry(island)
        islandEnabled[island] = false
        islandSessions.remove(island)?.let { cancelFallback(island, it) }
        islandTextViews(island).forEach {
            unobserve(it)
            stopMarquee(it)
        }
    }

    /**
     * Quiesces our controller without putting Xiaomi's END ellipsize mode back. Restoring it
     * before TimerTextEffectView.setText() makes Xiaomi's createSafeText() permanently replace
     * the incoming full string with the clipped one, and a still-attached TextWatcher can race
     * the native 600 ms glyph transition. Full restoration still happens for non-owned content.
     */
    private fun pauseIsland(island: ViewGroup) {
        removeVisibilityRetry(island)
        islandEnabled[island] = false
        islandSessions.remove(island)?.let { cancelFallback(island, it) }
        islandTextViews(island).forEach { view ->
            unobserve(view)
            controllers.remove(view)?.stop()
            unregisterScrolling(view)
            view.scrollTo(0, 0)
        }
    }

    private fun islandTextViews(island: ViewGroup): List<TextView> =
        (controllers.keys.toList() + observed.keys.toList())
            .distinct()
            .filter { findIsland(it) === island }

    /**
     * Xiaomi keeps previous carousel entries inflated but ancestor-invisible. Their update hook
     * can finish long before the user cycles them into the camera slot, and visibility changes
     * do not trigger TextView layout listeners. Keep a token-scoped pre-draw retry on the shared
     * window tree, then run the normal stable-geometry gate when this entry actually becomes
     * visible. The listener removes itself after one activation or when a newer update wins.
     */
    private fun armVisibilityRetry(
        island: ViewGroup,
        token: Any,
        mode: MarqueeDismissMode,
        originalTimeoutSecs: Int,
        generation: Long,
        ongoing: Boolean,
        notification: StatusBarNotification?,
    ) {
        removeVisibilityRetry(island)
        Log.i(
            "HyperBridge",
            "HyperBridge: compact marquee waiting for visible carousel slot " +
                "key=${islandKeys[island].orEmpty()}",
        )
        lateinit var listener: ViewTreeObserver.OnPreDrawListener
        listener = ViewTreeObserver.OnPreDrawListener {
            if (islandTokens[island] !== token || !island.isAttachedToWindow) {
                removeVisibilityRetry(island, listener)
                return@OnPreDrawListener true
            }
            if (compactTextViews(island).isEmpty()) return@OnPreDrawListener true
            removeVisibilityRetry(island, listener)
            Log.i(
                "HyperBridge",
                "HyperBridge: visible carousel slot restored " +
                    "key=${islandKeys[island].orEmpty()}",
            )
            island.postOnAnimation {
                scheduleMarquee(
                    island = island,
                    token = token,
                    enabled = true,
                    mode = mode,
                    originalTimeoutSecs = originalTimeoutSecs,
                    generation = generation,
                    ongoing = ongoing,
                    notification = notification,
                    visualBefore = null,
                    settleUntilMs = 0L,
                )
            }
            true
        }
        visibilityRetries[island] = listener
        island.viewTreeObserver.takeIf { it.isAlive }?.addOnPreDrawListener(listener)
    }

    private fun removeVisibilityRetry(
        island: ViewGroup,
        expected: ViewTreeObserver.OnPreDrawListener? = null,
    ) {
        val listener = visibilityRetries[island] ?: return
        if (expected != null && listener !== expected) return
        visibilityRetries.remove(island)
        island.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
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
        if (view is TextView) return isUsableCompactText(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) if (hasMarqueeText(view.getChildAt(index))) return true
        }
        return false
    }

    private fun compactTextViews(view: View): List<TextView> {
        val result = ArrayList<TextView>(2)
        collectCompactTextViews(view, result)
        return result
    }

    private fun collectCompactTextViews(view: View, result: MutableList<TextView>) {
        if (view is TextView) {
            if (isUsableCompactText(view)) result += view
            return
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                collectCompactTextViews(view.getChildAt(index), result)
            }
        }
    }

    private fun geometrySignature(views: List<TextView>): Int = views.fold(1) { signature, view ->
        var next = 31 * signature + System.identityHashCode(view)
        next = 31 * next + view.text?.toString().orEmpty().hashCode()
        next = 31 * next + view.width
        next = 31 * next + availableTextWidth(view)
        next
    }

    private fun isUsableCompactText(view: TextView): Boolean =
        view.isShown &&
            !isExpanded(view) &&
            normalize(view.text?.toString().orEmpty()).isNotEmpty()

    private fun isExpanded(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            val name = current.javaClass.simpleName
            if (name.contains("DynamicIslandExpandedView") || name.contains("ExpandedView")) return true
            current = current.parent as? View
        }
        return false
    }

    private fun availableTextWidth(view: TextView): Int {
        if (view.width <= 0) return 0
        val viewLocation = IntArray(2)
        view.getLocationInWindow(viewLocation)
        val textOrigin = viewLocation[0] + view.compoundPaddingLeft
        val viewRight = viewLocation[0] + view.width
        val drawableInset = (view.compoundPaddingRight - view.paddingRight).coerceAtLeast(0)
        compactArea(view)?.let { area ->
            if (area.width <= 0) return 0
            val areaLocation = IntArray(2)
            area.getLocationInWindow(areaLocation)
            val clipLeft = areaLocation[0] + if (area.clipToPadding) area.paddingLeft else 0
            val clipRight = areaLocation[0] + area.width -
                if (area.clipToPadding) area.paddingRight else 0
            return MarqueeMotion.visibleSlotWidth(
                textOrigin = textOrigin,
                viewRight = viewRight,
                rightDrawableInset = drawableInset,
                clipLeft = clipLeft,
                clipRight = clipRight,
            )
        }
        val visible = Rect()
        val clippedRight = if (view.getLocalVisibleRect(visible) && visible.width() > 0) {
            viewLocation[0] + minOf(view.width, visible.right)
        } else {
            viewRight
        }
        return MarqueeMotion.visibleSlotWidth(
            textOrigin = textOrigin,
            viewRight = viewRight,
            rightDrawableInset = drawableInset,
            clipLeft = textOrigin,
            clipRight = clippedRight,
        )
    }

    private fun compactArea(view: TextView): ViewGroup? {
        compactAreas[view]?.get()?.takeIf { isDescendantOf(view, it) }?.let { return it }
        var parent = view.parent
        while (parent is ViewGroup) {
            val entryName = runCatching {
                parent.resources.getResourceEntryName(parent.id)
            }.getOrNull()
            if (entryName == "area_left" || entryName == "area_right") {
                compactAreas[view] = WeakReference(parent)
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    private fun compactAreaName(view: TextView): String = compactArea(view)?.let { area ->
        runCatching { area.resources.getResourceEntryName(area.id) }.getOrNull()
    } ?: "other"

    private fun isDescendantOf(view: View, ancestor: ViewGroup): Boolean {
        var parent = view.parent
        while (parent is ViewGroup) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    private fun overflowTolerancePx(view: TextView): Float =
        maxOf(1f, view.resources.displayMetrics.density * 0.5f)

    private fun scrollingTextWidth(view: TextView, text: String): Float {
        val advance = laidOutTextWidth(view, text)
        if (text.isEmpty()) return advance
        val bounds = Rect()
        view.paint.getTextBounds(text, 0, text.length, bounds)
        return MarqueeMotion.visibleTextWidth(
            advanceWidthPx = advance,
            inkRightPx = bounds.right.toFloat(),
            maxTrimPx = view.resources.displayMetrics.density * 16f,
        )
    }

    private fun laidOutTextWidth(view: TextView, fallbackText: String): Float {
        val layout = view.layout
        if (layout != null && layout.lineCount > 0) {
            var widest = 0f
            for (line in 0 until layout.lineCount) {
                widest = maxOf(widest, layout.getLineWidth(line))
            }
            if (widest > 0f) return widest
        }
        return view.paint.measureText(fallbackText)
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
        private var returnDurationMs = 0L

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
            val maxScroll = MarqueeMotion.overflowDistance(
                textWidthPx = scrollingTextWidth(view, text),
                availableWidthPx = availableTextWidth(view),
                tolerancePx = overflowTolerancePx(view),
            )
            if (maxScroll <= 0f) {
                unregisterScrolling(view)
                stop()
                return
            }
            // Xiaomi can remeasure TimerTextEffectView after the controller starts. Never keep
            // an offset beyond the newly laid-out last glyph, even while paused at the end.
            if (scrollX > maxScroll) {
                scrollX = maxScroll
                view.scrollTo(scrollX.toInt(), 0)
                view.invalidate()
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
                    state = 3
                    returnDurationMs = MarqueeMotion.returnDurationMs(maxScroll, speedPxPerSec)
                    startNanos = frameTimeNanos
                }
                3 -> {
                    scrollX = MarqueeMotion.returnOffset(maxScroll, elapsedMs, returnDurationMs)
                    view.scrollTo(scrollX.toInt(), 0)
                    view.invalidate()
                    if (elapsedMs >= returnDurationMs) {
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
            }
            lastNanos = frameTimeNanos
            choreographer.postFrameCallback(this)
        }
    }
}
