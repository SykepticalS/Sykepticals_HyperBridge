package com.sykeptical.hyperpop.xposed.hooks

import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.view.View
import com.sykeptical.hyperpop.BuildConfig
import com.sykeptical.hyperpop.service.animation.expanded.QuarantineFlush
import com.sykeptical.hyperpop.service.animation.expanded.QuarantineRegion
import com.sykeptical.hyperpop.service.animation.expanded.QuarantineSnapshot
import com.sykeptical.hyperpop.service.animation.expanded.QuarantineTouchMask
import com.sykeptical.hyperpop.service.animation.expanded.SecondaryUiQuarantine
import com.sykeptical.hyperpop.service.animation.expanded.releasesQuarantineForDespan
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * While Xiaomi has an expanded island, every other compact island stays in its
 * native slot but cannot be drawn, hit, or promoted. Releasing the quarantine
 * lets the current flush render natively, then redraws any compact island that
 * flush did not already draw.
 *
 * Slot changes themselves are not blocked. Xiaomi still owns promotion.
 */
object SecondaryQuarantineHook {
    private const val STATE_HANDLER = "miui.systemui.dynamicisland.event.handler.StateHandler"
    private const val DELEGATE = "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
    private const val ANIM_LISTENER = "miui.systemui.dynamicisland.anim.DynamicIslandAnimListener"
    private const val CONTENT = "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val COORDINATOR = "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val TOUCH = "miui.systemui.dynamicisland.touch.domain.interactor.DynamicIslandTouchInteractor"
    private val APP_STATES = setOf(
        "AppExpanded",
        "SubAppExpanded",
        "MiniWindowExpanded",
        "SubMiniWindowExpanded",
    )
    /** Xiaomi's alpha spring response is 0.15. A slightly longer return fade. */
    private const val REVEAL_ALPHA_RESPONSE = 0.18f
    private val BLOCKED_EVENTS = setOf("ClickDynamicIsland", "IslandLongPressed")
    private val GONE_STATES = setOf("Hidden", "Deleted", "Empty")
    /** Xiaomi's animated Expanded -> compact morphs that a replaced owner runs. */
    private val REPLACEMENT_MOTIONS = setOf("expandedToBigIslandAnimation", "expandedToSmallIslandAnimation")
    /** Backstop if neither listener callback of the native morph arrives. */
    private const val HANDOFF_TIMEOUT_MS = 1_500L
    private const val TRACE_TAG = "HyperPopQuarantine"

