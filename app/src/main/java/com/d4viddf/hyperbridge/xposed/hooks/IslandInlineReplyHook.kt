package com.d4viddf.hyperbridge.xposed.hooks

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.d4viddf.hyperbridge.models.IslandWindowImePolicy
import com.d4viddf.hyperbridge.ui.InlineReplyActivity
import com.d4viddf.hyperbridge.ui.InlineReplyIntents
import com.d4viddf.hyperbridge.xposed.dispatch.SystemUiDispatcher
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Shows the reply composer inside the expanded island instead of launching a
 * bottom-of-screen HyperBridge activity.
 */
object IslandInlineReplyHook {
    private const val CONTROLLER =
        "com.android.systemui.statusbar.notification.DynamicIslandController"
    private val stayExpandedCallbacks = setOf(
        "onDynamicPluginCallback_expandedToSmall",
        "onDynamicPluginCallback_bigToSmall",
        "onDynamicPluginCallback_expandedToBig",
    )
    private const val DROP_DOWN = "onDynamicPluginCallback_dropDownExpandedIsland"
    private val OUTSIDE_COLLAPSE_REASONS = setOf("outside", "input monitor", "action collapse")
    private const val EXPANDED_TO_BIG = "onDynamicPluginCallback_expandedToBig"
    private val loaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    private val dispatchers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()),
    )
    @Volatile private var sendHooked = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookPendingIntentSend(module, param.defaultClassLoader)
        hookDispatchers(module, param.defaultClassLoader)
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hookDispatchers(module, loader)
            hook(module, loader)
        }
    }

    private val handling = ThreadLocal<Boolean>()

    private fun hookPendingIntentSend(module: XposedModule, loader: ClassLoader) {
        if (sendHooked) return
        sendHooked = true
        runCatching {
            val clazz = loader.loadClass("android.app.PendingIntent")
            val methods = clazz.declaredMethods.filter {
                it.name == "send" || it.name == "sendAndReturnResult"
            }
            check(methods.isNotEmpty()) { "PendingIntent.send missing" }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    if (openReply(chain.thisObject, chain.args, module)) null else chain.proceed()
                }
            }
            module.log("HyperBridge: island inline-reply PendingIntent send hooked count=${methods.size}")
            Log.i("HyperBridge", "island inline-reply PendingIntent send hooked count=${methods.size}")
        }.onFailure {
            sendHooked = false
            module.log("HyperBridge: island inline-reply send hook unavailable: ${it.message}")
        }
    }

    private fun hookDispatchers(module: XposedModule, loader: ClassLoader) {
        if (!dispatchers.add(loader)) return
        hookLiveClickPath(module, loader)
        hookLiveCollapsePath(module, loader)
        val names = arrayOf(
            "miui.systemui.notification.focus.FocusNotifPreHandler\$ClickHandler",
            "miui.systemui.notification.focus.FocusNotifPreHandler",
            "miui.systemui.notification.focus.FocusNotifUtils",
            "miui.systemui.dynamicisland.event.ClickEventCoordinator",
            "miui.systemui.notification.focus.moduleV3.ModuleTextButtonViewHolder",
            "miui.systemui.notification.focus.moduleV3.ModuleTextButton4ViewHolder",
            "miui.systemui.notification.focus.moduleV3.ModuleTextButton5ViewHolder",
            "miui.systemui.notification.focus.moduleV3.ModuleDecoPortTextButtonViewHolder",
            "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate",
        )
        names.forEach { name ->
            runCatching {
                val clazz = loader.loadClass(name)
                val collapseClass = name.contains("AnimationDelegate")
                val methods = clazz.declaredMethods.filter { method ->
                    if (collapseClass) {
                        holdTimer(method)
                    } else {
                        method.parameterTypes.any { PendingIntent::class.java.isAssignableFrom(it) } ||
                            method.name.contains("startPendingIntent", ignoreCase = true) ||
                            method.name.contains("clickWithCollapse", ignoreCase = true) ||
                            method.name.contains("onClick", ignoreCase = true) ||
                            method.name.contains("ExpandedTime", ignoreCase = true) ||
                            method.name.contains("TimeoutMs", ignoreCase = true)
                    }
                }
                methods.forEach { method ->
                    module.hook(method).intercept { chain ->
                        if (holdTimer(method) && IslandReplyComposer.shouldStayExpanded()) {
                            Log.i("HyperBridge", "island reply held ${clazz.simpleName}.${method.name}")
                            return@intercept heldResult(method)
                        }
                        if (collapseClass) return@intercept chain.proceed()
                        val collapseMethod = method.name.contains("collapse", ignoreCase = true) ||
                            method.name.contains("toSmall", ignoreCase = true)
                        if (openReply(chain.thisObject, chain.args, module)) {
                            null
                        } else if (collapseMethod && IslandReplyComposer.shouldStayExpanded()) {
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }
                if (methods.isNotEmpty()) {
                    module.log("HyperBridge: island reply hooked ${clazz.simpleName} methods=${methods.size}")
                    Log.i("HyperBridge", "island reply hooked ${clazz.simpleName} methods=${methods.size}")
                }
            }
        }
    }

    /** Exact HyperOS 4 path from MIUISystemUIPlugin 17.1.4.71.0. */
    private fun hookLiveClickPath(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val clazz = loader.loadClass(
                "miui.systemui.notification.focus.moduleV3.ModuleViewHolder",
            )
            val methods = clazz.declaredMethods.filter { method ->
                method.name == "handleBtnClick" &&
                    method.parameterTypes.firstOrNull() == PendingIntent::class.java
            }
            check(methods.isNotEmpty()) { "ModuleViewHolder.handleBtnClick missing" }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    if (openReply(chain.thisObject, chain.args, module)) null else chain.proceed()
                }
            }
            module.log("HyperBridge: plugin class hooked ModuleViewHolder.handleBtnClick count=${methods.size}")
            Log.i("HyperBridge", "plugin class hooked ModuleViewHolder.handleBtnClick count=${methods.size}")
        }.onFailure {
            module.log("HyperBridge: ModuleViewHolder.handleBtnClick hook unavailable: ${it.message}")
        }
    }

    /**
     * The live plugin schedules expanded -> small in delayCollapsed(), then its
     * private lambda calls DynamicIslandWindowView.collapse("delay"). Guard all
     * three points so an already queued runnable cannot race the composer open.
     */
    private fun hookLiveCollapsePath(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val safeguards = loader.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandSafeguardsController",
            )
            val delay = safeguards.declaredMethods.single {
                it.name == "delayCollapsed" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == java.lang.Long.TYPE
            }
            module.hook(delay).intercept { chain ->
                IslandReplyCollapseGuard.remember(chain.thisObject)
                val seconds = chain.args.getOrNull(0) as? Long ?: 0L
                if (IslandReplyComposer.shouldStayExpanded()) {
                    IslandReplyCollapseGuard.deferCollapse(chain.thisObject, seconds)
                    Log.i("HyperBridge", "timeout method blocked DynamicIslandSafeguardsController.delayCollapsed")
                    null
                } else {
                    IslandReplyCollapseGuard.scheduledCollapse(chain.thisObject, seconds)
                    chain.proceed()
                }
            }
            IslandLifetimeHold.install(module, safeguards)
            safeguards.declaredMethods
                .filter { it.name == "delayCollapsed\$lambda\$3" }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        if (IslandReplyComposer.shouldStayExpanded()) {
                            Log.i("HyperBridge", "timeout method blocked DynamicIslandSafeguardsController.delayCollapsed\$lambda\$3")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }

            val window = loader.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowView",
            )
            window.declaredMethods
                .filter {
                    it.name == "collapse" &&
                        it.parameterTypes.contentEquals(arrayOf(String::class.java))
                }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val reason = chain.args.firstOrNull() as? String
                        if (reason == "delay" && IslandReplyComposer.shouldStayExpanded()) {
                            Log.i("HyperBridge", "timeout method blocked DynamicIslandWindowView.collapse(delay)")
                            null
                        } else if (reason in OUTSIDE_COLLAPSE_REASONS && IslandReplyComposer.consumeOutsideCollapse()) {
                            Log.i("HyperBridge", "island reply kept island expanded, blocked collapse($reason)")
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }
            window.declaredMethods
                .filter {
                    it.name == "maybeCollapseExpand" &&
                        it.parameterTypes.contentEquals(arrayOf(Integer.TYPE, Integer.TYPE))
                }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        if (!IslandReplyComposer.isOpen()) return@intercept chain.proceed()
                        val x = chain.args[0] as Int
                        val y = chain.args[1] as Int
                        if (IslandReplyComposer.onMonitoredTouch(chain.thisObject, x, y)) chain.proceed() else null
                    }
                }
            module.log("HyperBridge: plugin class hooked DynamicIslandSafeguardsController.delayCollapsed")
            Log.i("HyperBridge", "plugin class hooked DynamicIslandSafeguardsController.delayCollapsed")
        }.onFailure {
            module.log("HyperBridge: live island collapse hook unavailable: ${it.message}")
        }
    }

    private fun openReply(thisObject: Any?, args: List<Any?>?, module: XposedModule): Boolean {
        if (handling.get() == true) return false
        val pendingIntent = (thisObject as? PendingIntent)
            ?: args?.firstOrNull { it is PendingIntent } as? PendingIntent
            ?: return false
        val payload = IslandReplyComposer.payloadFrom(pendingIntent) ?: return false
        val context = args?.firstOrNull { it is Context } as? Context
            ?: (thisObject as? View)?.context
            ?: (thisObject as? Context)
        val source = args?.firstOrNull { it is View } ?: thisObject
        IslandReplyComposer.markOpening()
        handling.set(true)
        return try {
            IslandReplyComposer.openNow(context, payload, module, source)
            Log.i("HyperBridge", "island reply intercept swallowed activity fallback")
            true
        } finally {
            handling.set(false)
        }
    }

    private fun holdTimer(method: java.lang.reflect.Method): Boolean {
        val name = method.name
        if (name.contains("dropDown", ignoreCase = true)) return false
        return name.contains("ToSmall", ignoreCase = true) ||
            name.contains("resetToSmall", ignoreCase = true) ||
            name.contains("expandedToBig", ignoreCase = true) ||
            name.contains("ExpandedTime", ignoreCase = true) ||
            name.contains("TimeoutMs", ignoreCase = true) ||
            name.contains("timeout", ignoreCase = true) && name.startsWith("get")
    }

    private fun heldResult(method: java.lang.reflect.Method): Any? = when (method.returnType) {
        java.lang.Boolean.TYPE -> java.lang.Boolean.FALSE
        java.lang.Integer.TYPE -> Int.MAX_VALUE / 4
        java.lang.Long.TYPE -> 24 * 60 * 60 * 1000L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        else -> null
    }

    private fun hook(module: XposedModule, loader: ClassLoader) {
        if (!loaders.add(loader)) return
        runCatching {
            val controller = loader.loadClass(CONTROLLER)
            val callback = findCallback(controller)
            module.hook(callback).intercept { chain ->
                val name = chain.args.getOrNull(0) as? String
                if (name == DROP_DOWN) {
                    IslandReplyComposer.dismiss()
                    return@intercept chain.proceed()
                }
                if (name in stayExpandedCallbacks && IslandReplyComposer.shouldStayExpanded()) {
                    Log.i("HyperBridge", "island reply holding expanded, skipped $name")
                    return@intercept null
                }
                val result = chain.proceed()
                if (name == EXPANDED_TO_BIG) IslandReplyComposer.ensureAttached()
                result
            }
        }.onFailure {
            loaders.remove(loader)
        }
    }

    private fun findCallback(controller: Class<*>): java.lang.reflect.Method {
        var current: Class<*>? = controller
        while (current != null) {
            runCatching {
                return current.getDeclaredMethod(
                    "onDynamicPluginCallback",
                    String::class.java,
                    Bundle::class.java,
                )
            }
            current = current.superclass
        }
        error("onDynamicPluginCallback(String, Bundle) not found")
    }
}

