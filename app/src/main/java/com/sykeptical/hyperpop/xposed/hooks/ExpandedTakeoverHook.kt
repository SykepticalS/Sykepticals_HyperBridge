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
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedIslandLayoutPolicy
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedLayoutDecision
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedLayoutRequest
import com.sykeptical.hyperpop.service.animation.expanded.ExpandedTakeoverCoordinator
import com.sykeptical.hyperpop.service.animation.expanded.IslandRect
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
 * getter truncates height to a multiple of the compact height), ear shift, and
 * the touch region. The secondary island is faded with transitionAlpha.
 * [smallIslandToTempHiddenAnimation] is intentionally not used: it retargets
 * that island's whole animation to the cutout.
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
    private const val UTILS = "miui.systemui.util.CommonUtils"
    private const val SELF_CHECK_PX = 20
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
    private val lock = Any()

    @Volatile private var selfCheckFailed = false
    @Volatile private var loggedFailure = false
    @Volatile private var session: Session? = null

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
            val bigSchedule = delegate.noArg("bigIslandScheduleUpdate")
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
                val state = chain.proceed()
                val takeover = view?.let { runCatching { activeDecision(it) }.getOrNull() }
                if (takeover != null && state != null) {
                    runCatching { patchEars(chain.thisObject, state, takeover) }
                }
                state
            }
            hook(module, loader, schedule) { chain ->
                val result = chain.proceed()
                onFrame(chain.thisObject)
                result
            }
            hook(module, loader, bigSchedule) { chain ->
                val result = chain.proceed()
                applyEarAlpha(chain.thisObject)
                result
            }
            hook(module, loader, region) { chain -> narrowedRegion(chain.thisObject, chain.proceed()) }
            hook(module, loader, setState) { chain ->
                val result = chain.proceed()
                onState(chain.thisObject as View, chain.args.getOrNull(0))
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
                hook(module, loader, method) { chain -> withoutSecondary(chain.proceed()) }
            }
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
        return base + takeover.bodyOffsetPx
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
        StatusBarTakeoverHook.apply(coordinator.statusBarAlpha)
        applySecondary(view, coordinator.secondaryAlpha)
        applyEarAlpha(delegate)
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

    private fun withoutSecondary(native: Any?): Any? {
        val region = native as? Region ?: return native
        if (!coordinator.secondarySuppressed) return region
        val rect = secondaryRect() ?: return region
        region.op(Region(rect.left, rect.top, rect.right, rect.bottom), Region.Op.DIFFERENCE)
        return region
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
        val request = layoutRequest(view) ?: return null
        val decision = ExpandedIslandLayoutPolicy.decide(request)
        if (decision !is ExpandedLayoutDecision.Takeover) return null
        val generation = coordinator.arm(id, SystemClock.uptimeMillis())
        if (generation < 0) return null
        val ears = earShifts(view, decision)
        synchronized(lock) {
            session = Session(view, generation, decision, request.compact, ears.first, ears.second)
        }
        applyOffset(view, decision.bodyOffsetPx)
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
        applySecondary(view, 1f)
        restoreEarAlpha(view)
        StatusBarTakeoverHook.restore()
        synchronized(lock) {
            if (coordinator.phase.name == "NATIVE") session = null
        }
    }

    private fun reapplyOffset(view: View) {
        val current = synchronized(lock) { session } ?: return
        if (!coordinator.accepts(System.identityHashCode(view), current.generation)) return
        applyOffset(view, current.decision.bodyOffsetPx)
    }

    private fun layoutRequest(view: View): ExpandedLayoutRequest? {
        val metrics = view.resources.displayMetrics
        val cutout = cutoutRect(view) ?: return null
        val compact = rectOf(view.call("getBigIslandRect", java.lang.Boolean.FALSE)) ?: return null
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

    private fun earShifts(
        view: View,
        decision: ExpandedLayoutDecision.Takeover,
    ): Pair<Float?, Float?> {
        val left = view.call("getBigIslandAreaLeft") as? View
        val right = view.call("getBigIslandAreaRight") as? View
        return shiftFor(left, decision.leadingEar) to shiftFor(right, decision.trailingEar)
    }

    private fun shiftFor(area: View?, placed: IslandRect?): Float? {
        if (area == null || placed == null) return null
        val measured = viewRect(area) ?: return null
        return area.translationX + (placed.left - measured.left)
    }

    private fun patchEars(delegate: Any, state: Any, decision: ExpandedLayoutDecision.Takeover) {
        val current = synchronized(lock) { session } ?: return
        val alpha = property(delegate, "BIG_ISLAND_ALPHA") ?: return
        val scale = property(delegate, "BIG_ISLAND_SCALE") ?: return
        val transY = property(delegate, "BIG_ISLAND_TRANS_Y") ?: return
        val left = property(delegate, "BIG_ISLAND_AREA_LEFT_TRANS_X")
        val right = property(delegate, "BIG_ISLAND_AREA_RIGHT_TRANS_X")
        state.addProperty(alpha, 1f)
        state.addProperty(scale, 1f)
        state.addProperty(transY, 0f)
        if (left != null) state.addProperty(left, current.leadingShift ?: 0f)
        if (right != null) state.addProperty(right, current.trailingShift ?: 0f)
        if (decision.leadingEar == null) current.owner.get()?.let { hideArea(it, "getBigIslandAreaLeft") }
        if (decision.trailingEar == null) current.owner.get()?.let { hideArea(it, "getBigIslandAreaRight") }
    }

    private fun applyEarAlpha(delegate: Any) {
        val view = contentView(delegate) ?: return
        val current = synchronized(lock) { session } ?: return
        if (!ownerIs(System.identityHashCode(view))) return
        setAreaAlpha(view, "getBigIslandAreaLeft", if (current.leadingShift == null && current.decision.leadingEar == null) 0f else 1f)
        setAreaAlpha(view, "getBigIslandAreaRight", if (current.trailingShift == null && current.decision.trailingEar == null) 0f else 1f)
    }

    private fun restoreEarAlpha(view: View) {
        setAreaAlpha(view, "getBigIslandAreaLeft", 1f)
        setAreaAlpha(view, "getBigIslandAreaRight", 1f)
    }

    private fun hideArea(view: View, getter: String) = setAreaAlpha(view, getter, 0f)

    private fun setAreaAlpha(view: View, getter: String, alpha: Float) {
        (view.call(getter) as? View)?.alpha = alpha
    }

    private fun applySecondary(expanded: View, alpha: Float) {
        val coordinatorView = expanded.call("getDynamicIslandEventCoordinator") ?: return
        val handler = coordinatorView.call("getSmallIslandStateHandler") ?: return
        val current = handler.call("getCurrent") as? View ?: run {
            coordinator.onSecondaryPresence(false)
            return
        }
        coordinator.onSecondaryPresence(current !== expanded)
        if (current === expanded) return
        val small = current.call("getSmallIslandView") as? View ?: current
        small.transitionAlpha = alpha
        if (alpha >= 0.99f) small.transitionAlpha = 1f
    }

    private fun secondaryRect(): IslandRect? {
        val current = synchronized(lock) { session }?.owner?.get() ?: return null
        val coordinatorView = current.call("getDynamicIslandEventCoordinator") ?: return null
        val secondary = coordinatorView.call("getSmallIslandStateHandler")?.call("getCurrent") as? View
            ?: return null
        if (secondary === current) return null
        return rectOf(secondary.call("getSmallIslandRect")) ?: viewRect(secondary.call("getSmallIslandView") as? View)
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
                val desired = base + extra
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
        )
        val coordinatorNames = setOf("getExpandedIslandRegion", "updateWindowHeight", "onAnimationStart")
        listOf(base to baseNames, delegate to delegateNames, coordinatorClass to coordinatorNames)
            .forEach { (clazz, names) -> deoptimizeNamed(module, clazz, names) }
        runCatching { loader.loadClass(PHONE_HELPER) }.getOrNull()?.let { helper ->
            deoptimizeNamed(module, helper, setOf("calcInitToExpandedParams", "calcLocationParams"))
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
        val decision: ExpandedLayoutDecision.Takeover,
        val compact: IslandRect,
        val leadingShift: Float?,
        val trailingShift: Float?,
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
