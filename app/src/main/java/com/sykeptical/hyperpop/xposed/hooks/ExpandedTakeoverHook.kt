package com.sykeptical.hyperpop.xposed.hooks

import android.app.KeyguardManager
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.sykeptical.hyperpop.service.animation.expanded.AppCloseOverlayPolicy
import com.sykeptical.hyperpop.service.animation.expanded.CameraBandGeometry
import com.sykeptical.hyperpop.service.animation.expanded.EnqueueExpansion
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedContentProfile
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedDecisionRefreshPolicy
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedFakeMirrorPolicy
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedIslandLayoutPolicy
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedLayoutDecision
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedLayoutRequest
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedSurfaceStyle
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedTakeoverCoordinator
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedVisualStyle
import com.sykeptical.hyperpop.service.animation.expanded.IslandRect
import com.sykeptical.hyperpop.service.animation.expanded.RetiringFrame
import com.sykeptical.hyperpop.service.animation.expanded.TakeoverPhase
import com.sykeptical.hyperpop.BuildConfig
import com.sykeptical.hyperpop.xposed.HookConfig
import com.sykeptical.hyperpop.xposed.log
import com.sykeptical.hyperpop.xposed.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Portrait expanded islands grow from the camera over the status bar.
 *
 * Xiaomi still owns the Folme transition. This hook only replaces the geometry
 * that transition reads: expanded Y and height, the clip bottom (Xiaomi's
 * getter truncates height to a multiple of the compact height), and the touch
 * region. Compact ears keep Xiaomi's expanded end state (alpha 0, blur 1) so
 * their fade can finish. Whichever island is expanding owns that fade. Any
 * other compact island is quarantined by [SecondaryQuarantineHook] for as
 * long as Xiaomi still has an expanded current, including while this card is
 * still collapsing over the status bar.
 */
object ExpandedTakeoverHook {
    private const val BASE =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val DELEGATE =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate"
    private const val COORDINATOR =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val PHONE_HELPER =
        "miui.systemui.dynamicisland.window.content.helpers.DynamicIslandContentViewPhoneHelper"
    private const val ADD =
        "miui.systemui.dynamicisland.event.AddEventCoordinator"
    private const val HANDOFF_REASON = "hyperpop_handoff"
    private const val FAKE_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView"
    private const val BACKGROUND_VIEW =
        "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
    private const val UTILS = "miui.systemui.util.CommonUtils"
    private const val SELF_CHECK_PX = 20
    /** Backstop for a replaced owner whose native compact morph stops emitting frames. */
    private const val RETIRE_TIMEOUT_MS = 2_000L
    private const val TRACE_TAG = "HyperPopTakeover"
    private val COLLAPSE_STATES = setOf("BigIsland", "SmallIsland")
    private val pluginLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val brokenLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val coordinator = ExpandedTakeoverCoordinator()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val realSaved = SavedLayout()
    private val fakeSaved = SavedLayout()
    private val fakeMarkers = Collections.synchronizedMap(
        WeakHashMap<View, ExpandedFakeMirrorPolicy.Marker>(),
    )
    private val fakeNativeRadii = Collections.synchronizedMap(WeakHashMap<View, Float>())
    private val heldAdds = ConcurrentHashMap<Long, HeldAdd>()
    /** Owners Xiaomi replaced in place, keyed by island id, until their compact morph lands. */
    private val retired = ConcurrentHashMap<Int, Session>()
    private val compactReplayKey = ThreadLocal<String?>()
    private val lock = Any()

    @Volatile private var selfCheckFailed = false
    @Volatile private var loggedFailure = false
    @Volatile private var session: Session? = null
    @Volatile private var expandRetryUntilMs = 0L
    @Volatile private var layoutStamp: Int = 0
    private val nativeContentVersion = java.util.concurrent.atomic.AtomicInteger()
    @Volatile private var mirroredExtension = Int.MIN_VALUE
    @Volatile private var fakeRestoreDeferred = false
    private var deferredFakeKeys: Set<View>? = null
    private var deferredShownKeys: Set<View>? = null

    private var tabletMethod: Method? = null
    private var tabletInstance: Any? = null
    private var tabletResolved = false

    /** True while the expanded card is collapsing back over the status bar. */
    fun holdsQuarantine(): Boolean = coordinator.phase == TakeoverPhase.COLLAPSING