/**
 * Pauses the expanded -> small timer (DynamicIslandSafeguardsController.delayCollapsed,
 * in seconds) while the composer is open and re-arms it with the time that was left.
 */
private object IslandReplyCollapseGuard {
    private val safeguards = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>()),
    )
    private var collapseOwner = WeakReference<Any>(null)
    private var collapseDeadline = 0L
    private var pausedRemainingMs: Long? = null

    fun remember(instance: Any?) {
        if (instance != null) safeguards += instance
    }

    @Synchronized
    fun scheduledCollapse(instance: Any?, seconds: Long) {
        collapseOwner = WeakReference(instance)
        collapseDeadline = SystemClock.uptimeMillis() + seconds * 1000L
    }

    @Synchronized
    fun deferCollapse(instance: Any?, seconds: Long) {
        collapseOwner = WeakReference(instance)
        pausedRemainingMs = seconds * 1000L
    }

    fun cancelScheduledCollapse() {
        synchronized(this) {
            val remaining = collapseDeadline - SystemClock.uptimeMillis()
            if (collapseDeadline > 0L && remaining > 0L) pausedRemainingMs = remaining
            collapseDeadline = 0L
        }
        val snapshot = synchronized(safeguards) { safeguards.toList() }
        snapshot.forEach { instance ->
            runCatching {
                instance.javaClass.getDeclaredMethod("cancelDelayCollapsed").apply {
                    isAccessible = true
                }.invoke(instance)
            }
        }
        if (snapshot.isNotEmpty()) {
            Log.i("HyperBridge", "cancelled scheduled island collapse instances=${snapshot.size}")
        }
    }

    fun resumeScheduledCollapse() {
        val (owner, remaining) = synchronized(this) {
            val value = collapseOwner.get() to pausedRemainingMs
            pausedRemainingMs = null
            value
        }
        if (owner == null || remaining == null) return
        val seconds = ((remaining + 999L) / 1000L).coerceAtLeast(1L)
        runCatching {
            owner.javaClass.getDeclaredMethod("delayCollapsed", java.lang.Long.TYPE).apply {
                isAccessible = true
            }.invoke(owner, seconds)
            Log.i("HyperBridge", "resumed island collapse timer seconds=$seconds")
        }
    }
}

