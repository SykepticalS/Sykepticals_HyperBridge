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
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedContentProfile
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedIslandLayoutPolicy
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedLayoutDecision
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedLayoutRequest
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedSurfaceStyle
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedTakeoverCoordinator
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedVisualStyle
import com.sykeptical.hyperpop.service.animation.expanded.IslandRect
import com.sykeptical.hyperpop.service.animation.expanded.TakeoverPhase
import com.sykeptical.hyperpop.service.animation.expanded.YieldedCompactTouch
import com.sykeptical.hyperpop.service.animation.expanded.YieldedCompactTouchPolicy
import com.sykeptical.hyperpop.xposed.HookConfig
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Portrait expanded islands grow from the camera over the status bar.
 *
 * Xiaomi still owns the Folme transition. This hook only replaces the geometry
 * that transition reads: expanded Y and height, the clip bottom (Xiaomi's
 * getter truncates height to a multiple of the compact height), and the touch
 * region. Compact ears keep Xiaomi's expanded end state (alpha 0, blur 1) so
 * their fade can finish. Whichever island is expanding owns that fade. The
 * other compact island yields: a circle plays Xiaomi's hidden Folme state at
 * its current X, and a big island fades with the same alpha as the status
 * bar. Its touch flags and compact touch region are cleared while it is
 * hidden, and the source is not cancelled.
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
    private const val TOUCH =
        "miui.systemui.dynamicisland.touch.domain.interactor.DynamicIslandTouchInteractor"
    private const val UTILS = "miui.systemui.util.CommonUtils"
    private const val SELF_CHECK_PX = 20
    private val SECONDARY_MOTION = setOf(
        "smallIslandChangedAnimation",
        "smallIslandChangedNoAnimation",
        "smallIslandToTempHiddenAnimation",
        "smallIslandToHiddenAnimation",
        "smallIslandToBigIslandAnimation",
        "bigIslandChangedAnimation",
        "bigIslandChangedNoAnimation",
        "bigIslandToTempHiddenAnimation",
    )
    private val COLLAPSE_STATES = setOf("BigIsland", "SmallIsland")
    private val pluginLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val brokenLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val coordinator = ExpandedTakeoverCoordinator()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val originalMargins = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val originalHeights = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val originalTranslations = Collections.synchronizedMap(WeakHashMap<View, Float>())
    private val originalScales = Collections.synchronizedMap(WeakHashMap<View, Float>())
    private val lock = Any()

    @Volatile private var selfCheckFailed = false
    @Volatile private var loggedFailure = false
    @Volatile private var session: Session? = null
    @Volatile private var parkedSecondaryX: Int? = null
    @Volatile private var parkedSecondaryId: Int? = null
    @Volatile private var yieldedSecondaryId: Int? = null

    private var tabletMethod: Method? = null
    private var tabletInstance: Any? = null
    private var tabletResolved = false

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
                val result = chain.proceed()
                onFrame(chain.thisObject)
                pinSecondary(chain.thisObject)
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
                    result
                }
            }
            SECONDARY_MOTION.forEach { name ->
                delegate.methodsNamed(name).filter { it.parameterTypes.isNotEmpty() }.forEach { method ->
                    hook(module, loader, method) { chain ->
                        val target = chain.args.getOrNull(0) as? View
                        if (target != null && suppressSecondaryMotion(target)) return@hook null
                        chain.proceed()
                    }
                }
            }
            hook(module, loader, region) { chain -> narrowedRegion(chain.thisObject, chain.proceed()) }
            hook(module, loader, setState) { chain ->
                val view = chain.thisObject as View
                val state = chain.args.getOrNull(0)
                val name = state?.javaClass?.simpleName
                if (name == "Expanded") beginOrContinue(view)
                else if (name in COLLAPSE_STATES && ownerIs(System.identityHashCode(view))) {
                    coordinator.beginCollapse(System.identityHashCode(view), coordinator.generation)
                }
                val result = chain.proceed()
                onState(view, state)
                if (name in COLLAPSE_STATES && ownerIs(System.identityHashCode(view))) {
                    revealSecondary(view)
                }
                result
            }
            hook(module, loader, updateSize) { chain ->
                val result = chain.proceed()
                reapplyOffset(chain.thisObject as View)
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
            coordinatorClass.noArgOrNull("getSmallBigIslandRegion")?.let { method ->
                hook(module, loader, method) { chain ->
                    withoutSecondary(chain.thisObject, chain.proceed())
                }
            }
            hookYieldedTouch(module, loader)
            deoptimizeCallers(module, base, delegate, coordinatorClass, loader)
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
        val takeover = activeDecision(view) ?: return native
        return takeover.card.top
    }

    private fun overriddenHeight(view: View, native: Any?): Any? {
        val takeover = activeDecision(view) ?: return native
        val base = (native as? Number)?.toInt() ?: return native
        return if (takeover.tightLayout || takeover.pillEnabled) takeover.card.height else base + takeover.bodyOffsetPx
    }

    private fun overriddenRadius(delegate: Any, native: Any?): Any? {
        val value = (native as? Number)?.toFloat() ?: return native
        val view = contentView(delegate) ?: return value
        val takeover = activeDecision(view) ?: return value
        if (!takeover.pillEnabled) return value
        val live = clipHeight(delegate)
        if (live <= 0f) return value
        val current = synchronized(lock) { session } ?: return value
        val progress = ExpandedSurfaceStyle.morphProgress(
            live.toInt(),
            current.compact.height,
            takeover.card.height,
        )
        val nativeCap = takeover.nativeRadiusPx.takeIf { it > 0f } ?: value
        return ExpandedSurfaceStyle.clipRadius(live, nativeCap, takeover.radiusPx, pill = true, progress)
    }

    private fun overriddenBottom(delegate: Any, native: Any?): Any? {
        val view = contentView(delegate) ?: return native
        val takeover = activeDecision(view) ?: return native
        return takeover.card.bottom.toFloat()
    }

    private fun onState(view: View, state: Any?) {
        val name = state?.javaClass?.simpleName ?: return
        val id = System.identityHashCode(view)
        when {
            name == "Expanded" -> beginOrContinue(view)
            name in COLLAPSE_STATES && ownerIs(id) -> coordinator.beginCollapse(id, coordinator.generation)
            ownerIs(id) -> abandon(view)
        }
    }

    private fun onFrame(delegate: Any) {
        val view = contentView(delegate) ?: return
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
        trackSecondary(view)
        if (coordinator.fadesUnexpandedCompact()) applyYieldedFade(view)
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

    private fun withoutSecondary(eventCoordinator: Any, native: Any?): Any? {
        val region = native as? Region ?: return native
        val big = handlerCurrent(eventCoordinator, "getBigIslandStateHandler")
        val small = handlerCurrent(eventCoordinator, "getSmallIslandStateHandler")
        val decision = YieldedCompactTouchPolicy.decide(
            blockTouch = coordinator.blocksYieldedIslandTouch(),
            hasBig = big != null,
            bigYielded = big != null && coordinator.blocksTouchFor(System.identityHashCode(big)),
            hasSmall = small != null,
            smallYielded = small != null && coordinator.blocksTouchFor(System.identityHashCode(small)),
        )
        return when (decision) {
            YieldedCompactTouch.KEEP -> region
            YieldedCompactTouch.DROP_ALL -> region.apply { setEmpty() }
            YieldedCompactTouch.DROP_SMALL -> {
                val rect = small?.let { xiaomiSmallRect(big, it) } ?: return region
                region.op(Region(rect.left, rect.top, rect.right, rect.bottom), Region.Op.DIFFERENCE)
                region
            }
            YieldedCompactTouch.DROP_BIG -> {
                val rect = big?.let { rectOf(it.call("getBigIslandRect", java.lang.Boolean.FALSE)) }
                    ?: return region
                region.op(Region(rect.left, rect.top, rect.right, rect.bottom), Region.Op.DIFFERENCE)
                region
            }
        }
    }

    private fun beginOrContinue(view: View): ExpandedLayoutDecision.Takeover? {
        if (selfCheckFailed) return null
        val id = System.identityHashCode(view)
        val existing = synchronized(lock) { session }
        if (existing != null && coordinator.accepts(id, existing.generation)) {
            if (blocked(view) || tempHidden(view)) {
                abandon(view)
                return null
            }
            if (!HookConfig.expandOverStatusBarEnabled()) coordinator.requestDisable()
            return existing.decision
        }
        if (!HookConfig.expandOverStatusBarEnabled()) {
            coordinator.requestDisable()
            return null
        }
        coordinator.requestEnable()
        if (blocked(view)) return null
        val fromSmallIsland = view.call("getState")?.javaClass?.simpleName == "SmallIsland"
        val content = realContent(view)
        val profile = content?.let { runCatching { ExpandedContentProbe.capture(it) }.getOrNull() }
        val style = ExpandedVisualStyle(
            blackBackground = HookConfig.expandedBlackBackground(),
            roundedPill = HookConfig.expandedRoundedPill(),
        )
        val request = layoutRequest(view, fromSmallIsland, profile, style) ?: return null
        val decision = ExpandedIslandLayoutPolicy.decide(request)
        if (decision !is ExpandedLayoutDecision.Takeover) return null
        val generation = coordinator.arm(
            id,
            SystemClock.uptimeMillis(),
            fromSmallIsland = fromSmallIsland,
        )
        if (generation < 0) return null
        synchronized(lock) {
            session = Session(
                view,
                generation,
                decision,
                request.compact,
                fromSmallIsland,
                profile?.nativeTopMarginPx ?: 0,
                style,
            )
        }
        applyOffset(view, decision.bodyOffsetPx)
        applyPresentation(view, decision)
        if (decision.blackBackground) ExpandedSurfaceStyler.arm(view, black = true)
        else if (decision.pillEnabled) ExpandedSurfaceStyler.arm(view, black = false)
        scheduleWatchdog(view, generation)
        yieldSecondary(view)
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
        val current = synchronized(lock) { session } ?: return
        if (current.owner.get() !== view && coordinator.ownerId != id) return
        coordinator.abandon(id, current.generation)
        synchronized(lock) {
            if (session === current) session = null
        }
        restore(view)
    }

    private fun restore(view: View) {
        restoreOffsets()
        ExpandedSurfaceStyler.restore(view)
        ExpandedFlowMaskApplicator.clear()
        parkedSecondaryX = null
        parkedSecondaryId = null
        yieldedSecondaryId = null
        StatusBarTakeoverHook.restore()
        revealSecondary(view)
        synchronized(lock) {
            if (coordinator.phase.name == "NATIVE") session = null
        }
    }

    private fun reapplyOffset(view: View) {
        val current = synchronized(lock) { session } ?: return
        if (!coordinator.accepts(System.identityHashCode(view), current.generation)) return
        if (current.decision.tightLayout) {
            val refreshed = refreshDecision(view, current)
            if (refreshed != null && refreshed.bodyOffsetPx > current.decision.bodyOffsetPx + 2) {
                current.decision = refreshed
            }
        }
        applyOffset(view, current.decision.bodyOffsetPx)
        applyPresentation(view, current.decision)
    }

    private fun layoutRequest(
        view: View,
        fromSmallIsland: Boolean,
        profile: ExpandedContentProfile?,
        style: ExpandedVisualStyle,
    ): ExpandedLayoutRequest? {
        val metrics = view.resources.displayMetrics
        val cutout = cutoutRect(view) ?: return null
        val compact = if (fromSmallIsland) {
            smallCompactRect(view) ?: rectOf(view.call("getBigIslandRect", java.lang.Boolean.FALSE))
        } else {
            rectOf(view.call("getBigIslandRect", java.lang.Boolean.FALSE))
        } ?: return null
        val nativeY = view.intField("expandedViewY") ?: return null
        val nativeHeight = view.intField("expandedViewHeight") ?: return null
        val margin = (view.call("getExpandedViewMarginHorizontal") as? Number)?.toInt() ?: return null
        val width = (view.call("getExpandedViewWidth") as? Number)?.toInt() ?: return null
        val statusBar = (view.call("getStatusBarHeight") as? Number)?.toInt() ?: return null
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
        )
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
        view.call("getDynamicIslandEventCoordinator")?.call("isTempHidden") as? Boolean ?: false

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

    private fun suppressSecondaryMotion(view: View): Boolean {
        val phase = coordinator.phase
        if (phase != TakeoverPhase.EXPANDING && phase != TakeoverPhase.EXPANDED) return false
        if (System.identityHashCode(view) == coordinator.ownerId) return false
        noteYielded(view)
        if (coordinator.fadesUnexpandedCompact()) return true
        if (yieldedSecondaryId != System.identityHashCode(view)) playSecondaryHide(view)
        return true
    }

    private fun pinSecondary(delegate: Any) {
        if (!coordinator.suppressesSecondaryVisual()) return
        val view = contentView(delegate) ?: return
        if (System.identityHashCode(view) != parkedSecondaryId) return
        val parked = parkedSecondaryX?.toFloat() ?: return
        setFloatField(delegate, "containerX", parked)
        view.translationX = parked
    }

    private fun retarget(delegate: Any, state: Any, propertyName: String, value: Float) {
        val target = property(delegate, propertyName) ?: return
        state.addProperty(target, value)
    }

    private fun yieldSecondary(expanded: View) {
        parkedSecondaryX = null
        parkedSecondaryId = null
        yieldedSecondaryId = null
        val secondary = secondaryView(expanded) ?: run {
            coordinator.onSecondaryPresence(false)
            return
        }
        noteYielded(secondary)
        if (coordinator.fadesUnexpandedCompact()) return
        playSecondaryHide(secondary)
    }

    private fun trackSecondary(expanded: View) {
        val secondary = secondaryView(expanded) ?: run {
            coordinator.onSecondaryPresence(false)
            return
        }
        noteYielded(secondary)
        if (coordinator.fadesUnexpandedCompact()) return
        if (coordinator.suppressesSecondaryVisual() &&
            yieldedSecondaryId != System.identityHashCode(secondary)
        ) {
            playSecondaryHide(secondary)
        }
    }

    private fun noteYielded(secondary: View) {
        coordinator.onSecondaryPresence(true, System.identityHashCode(secondary))
    }

    private fun applyYieldedFade(expanded: View) {
        val sibling = secondaryView(expanded) ?: return
        if (System.identityHashCode(sibling) == coordinator.ownerId) return
        val alpha = coordinator.secondaryAlpha
        sibling.alpha = alpha
        (sibling.call("getBigIslandView") as? View)?.alpha = alpha
        (sibling.call("getSmallIslandView") as? View)?.alpha = alpha
    }

    private fun playSecondaryHide(secondary: View) {
        val delegate = secondary.call("getAnimatorDelegate") ?: return
        val id = System.identityHashCode(secondary)
        yieldedSecondaryId = id
        val current = delegate.floatField("containerX")
        if (current != null) {
            parkedSecondaryId = id
            parkedSecondaryX = current.toInt()
        }
        val state = delegate.call("getHiddenAnimState") ?: return
        parkedSecondaryX?.let { retarget(delegate, state, "CONTAINER_X", it.toFloat()) }
        retarget(delegate, state, "CONTAINER_ALPHA", 0f)
        if (folmeTo(delegate, state)) return
        parkedSecondaryX?.toFloat()?.let { secondary.translationX = it }
        secondary.alpha = 0f
    }

    private fun folmeTo(delegate: Any, state: Any): Boolean {
        val loader = delegate.javaClass.classLoader ?: return false
        val folmeKt = runCatching { loader.loadClass("miui.systemui.animation.FolmeKt") }.getOrNull() ?: return false
        val getFolme = folmeKt.declaredMethods.firstOrNull { method ->
            method.name == "getFolme" &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].isInstance(delegate)
        } ?: return false
        getFolme.isAccessible = true
        val folme = runCatching { getFolme.invoke(null, delegate) }.getOrNull() ?: return false
        val configClass = runCatching { loader.loadClass("miuix.animation.base.AnimConfig") }.getOrNull() ?: return false
        val configs = java.lang.reflect.Array.newInstance(configClass, 0)
        val to = folme.javaClass.methods.firstOrNull { method ->
            method.name == "to" &&
                method.parameterTypes.size == 2 &&
                !method.parameterTypes[0].isArray &&
                method.parameterTypes[1].isArray &&
                method.parameterTypes[0].isAssignableFrom(state.javaClass)
        } ?: return false
        to.isAccessible = true
        return runCatching { to.invoke(folme, state, configs) }.isSuccess
    }

    private fun revealSecondary(expanded: View) {
        val secondary = secondaryView(expanded) ?: run {
            coordinator.onSecondaryPresence(false)
            return
        }
        val id = System.identityHashCode(secondary)
        coordinator.onSecondaryPresence(true, id)
        if (!coordinator.shouldRestoreYieldedIsland()) return
        val fromSmallIsland = synchronized(lock) { session }?.fromSmallIsland == true
        if (fromSmallIsland && coordinator.phase != TakeoverPhase.NATIVE) return
        secondary.alpha = 1f
        (secondary.call("getBigIslandView") as? View)?.alpha = 1f
        (secondary.call("getSmallIslandView") as? View)?.alpha = 1f
        if (fromSmallIsland) return
        val delegate = secondary.call("getAnimatorDelegate") ?: return
        if (secondary.call("getState")?.javaClass?.simpleName == "BigIsland") {
            delegate.call("bigIslandChangedAnimation", secondary)
            return
        }
        delegate.call("smallIslandChangedAnimation", secondary)
    }

    private fun secondaryView(expanded: View): View? {
        val coordinatorView = expanded.call("getDynamicIslandEventCoordinator") ?: return null
        listOf("getSmallIslandStateHandler", "getBigIslandStateHandler").forEach { name ->
            val current = coordinatorView.call(name)?.call("getCurrent") as? View ?: return@forEach
            if (current !== expanded) return current
        }
        return null
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

    private fun handlerCurrent(host: Any, accessor: String): View? =
        host.call(accessor)?.call("getCurrent") as? View

    private fun hookYieldedTouch(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val touch = loader.loadClass(TOUCH)
            val names = setOf(
                "performClick",
                "onInterceptTouchEvent",
                "access$" + "onInterceptTouchEvent",
            )
            touch.methodsNamedIn(names).forEach { method ->
                hook(module, loader, method) { chain ->
                    if (method.name == "performClick") clearYieldedPress(chain.thisObject)
                    val result = chain.proceed()
                    if (method.name != "performClick") clearYieldedPress(chain.thisObject)
                    result
                }
            }
            deoptimizeNamed(module, touch, names)
        }.onFailure {
            module.log("HyperPop: yielded-island touch hook unavailable: ${it.message}")
        }
    }

    private fun clearYieldedPress(interactor: Any) {
        if (!coordinator.blocksYieldedIslandTouch()) return
        val big = lazyCurrent(interactor, "bigIslandStateHandler")
        val small = lazyCurrent(interactor, "smallIslandStateHandler")
        if (big != null && coordinator.blocksTouchFor(System.identityHashCode(big))) {
            setBooleanField(interactor, "downInBigIsland", false)
        }
        if (small != null && coordinator.blocksTouchFor(System.identityHashCode(small))) {
            setBooleanField(interactor, "downInSmallIsland", false)
        }
    }

    private fun lazyCurrent(host: Any, fieldName: String): View? {
        val lazy = host.field(fieldName) ?: return null
        val handler = lazy.call("get") ?: return null
        return handler.call("getCurrent") as? View
    }

    private fun present(
        view: View,
        delegate: Any,
        current: Session,
        live: IslandRect,
    ) {
        val decision = current.decision
        val progress = ExpandedSurfaceStyle.morphProgress(live.height, current.compact.height, decision.card.height)
        val clip = clipHeight(delegate).takeIf { it > 0f } ?: live.height.toFloat()
        val nativeCap = decision.nativeRadiusPx.takeIf { it > 0f } ?: decision.radiusPx
        val radius = ExpandedSurfaceStyle.clipRadius(
            clip,
            nativeCap,
            decision.radiusPx,
            decision.pillEnabled,
            progress,
        )
        if (decision.blackBackground || decision.pillEnabled) {
            if (!ExpandedSurfaceStyler.owns(view)) {
                ExpandedSurfaceStyler.arm(view, decision.blackBackground)
            }
            ExpandedSurfaceStyler.frame(view, progress, radius)
        }
        if (decision.blackBackground) {
            realContent(view)?.let { root ->
                ExpandedFlowMaskApplicator.apply(root, decision.flowMask, progress)
            }
        }
    }

    private fun refreshDecision(view: View, current: Session): ExpandedLayoutDecision.Takeover? {
        val content = realContent(view) ?: return null
        val probed = runCatching { ExpandedContentProbe.capture(content) }.getOrNull() ?: return null
        val profile = probed.copy(nativeTopMarginPx = current.nativeTopMargin)
        val request = layoutRequest(view, current.fromSmallIsland, profile, current.style) ?: return null
        return ExpandedIslandLayoutPolicy.decide(request) as? ExpandedLayoutDecision.Takeover
    }

    private fun applyPresentation(view: View, decision: ExpandedLayoutDecision.Takeover) {
        val content = realContent(view) ?: return
        val scale = decision.contentScale
        if (scale < 0.999f) {
            originalScales.getOrPut(content) { content.scaleX }
            content.pivotX = content.width / 2f
            content.pivotY = 0f
            content.scaleX = scale
            content.scaleY = scale
        }
        val group = content as? ViewGroup ?: return
        decision.sideLifts.forEach { lift ->
            val child = group.getChildAt(lift.clusterIndex) ?: return@forEach
            originalTranslations.getOrPut(child) { child.translationY }
            child.translationY = lift.translationY.toFloat()
        }
    }

    private fun realContent(view: View): View? =
        view.call("getCurrentIslandData")?.call("getView") as? View

    private fun islandRadius(view: View): Float {
        val id = view.resources.getIdentifier("island_radius", "dimen", view.context.packageName)
        val density = view.resources.displayMetrics.density
        if (id == 0) return 30f * density
        return view.resources.getDimension(id)
    }

    private fun clipHeight(delegate: Any): Float {
        val top = delegate.floatField("containerClipTopProgress") ?: return 0f
        val bottom = delegate.floatField("containerClipBottomProgress") ?: return 0f
        return bottom - top
    }

    private fun applyOffset(view: View, extra: Int) {
        val data = view.call("getCurrentIslandData") ?: return
        listOf(data.call("getView"), data.call("getFakeView")).forEach { child ->
            val target = child as? View ?: return@forEach
            val params = target.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            val base = originalMargins.getOrPut(target) { params.topMargin }
            params.topMargin = base + extra
            target.layoutParams = params
            growAncestors(view, target, extra)
        }
    }

    private fun growAncestors(content: View, leaf: View, extra: Int) {
        var parent = leaf.parent as? View
        while (parent != null && parent !== content) {
            val params = parent.layoutParams
            if (params != null && params.height > 0) {
                val base = originalHeights.getOrPut(parent) { params.height }
                val desired = (base + extra).coerceAtLeast(1)
                if (params.height != desired) {
                    params.height = desired
                    parent.layoutParams = params
                }
            }
            parent = parent.parent as? View
        }
    }

    private fun restoreOffsets() {
        originalMargins.entries.toList().forEach { (view, margin) ->
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            params.topMargin = margin
            view.layoutParams = params
        }
        originalHeights.entries.toList().forEach { (view, height) ->
            val params = view.layoutParams ?: return@forEach
            params.height = height
            view.layoutParams = params
        }
        originalMargins.clear()
        originalHeights.clear()
        originalTranslations.entries.toList().forEach { (target, translation) ->
            target.translationY = translation
        }
        originalTranslations.clear()
        originalScales.entries.toList().forEach { (target, scale) ->
            target.scaleX = scale
            target.scaleY = scale
        }
        originalScales.clear()
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
            "resetPress",
            "containerScheduleUpdate",
            "containerClipRadius",
            "smallIslandChangedAnimation",
            "smallIslandChangedNoAnimation",
            "smallIslandToTempHiddenAnimation",
            "smallIslandToHiddenAnimation",
            "smallIslandToBigIslandAnimation",
            "bigIslandChangedAnimation",
            "bigIslandChangedNoAnimation",
            "bigIslandToTempHiddenAnimation",
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

    private fun property(delegate: Any, name: String): Any? = delegate.field(name)

    private fun Any.addProperty(property: Any, value: Float) {
        val add = javaClass.methods.firstOrNull { method ->
            method.name == "add" &&
                method.parameterTypes.size >= 2 &&
                method.parameterTypes[1] == Float::class.javaPrimitiveType &&
                method.parameterTypes[0].isAssignableFrom(property.javaClass)
        } ?: return
        add.isAccessible = true
        if (add.parameterTypes.size == 2) add.invoke(this, property, value)
        else add.invoke(this, property, value, LongArray(0))
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

    private class Session(
        view: View,
        val generation: Long,
        var decision: ExpandedLayoutDecision.Takeover,
        val compact: IslandRect,
        val fromSmallIsland: Boolean,
        val nativeTopMargin: Int,
        val style: ExpandedVisualStyle,
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

private fun Any.call(name: String, vararg args: Any?): Any? {
    val method = javaClass.methodsNamed(name).firstOrNull { it.parameterTypes.size == args.size } ?: return null
    method.isAccessible = true
    return method.invoke(this, *args)
}

private fun Any.field(name: String): Any? {
    val found = javaClass.findField(name) ?: return null
    found.isAccessible = true
    return found.get(if (java.lang.reflect.Modifier.isStatic(found.modifiers)) null else this)
}

private fun Any.intField(name: String): Int? = (field(name) as? Number)?.toInt()

private fun Any.floatField(name: String): Float? = (field(name) as? Number)?.toFloat()

private fun setFloatField(target: Any, name: String, value: Float) {
    val found = target.javaClass.findField(name) ?: return
    found.isAccessible = true
    found.setFloat(target, value)
}

private fun setBooleanField(target: Any, name: String, value: Boolean) {
    val found = target.javaClass.findField(name) ?: return
    found.isAccessible = true
    found.setBoolean(target, value)
}