    private val policy = SecondaryUiQuarantine()
    private val reflect = ReflectionLookup()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pluginLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private var coordinatorRef = java.lang.ref.WeakReference<Any>(null)
    private var ownerViewRef = java.lang.ref.WeakReference<View>(null)
    private var loggedFailure = false
    private var lastViolations = emptySet<Int>()
    private val pendingReveal = mutableSetOf<Int>()
    private val watchedHandoffs = mutableSetOf<Int>()
    /** Replaced owners playing expandedToDeletedAnimation without leaving their slot. */
    private val expiryDismissals = mutableSetOf<Int>()

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookLoader(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hookLoader(module, loader)
        }
    }

    /** The expanded card has finished leaving the status bar. Redraw whatever slots remain. */
    fun onTakeoverSettled() {
        mainHandler.post {
            if (!policy.active) return@post
            val coordinator = coordinatorRef.get() ?: return@post
            val slots = readSlots(coordinator, pending = emptyList()) ?: return@post
            if (slots.snapshot.expandedId != null || slots.snapshot.takeoverHolding) return@post
            val decision = policy.onFlushStart(slots.snapshot)
            if (decision is QuarantineFlush.Release) finishRelease(coordinator, slots, decision.generation)
        }
    }

    private fun hookLoader(module: XposedModule, loader: ClassLoader) {
        if (!pluginLoaders.add(loader)) return
        hookFlush(module, loader)
        val guarded = hookMotion(module, loader)
        hookExpiryRemoval(module, loader)
        hookReplacementEnd(module, loader)
        hookBurnIn(module, loader)
        hookTouch(module, loader)
        hookRegion(module, loader)
        hookDispatch(module, loader)
        module.log("HyperPop: secondary quarantine installed loader=${loader.hashCode()} motions=$guarded")
    }

    private fun hookFlush(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val handler = loader.loadClass(STATE_HANDLER)
            val stop = handler.declaredMethods.firstOrNull { it.name == "stop" && it.parameterTypes.isEmpty() }
                ?: error("stop missing")
            hook(module, loader, stop) { chain -> onStop(chain) }
        }.onFailure { module.log("HyperPop: secondary quarantine flush unavailable: ${it.message}") }
    }

    private fun onStop(chain: Chain): Any? {
        val handler = chain.thisObject ?: return chain.proceed()
        val pending = pendingViews(handler)
        if (pending.isEmpty()) return chain.proceed()
        val eventCoordinator = pending.firstNotNullOfOrNull { view ->
            view.call("getDynamicIslandEventCoordinator")
        } ?: return chain.proceed()
        coordinatorRef = java.lang.ref.WeakReference(eventCoordinator)
        val slots = readSlots(eventCoordinator, pending) ?: return chain.proceed()
        val decision = policy.onFlushStart(slots.snapshot)
        trace {
            "flush ${decision.javaClass.simpleName} owner=${hex(policy.ownerId)} ${describe(slots.snapshot)} " +
                "pending=${pending.joinToString { hex(System.identityHashCode(it)) + ":" + it.call("getState")?.javaClass?.simpleName }} " +
                "handoff=${slots.views.keys.filter { policy.inHandoff(it) }.map(::hex)} " +
                "hidden=${policy.hiddenIds().map(::hex)}"
        }
        watchHandoffs(slots)
        when (decision) {
            is QuarantineFlush.Activate -> sweep(slots, decision.sweepIds, decision.expiryIds)
            is QuarantineFlush.Hold -> sweep(slots, decision.sweepIds)
            is QuarantineFlush.Release -> cancelHidden(slots)
            QuarantineFlush.None -> Unit
        }
        val result = chain.proceed()
        val after = readSlots(eventCoordinator, pending) ?: slots
        if (decision is QuarantineFlush.Release) finishRelease(eventCoordinator, after, decision.generation)
        else policy.onFlushEnd(after.snapshot)
        noteViolations(after.snapshot)
        return result
    }

    private fun finishRelease(coordinator: Any, slots: IslandSlots, generation: Long) {
        val refresh = policy.onFlushEnd(slots.snapshot)
        trace { "release gen=$generation ${describe(slots.snapshot)} refresh=${refresh.map(::hex)}" }
        if (refresh.isEmpty()) return
        pendingReveal += refresh
        mainHandler.post {
            if (!policy.acceptsRefresh(generation)) {
                pendingReveal -= refresh
                return@post
            }
            restore(coordinator, refresh)
        }
    }

    private fun restore(coordinator: Any, ids: Set<Int>) {
        val slots = readSlots(coordinator, pending = emptyList()) ?: return
        val handlers = LinkedHashMap<Int, Any>()
        ids.forEach { id ->
            val view = slots.views[id]
            if (view == null || !view.isAttachedToWindow) {
                pendingReveal.remove(id)
                return@forEach
            }
            val stateName = view.call("getState")?.javaClass?.simpleName
            if (stateName == null || stateName in GONE_STATES) {
                pendingReveal.remove(id)
                return@forEach
            }
            view.call("getAnimatorDelegate")?.let { cancelFolme(it) }
            val handler = slots.handlers[id] ?: return@forEach
            val state = view.call("getState") ?: return@forEach
            trace { "redraw ${hex(id)} state=$stateName ${alphaOf(view)}" }
            if (!invoked(handler, "addState", view, state)) return@forEach
            handlers[System.identityHashCode(handler)] = handler
        }
        handlers.values.forEach { handler -> handler.call("stop") }
    }

    private fun sweep(slots: IslandSlots, ids: Set<Int>, expiryIds: Set<Int> = emptySet()) {
        ids.forEach { id ->
            val view = slots.views[id] ?: return@forEach
            trace { "sweep ${hex(id)} state=${view.call("getState")?.javaClass?.simpleName} ${alphaOf(view)}" }
            hide(view, repeat = true, expiry = id in expiryIds)
            policy.markHidden(id)
        }
    }

    private fun cancelHidden(slots: IslandSlots) {
        policy.hiddenIds().forEach { id ->
            if (id in expiryDismissals) return@forEach
            val view = slots.views[id] ?: return@forEach
            view.call("getAnimatorDelegate")?.let { cancelFolme(it) }
        }
    }

    private fun hookMotion(module: XposedModule, loader: ClassLoader): Int {
        return runCatching {
            val content = loader.loadClass(CONTENT)
            val delegate = loader.loadClass(DELEGATE)
            val methods = delegate.declaredMethods.filter { guardsCompactMotion(it, content) }
            methods.forEach { method ->
                hook(module, loader, method) { chain ->
                    val view = chain.args.getOrNull(0) as? View ?: return@hook chain.proceed()
                    val id = System.identityHashCode(view)
                    if (policy.inHandoff(id)) {
                        if (method.name in REPLACEMENT_MOTIONS) {
                            trace { "motion ${method.name} ${hex(id)} handoff-native ${alphaOf(view)}" }
                            return@hook chain.proceed()
                        }
                        policy.endHandoff(id)
                        trace { "motion ${method.name} ${hex(id)} ends handoff" }
                    }
                    if (!policy.blocks(id)) {
                        val fromHidden = policy.wasHidden(id)
                        if (fromHidden && isSlotRender(method.name)) policy.markRendered(id)
                        val result = chain.proceed()
                        if (fromHidden && isSlotRender(method.name)) expiryDismissals.remove(id)
                        val reveal = isSlotRender(method.name) && (fromHidden || pendingReveal.remove(id))
                        if (reveal) slowReveal(view)
                        if (!method.name.startsWith("onSwipe")) trace {
                            "motion ${method.name} ${hex(id)} native fromHidden=$fromHidden reveal=$reveal " +
                                "state=${view.call("getState")?.javaClass?.simpleName} ${alphaOf(view)}"
                        }
                        return@hook result
                    }
                    if (id in expiryDismissals) {
                        trace { "motion ${method.name} ${hex(id)} expiry in flight" }
                        return@hook null
                    }
                    trace { "motion ${method.name} ${hex(id)} blocked owner=${hex(policy.ownerId)}" }
                    hide(view, repeat = false)
                    policy.markHidden(id)
                    null
                }
            }
            methods.size
        }.getOrElse {
            module.log("HyperPop: secondary quarantine motion unavailable: ${it.message}")
            0
        }
    }

    /**
     * The listener Xiaomi attaches to each native Expanded -> compact morph
     * captures the island as `$view`. Its finish or cancel is where the replaced
     * owner stops being a handoff and joins the settled quarantine.
     */
    private fun hookReplacementEnd(module: XposedModule, loader: ClassLoader) {
        REPLACEMENT_MOTIONS.forEach { motion ->
            runCatching {
                val listener = loader.loadClass("$DELEGATE\$$motion\$1")
                val view = listener.getDeclaredField("\$view").apply { isAccessible = true }
                listener.declaredMethods
                    .filter { (it.name == "onComplete" || it.name == "onCancel") && it.parameterTypes.size == 1 }
                    .forEach { method ->
                        hook(module, loader, method) { chain ->
                            val result = chain.proceed()
                            runCatching { view.get(chain.thisObject) as? View }.getOrNull()?.let { island ->
                                mainHandler.post { runCatching { settleHandoff(island) } }
                            }
                            result
                        }
                    }
            }.onFailure { module.log("HyperPop: secondary quarantine $motion end unavailable: ${it.message}") }
        }
    }

    private fun watchHandoffs(slots: IslandSlots) {
        watchedHandoffs.retainAll { policy.inHandoff(it) }
        slots.views.forEach { (id, view) ->
            if (!policy.inHandoff(id) || !watchedHandoffs.add(id)) return@forEach
            val ref = java.lang.ref.WeakReference(view)
            mainHandler.postDelayed({
                watchedHandoffs.remove(id)
                ref.get()?.let { runCatching { settleHandoff(it) } }
            }, HANDOFF_TIMEOUT_MS)
        }
    }

    private fun settleHandoff(view: View) {
        val id = System.identityHashCode(view)
        val ended = policy.endHandoff(id)
        trace { "settle ${hex(id)} ended=$ended blocks=${policy.blocks(id)} ${alphaOf(view)}" }
        if (!ended || !policy.blocks(id)) return
        hide(view, repeat = false)
        policy.markHidden(id)
    }

    private fun hookBurnIn(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val content = loader.loadClass(CONTENT)
            listOf("animBigIslandBurnIn", "animSmallIslandBurnIn", "animExitBurnIn").forEach { name ->
                content.declaredMethods.filter { it.name == name }.forEach { method ->
                    hook(module, loader, method) { chain ->
                        val self = chain.thisObject as? View
                        val other = chain.args.getOrNull(0) as? View
                        val blocked = (self != null && policy.blocks(System.identityHashCode(self))) ||
                            (other != null && policy.blocks(System.identityHashCode(other)))
                        if (blocked) null else chain.proceed()
                    }
                }
            }
        }.onFailure { module.log("HyperPop: secondary quarantine burn-in unavailable: ${it.message}") }
    }

    private fun hookTouch(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val touch = loader.loadClass(TOUCH)
            val before = setOf("performClick", "performLongClick", "onTouchEvent")
            val after = setOf("onInterceptTouchEvent", "access\$onInterceptTouchEvent")
            touch.declaredMethods.filter { it.name in before || it.name in after }.forEach { method ->
                hook(module, loader, method) { chain ->
                    val host = chain.thisObject
                    if (host != null && method.name in before) clearPress(host)
                    val result = chain.proceed()
                    if (host != null && method.name in after) clearPress(host)
                    result
                }
            }
        }.onFailure { module.log("HyperPop: secondary quarantine touch unavailable: ${it.message}") }
    }

    private fun hookRegion(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val coordinator = loader.loadClass(COORDINATOR)
            coordinator.declaredMethods
                .filter { it.name == "getSmallBigIslandRegion" && it.parameterTypes.isEmpty() }
                .forEach { method ->
                    hook(module, loader, method) { chain ->
                        withoutCompact(chain.thisObject, chain.proceed())
                    }
                }
        }.onFailure { module.log("HyperPop: secondary quarantine region unavailable: ${it.message}") }
    }

    private fun hookDispatch(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val coordinator = loader.loadClass(COORDINATOR)
            coordinator.declaredMethods.filter { it.name == "dispatchPress" && it.parameterTypes.size == 3 }
                .forEach { method ->
                    hook(module, loader, method) { chain ->
                        maskPress(chain, policy.touchMask())
                        chain.proceed()
                    }
                }
            coordinator.declaredMethods.filter { it.name == "dispatchEvent" && it.parameterTypes.size == 2 }
                .forEach { method ->
                    hook(module, loader, method) { chain ->
                        val event = chain.args.getOrNull(0)
                        val view = chain.args.getOrNull(1) as? View
                        if (event != null && view != null &&
                            event.javaClass.simpleName in BLOCKED_EVENTS &&
                            policy.blocks(System.identityHashCode(view))
                        ) {
                            if (BuildConfig.DEBUG) {
                                module.log(
                                    "HyperPop: quarantined ${event.javaClass.simpleName} " +
                                        "id=${System.identityHashCode(view)}",
                                )
                            }
                            return@hook null
                        }
                        chain.proceed()
                    }
                }
        }.onFailure { module.log("HyperPop: secondary quarantine dispatch unavailable: ${it.message}") }
    }

    private fun withoutCompact(host: Any?, native: Any?): Any? {
        val region = native as? Region ?: return native
        if (host == null) return region
        val slots = readSlots(host, pending = emptyList()) ?: return region
        return when (policy.region()) {
            QuarantineRegion.KEEP -> region
            QuarantineRegion.DROP_ALL -> region.apply { setEmpty() }
            QuarantineRegion.DROP_SMALL -> {
                val small = slots.snapshot.smallId?.let { slots.views[it] }
                val big = slots.snapshot.bigId?.let { slots.views[it] }
                if (small != null) region.difference(smallRect(big, small))
                dropExtras(region, slots)
                region
            }
            QuarantineRegion.DROP_BIG -> {
                val big = slots.snapshot.bigId?.let { slots.views[it] }
                if (big != null) region.difference(rectOf(big.call("getBigIslandRect", java.lang.Boolean.FALSE)))
                dropExtras(region, slots)
                region
            }
        }
    }

    private fun dropExtras(region: Region, slots: IslandSlots) {
        val mask = policy.touchMask()
        if (mask.blockShowOnce) {
            slots.snapshot.showOnceId?.let { region.difference(windowRect(slots.views[it])) }
        }
        if (mask.blockBigTemp) {
            slots.snapshot.bigTempId?.let { region.difference(windowRect(slots.views[it])) }
        }
    }

    private fun clearPress(interactor: Any) {
        val mask = policy.touchMask()
        if (mask.blockBig) setBooleanField(interactor, "downInBigIsland", false)
        if (mask.blockSmall) setBooleanField(interactor, "downInSmallIsland", false)
        if (mask.blockShowOnce) setBooleanField(interactor, "downInShowOnceIsland", false)
        if (mask.blockBigTemp) setBooleanField(interactor, "downInBigTempIsland", false)
        if (mask.blockDefault) setBooleanField(interactor, "downInDefault", false)
    }

    private fun maskPress(chain: Chain, mask: QuarantineTouchMask) {
        val args = chain.args
        if (args.size < 2) return
        if (mask.blockBig) args[0] = false
        if (mask.blockSmall) args[1] = false
    }

    private fun noteViolations(snapshot: QuarantineSnapshot) {
        if (!BuildConfig.DEBUG) return
        val found = policy.violations(snapshot)
        if (found.isEmpty()) {
            lastViolations = emptySet()
            return
        }
        if (found == lastViolations) return
        lastViolations = found
        // module is not stored; violations are still visible to unit tests.
        // The flush hook logs through the installed module only when a module
        // reference is available. See [logViolations].
        logViolations(found)
    }

    private var violationLogger: ((String) -> Unit)? = null

    private fun trace(message: () -> String) {
        if (BuildConfig.DEBUG) android.util.Log.d(TRACE_TAG, message())
    }

    private fun hex(id: Int?): String = id?.let(Integer::toHexString) ?: "-"

    private fun describe(snapshot: QuarantineSnapshot): String =
        "exp=${hex(snapshot.expandedId)} big=${hex(snapshot.bigId)} small=${hex(snapshot.smallId)} " +
            "once=${hex(snapshot.showOnceId)} temp=${hex(snapshot.bigTempId)} hold=${snapshot.takeoverHolding}"

    private fun alphaOf(view: View): String {
        val container = view.call("getAnimatorDelegate")?.floatField("containerAlpha")
        return "view=${view.alpha} container=$container"
    }

    private fun logViolations(found: Set<Int>) {
        violationLogger?.invoke("HyperPop: secondary quarantine invariant missing hide $found")
    }

    private fun readSlots(coordinator: Any, pending: List<View>): IslandSlots? {
        val expanded = current(coordinator, "getExpandedStateHandler")
        val bigHandler = coordinator.call("getBigIslandStateHandler")
        val smallHandler = coordinator.call("getSmallIslandStateHandler")
        val showHandler = coordinator.call("getShowOnceIslandHandler")
        val big = bigHandler?.call("getCurrent") as? View
        val small = smallHandler?.call("getCurrent") as? View
        val showOnce = showHandler?.call("getCurrent") as? View
        val bigTemp = bigHandler?.field("currentTempShow") as? View
        if (expanded != null) ownerViewRef = java.lang.ref.WeakReference(expanded)
        val openingApp = isOpeningApp(pending)
        val despan = isDespan(pending)
        val holding = !openingApp && !despan && (
            ExpandedTakeoverHook.holdsQuarantine() ||
                (expanded == null && ExpandedTakeoverHook.takeoverOpen())
            )
        val views = LinkedHashMap<Int, View>()
        val handlers = LinkedHashMap<Int, Any>()
        fun put(view: View?, handler: Any?) {
            if (view == null || handler == null) return
            val id = System.identityHashCode(view)
            views[id] = view
            handlers[id] = handler
        }
        put(big, bigHandler)
        put(small, smallHandler)
        put(showOnce, showHandler)
        put(bigTemp, bigHandler)
        return IslandSlots(
            snapshot = QuarantineSnapshot(
                expandedId = expanded?.let { System.identityHashCode(it) },
                bigId = big?.let { System.identityHashCode(it) },
                smallId = small?.let { System.identityHashCode(it) },
                showOnceId = showOnce?.let { System.identityHashCode(it) },
                bigTempId = bigTemp?.let { System.identityHashCode(it) },
                takeoverHolding = holding,
            ),
            views = views,
            handlers = handlers,
        )
    }

    /**
     * The primary has already returned to the big slot. Release in this flush
     * so the hidden sibling's fade starts with the status-bar fade. A
     * secondary returning to the circle does not release: its card still
     * covers the main island's ears until the takeover settles.
     */
    private fun isDespan(pending: List<View>): Boolean {
        if (!policy.active) return false
        val ownerId = policy.ownerId ?: return false
        val owner = ownerViewRef.get()
        if (owner != null && System.identityHashCode(owner) == ownerId &&
            releasesQuarantineForDespan(owner.call("getState")?.javaClass?.simpleName)
        ) {
            return true
        }
        return pending.any { view ->
            System.identityHashCode(view) == ownerId &&
                releasesQuarantineForDespan(view.call("getState")?.javaClass?.simpleName)
        }
    }

    /**
     * App and mini-window open change the owner's state before [StateHandler.stop].
     * The sibling's promotion can be in another handler's list, so the owner's
     * live state is what releases the quarantine in time for that animation.
     */
    private fun isOpeningApp(pending: List<View>): Boolean {
        val owner = ownerViewRef.get()
        if (owner != null && owner.call("getState")?.javaClass?.simpleName in APP_STATES) return true
        val ownerId = policy.ownerId
        return pending.any { view ->
            System.identityHashCode(view) == ownerId &&
                view.call("getState")?.javaClass?.simpleName in APP_STATES
        }
    }

    private fun current(coordinator: Any, accessor: String): View? =
        coordinator.call(accessor)?.call("getCurrent") as? View

    private fun pendingViews(handler: Any): List<View> {
        val list = handler.field("newList") as? Iterable<*> ?: return emptyList()
        return list.mapNotNull { it as? View }
    }

    private fun hide(view: View, repeat: Boolean, expiry: Boolean = false) {
        val id = System.identityHashCode(view)
        if (id in expiryDismissals) return
        val delegate = view.call("getAnimatorDelegate")
        if (expiry && playExpandedExpiry(view)) return
        if (policy.wasHidden(id) && (!repeat || delegate == null || !stillVisible(view, delegate))) return
        if (delegate == null) {
            trace { "hide ${hex(id)} no delegate: view alpha 0" }
            view.alpha = 0f
            return
        }
        val state = runCatching { delegate.call("getHiddenAnimState") }.getOrNull() ?: run {
            trace { "hide ${hex(id)} no hidden state: view alpha 0" }
            view.alpha = 0f
            return
        }
        val currentX = delegate.floatField("containerX")
        if (currentX != null && kotlin.math.abs(currentX) > 1f) {
            retarget(delegate, state, "CONTAINER_X", currentX)
        }
        retarget(delegate, state, "CONTAINER_ALPHA", 0f)
        val last = view.call("getLastState")?.javaClass?.simpleName
        val immediate = policy.wasHidden(id) || last == null || last == "Init" || last == "Empty"
        if (immediate) cancelFolme(delegate)
        val played = if (immediate) folmeSetTo(delegate, state) else folmeTo(delegate, state)
        trace {
            "hide ${hex(id)} state=${view.call("getState")?.javaClass?.simpleName} last=$last " +
                "instant=$immediate played=$played"
        }
        if (!played) view.alpha = 0f
    }

    /**
     * Xiaomi plays this when an expanded island times out: Folme to the cutout
     * state, with the expanded content fading as it goes. The island stays in
     * its slot; the animation's own end otherwise removes the view.
     */
    private fun playExpandedExpiry(view: View): Boolean {
        ExpandedTakeoverHook.releaseRetiringSurface(view)
        val delegate = view.call("getAnimatorDelegate") ?: return false
        val played = invoked(delegate, "expandedToDeletedAnimation", view)
        if (!played) return false
        expiryDismissals += System.identityHashCode(view)
        trace { "expiry ${hex(System.identityHashCode(view))} expandedToDeletedAnimation" }
        return true
    }

    private fun hookExpiryRemoval(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val delegate = loader.loadClass(DELEGATE)
            delegate.declaredMethods.filter { it.name == "removeViewFromWindow" }.forEach { method ->
                hook(module, loader, method) { chain ->
                    val view = chain.args.getOrNull(0) as? View ?: return@hook chain.proceed()
                    val id = System.identityHashCode(view)
                    val state = view.call("getState")?.javaClass?.simpleName
                    if (id in expiryDismissals && state != "Deleted") {
                        trace { "expiry keep ${hex(id)} state=$state" }
                        return@hook null
                    }
                    chain.proceed()
                }
            }
        }.onFailure { module.log("HyperPop: secondary quarantine expiry removal unavailable: ${it.message}") }
    }

    /**
     * The native return animation has already started. Replay its end state
     * with a slightly slower alpha spring so the fade begins on this frame
     * and finishes a little after Xiaomi's default.
     */
    private fun slowReveal(view: View) {
        val delegate = view.call("getAnimatorDelegate") ?: return
        val state = revealState(delegate, view) ?: return
        val config = slowerAlphaConfig(delegate) ?: return
        folmeTo(delegate, state, config)
    }

    private fun revealState(delegate: Any, view: View): Any? {
        val method = when (view.call("getState")?.javaClass?.simpleName) {
            "SmallIsland" -> "getSmallIslandAnimState"
            "BigIsland", "ShowOnceBigIsland" -> "getBigIslandAnimState"
            else -> return null
        }
        return delegate.call(method)
    }

    private fun slowerAlphaConfig(delegate: Any): Any? {
        val property = delegate.field("CONTAINER_ALPHA") ?: return null
        val loader = delegate.javaClass.classLoader ?: return null
        val ease = runCatching {
            val folmeEase = loader.loadClass("miuix.animation.FolmeEase")
            val spring = folmeEase.methods.firstOrNull { method ->
                method.name == "spring" && method.parameterTypes.size == 2
            } ?: return null
            spring.isAccessible = true
            val scale = delegate.floatField("debugIslandAnimScale") ?: 1f
            spring.invoke(null, 0.95f, REVEAL_ALPHA_RESPONSE * scale)
        }.getOrNull() ?: return null
        return runCatching {
            val configClass = loader.loadClass("miuix.animation.base.AnimConfig")
            val config = configClass.getDeclaredConstructor().newInstance()
            val setSpecial = configClass.methods.firstOrNull { method ->
                method.name == "setSpecial" &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0].isAssignableFrom(property.javaClass) &&
                    method.parameterTypes[1].isAssignableFrom(ease.javaClass) &&
                    method.parameterTypes[2].isArray
            } ?: return null
            setSpecial.isAccessible = true
            val factors = java.lang.reflect.Array.newInstance(java.lang.Float.TYPE, 0)
            setSpecial.invoke(config, property, ease, factors)
            config
        }.getOrNull()
    }

    private fun stillVisible(view: View, delegate: Any): Boolean {
        if (view.alpha > 0.05f) return true
        val alpha = delegate.floatField("containerAlpha") ?: return false
        return alpha > 0.05f
    }

    /**
     * Compact-slot motion only. Methods that enter Expanded, App, MiniWindow, or
     * Deleted stay native: a quarantined current is not the view taking those states.
     */
    private fun guardsCompactMotion(method: Method, content: Class<*>): Boolean {
        val params = method.parameterTypes
        if (params.isEmpty() || !content.isAssignableFrom(params[0])) return false
        val name = method.name
        if (name.contains("Deleted") || name.contains("App") || name.contains("Mini") || name.contains("Fake")) {
            return false
        }
        if (name.contains("ToExpanded") || name.contains("toExpanded")) return false
        return name.startsWith("small") ||
            name.startsWith("big") ||
            name.startsWith("hidden") ||
            name.startsWith("init") ||
            name.startsWith("tempHidden") ||
            name.startsWith("updateOrientation") ||
            name.startsWith("expandedTo") ||
            name.startsWith("before") ||
            name.startsWith("onSwipe") ||
            name.startsWith("onPress") ||
            name.startsWith("resetPress") ||
            name.startsWith("resetSwipe") ||
            name.startsWith("resetTo") ||
            name.contains("ScaleAnimation")
    }

    private fun isSlotRender(name: String): Boolean {
        if (name.contains("Scale") || name.startsWith("onSwipe") || name.startsWith("onPress")) return false
        return name.endsWith("Animation") || name.endsWith("NoAnimation") || name.endsWith("NoAnim")
    }

    private fun smallRect(big: View?, small: View): Rect? {
        val anchor = big ?: return windowRect(small.call("getSmallIslandView") as? View)
        val rtl = small.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val bigX = (anchor.call("getCurrentBigIslandX", java.lang.Boolean.FALSE) as? Number)?.toInt()
            ?: return windowRect(small.call("getSmallIslandView") as? View)
        val bigWidth = (anchor.call("getCurrentBigIslandWidth", java.lang.Boolean.FALSE) as? Number)?.toInt()
            ?: return null
        val space = (anchor.call("getSpace") as? Number)?.toInt() ?: return null
        val smallWidth = (small.call("getSmallIslandViewWidth") as? Number)?.toInt() ?: return null
        val x = if (rtl) bigX - space - smallWidth else bigX + bigWidth + space
        return rectOf(small.call("getSmallIslandRect", x))
            ?: windowRect(small.call("getSmallIslandView") as? View)
    }

    private fun Region.difference(rect: Rect?) {
        if (rect == null || rect.isEmpty) return
        op(Region(rect), Region.Op.DIFFERENCE)
    }

    private fun rectOf(value: Any?): Rect? {
        val rect = value as? Rect ?: return null
        if (rect.isEmpty) return null
        return rect
    }

    private fun windowRect(view: View?): Rect? {
        view ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun retarget(delegate: Any, state: Any, propertyName: String, value: Float) {
        val target = delegate.field(propertyName) ?: return
        val add = state.javaClass.methods.firstOrNull { method ->
            method.name == "add" &&
                method.parameterTypes.size >= 2 &&
                method.parameterTypes[1] == Float::class.javaPrimitiveType &&
                method.parameterTypes[0].isAssignableFrom(target.javaClass)
        } ?: return
        add.isAccessible = true
        if (add.parameterTypes.size == 2) add.invoke(state, target, value)
        else add.invoke(state, target, value, LongArray(0))
    }

    /**
     * Folme only moves the delegate's fields. Xiaomi pushes them to the views in
     * [ANIM_LISTENER]'s onUpdate, so every transition started here carries it.
     */
    private fun folmeTo(delegate: Any, state: Any, config: Any? = null): Boolean =
        folmeInvoke(delegate, state, "to", config = withViewUpdates(delegate, config))

    private fun folmeSetTo(delegate: Any, state: Any): Boolean {
        val set = folmeInvoke(delegate, state, "setTo") ||
            folmeInvoke(delegate, state, "setTo", withConfigs = false)
        if (set) invoked(delegate, "scheduleUpdate")
        return set
    }

    private fun withViewUpdates(delegate: Any, config: Any?): Any? {
        val loader = delegate.javaClass.classLoader ?: return config
        return runCatching {
            val configClass = loader.loadClass("miuix.animation.base.AnimConfig")
            val target = config ?: configClass.getDeclaredConstructor().newInstance()
            val listener = loader.loadClass(ANIM_LISTENER).declaredConstructors
                .first { it.parameterTypes.size == 1 && it.parameterTypes[0].isInstance(delegate) }
                .apply { isAccessible = true }
                .newInstance(delegate)
            val add = configClass.methods.first { method ->
                method.name == "addListeners" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0].isArray &&
                    method.parameterTypes[0].componentType!!.isInstance(listener)
            }
            val listeners = java.lang.reflect.Array.newInstance(add.parameterTypes[0].componentType!!, 1)
            java.lang.reflect.Array.set(listeners, 0, listener)
            add.invoke(target, listeners)
            target
        }.getOrDefault(config)
    }

    private fun cancelFolme(delegate: Any): Boolean {
        val folme = folmeOf(delegate) ?: return false
        val cancel = folme.javaClass.methods.firstOrNull { method ->
            method.name == "cancel" && method.parameterTypes.isEmpty()
        } ?: return false
        cancel.isAccessible = true
        return runCatching { cancel.invoke(folme) }.isSuccess
    }

    private fun folmeOf(delegate: Any): Any? {
        val loader = delegate.javaClass.classLoader ?: return null
        val folmeKt = runCatching { loader.loadClass("miui.systemui.animation.FolmeKt") }.getOrNull() ?: return null
        val getFolme = folmeKt.declaredMethods.firstOrNull { method ->
            method.name == "getFolme" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].isInstance(delegate)
        } ?: return null
        getFolme.isAccessible = true
        return runCatching { getFolme.invoke(null, delegate) }.getOrNull()
    }

    private fun folmeInvoke(
        delegate: Any,
        state: Any,
        name: String,
        withConfigs: Boolean = true,
        config: Any? = null,
    ): Boolean {
        val folme = folmeOf(delegate) ?: return false
        if (!withConfigs) {
            val method = folme.javaClass.methods.firstOrNull { candidate ->
                candidate.name == name &&
                    candidate.parameterTypes.size == 1 &&
                    candidate.parameterTypes[0].isAssignableFrom(state.javaClass)
            } ?: return false
            method.isAccessible = true
            return runCatching { method.invoke(folme, state) }.isSuccess
        }
        val loader = delegate.javaClass.classLoader ?: return false
        val configClass = runCatching { loader.loadClass("miuix.animation.base.AnimConfig") }.getOrNull()
            ?: return false
        val configs = if (config != null && configClass.isInstance(config)) {
            java.lang.reflect.Array.newInstance(configClass, 1).also { array ->
                java.lang.reflect.Array.set(array, 0, config)
            }
        } else {
            java.lang.reflect.Array.newInstance(configClass, 0)
        }
        val method = folme.javaClass.methods.firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterTypes.size == 2 &&
                !candidate.parameterTypes[0].isArray &&
                candidate.parameterTypes[1].isArray &&
                candidate.parameterTypes[0].isAssignableFrom(state.javaClass)
        } ?: return false
        method.isAccessible = true
        return runCatching { method.invoke(folme, state, configs) }.isSuccess
    }

    private fun invoked(target: Any, name: String, vararg args: Any?): Boolean {
        val method = reflect.method(target.javaClass, name, args.size) ?: return false
        return runCatching { method.invoke(target, *args) }.isSuccess
    }

    private fun hook(
        module: XposedModule,
        loader: ClassLoader,
        method: Method,
        block: (Chain) -> Any?,
    ) {
        if (violationLogger == null) violationLogger = { message -> module.log(message) }
        method.isAccessible = true
        runCatching { module.deoptimize(method) }
        module.hook(method).intercept { chain ->
            try {
                block(chain)
            } catch (error: Throwable) {
                if (!loggedFailure) {
                    loggedFailure = true
                    module.log("HyperPop: secondary quarantine ${method.name} failed open: ${error.message}")
                }
                chain.proceed()
            }
        }
    }

    private class IslandSlots(
        val snapshot: QuarantineSnapshot,
        val views: Map<Int, View>,
        val handlers: Map<Int, Any>,
    )
}

private fun Any.call(name: String, vararg args: Any?): Any? {
    val method = quarantineReflect.method(javaClass, name, args.size) ?: return null
    return runCatching { method.invoke(this, *args) }.getOrNull()
}

private fun Any.field(name: String): Any? {
    val found = quarantineReflect.field(javaClass, name) ?: return null
    return runCatching {
        found.get(if (java.lang.reflect.Modifier.isStatic(found.modifiers)) null else this)
    }.getOrNull()
}

private fun Any.floatField(name: String): Float? = (field(name) as? Number)?.toFloat()

private fun setBooleanField(target: Any, name: String, value: Boolean) {
    val found = quarantineReflect.field(target.javaClass, name) ?: return
    runCatching { found.setBoolean(target, value) }
}

private val quarantineReflect = ReflectionLookup()