/**
 * HyperOS removes an island after `islandTimeout` via
 * DynamicIslandSafeguardsController.delayDeleted(key, seconds). Freeze those timers while
 * the composer is open and resume each with its remaining time afterwards.
 */
private object IslandLifetimeHold {
    private class Timer(val owner: Any, val deadline: Long)
    private class Paused(val owner: Any, val remainingMs: Long)

    private val scheduled = HashMap<String, Timer>()
    private val paused = LinkedHashMap<String, Paused>()
    private var cancelMethod: java.lang.reflect.Method? = null
    private var delayMethod: java.lang.reflect.Method? = null
    @Volatile private var internalCall = false
    @Volatile private var held = false

    fun install(module: XposedModule, safeguards: Class<*>) {
        runCatching {
            val delay = safeguards.getDeclaredMethod(
                "delayDeleted",
                String::class.java,
                java.lang.Long.TYPE,
            ).apply { isAccessible = true }
            val cancel = safeguards.getDeclaredMethod("cancelDelayDeleted", String::class.java)
                .apply { isAccessible = true }
            delayMethod = delay
            cancelMethod = cancel
            module.hook(delay).intercept { chain ->
                val key = chain.args.getOrNull(0) as? String ?: return@intercept chain.proceed()
                val seconds = chain.args.getOrNull(1) as? Long ?: return@intercept chain.proceed()
                val owner = chain.thisObject ?: return@intercept chain.proceed()
                synchronized(this) {
                    if (held) {
                        scheduled.remove(key)
                        paused[key] = Paused(owner, seconds * 1000L)
                        Log.i("HyperBridge", "island lifetime deferred while replying key=$key seconds=$seconds")
                        return@intercept null
                    }
                }
                val result = chain.proceed()
                synchronized(this) {
                    scheduled[key] = Timer(owner, SystemClock.uptimeMillis() + seconds * 1000L)
                }
                result
            }
            module.hook(cancel).intercept { chain ->
                if (!internalCall) {
                    val key = chain.args.getOrNull(0) as? String
                    if (key != null) synchronized(this) {
                        scheduled.remove(key)
                        paused.remove(key)
                    }
                }
                chain.proceed()
            }
            safeguards.declaredMethods
                .filter { it.name == "delayDeleted\$lambda\$1" && it.parameterCount == 2 }
                .forEach { method ->
                    module.hook(method).intercept { chain ->
                        val key = chain.args.getOrNull(0) as? String
                        val owner = chain.args.getOrNull(1)
                        synchronized(this) {
                            if (key != null) scheduled.remove(key)
                            if (held && key != null && owner != null) {
                                paused[key] = Paused(owner, 1000L)
                                return@intercept null
                            }
                        }
                        chain.proceed()
                    }
                }
            module.log("HyperBridge: island reply lifetime hold hooked delayDeleted")
        }.onFailure {
            module.log("HyperBridge: island reply lifetime hold unavailable: ${it.message}")
        }
    }