    /** True from arm until the takeover has fully returned to Xiaomi. */
    fun takeoverOpen(): Boolean = coordinator.phase != TakeoverPhase.NATIVE

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookPlugin(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hookPlugin(module, loader)
        }
    }

    private fun hookPlugin(module: XposedModule, loader: ClassLoader) {
        if (!pluginLoaders.add(loader)) return
        runCatching {
            val base = loader.loadClass(BASE)
            val delegate = loader.loadClass(DELEGATE)
            val coordinatorClass = loader.loadClass(COORDINATOR)
            resolveTablet(loader)
            val expandedY = base.noArg("getExpandedViewY")
            val expandedHeight = base.noArg("getExpandedViewHeight")
            val expandedBottom = delegate.noArg("getExpandedBottom")
            val animState = delegate.noArg("getExpandedAnimState")
            val schedule = delegate.noArg("containerScheduleUpdate")
            val region = coordinatorClass.noArg("getExpandedIslandRegion")
            val setState = base.methodsNamed("setState").firstOrNull { it.parameterTypes.size == 1 }
                ?: error("setState missing")
            val updateSize = base.methodsNamed("updateExpandedSize").firstOrNull()
                ?: error("updateExpandedSize missing")
            hook(module, loader, expandedY) { chain -> overriddenY(chain.thisObject as View, chain.proceed()) }
            hook(module, loader, expandedHeight) { chain -> overriddenHeight(chain.thisObject as View, chain.proceed()) }
            hook(module, loader, expandedBottom) { chain -> overriddenBottom(chain.thisObject, chain.proceed()) }
            hook(module, loader, animState) { chain ->
                val view = runCatching { contentView(chain.thisObject) }.getOrNull()
                if (view != null) runCatching { beginOrContinue(view) }
                chain.proceed()
            }
            hook(module, loader, schedule) { chain ->
                capClipBottom(chain.thisObject)
                val result = chain.proceed()
                onFrame(chain.thisObject)
                result
            }
            delegate.noArgOrNull("containerClipRadius")?.let { method ->
                hook(module, loader, method) { chain -> overriddenRadius(chain.thisObject, chain.proceed()) }
            }
            base.methodsNamed("updateDarkLightMode").forEach { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    if (ExpandedSurfaceStyler.owns(view)) ExpandedSurfaceStyler.onDrawableReplaced(view)
                    result
                }
            }
            base.methodsNamed("updateBackgroundBg").filter { it.parameterTypes.size == 2 }.forEach { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    ExpandedSurfaceStyler.onBlurReapplied(chain.thisObject as View)
                    ExpandedSurfaceStyler.onFakeBlurReapplied(chain.thisObject as View)
                    result
                }
            }
            hookOwnedPlate(module, loader)
            hook(module, loader, region) { chain -> narrowedRegion(chain.thisObject, chain.proceed()) }
            hook(module, loader, setState) { chain ->
                val view = chain.thisObject as View
                val state = chain.args.getOrNull(0)
                val name = state?.javaClass?.simpleName
                val ownerId = System.identityHashCode(view)
                if (name == "Expanded") {
                    finishRetire(view)
                    beginOrContinue(view)
                } else if (name in COLLAPSE_STATES && ownerIs(ownerId)) {
                    coordinator.beginCollapse(ownerId, coordinator.generation)
                }
                val result = chain.proceed()
                onState(view, state)
                result
            }
            hook(module, loader, updateSize) { chain ->
                val result = chain.proceed()
                val view = chain.thisObject as View
                if (synchronized(lock) { session } == null &&
                    view.call("getState")?.javaClass?.simpleName == "Expanded"
                ) {
                    beginOrContinue(view)
                } else {
                    reapplyOffset(view)
                }
                result
            }
            base.noArgOrNull("onDetachedFromWindow")?.let { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    abandon(chain.thisObject as View)
                    result
                }
            }
            base.noArgOrNull("reset")?.let { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    abandon(chain.thisObject as View)
                    result
                }
            }
            hookIncomingExpansion(module, loader)
            hookFakeExpandedOverlay(module, loader)
            deoptimizeCallers(module, base, delegate, coordinatorClass, loader)
            FocusIslandLayoutApplier.install(module, loader)
            module.log("HyperPop: expand-over-status-bar hook installed loader=${loader.hashCode()}")
        }.onFailure {
            pluginLoaders.remove(loader)
            brokenLoaders.add(loader)
            module.log(
                "HyperPop: expand-over-status-bar hook unavailable " +
                    "loader=${loader.hashCode()}: ${it.message}",
            )
        }
    }

    private fun overriddenY(view: View, native: Any?): Any? {
        val takeover = geometryDecision(view) ?: return native
        return takeover.card.top
    }

    private fun overriddenHeight(view: View, native: Any?): Any? {
        val takeover = geometryDecision(view) ?: return native
        val base = (native as? Number)?.toInt() ?: return native
        return if (takeover.tightLayout || takeover.pillEnabled) takeover.card.height else base + takeover.bodyOffsetPx
    }

    private fun overriddenRadius(delegate: Any, native: Any?): Any? {
        val value = (native as? Number)?.toFloat() ?: return native
        val view = contentView(delegate) ?: return value
        val current = retiringSession(view)
            ?: activeDecision(view)?.let { synchronized(lock) { session } }
            ?: return value
        val takeover = current.decision
        if (!takeover.pillEnabled) return value
        val shown = shownHeight(view, clipHeight(delegate).toInt())
        if (shown <= 0) return value
        val progress = ExpandedSurfaceStyle.morphProgress(
            shown,
            current.compact.height,
            takeover.card.height,
        )
        val nativeCap = takeover.nativeRadiusPx.takeIf { it > 0f } ?: value
        return ExpandedSurfaceStyle.clipRadius(shown.toFloat(), nativeCap, takeover.radiusPx, pill = true, progress)
    }

    private fun overriddenBottom(delegate: Any, native: Any?): Any? {
        val view = contentView(delegate) ?: return native
        val takeover = geometryDecision(view) ?: return native
        return takeover.card.bottom.toFloat()
    }

    /** The owner's decision, or a replaced owner's while its native compact morph runs. */
    private fun geometryDecision(view: View): ExpandedLayoutDecision.Takeover? =
        retiringSession(view)?.decision ?: activeDecision(view)

    private fun retiringSession(view: View): Session? {
        if (retired.isEmpty()) return null
        val id = System.identityHashCode(view)
        val current = retired[id] ?: return null
        if (current.owner.get() !== view || !coordinator.retires(id, current.generation)) return null
        return current
    }

    private fun onState(view: View, state: Any?) {
        val name = state?.javaClass?.simpleName ?: return
        val id = System.identityHashCode(view)
        if (name !in COLLAPSE_STATES) finishRetire(view)
        when {
            name == "Expanded" -> beginOrContinue(view)
            name in COLLAPSE_STATES && ownerIs(id) -> coordinator.beginCollapse(id, coordinator.generation)
            ownerIs(id) -> abandon(view)
        }
    }

    private fun onFrame(delegate: Any) {
        val view = contentView(delegate) ?: return
        retiringSession(view)?.let { retiring ->
            retiringFrame(view, delegate, retiring)
            return
        }
        if (synchronized(lock) { session } == null &&
            SystemClock.uptimeMillis() < expandRetryUntilMs &&
            view.call("getState")?.javaClass?.simpleName == "Expanded"
        ) {
            beginOrContinue(view)
        }
        val current = synchronized(lock) { session } ?: return
        if (!ownerIs(System.identityHashCode(view))) return
        if (blocked(view)) {
            abandon(view)
            return
        }
        if (!HookConfig.expandOverStatusBarEnabled()) coordinator.requestDisable()
        if (tempHidden(view)) {
            abandon(view)
            return
        }
        val live = liveRect(delegate) ?: return
        val accepted = coordinator.onFrame(
            ownerId = System.identityHashCode(view),
            generation = current.generation,
            live = live,
            compact = current.compact,
            target = current.decision.card,
            statusGroup = StatusBarTakeoverHook.groupBounds(),
            nowMs = SystemClock.uptimeMillis(),
        )
        if (!accepted) return
        if (coordinator.phase.name == "NATIVE") {
            restore(view)
            return
        }
        present(view, delegate, current, live)
        StatusBarTakeoverHook.apply(coordinator.statusBarAlpha)
    }

    /**
     * Xiaomi is morphing a replaced owner into its compact slot with its own
     * Expanded -> Big/Small animation. Only the pill radius and the plate
     * follow that morph here; the bounds are Xiaomi's Folme state.
     */
    private fun retiringFrame(view: View, delegate: Any, retiring: Session) {
        if (blocked(view) || tempHidden(view)) {
            finishRetire(view)
            return
        }
        val live = liveRect(delegate) ?: return
        val frame = coordinator.onRetiringFrame(
            id = System.identityHashCode(view),
            generation = retiring.generation,
            live = live,
            compact = retiring.compact,
            target = retiring.decision.card,
            statusGroup = StatusBarTakeoverHook.groupBounds(),
        )
        when (frame) {
            RetiringFrame.SETTLED -> finishRetire(view)
            RetiringFrame.MOVING -> {
                presentSurface(view, delegate, retiring, live, armIfMissing = false)
                StatusBarTakeoverHook.apply(coordinator.statusBarAlpha)
            }
            RetiringFrame.IGNORED -> Unit
        }
    }

    private fun trace(message: () -> String) {
        if (BuildConfig.DEBUG) android.util.Log.d(TRACE_TAG, message())
    }

    private fun hex(id: Int?): String = id?.let(Integer::toHexString) ?: "-"

    private fun retire(view: View, retiring: Session) {
        val id = System.identityHashCode(view)
        trace { "retire ${hex(id)} gen=${retiring.generation} state=${view.call("getState")?.javaClass?.simpleName}" }
        retired[id] = retiring
        ExpandedSurfaceStyler.retire(view)
        mainHandler.postDelayed({
            if (retired[id] === retiring) dropRetiring(id, retiring)
        }, RETIRE_TIMEOUT_MS)
    }

    private fun dropRetiring(id: Int, retiring: Session) {
        val island = retiring.owner.get()
        if (island != null) {
            finishRetire(island)
        } else if (retired.remove(id, retiring)) {
            coordinator.finishRetiring(id, retiring.generation)
        }
    }

    /** The replaced owner reached its compact slot, left it, or detached. */
    private fun finishRetire(view: View) {
        if (retired.isEmpty()) return
        val id = System.identityHashCode(view)
        val retiring = retired.remove(id) ?: return
        trace {
            "finishRetire ${hex(id)} state=${view.call("getState")?.javaClass?.simpleName} " +
                "alpha=${view.alpha} at=${Throwable().stackTrace.getOrNull(1)?.methodName}"
        }
        coordinator.finishRetiring(id, retiring.generation)
        val scopes = listOfNotNull(view, runCatching { view.call("getFakeView") as? View }.getOrNull())
        realSaved.restoreWithin(scopes)
        fakeSaved.restoreWithin(scopes)
        runCatching { fakeLeaf(view) }.getOrNull()?.let { fakeMarkers.remove(it) }
        ExpandedSurfaceStyler.restore(view)
        ExpandedFlowMaskApplicator.clearWithin(scopes)
        ExpandedMediaSurfaceApplicator.restoreWithin(scopes)
        FocusIslandLayoutApplier.restoreTitlesWithin(scopes)
        if (coordinator.phase == TakeoverPhase.NATIVE && !coordinator.hasRetiring()) StatusBarTakeoverHook.restore()
        else StatusBarTakeoverHook.apply(coordinator.statusBarAlpha)
    }

    /** A global restore would undo a retiring island's layout too, so those end first. */
    private fun finishAllRetiring() {
        retired.entries.toList().forEach { (id, retiring) -> dropRetiring(id, retiring) }
    }

    private fun narrowedRegion(eventCoordinator: Any, native: Any?): Any? {
        val region = native as? Region ?: return native
        val current = synchronized(lock) { session } ?: return region
        val expanded = eventCoordinator.call("getExpandedStateHandler")?.call("getCurrent")
        if (expanded == null || System.identityHashCode(expanded) != coordinator.ownerId) return region
        if (region.isEmpty) return region
        val bounds = region.bounds
        if (!current.selfCheckDone) {
            current.selfCheckDone = true
            val card = current.decision.card
            if (kotlin.math.abs(bounds.top - card.top) > SELF_CHECK_PX ||
                kotlin.math.abs(bounds.bottom - card.bottom) > SELF_CHECK_PX
            ) {
                selfCheckFailed = true
                coordinator.requestDisable()
                current.owner.get()?.let { abandon(it) }
                return region
            }
        }
        if (selfCheckFailed) return region
        region.setEmpty()
        current.decision.touchRegions.forEach { rect ->
            region.op(Region(rect.left, rect.top, rect.right, rect.bottom), Region.Op.UNION)
        }
        return region
    }


    private fun beginOrContinue(view: View): ExpandedLayoutDecision.Takeover? {
        if (selfCheckFailed) return null
        val id = System.identityHashCode(view)
        // Only a real return to Expanded (setState/onState) ends a retirement first.
        if (retired.containsKey(id)) return null
        val existing = synchronized(lock) { session }
        if (existing != null && coordinator.accepts(id, existing.generation)) {
            if (blocked(view) || tempHidden(view)) {
                abandon(view)
                return null
            }
            if (!HookConfig.expandOverStatusBarEnabled()) coordinator.requestDisable()
            uncoverRealIsland(view)
            // Xiaomi builds the expanded target right after this call. A session
            // armed before the template was complete reads it now, not after.
            if (ExpandedDecisionRefreshPolicy.needsSettle(
                    existing.provisional,
                    nativeContentVersion.get(),
                    existing.settledVersion,
                ) && settle(view, existing)
            ) {
                applyResolved(view, existing)
            }
            return existing.decision
        }
        if (!HookConfig.expandOverStatusBarEnabled()) {
            coordinator.requestDisable()
            return null
        }
        coordinator.requestEnable()
        if (blocked(view)) return null
        uncoverRealIsland(view)
        val fromSmallIsland = view.call("getState")?.javaClass?.simpleName == "SmallIsland"
        val content = realContent(view)
        val readVersion = nativeContentVersion.get()
        val profile = content?.let {
            runCatching { ExpandedContentProbe.capture(it, expandedWidthHint(view)) }.getOrNull()
        }
        val style = ExpandedVisualStyle(
            blackBackground = HookConfig.expandedBlackBackground(),
            roundedPill = HookConfig.expandedRoundedPill(),
        )
        val request = layoutRequest(view, fromSmallIsland, profile, style) ?: return rejectExpand(view)
        val resolved = resolveLayout(request)
        val decision = resolved.second
        if (decision !is ExpandedLayoutDecision.Takeover) return rejectExpand(view)
        val now = SystemClock.uptimeMillis()
        val outgoing = existing?.owner?.get()?.takeIf {
            it !== view && it.isAttachedToWindow && coordinator.ownerId == System.identityHashCode(it)
        }
        // Xiaomi's handleReplacedState is moving the current owner to a compact
        // slot in this same operation. It keeps its geometry until it lands.
        val replacement = if (outgoing != null) coordinator.replace(id, now) else null
        val generation = replacement?.generation ?: coordinator.arm(id, now)
        trace {
            "arm ${hex(id)} gen=$generation outgoing=${hex(outgoing?.let(System::identityHashCode))} " +
                "replacement=${replacement != null} phase=${coordinator.phase}"
        }
        if (generation < 0) return rejectExpand(view)
        expandRetryUntilMs = 0L
        val previous = synchronized(lock) {
            val old = session
            session = Session(
                view,
                generation,
                decision,
                resolved.first.compact,
                fromSmallIsland,
                profile?.nativeTopMarginPx ?: 0,
                style,
                provisional = profile == null,
                settledVersion = readVersion,
            )
            old
        }
        previous?.owner?.get()?.takeIf { it !== view }?.let { old ->
            if (replacement?.retired?.id == System.identityHashCode(old)) {
                retire(old, previous)
            } else {
                finishAllRetiring()
                val deferred = restoreOffsets(old)
                ExpandedSurfaceStyler.restore(old)
                ExpandedFlowMaskApplicator.clear()
                ExpandedMediaSurfaceApplicator.restoreReal(endDrag = !deferred)
                if (!deferred) ExpandedMediaSurfaceApplicator.restoreShown()
            }
            mirroredExtension = Int.MIN_VALUE
        }
        layoutStamp = ExpandedFakeMirrorPolicy.stamp(
            decision.bodyOffsetPx,
            decision.card.bottom,
            decision.contentScale,
        )
        applyOffset(view, decision.bodyOffsetPx)
        ExpandedVisualSession.publish(request.cutout, decision.pillEnabled, request.rtl)
        realContent(view)?.let { content ->
            ExpandedLayoutProbe.capture(content, decision.card, request.cutout, displayCutoutWidth(view))
        }
        tuneRoots(view, decision)
        if (decision.blackBackground) ExpandedSurfaceStyler.arm(view, black = true)
        else if (decision.pillEnabled) ExpandedSurfaceStyler.arm(view, black = false)
        if (decision.blackBackground || decision.pillEnabled) {
            val radius = if (decision.pillEnabled) decision.radiusPx else 0f
            ExpandedSurfaceStyler.frame(view, 1f, radius)
        }
        scheduleWatchdog(view, generation)
        return decision
    }

    private fun activeDecision(view: View): ExpandedLayoutDecision.Takeover? {
        if (selfCheckFailed) return null
        val current = synchronized(lock) { session } ?: return null
        if (!coordinator.accepts(System.identityHashCode(view), current.generation)) return null
        if (blocked(view)) {
            abandon(view)
            return null
        }
        return current.decision
    }

    private fun abandon(view: View) {
        val id = System.identityHashCode(view)
        if (retired.containsKey(id)) {
            finishRetire(view)
            return
        }
        val current = synchronized(lock) { session } ?: return
        if (current.owner.get() !== view && coordinator.ownerId != id) return
        coordinator.abandon(id, current.generation)
        synchronized(lock) {
            if (session === current) session = null
        }
        restore(view)
    }

    private fun restore(view: View) {
        val pending = if (coordinator.phase == TakeoverPhase.NATIVE) coordinator.takePendingExpansion() else null
        trace { "restore ${hex(System.identityHashCode(view))} pending=${pending != null} retiring=${retired.keys.map(::hex)}" }
        finishAllRetiring()
        restoreOffsets(view)
        FocusIslandLayoutApplier.restoreTitles()
        ExpandedVisualSession.clear()
        ExpandedSurfaceStyler.restore(view)
        ExpandedFlowMaskApplicator.clear()
        ExpandedMediaSurfaceApplicator.restoreReal(endDrag = !fakeRestoreDeferred)
        if (!fakeRestoreDeferred) ExpandedMediaSurfaceApplicator.restoreShown()
        mirroredExtension = Int.MIN_VALUE
        StatusBarTakeoverHook.restore()
        synchronized(lock) {
            if (coordinator.phase == TakeoverPhase.NATIVE) session = null
        }
        releaseHeld(pending, expand = true)
        if (coordinator.phase == TakeoverPhase.NATIVE) SecondaryQuarantineHook.onTakeoverSettled()
    }

    private fun reapplyOffset(view: View) {
        val current = synchronized(lock) { session } ?: return
        if (!coordinator.accepts(System.identityHashCode(view), current.generation)) return
        if (ExpandedDecisionRefreshPolicy.shouldRefresh(current.decision.tightLayout, current.provisional)) {
            settle(view, current)
        }
        applyResolved(view, current)
    }

    private fun applyResolved(view: View, current: Session) {
        ExpandedFlowMaskApplicator.invalidateStructure()
        layoutStamp = ExpandedFakeMirrorPolicy.stamp(
            current.decision.bodyOffsetPx,
            current.decision.card.bottom,
            current.decision.contentScale,
        )
        applyOffset(view, current.decision.bodyOffsetPx)
        tuneRoots(view, current.decision)
    }

    /**
     * Re-reads Xiaomi's content for an armed session and keeps the session, its
     * generation, and its phase. Only the resolved decision changes, so no second
     * expansion starts. Returns whether the decision was replaced.
     */
    private fun settle(view: View, current: Session): Boolean {
        current.settledVersion = nativeContentVersion.get()
        val refreshed = refreshDecision(view, current) ?: return false
        if (!ExpandedDecisionRefreshPolicy.adopt(current.decision, refreshed.decision, current.provisional)) {
            return false
        }
        current.decision = refreshed.decision
        current.nativeTopMargin = refreshed.topMargin
        current.provisional = false
        return true
    }

    /** Xiaomi bound a Focus module. The next expanded target re-reads the session. */
    fun noteNativeBind() {
        nativeContentVersion.incrementAndGet()
    }

    /**
     * `updateExpandedView` has placed the template. A takeover armed before the
     * template existed or could be measured resolves from it now, in the same
     * call chain that Xiaomi then sizes and animates from.
     */
    fun onNativeContentInstalled(view: View) {
        nativeContentVersion.incrementAndGet()
        val current = synchronized(lock) { session }
        if (current == null) {
            if (view.call("getState")?.javaClass?.simpleName == "Expanded") beginOrContinue(view)
            return
        }
        if (!current.provisional || current.owner.get() !== view) return
        if (!coordinator.accepts(System.identityHashCode(view), current.generation)) return
        if (settle(view, current)) applyResolved(view, current)
    }

    private fun layoutRequest(
        view: View,
        fromSmallIsland: Boolean,
        profile: ExpandedContentProfile?,
        style: ExpandedVisualStyle,
    ): ExpandedLayoutRequest? {
        val metrics = view.resources.displayMetrics
        val compact = if (fromSmallIsland) {
            smallCompactRect(view) ?: rectOf(view.call("getBigIslandRect", java.lang.Boolean.FALSE))
        } else {
            rectOf(view.call("getBigIslandRect", java.lang.Boolean.FALSE))
        } ?: return null
        val nativeY = view.intField("expandedViewY") ?: return null
        // Right after an app exit the stored height is still 0, while the
        // maximum expanded height is already the value media will expand to.
        // Waiting for the field leaves the first second on Xiaomi's own card.
        val nativeHeight = view.intField("expandedViewHeight")?.takeIf { it > 0 }
            ?: runCatching { (view.call("getExpandedViewMaxHeight") as? Number)?.toInt() }
                .getOrNull()
                ?.takeIf { it > 0 }
            ?: return null
        val margin = (view.call("getExpandedViewMarginHorizontal") as? Number)?.toInt() ?: return null
        val width = (view.call("getExpandedViewWidth") as? Number)?.toInt() ?: return null
        val statusBar = (view.call("getStatusBarHeight") as? Number)?.toInt() ?: return null
        val cutout = CameraBandGeometry.resolve(cutoutRect(view), compact, displayCutoutWidth(view)) ?: return null
        return ExpandedLayoutRequest(
            enabled = true,
            portrait = view.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
            keyguard = keyguard(view),
            tablet = tablet(),
            displayWidth = metrics.widthPixels,
            displayHeight = metrics.heightPixels,
            cutout = cutout,
            compact = compact,
            nativeExpanded = IslandRect(margin, nativeY, margin + width, nativeY + nativeHeight),
            statusBarHeight = statusBar,
            density = metrics.density,
            leadingEar = viewRect(view.call("getBigIslandAreaLeft") as? View),
            trailingEar = viewRect(view.call("getBigIslandAreaRight") as? View),
            style = style,
            nativeRadiusPx = islandRadius(view),
            content = profile,
            rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL,
        )
    }

    private fun displayCutoutWidth(view: View): Int {
        val cutout = view.rootWindowInsets?.displayCutout ?: return 0
        val top = cutout.boundingRectTop
        if (!top.isEmpty) return top.width()
        return cutout.boundingRects.maxOfOrNull { it.width() } ?: 0
    }

    private fun blocked(view: View): Boolean {
        val portrait = view.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return !portrait || keyguard(view) || tablet()
    }

    private fun keyguard(view: View): Boolean {
        val coordinatorView = view.call("getDynamicIslandEventCoordinator")
        val showing = coordinatorView?.call("getKeyguardShowing") as? Boolean ?: false
        val manager = view.context.getSystemService(KeyguardManager::class.java)
        val locked = manager?.isKeyguardLocked == true || manager?.isDeviceLocked == true
        return showing || locked
    }

    private fun tablet(): Boolean {
        val method = tabletMethod ?: return true
        val instance = tabletInstance ?: return true
        return runCatching { method.invoke(instance) as? Boolean ?: true }.getOrDefault(true)
    }

    private fun tempHidden(view: View): Boolean =
        view.call("getDynamicIslandEventCoordinator")?.call("isTempHidden", view) as? Boolean ?: false

    private fun cutoutRect(view: View): IslandRect? {
        val window = view.call("getDynamicIslandEventCoordinator")?.call("getWindowView") ?: return null
        return rectOf(window.call("getCutoutRect"))
    }

    private fun liveRect(delegate: Any): IslandRect? {
        val x = delegate.floatField("containerX") ?: return null
        val y = delegate.floatField("containerTransY") ?: return null
        val start = delegate.floatField("containerClipStartProgress") ?: return null
        val end = delegate.floatField("containerClipEndProgress") ?: return null
        val top = delegate.floatField("containerClipTopProgress") ?: return null
        val bottom = delegate.floatField("containerClipBottomProgress") ?: return null
        return IslandRect(
            left = (x + start).toInt(),
            top = (y + top).toInt(),
            right = (x + end).toInt(),
            bottom = (y + bottom).toInt(),
        )
    }



















    private fun smallCompactRect(view: View): IslandRect? {
        viewRect(view.call("getSmallIslandView") as? View)?.let { return it }
        val big = view.call("getDynamicIslandEventCoordinator")
            ?.call("getBigIslandStateHandler")
            ?.call("getCurrent") as? View
            ?: return null
        if (big === view) return null
        return xiaomiSmallRect(big, view)
    }

    private fun xiaomiSmallRect(big: View?, small: View): IslandRect? {
        val anchor = big ?: return viewRect(small.call("getSmallIslandView") as? View)
        val rtl = small.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val bigX = (anchor.call("getCurrentBigIslandX", java.lang.Boolean.FALSE) as? Number)?.toInt()
            ?: return viewRect(small.call("getSmallIslandView") as? View)
        val bigWidth = (anchor.call("getCurrentBigIslandWidth", java.lang.Boolean.FALSE) as? Number)?.toInt()
            ?: return null
        val space = (anchor.call("getSpace") as? Number)?.toInt() ?: return null
        val smallWidth = (small.call("getSmallIslandViewWidth") as? Number)?.toInt() ?: return null
        val x = if (rtl) bigX - space - smallWidth else bigX + bigWidth + space
        return rectOf(small.call("getSmallIslandRect", x))
            ?: viewRect(small.call("getSmallIslandView") as? View)
    }

    private fun hookIncomingExpansion(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val add = loader.loadClass(ADD)
            val handle = add.methodsNamed("handleAppEvent").firstOrNull { it.parameterTypes.size == 3 }
                ?: error("handleAppEvent missing")
            hook(module, loader, handle) { chain ->
                if (deferIncoming(chain, handle)) null else chain.proceed()
            }
            val canExpand = add.methodsNamed("canExpanded").firstOrNull { it.parameterTypes.size == 3 }
            if (canExpand != null) {
                hook(module, loader, canExpand) { chain ->
                    val key = chain.args.getOrNull(2) as? String
                    if (key != null && key == compactReplayKey.get()) false else chain.proceed()
                }
            }
        }.onFailure {
            module.log("HyperPop: expansion handoff hook unavailable: ${it.message}")
        }
    }

    private fun deferIncoming(chain: Chain, handle: Method): Boolean {
        return runCatching { holdIncoming(chain, handle) }.getOrDefault(false)
    }

    private fun holdIncoming(chain: Chain, handle: Method): Boolean {
        val event = chain.args.getOrNull(0) ?: return false
        if (event.javaClass.simpleName != "AddDynamicIsland") return false
        if (event.call("getTempShow") == true) return false
        val content = chain.args.getOrNull(1) as? View ?: return false
        if (!HookConfig.expandOverStatusBarEnabled() || blocked(content)) return false
        val state = content.call("getState") ?: return false
        if (state.call("getExpanded") != true) return false
        val data = content.call("getCurrentIslandData") ?: return false
        val key = data.call("getKey") as? String ?: return false
        val focus = data.call("getView")
        val can = chain.thisObject.call("canExpanded", true, focus, key) as? Boolean ?: false
        if (!can) return false
        val incomingId = System.identityHashCode(content)
        val owner = synchronized(lock) { session }?.owner?.get()
        if (owner != null) {
            val xiaomiExpanded = chain.thisObject.call("getExpandedStateHandler")?.call("getCurrent")
            val ownerTempShow = owner.call("getState")?.call("getTempShow") == true
            // Let Xiaomi's handleReplacedState expand the incoming island and morph
            // the owner into its compact slot in one native operation.
            if (coordinator.admitsNativeReplacement(incomingId, xiaomiExpanded === owner, ownerTempShow)) {
                trace { "incoming ${hex(incomingId)} native replacement of ${hex(System.identityHashCode(owner))}" }
                return false
            }
        }
        val queued = coordinator.enqueueExpansion(incomingId, key)
        trace { "incoming ${hex(incomingId)} ${queued.javaClass.simpleName} phase=${coordinator.phase} owner=${hex(coordinator.ownerId)}" }
        if (queued !is EnqueueExpansion.Queued) return false
        heldAdds[queued.pending.token] = HeldAdd(
            host = chain.thisObject,
            method = handle,
            event = event,
            content = java.lang.ref.WeakReference(content),
            hidden = chain.args.getOrNull(2),
            key = key,
        )
        queued.displaced?.token?.let { token ->
            heldAdds.remove(token)?.let { displaced ->
                mainHandler.post { invokeHeld(displaced, expand = false) }
            }
        }
        if (coordinator.phase == TakeoverPhase.EXPANDING || coordinator.phase == TakeoverPhase.EXPANDED) {
            mainHandler.post { collapseForHandoff() }
        }
        return true
    }

    private fun collapseForHandoff() {
        val owner = synchronized(lock) { session }?.owner?.get()
        trace { "collapseForHandoff owner=${hex(owner?.let(System::identityHashCode))}" }
        val eventCoordinator = owner?.call("getDynamicIslandEventCoordinator")
        val window = eventCoordinator?.call("getWindowView")
        if (window == null) {
            releaseHeld(coordinator.clearPendingExpansion(), expand = true)
            return
        }
        val invoked = runCatching { window.call("collapse", HANDOFF_REASON) }.isSuccess
        val stillExpanded = eventCoordinator.call("getUserExpanded") as? Boolean ?: true
        if (!invoked || stillExpanded) {
            releaseHeld(coordinator.clearPendingExpansion(), expand = true)
        }
    }

    private fun releaseHeld(candidate: com.sykeptical.hyperpop.service.animation.expanded.ExpansionCandidate?, expand: Boolean) {
        val held = candidate?.let { heldAdds.remove(it.token) } ?: return
        if (expand && !incomingStillCurrent(held)) return
        mainHandler.post { invokeHeld(held, expand) }
    }

    private fun incomingStillCurrent(held: HeldAdd): Boolean {
        val view = held.content.get() ?: return false
        if (!view.isAttachedToWindow) return false
        val state = view.call("getState")?.javaClass?.simpleName ?: return false
        if (state == "Deleted") return false
        val key = view.call("getCurrentIslandData")?.call("getKey") as? String
        return key == held.key
    }

    private fun invokeHeld(held: HeldAdd, expand: Boolean) {
        val content = held.content.get() ?: return
        runCatching {
            if (!expand) compactReplayKey.set(held.key)
            held.method.invoke(held.host, held.event, content, held.hidden)
        }
        compactReplayKey.remove()
    }

    private class HeldAdd(
        val host: Any,
        val method: Method,
        val event: Any,
        val content: java.lang.ref.WeakReference<View>,
        val hidden: Any?,
        val key: String,
    )

    /**
     * updateMedianLuma and updateDarkLightMode install Xiaomi's stroked plate.
     * Let that plate through, then aim its corner radius at the pill. The
     * stroke and the outset stay Xiaomi's.
     */
    private fun hookOwnedPlate(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val background = loader.loadClass(BACKGROUND_VIEW)
            background.methodsNamed("setDrawable").forEach { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let(ExpandedSurfaceStyler::alignNativeOutline)
                    result
                }
            }
        }.onFailure {
            module.log("HyperPop: owned-plate hook unavailable: ${it.message}")
        }
    }

    private fun hookFakeExpandedOverlay(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val fakeClass = loader.loadClass(FAKE_VIEW)
            fakeClass.methodsNamed("updateFakeExpandedViewState").forEach { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    val fake = chain.thisObject as? View
                    val real = fake?.call("getRealView") as? View
                    if (real != null && real.call("getState")?.javaClass?.simpleName == "Expanded") {
                        uncoverRealIsland(real)
                        beginOrContinue(real)
                    }
                    result
                }
            }
            fakeClass.methodsNamed("updateExpandedView").forEach { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    val fake = chain.thisObject as? View
                    val real = fake?.call("getRealView") as? View
                    if (real != null) runCatching { syncFake(real) }
                    result
                }
            }
            fakeClass.declaredMethods.filter { it.name == "onTrackingFakeViewDown" }.forEach { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { traceDrag("down", it) }
                    result
                }
            }
            fakeClass.declaredMethods.filter { it.name == "onTrackingFakeViewStart" }.forEach { method ->
                hook(module, loader, method) { chain ->
                    val fake = chain.thisObject as? View
                    fake?.let(::fitTrackingRadius)
                    ExpandedMediaSurfaceApplicator.noteDragging(true)
                    val result = chain.proceed()
                    fake?.let(::styleTrackingFake)
                    val real = fake?.call("getRealView") as? View
                    if (real != null) runCatching { syncFake(real) }
                    fake?.let { traceDrag("start", it) }
                    result
                }
            }
            fakeClass.declaredMethods.filter { it.name == "onTrackingFakeViewUpdate" }.forEach { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { traceDrag("update", it) }
                    result
                }
            }
            listOf("onTrackingFakeViewEnd", "onTrackingFakeViewReset").forEach { name ->
                fakeClass.declaredMethods.filter { it.name == name }.forEach { method ->
                    hook(module, loader, method) { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? View)?.let { traceDrag("end", it) }
                        result
                    }
                }
            }
            fakeClass.declaredMethods.filter { it.name == "setVisibility" }.forEach { method ->
                hook(module, loader, method) { chain ->
                    val result = chain.proceed()
                    val fake = chain.thisObject as? View
                    if (fake != null && fake.visibility != View.VISIBLE) {
                        runCatching { ExpandedSurfaceStyler.restoreFake(fake) }
                        ExpandedMediaSurfaceApplicator.noteDragging(false)
                        if (fakeRestoreDeferred) restoreDeferredFake()
                    }
                    result
                }
            }
        }.onFailure {
            module.log("HyperPop: fake-expand hook unavailable: ${it.message}")
        }
    }




    private fun present(
        view: View,
        delegate: Any,
        current: Session,
        live: IslandRect,
    ) {
        val decision = current.decision
        val progress = presentSurface(view, delegate, current, live, armIfMissing = true)
        realContent(view)?.let { root ->
            if (decision.blackBackground) {
                ExpandedFlowMaskApplicator.apply(root, decision.flowMask, progress)
            }
            val extension = ExpandedMediaSurfaceApplicator.apply(root, decision.card.bottom)
            if (extension != mirroredExtension) {
                fakeLeaf(view)?.let { ExpandedMediaSurfaceApplicator.mirror(it) }
                mirroredExtension = extension
            }
        }
    }

    /** Plate and pill radius for the shown height. Returns the morph progress. */
    private fun presentSurface(
        view: View,
        delegate: Any,
        current: Session,
        live: IslandRect,
        armIfMissing: Boolean,
    ): Float {
        val decision = current.decision
        val clip = clipHeight(delegate).takeIf { it > 0f } ?: live.height.toFloat()
        val shown = shownHeight(view, maxOf(live.height, clip.toInt()))
        val progress = ExpandedSurfaceStyle.morphProgress(shown, current.compact.height, decision.card.height)
        val nativeCap = decision.nativeRadiusPx.takeIf { it > 0f } ?: decision.radiusPx
        val radius = ExpandedSurfaceStyle.clipRadius(
            shown.toFloat(),
            nativeCap,
            decision.radiusPx,
            decision.pillEnabled,
            progress,
        )
        if (decision.blackBackground || decision.pillEnabled) {
            if (armIfMissing && !ExpandedSurfaceStyler.owns(view)) {
                ExpandedSurfaceStyler.arm(view, decision.blackBackground)
            }
            ExpandedSurfaceStyler.frame(view, progress, radius)
        }
        return progress
    }

    private fun resolveLayout(
        request: ExpandedLayoutRequest,
    ): Pair<ExpandedLayoutRequest, ExpandedLayoutDecision> {
        val seated = ExpandedIslandLayoutPolicy.seatOnCutout(request)
        val seatedDecision = ExpandedIslandLayoutPolicy.decide(seated)
        if (seatedDecision is ExpandedLayoutDecision.Takeover || seated === request) {
            return seated to seatedDecision
        }
        return request to ExpandedIslandLayoutPolicy.decide(request)
    }

    private class Refreshed(val decision: ExpandedLayoutDecision.Takeover, val topMargin: Int)

    private fun refreshDecision(view: View, current: Session): Refreshed? {
        val content = realContent(view) ?: return null
        val probed = runCatching { ExpandedContentProbe.capture(content, expandedWidthHint(view)) }
            .getOrNull() ?: return null
        // A session armed without a profile never recorded the content's own top
        // margin. The margin saved before any offset is the native one; a view
        // not offset yet still carries it.
        val topMargin = if (current.provisional) {
            realSaved.margins[content] ?: probed.nativeTopMarginPx
        } else {
            current.nativeTopMargin
        }
        val profile = probed.copy(nativeTopMarginPx = topMargin)
        val request = layoutRequest(view, current.fromSmallIsland, profile, current.style) ?: return null
        val decision = resolveLayout(request).second as? ExpandedLayoutDecision.Takeover ?: return null
        return Refreshed(decision, topMargin)
    }

    private fun expandedWidthHint(view: View): Int =
        (view.call("getExpandedViewWidth") as? Number)?.toInt() ?: 0

    private fun applyPresentation(
        content: View,
        decision: ExpandedLayoutDecision.Takeover,
        saved: SavedLayout,
    ) {
        val scale = decision.contentScale
        if (scale < 0.999f) {
            saved.scales.getOrPut(content) { content.scaleX }
            content.pivotX = content.width / 2f
            content.pivotY = 0f
            content.scaleX = scale
            content.scaleY = scale
        }
        val group = content as? ViewGroup ?: return
        decision.sideLifts.forEach { lift ->
            val child = group.getChildAt(lift.clusterIndex) ?: return@forEach
            saved.translations.getOrPut(child) { child.translationY }
            child.translationY = lift.translationY.toFloat()
        }
    }

    /** Real copy and the drag copy, from the decision already resolved for this session. */
    private fun tuneRoots(view: View, decision: ExpandedLayoutDecision.Takeover) {
        realContent(view)?.let { root ->
            tuneRoot(root, decision, realSaved)
        }
        syncFake(view, force = true)
    }

    private fun tuneRoot(
        root: View,
        decision: ExpandedLayoutDecision.Takeover,
        saved: SavedLayout,
    ) {
        applyPresentation(root, decision, saved)
        FocusIslandLayoutApplier.apply(root)
    }

    /**
     * Writes the settled layout onto the drag copy when its marker is stale.
     * A matching marker is one comparison. Reparenting that resets child
     * margins changes the token, so the next drag start writes it again.
     */
    private fun syncFake(owner: View, force: Boolean = false) {
        val current = synchronized(lock) { session } ?: return
        if (current.owner.get() !== owner) return
        if (!coordinator.accepts(System.identityHashCode(owner), current.generation)) return
        val leaf = fakeLeaf(owner) ?: return
        val token = layoutToken(leaf)
        val recorded = fakeMarkers[leaf]
        if (!force && !ExpandedFakeMirrorPolicy.needsSync(recorded, current.generation, layoutStamp, token)) {
            return
        }
        tuneRoot(leaf, current.decision, fakeSaved)
        ExpandedMediaSurfaceApplicator.mirror(leaf)
        runCatching {
            val overlay = fakeOverlay(leaf) ?: return@runCatching
            val expanded = overlay.call("getFakeExpandedView") as? View ?: return@runCatching
            IslandExpandedMediaAmbientFlowHooker.applyFakeTransitionElements(overlay, expanded)
            IslandExpandedMediaAmbientFlowHooker.applyFakeTransitionTheme(overlay)
        }
        fakeMarkers[leaf] = ExpandedFakeMirrorPolicy.marker(
            current.generation,
            layoutStamp,
            layoutToken(leaf),
        )
    }

    private fun rejectExpand(view: View): ExpandedLayoutDecision.Takeover? {
        val name = view.call("getState")?.javaClass?.simpleName
        trace { "rejectExpand ${hex(System.identityHashCode(view))} state=$name phase=${coordinator.phase}" }
        val now = SystemClock.uptimeMillis()
        if ((name == "Expanded" || name == "BigIsland" || name == "AppExpanded") &&
            expandRetryUntilMs < now
        ) {
            expandRetryUntilMs = now + 1_500L
        }
        return null
    }

    /**
     * The app-close overlay can still be the surface the user sees when they
     * expand. It does not go through the takeover, so the card stays native
     * until Xiaomi hides it. Hide it as soon as we take over the real island,
     * with the visibility that runs Xiaomi's own handoff.
     */
    private fun uncoverRealIsland(view: View) {
        runCatching {
            val fake = view.call("getFakeView") as? View ?: return
            fake.visibility = AppCloseOverlayPolicy.hideVisibility(fake.visibility) ?: return
            if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
            (view.call("getBackgroundView") as? View)?.let { background ->
                if (background.visibility != View.VISIBLE) background.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Swiping the expanded island down to open the app as a window hides the
     * real island and drags the fake one, whose corners use its own radius.
     */
    private fun fitTrackingRadius(fake: View) {
        runCatching {
            val native = fakeNativeRadii.getOrPut(fake) { fake.floatField("radius") ?: return }
            val real = fake.call("getRealView") as? View
            val pill = real?.let { activeDecision(it) }?.takeIf { it.pillEnabled }?.radiusPx
            val radius = AppCloseOverlayPolicy.trackingRadius(native, pill)
            if (fake.floatField("radius") == radius) return
            setFloatField(fake, "radius", radius)
            fake.invalidateOutline()
            (fake.call("getFakeExpandedView") as? View)?.invalidateOutline()
        }
    }

    private fun styleTrackingFake(fake: View) {
        runCatching {
            val real = fake.call("getRealView") as? View
            val takeover = real?.let { activeDecision(it) }
            if (takeover == null || (!takeover.blackBackground && !takeover.pillEnabled)) {
                ExpandedSurfaceStyler.restoreFake(fake)
                return
            }
            if (!takeover.blackBackground) return
            val relative = takeover.flowMask?.let { ExpandedSurfaceStyle.flowMaskFromCardTop(it, takeover.card.top) }
            val cardTop = (real.call("getIslandViewMarginTop") as? Number)?.toInt() ?: takeover.card.top
            val radius = fake.floatField("radius") ?: takeover.radiusPx
            ExpandedSurfaceStyler.blackenFake(fake, radius, relative, cardTop)
        }
    }

    /** Keeps Xiaomi's spring from drawing the clip past the card bottom. */
    private fun capClipBottom(delegate: Any) {
        runCatching {
            if (coordinator.phase != TakeoverPhase.EXPANDING && coordinator.phase != TakeoverPhase.EXPANDED) return
            val view = contentView(delegate) ?: return
            val takeover = activeDecision(view) ?: return
            val bottom = delegate.floatField("containerClipBottomProgress") ?: return
            val capped = ExpandedSurfaceStyle.cappedClipBottom(bottom, takeover.card.bottom)
            if (capped < bottom) setFloatField(delegate, "containerClipBottomProgress", capped)
        }
    }

    private fun realContent(view: View): View? = islandData(view)?.call("getView") as? View

    private fun fakeLeaf(view: View): View? = islandData(view)?.call("getFakeView") as? View

    private fun islandData(view: View): Any? = view.call("getCurrentIslandData")

    private fun fakeOverlay(leaf: View): ViewGroup? {
        var current: View? = leaf
        var depth = 0
        while (current != null && depth < 12) {
            if (current.javaClass.name == FAKE_VIEW) return current as? ViewGroup
            current = current.parent as? View
            depth += 1
        }
        return null
    }

    private fun layoutToken(root: View): Int {
        val group = root as? ViewGroup ?: return 0
        val margins = IntArray(group.childCount)
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            val params = child.layoutParams as? ViewGroup.MarginLayoutParams
            margins[index] = params?.topMargin ?: child.top
        }
        return ExpandedFakeMirrorPolicy.layoutToken(margins)
    }

    private fun islandRadius(view: View): Float {
        val id = view.resources.getIdentifier("island_radius", "dimen", view.context.packageName)
        val density = view.resources.displayMetrics.density
        if (id == 0) return 30f * density
        return view.resources.getDimension(id)
    }

    /**
     * The outline draws the card into the background view's actualHeight before
     * the layout height catches up. The pill has to follow that drawn card.
     */
    private fun shownHeight(view: View, clipPx: Int): Int {
        val expanded = (view.call("getExpandedView") as? View)?.let { child ->
            child.height.coerceAtLeast(child.measuredHeight)
        } ?: 0
        val background = view.call("getBackgroundView") as? View
        return ExpandedSurfaceStyle.drawnHeight(
            clipPx,
            expanded,
            background?.height ?: 0,
            background?.intField("actualHeight") ?: 0,
        )
    }

    private fun clipHeight(delegate: Any): Float {
        val top = delegate.floatField("containerClipTopProgress") ?: return 0f
        val bottom = delegate.floatField("containerClipBottomProgress") ?: return 0f
        return bottom - top
    }

    private fun applyOffset(view: View, extra: Int) {
        val real = realContent(view)
        val fake = fakeLeaf(view)
        listOf(real to realSaved, fake to fakeSaved).forEach { (child, saved) ->
            val target = child ?: return@forEach
            val params = target.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            val base = saved.margins.getOrPut(target) { params.topMargin }
            val desired = base + extra
            if (params.topMargin != desired) {
                params.topMargin = desired
                target.layoutParams = params
            }
            growAncestors(view, target, extra.coerceAtLeast(0), saved)
        }
    }

    private fun growAncestors(content: View, leaf: View, extra: Int, saved: SavedLayout) {
        val limit = if (saved === fakeSaved) fakeOverlay(leaf) ?: leaf else content
        var parent = leaf.parent as? View
        var depth = 0
        while (parent != null && parent !== limit && parent !== content && depth < 8) {
            depth += 1
            val params = parent.layoutParams
            if (params != null && params.height > 0) {
                val base = saved.heights.getOrPut(parent) { params.height }
                val desired = (base + extra).coerceAtLeast(1)
                if (params.height != desired) {
                    params.height = desired
                    parent.layoutParams = params
                }
            }
            parent = parent.parent as? View
        }
    }

    /**
     * Real copy returns immediately. The drag copy keeps its tuned geometry
     * until Xiaomi hides it, so a completed launch does not snap mid-animation.
     * Returns whether that copy is still waiting.
     */
    private fun restoreOffsets(view: View): Boolean {
        realSaved.restoreAll()
        val overlay = view.call("getFakeView") as? View
        val defer = overlay != null && ExpandedFakeMirrorPolicy.deferRestore(overlay.visibility == View.VISIBLE)
        if (!defer) {
            fakeSaved.restoreAll()
            fakeMarkers.clear()
            fakeRestoreDeferred = false
            deferredFakeKeys = null
            deferredShownKeys = null
            return false
        }
        deferredFakeKeys = fakeSaved.keys()
        deferredShownKeys = ExpandedMediaSurfaceApplicator.captureShown()
        fakeRestoreDeferred = true
        return true
    }

    private fun restoreDeferredFake() {
        val keys = deferredFakeKeys
        val shown = deferredShownKeys
        fakeRestoreDeferred = false
        deferredFakeKeys = null
        deferredShownKeys = null
        if (keys == null) {
            fakeSaved.restoreAll()
            fakeMarkers.clear()
        } else {
            fakeSaved.restoreOnly(keys)
            keys.forEach { fakeMarkers.remove(it) }
        }
        ExpandedMediaSurfaceApplicator.restoreShown(shown)
    }

    private fun traceDrag(phase: String, fake: View) {
        if (phase == "update" && !HyperPopDragTrace.updateOpen()) return
        val real = fake.call("getRealView") as? View
        val realLeaf = real?.let { realContent(it) }
        val shown = real?.let { fakeLeaf(it) }
        val marker = shown?.let { fakeMarkers[it] }
        val current = synchronized(lock) { session }
        val message = "phase=$phase active=${ExpandedVisualSession.active} " +
            "takeover=${coordinator.phase.name} gen=${current?.generation} stamp=$layoutStamp " +
            "marker=${marker?.generation}/${marker?.stamp}/${marker?.token} " +
            "real=${describeLeaf(realLeaf)} fake=${describeLeaf(shown)}"
        when (phase) {
            "down" -> HyperPopDragTrace.down(message)
            "start" -> HyperPopDragTrace.start(message)
            "update" -> HyperPopDragTrace.update(message)
            else -> HyperPopDragTrace.end(message)
        }
    }

    private fun describeLeaf(view: View?): String {
        if (view == null) return "null"
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams
        val parent = (view.parent as? View)?.javaClass?.simpleName
        val group = view as? ViewGroup
        val children = buildString {
            if (group == null) return@buildString
            val count = group.childCount.coerceAtMost(6)
            for (index in 0 until count) {
                val child = group.getChildAt(index)
                val childParams = child.layoutParams as? ViewGroup.MarginLayoutParams
                append(" {")
                append(child.javaClass.simpleName)
                append(" t=")
                append(childParams?.topMargin ?: child.top)
                append(" ty=")
                append(child.translationY)
                append(" sy=")
                append(child.scaleY)
                append('}')
            }
        }
        return "${view.javaClass.simpleName} parent=$parent " +
            "bounds=${view.left},${view.top},${view.right},${view.bottom} " +
            "lp=${params?.width}x${params?.height} top=${params?.topMargin} " +
            "ty=${view.translationY} sy=${view.scaleY}$children"
    }

    private fun scheduleWatchdog(view: View, generation: Long) {
        mainHandler.postDelayed({
            val current = synchronized(lock) { session } ?: return@postDelayed
            if (current.generation != generation) return@postDelayed
            if (coordinator.expireIfStale(SystemClock.uptimeMillis())) {
                restore(view)
            } else if (coordinator.accepts(System.identityHashCode(view), generation)) {
                scheduleWatchdog(view, generation)
            }
        }, 1_000L)
    }

    private fun contentView(delegate: Any): View? =
        delegate.field("view") as? View ?: delegate.call("getView") as? View

    private fun ownerIs(id: Int): Boolean = coordinator.ownerId == id

    private fun resolveTablet(loader: ClassLoader) {
        if (tabletResolved) return
        tabletResolved = true
        runCatching {
            val utils = loader.loadClass(UTILS)
            val field = utils.findField("INSTANCE") ?: return
            field.isAccessible = true
            tabletInstance = field.get(null)
            tabletMethod = utils.methodsNamed("getIS_TABLET").firstOrNull { it.parameterTypes.isEmpty() }
            tabletMethod?.isAccessible = true
        }
    }

    private fun deoptimizeCallers(
        module: XposedModule,
        base: Class<*>,
        delegate: Class<*>,
        coordinatorClass: Class<*>,
        loader: ClassLoader,
    ) {
        val baseNames = setOf("getExpandedIslandRect", "getExpandedPosition")
        val delegateNames = setOf(
            "getExpandedAnimState",
            "getExpandedBottom",
            "getExpandedTop",
            "getExpandedTransYToBig",
            "bigIslandToExpandedAnimation",
            "smallIslandToExpandedAnimation",
            "initToExpandedAnimation",
            "expandedChangedAnimation",
            "resetToExpanded",
            "containerScheduleUpdate",
            "containerClipRadius",
        )
        val coordinatorNames = setOf(
            "getExpandedIslandRegion",
            "getSmallBigIslandRegion",
            "updateTouchRegion",
            "updateWindowHeight",
            "onAnimationStart",
        )
        listOf(base to baseNames, delegate to delegateNames, coordinatorClass to coordinatorNames)
            .forEach { (clazz, names) -> deoptimizeNamed(module, clazz, names) }
        runCatching { loader.loadClass(PHONE_HELPER) }.getOrNull()?.let { helper ->
            deoptimizeNamed(module, helper, setOf("calcInitToExpandedParams", "calcLocationParams"))
        }
        delegate.declaredClasses.forEach { inner ->
            inner.methodsNamed("getOutline").forEach { method ->
                method.isAccessible = true
                runCatching { module.deoptimize(method) }
            }
        }
    }

    private fun deoptimizeNamed(module: XposedModule, clazz: Class<*>, names: Set<String>) {
        clazz.methodsNamedIn(names).forEach { method ->
            method.isAccessible = true
            runCatching { module.deoptimize(method) }
        }
    }

    private fun hook(
        module: XposedModule,
        loader: ClassLoader,
        method: Method,
        block: (Chain) -> Any?,
    ) {
        method.isAccessible = true
        runCatching { module.deoptimize(method) }
        module.hook(method).intercept { chain ->
            if (brokenLoaders.contains(loader)) return@intercept chain.proceed()
            try {
                block(chain)
            } catch (error: Throwable) {
                logOnce(module, "${method.name} failed open: ${error.message}")
                null
            }
        }
    }

    private fun logOnce(module: XposedModule, message: String) {
        if (loggedFailure) return
        loggedFailure = true
        module.log("HyperPop: expand-over-status-bar $message")
    }


    private fun rectOf(value: Any?): IslandRect? {
        val rect = value as? Rect ?: return null
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return IslandRect(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun viewRect(view: View?): IslandRect? {
        view ?: return null
        if (view.width <= 0 || view.height <= 0) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return IslandRect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private class SavedLayout {
        val margins = Collections.synchronizedMap(WeakHashMap<View, Int>())
        val heights = Collections.synchronizedMap(WeakHashMap<View, Int>())
        val translations = Collections.synchronizedMap(WeakHashMap<View, Float>())
        val scales = Collections.synchronizedMap(WeakHashMap<View, Float>())

        fun keys(): Set<View> {
            val all = HashSet<View>(margins.size + heights.size + translations.size + scales.size)
            all += margins.keys
            all += heights.keys
            all += translations.keys
            all += scales.keys
            return all
        }

        fun restoreAll() = restoreOnly(null)

        fun restoreWithin(scopes: List<View>) {
            val owned = keys().filterTo(HashSet()) { key -> scopes.any { key.isWithin(it) } }
            if (owned.isNotEmpty()) restoreOnly(owned)
        }

        fun restoreOnly(keys: Set<View>?) {
            restoreInts(margins, keys) { view, margin ->
                val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@restoreInts
                params.topMargin = margin
                view.layoutParams = params
            }
            restoreInts(heights, keys) { view, height ->
                val params = view.layoutParams ?: return@restoreInts
                params.height = height
                view.layoutParams = params
            }
            restoreFloats(translations, keys) { view, translation ->
                view.translationY = translation
            }
            restoreFloats(scales, keys) { view, scale ->
                view.scaleX = scale
                view.scaleY = scale
            }
        }

        private fun restoreInts(
            saved: MutableMap<View, Int>,
            keys: Set<View>?,
            write: (View, Int) -> Unit,
        ) {
            saved.entries.filter { keys == null || it.key in keys }.forEach { (view, value) ->
                runCatching { write(view, value) }
                saved.remove(view)
            }
            if (keys == null) saved.clear()
        }

        private fun restoreFloats(
            saved: MutableMap<View, Float>,
            keys: Set<View>?,
            write: (View, Float) -> Unit,
        ) {
            saved.entries.filter { keys == null || it.key in keys }.forEach { (view, value) ->
                runCatching { write(view, value) }
                saved.remove(view)
            }
            if (keys == null) saved.clear()
        }
    }

    private class Session(
        view: View,
        val generation: Long,
        var decision: ExpandedLayoutDecision.Takeover,
        val compact: IslandRect,
        val fromSmallIsland: Boolean,
        var nativeTopMargin: Int,
        val style: ExpandedVisualStyle,
        /** Armed without a content profile, before Xiaomi's template was complete. */
        @Volatile var provisional: Boolean = false,
        /** [nativeContentVersion] when the session last read the content. */
        @Volatile var settledVersion: Int = 0,
    ) {
        val owner = java.lang.ref.WeakReference(view)
        @Volatile var selfCheckDone: Boolean = false
    }
}

private fun Class<*>.noArg(name: String): Method =
    noArgOrNull(name) ?: error("$name missing on $simpleName")

private fun Class<*>.noArgOrNull(name: String): Method? =
    methodsNamed(name).firstOrNull { it.parameterTypes.isEmpty() }

private fun Class<*>.methodsNamed(name: String): List<Method> {
    val found = mutableListOf<Method>()
    var type: Class<*>? = this
    while (type != null) {
        found += type.declaredMethods.filter { it.name == name }
        type = type.superclass
    }
    return found
}

private fun Class<*>.methodsNamedIn(names: Set<String>): List<Method> {
    val found = mutableListOf<Method>()
    var type: Class<*>? = this
    while (type != null) {
        found += type.declaredMethods.filter { it.name in names }
        type = type.superclass
    }
    return found
}

private fun Class<*>.findField(name: String): Field? {
    var type: Class<*>? = this
    while (type != null) {
        val field = type.declaredFields.firstOrNull { it.name == name }
        if (field != null) return field
        type = type.superclass
    }
    return null
}

private val reflect = ReflectionLookup()

private fun Any.call(name: String, vararg args: Any?): Any? {
    val method = reflect.method(javaClass, name, args.size) ?: return null
    return method.invoke(this, *args)
}

private fun Any.field(name: String): Any? {
    val found = reflect.field(javaClass, name) ?: return null
    return found.get(if (java.lang.reflect.Modifier.isStatic(found.modifiers)) null else this)
}

private fun Any.intField(name: String): Int? = (field(name) as? Number)?.toInt()

private fun Any.floatField(name: String): Float? = (field(name) as? Number)?.toFloat()

private fun setFloatField(target: Any, name: String, value: Float) {
    val found = reflect.field(target.javaClass, name) ?: return
    found.setFloat(target, value)
}