    fun pause() {
        val cancel = cancelMethod ?: return
        val toCancel = synchronized(this) {
            if (held) return
            held = true
            val now = SystemClock.uptimeMillis()
            scheduled.forEach { (key, timer) ->
                val remaining = timer.deadline - now
                if (remaining > 0L) paused[key] = Paused(timer.owner, remaining)
            }
            scheduled.clear()
            paused.map { it.key to it.value.owner }
        }
        internalCall = true
        try {
            toCancel.forEach { (key, owner) -> runCatching { cancel.invoke(owner, key) } }
        } finally {
            internalCall = false
        }
        if (toCancel.isNotEmpty()) Log.i("HyperBridge", "island lifetime paused count=${toCancel.size}")
    }

    fun resume() {
        val delay = delayMethod ?: return
        val toResume = synchronized(this) {
            if (!held) return
            held = false
            val snapshot = paused.toList()
            paused.clear()
            snapshot
        }
        toResume.forEach { (key, timer) ->
            val seconds = ((timer.remainingMs + 999L) / 1000L).coerceAtLeast(1L)
            runCatching { delay.invoke(timer.owner, key, seconds) }
        }
        if (toResume.isNotEmpty()) Log.i("HyperBridge", "island lifetime resumed count=${toResume.size}")
    }
}

internal data class IslandReplyPayload(
    val replyAction: PendingIntent,
    val resultKey: String,
    val sourcePackage: String?,
)

internal object IslandReplyComposer {
    private const val TAG = "hyperbridge.island_reply"
    private val main = Handler(Looper.getMainLooper())
    private var overlay = WeakReference<View>(null)
    private var imeFlags: Pair<Int, Int>? = null
    @Volatile private var intendedOpen = false
    private var lastPayload: IslandReplyPayload? = null
    private var lastModule: XposedModule? = null
    private var lastContext = WeakReference<Context?>(null)
    private var lastSource: Any? = null
    private var activeRow: ViewGroup? = null
    private var retryRoot: View? = null
    private var retryListener: View.OnLayoutChangeListener? = null
    private var dumpedMissingHost = false
    private var hiddenButtons: List<View> = emptyList()
    @Volatile private var lastMonitoredTouchAt = 0L
    @Volatile private var outsideDismissedUntil = 0L

    fun openNow(
        context: Context?,
        payload: IslandReplyPayload,
        module: XposedModule,
        source: Any? = null,
    ): Boolean {
        lastPayload = payload
        lastModule = module
        lastContext = WeakReference(context)
        lastSource = source
        intendedOpen = true
        MarqueeHook.holdAutoHide()
        SystemUiDispatcher.notifyReplyComposer(true)
        IslandReplyCollapseGuard.cancelScheduledCollapse()
        pauseLifetime()
        val run = {
            runCatching { embed(payload, module, source) }
                .onFailure { module.log("HyperBridge: island reply embed failed: ${it.message}") }
                .getOrDefault(false)
        }
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            run() || scheduleRetry(payload, module, source)
        } else {
            var shown = false
            val posted = java.util.concurrent.CountDownLatch(1)
            main.postAtFrontOfQueue {
                shown = run() || scheduleRetry(payload, module, source)
                posted.countDown()
            }
            posted.await(400, java.util.concurrent.TimeUnit.MILLISECONDS)
            shown || intendedOpen
        }
    }

    fun open(context: Context?, payload: IslandReplyPayload, module: XposedModule) {
        openNow(context, payload, module)
    }

    private fun scheduleRetry(
        payload: IslandReplyPayload,
        module: XposedModule,
        source: Any?,
    ): Boolean {
        main.postDelayed({
            if (!intendedOpen) return@postDelayed
            if (overlay.get()?.isAttachedToWindow == true) return@postDelayed
            runCatching { embed(payload, module, source) }
        }, 50)
        main.postDelayed({
            if (!intendedOpen) return@postDelayed
            if (overlay.get()?.isAttachedToWindow == true) return@postDelayed
            runCatching { embed(payload, module, source) }
        }, 160)
        return true
    }

    fun isOpen(): Boolean = intendedOpen

    fun markOpening() {
        intendedOpen = true
        MarqueeHook.holdAutoHide()
        IslandReplyCollapseGuard.cancelScheduledCollapse()
        pauseLifetime()
    }

    fun markAborted() {
        if (overlay.get()?.isAttachedToWindow == true) return
        intendedOpen = false
        resumeTimers()
    }

    private fun pauseLifetime() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            IslandLifetimeHold.pause()
        } else {
            main.postAtFrontOfQueue { if (intendedOpen) IslandLifetimeHold.pause() }
        }
    }

    private fun resumeTimers() {
        IslandLifetimeHold.resume()
        IslandReplyCollapseGuard.resumeScheduledCollapse()
    }

    /**
     * Returns true to let SystemUI handle the monitored touch (inside the island),
     * false to swallow it: keyboard taps keep the composer, other taps close only the composer.
     */
    fun onMonitoredTouch(windowView: Any?, x: Int, y: Int): Boolean {
        lastMonitoredTouchAt = SystemClock.uptimeMillis()
        val composer = overlay.get() ?: return true
        if (insideIsland(windowView, composer, x, y)) return true
        if (imeFrame(composer)?.contains(x, y) == true) return false
        if (imeFrame(composer) == null && imeShowing(composer) && y >= screenHeight(composer) / 2) return false
        dismissFromOutside()
        return false
    }

    /** True when an outside collapse must be swallowed so only the composer closes. */
    fun consumeOutsideCollapse(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now < outsideDismissedUntil) return true
        if (!intendedOpen) return false
        main.postDelayed({
            if (!intendedOpen) return@postDelayed
            if (SystemClock.uptimeMillis() - lastMonitoredTouchAt < 400L) return@postDelayed
            val composer = overlay.get()
            if (composer != null && imeShowing(composer)) return@postDelayed
            dismissFromOutside()
        }, 150L)
        return true
    }

    private fun dismissFromOutside() {
        outsideDismissedUntil = SystemClock.uptimeMillis() + 600L
        Log.i("HyperBridge", "island reply dismissed by outside tap; island stays expanded")
        dismiss()
    }

    private fun insideIsland(windowView: Any?, composer: View, x: Int, y: Int): Boolean {
        val region = runCatching {
            val coordinator = windowView?.javaClass?.getDeclaredField("eventCoordinator")
                ?.apply { isAccessible = true }?.get(windowView) ?: return@runCatching null
            val flow = coordinator.javaClass.getMethod("getTouchRegion").invoke(coordinator)
                ?: return@runCatching null
            flow.javaClass.getMethod("getValue").apply { isAccessible = true }.invoke(flow) as? Region
        }.getOrNull()
        if (region != null && !region.isEmpty) return region.contains(x, y)
        val island = expandedAncestor(composer) ?: return false
        val location = IntArray(2)
        island.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + island.width, location[1] + island.height)
            .contains(x, y)
    }

    private fun imeShowing(view: View): Boolean =
        view.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true

    private fun screenHeight(view: View): Int =
        view.context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds?.height()
            ?: view.resources.displayMetrics.heightPixels

    /** Keyboard frame in display coordinates; the island window never overlaps it, so insets read 0. */
    private fun imeFrame(view: View): Rect? = runCatching {
        val root = View::class.java.getMethod("getViewRootImpl").invoke(view) ?: return@runCatching null
        val controller = root.javaClass.getMethod("getInsetsController").invoke(root)
        val state = controller.javaClass.getMethod("getState").invoke(controller)
        val size = state.javaClass.getMethod("sourceSize").invoke(state) as Int
        val sourceAt = state.javaClass.getMethod("sourceAt", Integer.TYPE)
        for (index in 0 until size) {
            val source = sourceAt.invoke(state, index) ?: continue
            val type = source.javaClass.getMethod("getType").invoke(source) as Int
            if (type != WindowInsets.Type.ime()) continue
            val visible = source.javaClass.getMethod("isVisible").invoke(source) as Boolean
            val frame = source.javaClass.getMethod("getFrame").invoke(source) as Rect
            if (visible && !frame.isEmpty) return@runCatching Rect(frame)
        }
        null
    }.getOrNull()

    fun shouldStayExpanded(): Boolean = intendedOpen

    fun ensureAttached() {
        if (!intendedOpen) return
        val view = overlay.get()
        if (view?.isAttachedToWindow == true && view.isShown) return
        val payload = lastPayload ?: return
        val module = lastModule ?: return
        val run = {
            runCatching { embed(payload, module, lastSource) }
            Unit
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else main.post(run)
    }

    fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { dismiss() }
            return
        }
        intendedOpen = false
        clearLayoutRetry()
        restoreHiddenButtons()
        val view = overlay.get()
        overlay = WeakReference(null)
        if (view != null) {
            hideIme(view)
            (view.parent as? ViewGroup)?.removeView(view)
            restoreImeWindow(view)
        }
        activeRow = null
        lastSource = null
        MarqueeHook.releaseAutoHide()
        resumeTimers()
        SystemUiDispatcher.notifyReplyComposer(false)
    }

    private fun dismissKeepingIntent() {
        restoreHiddenButtons()
        val view = overlay.get() ?: return
        overlay = WeakReference(null)
        hideIme(view)
        (view.parent as? ViewGroup)?.removeView(view)
        restoreImeWindow(view)
    }

    private fun restoreHiddenButtons() {
        hiddenButtons.forEach { button ->
            button.visibility = View.VISIBLE
        }
        hiddenButtons = emptyList()
    }

    fun payloadFrom(pendingIntent: PendingIntent): IslandReplyPayload? {
        val intent = pendingIntentIntent(pendingIntent) ?: return null
        if (!intent.getBooleanExtra(InlineReplyIntents.EXTRA_INLINE_REPLY, false) &&
            intent.action != InlineReplyIntents.ACTION &&
            intent.component?.className?.contains("InlineReply") != true
        ) {
            return null
        }
        val replyAction = parcelablePendingIntent(intent, InlineReplyActivity.EXTRA_PENDING_INTENT)
            ?: return null
        val resultKey = intent.getStringExtra(InlineReplyActivity.EXTRA_RESULT_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return IslandReplyPayload(
            replyAction = replyAction,
            resultKey = resultKey,
            sourcePackage = intent.getStringExtra(InlineReplyActivity.EXTRA_PACKAGE_NAME),
        )
    }

    private fun embed(
        payload: IslandReplyPayload,
        module: XposedModule,
        source: Any?,
    ): Boolean {
        val row = findButtonRow(source)?.takeIf { host ->
            host.javaClass.simpleName.contains("Window", ignoreCase = true).not() &&
                host.childCount <= 12
        } ?: run {
            installLayoutRetry(payload, module, source)
            if (!dumpedMissingHost) {
                dumpedMissingHost = true
                dumpExpandedTree(module, source)
            }
            return false
        }
        clearLayoutRetry()
        dumpedMissingHost = false
        val existing = overlay.get()
        if (existing?.parent === row && existing.isAttachedToWindow) return true
        dismissKeepingIntent()
        val hostHeight = row.height - row.paddingTop - row.paddingBottom
        val composer = buildComposer(row.context, payload, module, hostHeight)
        hiddenButtons = (0 until row.childCount).map { row.getChildAt(it) }.filter { it !== composer }
        hiddenButtons.forEach { it.visibility = View.GONE }
        val params = when (row) {
            is LinearLayout -> LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (row.orientation == LinearLayout.HORIZONTAL) 1f else 0f,
            ).apply { gravity = Gravity.CENTER_VERTICAL }
            is FrameLayout -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
            else -> ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        row.addView(composer, params)
        activeRow = row
        overlay = WeakReference(composer)
        intendedOpen = true
        IslandWindowImeHook.sanitize(row)
        composer.post {
            val field = composer.findViewWithTag<EditText>("$TAG.field") ?: return@post
            showKeyboard(field)
        }
        module.log("HyperBridge: island reply embedded in ${row.javaClass.simpleName} children=${row.childCount}")
        Log.i("HyperBridge", "island reply embedded in ${row.javaClass.simpleName} width=${row.width} height=$hostHeight")
        return true
    }

    /**
     * The island window only becomes focusable after prepareImeWindow's relayout, so an
     * immediate showSoftInput is dropped; show again once the window actually gains focus.
     */
    private fun showKeyboard(field: EditText) {
        prepareImeWindow(field)
        field.requestFocus()
        val show = show@{
            if (overlay.get() == null || !field.isAttachedToWindow) return@show
            if (imeShowing(field)) return@show
            if (!field.isFocused) field.requestFocus()
            field.windowInsetsController?.show(WindowInsets.Type.ime())
            field.context.getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
        if (field.hasWindowFocus()) show()
        val observer = field.viewTreeObserver
        if (observer.isAlive) {
            observer.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (!hasFocus) return
                    field.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(this)
                    field.post { show() }
                }
            })
        }
        longArrayOf(120L, 300L, 600L).forEach { delay -> field.postDelayed({ show() }, delay) }
    }

    private fun installLayoutRetry(
        payload: IslandReplyPayload,
        module: XposedModule,
        source: Any?,
    ) {
        if (retryListener != null) return
        val root = viewFrom(source)?.rootView
            ?: findNamedInWindows("DynamicIslandExpandedView")?.rootView
            ?: return
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!intendedOpen || overlay.get()?.isAttachedToWindow == true) return@OnLayoutChangeListener
            runCatching { embed(payload, module, source) }
        }
        retryRoot = root
        retryListener = listener
        root.addOnLayoutChangeListener(listener)
    }

    private fun clearLayoutRetry() {
        val root = retryRoot
        val listener = retryListener
        if (root != null && listener != null) root.removeOnLayoutChangeListener(listener)
        retryRoot = null
        retryListener = null
    }

    private fun dumpExpandedTree(module: XposedModule, source: Any?) {
        val root = findNamedInWindows("DynamicIslandExpandedView")
            ?: viewFrom(source)?.rootView
            ?: return
        fun describe(view: View, depth: Int, out: MutableList<String>) {
            if (out.size >= 80 || depth > 8) return
            val text = (view as? TextView)?.text?.toString()?.take(40).orEmpty()
            out += "${"  ".repeat(depth)}${view.javaClass.name} id=${view.id} text=$text children=${(view as? ViewGroup)?.childCount ?: 0}"
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) describe(view.getChildAt(index), depth + 1, out)
            }
        }
        val lines = mutableListOf<String>()
        describe(root, 0, lines)
        module.log("HyperBridge: island reply host missing; expanded tree:\n${lines.joinToString("\n")}")
        Log.i("HyperBridge", "island reply host missing; expanded tree:\n${lines.joinToString("\n")}")
    }

    private fun findButtonRow(source: Any?): ViewGroup? {
        viewFrom(source)?.let { view ->
            (fullWidthHost(view) ?: buttonRowAround(view))?.let { return it }
        }
        val expanded = findNamedInWindows("DynamicIslandExpandedView") as? ViewGroup ?: return null
        val clickable = mutableListOf<TextView>()
        collectClickableTexts(expanded, clickable)
        val replyText = clickable.firstOrNull { text ->
            text.text?.toString()?.contains("Reply", ignoreCase = true) == true
        }
        if (replyText != null) {
            (fullWidthHost(replyText) ?: replyText.parent as? ViewGroup)?.let { return it }
        }
        return clickable.mapNotNull { it.parent as? ViewGroup }
            .firstOrNull { parent ->
                parent.childCount in 1..4 && clickable.count { it.parent === parent } >= 1
            } ?: clickable.lastOrNull()?.parent as? ViewGroup
            ?: bottomRow(expanded)
    }

    private fun bottomRow(root: ViewGroup): ViewGroup? {
        var best: ViewGroup? = null
        fun walk(view: View) {
            if (view is ViewGroup &&
                view.childCount in 1..4 &&
                view !== root &&
                view.javaClass.simpleName.contains("Window", ignoreCase = true).not()
            ) {
                val texts = (0 until view.childCount).count { view.getChildAt(it) is TextView }
                if (texts >= 1) best = view
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(root)
        return best
    }

    /**
     * The tapped reply action is often its own small container (icon + label),
     * so the composer must be hosted by the nearest ancestor that spans the
     * expanded island's width, otherwise it renders inside the button.
     */
    private fun fullWidthHost(view: View): ViewGroup? {
        val expanded = expandedAncestor(view) ?: return null
        val available = expanded.width - expanded.paddingLeft - expanded.paddingRight
        if (available <= 0) return null
        var current: View = view
        while (true) {
            val parent = current.parent as? ViewGroup ?: return null
            if (parent === expanded) return current as? ViewGroup ?: expanded
            if (parent.width >= available * 0.8f &&
                parent.javaClass.simpleName.contains("Window", ignoreCase = true).not()
            ) {
                return parent
            }
            current = parent
        }
    }

    private fun expandedAncestor(view: View): ViewGroup? {
        var current: View? = view
        while (current != null) {
            if (current.javaClass.simpleName == "DynamicIslandExpandedView") return current as? ViewGroup
            current = current.parent as? View
        }
        return null
    }

    private fun buttonRowAround(view: View): ViewGroup? {
        var current: View? = view
        repeat(6) {
            val parent = current?.parent as? ViewGroup ?: return@repeat
            if (parent.childCount in 2..4) {
                val clickable = (0 until parent.childCount).count { index ->
                    val child = parent.getChildAt(index)
                    child.isClickable || child is TextView || child is ViewGroup
                }
                if (clickable >= 1 && parent.javaClass.simpleName.contains("Window", ignoreCase = true).not()) {
                    return parent
                }
            }
            current = parent
        }
        return view.parent as? ViewGroup
    }

    private fun viewFrom(source: Any?): View? {
        when (source) {
            is View -> return source
            null -> return null
        }
        val clazz = source!!.javaClass
        val names = arrayOf("itemView", "view", "mView", "rootView", "binding")
        for (name in names) {
            var current: Class<*>? = clazz
            while (current != null) {
                val field = runCatching { current!!.getDeclaredField(name).apply { isAccessible = true } }.getOrNull()
                if (field != null) {
                    when (val value = runCatching { field.get(source) }.getOrNull()) {
                        is View -> return value
                    }
                }
                current = current.superclass
            }
        }
        return clazz.declaredFields.firstNotNullOfOrNull { field ->
            field.isAccessible = true
            runCatching { field.get(source) as? View }.getOrNull()
        }
    }

    private fun collectClickableTexts(view: View, out: MutableList<TextView>) {
        if (view is TextView && (view.isClickable || view.parent is ViewGroup)) {
            val text = view.text?.toString().orEmpty()
            if (text.isNotBlank()) out += view
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectClickableTexts(view.getChildAt(index), out)
        }
    }

    private fun findNamedInWindows(simpleName: String): View? {
        windowRoots().forEach { root ->
            findNamed(root, simpleName)?.let { return it }
        }
        return null
    }

    private fun buildComposer(
        context: Context,
        payload: IslandReplyPayload,
        module: XposedModule,
        hostHeight: Int,
    ): View {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val controlSize = if (hostHeight in dp(24) until dp(40)) hostHeight else dp(40)
        val compact = controlSize < dp(40)
        val field = EditText(context).apply {
            tag = "$TAG.field"
            hint = "Reply"
            setHintTextColor(Color.argb(255, 170, 170, 170))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            maxLines = if (compact) 1 else 3
            minHeight = controlSize
            minimumHeight = controlSize
            alpha = 1f
            background = pill(opaque(0xFF2C2C2E.toInt()))
            val vertical = if (compact) dp(2) else dp(8)
            setPadding(dp(14), vertical, dp(14), vertical)
            setOnEditorActionListener { _, actionId, event ->
                val send = actionId == EditorInfo.IME_ACTION_SEND ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
                if (send) {
                    submit(payload, text?.toString().orEmpty(), module)
                    true
                } else false
            }
        }
        val send = TextView(context).apply {
            text = "➤"
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            textSize = if (compact) 14f else 16f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            alpha = 1f
            background = pill(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(controlSize, controlSize).apply { leftMargin = dp(8) }
            setOnClickListener { submit(payload, field.text?.toString().orEmpty(), module) }
        }
        return LinearLayout(context).apply {
            tag = TAG
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 0)
            alpha = 1f
            background = null
            addView(
                field,
                LinearLayout.LayoutParams(
                    0,
                    if (compact) controlSize else ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            addView(send)
            isClickable = true
            setOnClickListener { }
        }
    }

    private fun submit(payload: IslandReplyPayload, message: String, module: XposedModule) {
        val text = message.trim()
        if (text.isEmpty()) return
        val replyIntent = Intent()
        val results = Bundle().apply { putCharSequence(payload.resultKey, text) }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(payload.resultKey).build()),
            replyIntent,
            results,
        )
        val sendContext = overlay.get()?.context
        runCatching { payload.replyAction.send(sendContext, 0, replyIntent) }
            .onFailure { module.log("HyperBridge: island reply send failed: ${it.message}") }
        dismiss()
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(opaque(color))
        alpha = 255
        cornerRadius = 48f
    }

    private fun opaque(color: Int): Int = Color.argb(255, Color.red(color), Color.green(color), Color.blue(color))

    private fun findNamed(view: View, simpleName: String): View? {
        if (view.javaClass.simpleName == simpleName) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findNamed(view.getChildAt(index), simpleName)?.let { return it }
            }
        }
        return null
    }

    private fun windowRoots(): List<View> = runCatching {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        val field = global.getDeclaredField("mViews").apply { isAccessible = true }
        when (val views = field.get(instance)) {
            is List<*> -> views.filterIsInstance<View>()
            is Array<*> -> views.filterIsInstance<View>()
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun prepareImeWindow(view: View) {
        val root = view.rootView
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        if (imeFlags == null) imeFlags = params.flags to params.softInputMode
        params.flags = IslandWindowImePolicy.composerFlags(params.flags)
        params.softInputMode = IslandWindowImePolicy.composerSoftInputMode(params.softInputMode)
        runCatching {
            view.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
        }
    }

    private fun restoreImeWindow(view: View) {
        val state = imeFlags ?: return
        imeFlags = null
        val root = view.rootView
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        params.flags = IslandWindowImePolicy.idleFlags(state.first)
        params.softInputMode = state.second
        runCatching {
            view.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
        }
    }

    private fun hideIme(view: View) {
        view.context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun pendingIntentIntent(pendingIntent: PendingIntent): Intent? = runCatching {
        PendingIntent::class.java.getDeclaredMethod("getIntent").apply { isAccessible = true }
            .invoke(pendingIntent) as? Intent
    }.getOrNull()

    private fun parcelablePendingIntent(intent: Intent, key: String): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(key, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }
}
