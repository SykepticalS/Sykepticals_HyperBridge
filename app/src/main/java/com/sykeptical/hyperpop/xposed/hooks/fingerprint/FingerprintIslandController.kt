package com.sykeptical.hyperpop.xposed.hooks.fingerprint

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import android.view.View
import android.view.ViewGroup
import com.sykeptical.hyperpop.service.animation.fingerprint.AcquiredCodeClassifier
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintContactMapper
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintIslandGeometry
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintIslandStateMachine
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintPhase
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintPresentationPolicy
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintSignal
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintTiming
import com.sykeptical.hyperpop.service.animation.fingerprint.LockWorld
import com.sykeptical.hyperpop.service.animation.fingerprint.ResetReason
import com.sykeptical.hyperpop.xposed.HookConfig
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field

/**
 * Owns the cosmetic session. Xiaomi still owns authentication, the keyguard,
 * and every native island animation. This class only raises the island window
 * and draws HyperPop's own child.
 */
internal object FingerprintIslandController : FingerprintIslandView.Listener {
    private const val TAG = "hyperpop_fingerprint_island"

    private val machine = FingerprintIslandStateMachine()
    private val main = Handler(Looper.getMainLooper())
    private val ticker = Runnable { onTick() }

    private var module: XposedModule? = null
    private var windowRef = WeakReference<ViewGroup>(null)
    private var coordinatorRef = WeakReference<Any>(null)
    private var monitorRef = WeakReference<Any>(null)
    private var islandView: FingerprintIslandView? = null
    private var mapper: FingerprintContactMapper? = null
    private var displayListener: DisplayManager.DisplayListener? = null

    private var featurePref = false
    private var lockPillPref = true
    private var keyguardShowing = false
    private var keyguardOccluded = false
    private var keyguardGoingAway = false
    private var bouncerShowing = false
    private var shadeExpanded = false
    private var lockedOut = false
    private var fingerprintRunning = false
    private var displayOn = false
    private var dozing = false
    private var portrait = true
    private var windowScreenY = 0
    private var lastDownMs = 0L
    private var loggedChild = false
    private var loggedHeight = false
    private var loggedIslands = false
    private var loggedAlpha = false
    private var loggedList = false
    private var loggedSensor = false
    private var loggedArtwork = false

    @Volatile var reservedHeightPx: Int = 0
        private set

    private val hiddenNatives = ArrayList<WeakReference<View>>()

    fun attachModule(value: XposedModule) {
        module = value
    }

    fun attachWindow(window: ViewGroup) {
        windowRef = WeakReference(window)
        val existing = window.findViewWithTag<View>(TAG) as? FingerprintIslandView
        val view = existing ?: runCatching {
            FingerprintIslandView(window.context).also { created ->
                created.tag = TAG
                window.addView(created, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }.onFailure {
            module?.log("HyperPop: fp probe child add failed: ${it.message}")
        }.getOrNull() ?: return
        if (!loggedChild) {
            loggedChild = true
            module?.log("HyperPop: fp probe child added parent=${window.javaClass.name}")
        }
        view.listener = this
        islandView = view
        portrait = window.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        watchDisplay(window.context)
        ensureSensor(window.context)
        ensureArtwork(window.context)
        findCoordinator(window)?.let { coordinatorRef = WeakReference(it) }
        publish()
    }

    fun offerCoordinator(coordinator: Any) {
        coordinatorRef = WeakReference(coordinator)
        publish()
    }

    fun onWindowHeightWritten(coordinator: Any) {
        coordinatorRef = WeakReference(coordinator)
        raise(coordinator)
        if (machine.snapshot.phase == FingerprintPhase.Success || machine.snapshot.phase == FingerprintPhase.SuccessHold) {
            if (shouldInterrupt(coordinator)) {
                dispatch(FingerprintSignal.HoldInterrupted)
                return
            }
        }
        if (machine.snapshot.suspendNatives) syncNatives() else restoreNatives()
    }

    fun noteMonitor(monitor: Any) {
        monitorRef = WeakReference(monitor)
        readMonitor(monitor)
        watchDisplay(contextOf(monitor) ?: return)
        ensureSensor(contextOf(monitor) ?: return)
        ensureArtwork(contextOf(monitor) ?: return)
        publish()
    }

    fun onFingerDown() {
        val now = SystemClock.uptimeMillis()
        if (machine.snapshot.fingerDown && now - lastDownMs < FingerprintTiming.DOWN_DEDUPE_MS) return
        lastDownMs = now
        mapper?.clear()
        dispatch(FingerprintSignal.FingerDown)
    }

    fun onFingerUp() = dispatch(FingerprintSignal.FingerUp)

    fun onHelp() = dispatch(FingerprintSignal.Help)

    fun onFailure() = dispatch(FingerprintSignal.Failure)

    fun onSuccess() = dispatch(FingerprintSignal.Success)

    fun onError(code: Int) {
        if (AcquiredCodeClassifier.isLockoutError(code)) {
            lockedOut = true
            dispatch(FingerprintSignal.Lockout)
        } else {
            dispatch(FingerprintSignal.SessionEnded)
        }
    }

    fun onRunning(state: Int) {
        fingerprintRunning = state == 1
        publish()
    }

    fun onSessionEnded() = dispatch(FingerprintSignal.SessionEnded)

    fun onKeyguard(showing: Boolean, occluded: Boolean) {
        keyguardShowing = showing
        keyguardOccluded = occluded
        if (!showing && FingerprintPresentationPolicy.resetsNow(machine.snapshot.phase, ResetReason.KeyguardGone)) {
            dispatch(FingerprintSignal.Reset(ResetReason.KeyguardGone))
            return
        }
        publish()
    }

    fun onGoingAway(going: Boolean) {
        keyguardGoingAway = going
        if (going && FingerprintPresentationPolicy.resetsNow(machine.snapshot.phase, ResetReason.KeyguardGone)) {
            dispatch(FingerprintSignal.Reset(ResetReason.KeyguardGone))
            return
        }
        publish()
    }

    fun onShadeExpanded(expanded: Boolean) {
        shadeExpanded = expanded
        publish()
    }

    fun onContact(x: Float, y: Float) {
        val active = mapper ?: return
        if (!active.push(x, y)) return
        val phase = machine.snapshot.phase
        if (phase == FingerprintPhase.Scanning || phase == FingerprintPhase.AwaitingResult) publish()
    }

    fun onDetached() {
        if (FingerprintPresentationPolicy.resetsNow(machine.snapshot.phase, ResetReason.Detach)) {
            dispatch(FingerprintSignal.Reset(ResetReason.Detach))
        }
        restoreNatives()
    }

    override fun onSuccessSettled(generation: Long) {
        if (generation != machine.snapshot.generation) return
        dispatch(FingerprintSignal.SuccessSettled)
    }

    override fun onCollapseFinished(generation: Long) {
        if (generation != machine.snapshot.generation) return
        dispatch(FingerprintSignal.CollapseFinished)
    }

    override fun onOrientationChanged() {
        val window = windowRef.get() ?: return
        portrait = window.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        if (FingerprintPresentationPolicy.resetsNow(machine.snapshot.phase, ResetReason.Rotation)) {
            dispatch(FingerprintSignal.Reset(ResetReason.Rotation))
            return
        }
        publish()
    }

    private fun onTick() {
        dispatch(FingerprintSignal.Tick(SystemClock.uptimeMillis()))
    }

    private fun dispatch(signal: FingerprintSignal) {
        readPrefs()
        monitorRef.get()?.let { readMonitor(it) }
        val now = SystemClock.uptimeMillis()
        val currentWorld = FingerprintSignal.WorldChanged(world())
        if (signal is FingerprintSignal.Success) {
            machine.dispatch(signal, now)
            machine.dispatch(currentWorld, now)
        } else {
            machine.dispatch(currentWorld, now)
            machine.dispatch(signal, now)
        }
        publishFromMachine()
        scheduleTick()
    }

    private fun publish() {
        readPrefs()
        machine.dispatch(FingerprintSignal.WorldChanged(world()), SystemClock.uptimeMillis())
        publishFromMachine()
        scheduleTick()
    }

    private fun publishFromMachine() {
        val snapshot = machine.snapshot
        val window = windowRef.get()
        window?.context?.let { if (featurePref && FingerprintArtworkLoader.artwork == null) ensureArtwork(it) }
        val view = islandView
        val layout = if (window != null) layoutFor(window) else null
        if (view != null && layout != null) {
            if (snapshot.suspendNatives) view.bringToFront()
            view.bind(
                phase = snapshot.phase,
                generation = snapshot.generation,
                fingerDown = snapshot.fingerDown,
                showBlue = snapshot.showBlue,
                shaking = snapshot.shaking,
                shakeSerial = snapshot.shakeSerial,
                playingSuccess = snapshot.playingSuccess,
                layout = layout,
                artwork = FingerprintArtworkLoader.artwork,
                animationsEnabled = animationsEnabled(view.context),
            )
        }
        val nextHeight = if (layout == null || snapshot.phase == FingerprintPhase.Hidden) {
            0
        } else {
            FingerprintIslandGeometry.reservedHeightPx(layout, snapshot.phase, window?.resources?.displayMetrics?.density ?: 3f) + windowScreenY
        }
        if (nextHeight != reservedHeightPx) {
            reservedHeightPx = nextHeight
            invokeHeightUpdate()
        }
        if (snapshot.suspendNatives) syncNatives() else restoreNatives()
    }

    private fun world(): LockWorld {
        val artworkOk = FingerprintArtworkLoader.artwork != null && !FingerprintArtworkLoader.failed
        if (featurePref && FingerprintArtworkLoader.failed && !loggedArtwork) {
            loggedArtwork = true
            module?.log("HyperPop: fingerprint island disabled, enrollment artwork unavailable")
        }
        return LockWorld(
            featureEnabled = featurePref && artworkOk,
            lockPillSetting = lockPillPref,
            keyguardShowing = keyguardShowing && !keyguardGoingAway,
            keyguardOccluded = keyguardOccluded,
            bouncerShowing = bouncerShowing,
            shadeExpanded = shadeExpanded,
            lockedOut = lockedOut,
            fingerprintRunning = fingerprintRunning,
            displayOn = displayOn,
            dozing = dozing,
            portrait = portrait,
        )
    }

    private fun readPrefs() {
        featurePref = HookConfig.fingerprintIslandEnabled()
        lockPillPref = HookConfig.fingerprintLockPill()
    }

    private fun scheduleTick() {
        main.removeCallbacks(ticker)
        val deadline = machine.snapshot.deadlineMs ?: return
        val delay = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        main.postDelayed(ticker, delay)
    }

    private fun layoutFor(window: View): FingerprintIslandGeometry.Layout? {
        val cutout = cutout(window) ?: return null
        val location = IntArray(2)
        window.getLocationOnScreen(location)
        windowScreenY = location[1]
        val hole = FingerprintIslandGeometry.Rect(
            left = cutout.left - location[0].toFloat(),
            top = cutout.top - location[1].toFloat(),
            right = cutout.right - location[0].toFloat(),
            bottom = cutout.bottom - location[1].toFloat(),
        )
        if (hole.width <= 1f || hole.height <= 1f) return null
        val density = window.resources.displayMetrics.density
        val chrome = islandChrome(window, hole.height, density)
        return FingerprintIslandGeometry.layout(
            hole,
            chrome.heightPx,
            density,
            chrome.radiusPx,
            chrome.strokePx,
            chrome.strokeColor,
        )
    }

    private fun islandChrome(window: View, holeHeight: Float, density: Float): IslandChrome {
        val resources = window.resources
        val pkg = window.context.packageName
        fun dimen(name: String, fallback: Float): Float {
            val id = resources.getIdentifier(name, "dimen", pkg)
            if (id == 0) return fallback
            return runCatching { resources.getDimension(id) }.getOrDefault(fallback).coerceAtLeast(0f)
        }
        val strokeFallback = FingerprintIslandGeometry.PLATE_STROKE_DP * density
        val colorId = resources.getIdentifier("stroke_color", "color", pkg)
        val strokeColor = if (colorId == 0) {
            FingerprintIslandGeometry.PLATE_STROKE_COLOR
        } else {
            runCatching { resources.getColor(colorId, null) }.getOrDefault(FingerprintIslandGeometry.PLATE_STROKE_COLOR)
        }
        return IslandChrome(
            heightPx = dimen("island_height", holeHeight).coerceAtLeast(1f),
            radiusPx = dimen("island_radius", FingerprintIslandGeometry.ISLAND_RADIUS_DP * density),
            strokePx = dimen("island_stroke", strokeFallback),
            strokeColor = strokeColor,
        )
    }

    private fun cutout(window: View): Rect? {
        val method = window.javaClass.methods.firstOrNull { it.name == "getCutoutRect" && it.parameterCount == 0 } ?: return null
        return runCatching { method.invoke(window) as? Rect }.getOrNull()
    }

    private fun invokeHeightUpdate() {
        val coordinator = coordinatorRef.get() ?: return
        val method = coordinator.javaClass.declaredMethods.firstOrNull {
            it.name == "updateWindowHeight" && it.parameterCount == 0
        } ?: return
        runCatching {
            method.isAccessible = true
            method.invoke(coordinator)
        }
    }

    private fun raise(coordinator: Any) {
        val want = reservedHeightPx
        if (want <= 0) return
        val flow = findField(coordinator.javaClass, "_state")?.let { field ->
            field.isAccessible = true
            field.get(coordinator)
        } ?: return
        val current = runCatching {
            (flow.javaClass.methods.first { it.name == "getValue" && it.parameterCount == 0 }.invoke(flow) as Number).toInt()
        }.getOrNull() ?: return
        if (want <= current) return
        runCatching {
            flow.javaClass.methods.first { it.name == "setValue" && it.parameterCount == 1 }.invoke(flow, want)
        }.onSuccess {
            if (!loggedHeight) {
                loggedHeight = true
                module?.log("HyperPop: fp probe height raised native=$current biometric=$want")
            }
        }
    }

    private fun syncNatives() {
        val window = windowRef.get() ?: return
        val views = contentViews(window)
        if (!loggedIslands) {
            loggedIslands = true
            module?.log(
                "HyperPop: fp probe lockscreen islands n=${views.size} " +
                    views.joinToString(",") { it.javaClass.simpleName },
            )
        }
        for (view in views) {
            if (view === islandView) continue
            val rewritten = hiddenNatives.any { it.get() === view } && view.transitionAlpha != 0f
            if (rewritten && !loggedAlpha) {
                loggedAlpha = true
                module?.log("HyperPop: fp probe transitionAlpha rewritten on ${view.javaClass.simpleName} to ${view.transitionAlpha}")
            }
            view.transitionAlpha = 0f
            if (hiddenNatives.none { it.get() === view }) hiddenNatives += WeakReference(view)
        }
    }

    private fun restoreNatives() {
        if (hiddenNatives.isEmpty()) return
        hiddenNatives.forEach { ref -> ref.get()?.let { it.transitionAlpha = 1f } }
        hiddenNatives.clear()
    }

    private fun contentViews(window: ViewGroup): List<View> {
        val field = findField(window.javaClass, "contentViewList") ?: findField(window.javaClass, "mContentViewList")
        if (field == null) {
            if (!loggedList) {
                loggedList = true
                module?.log("HyperPop: fp probe contentViewList missing")
            }
            return emptyList()
        }
        field.isAccessible = true
        val value = runCatching { field.get(window) }.getOrNull() ?: return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { it as? View }
            is Iterable<*> -> value.mapNotNull { it as? View }
            else -> emptyList()
        }
    }

    private fun shouldInterrupt(coordinator: Any): Boolean =
        memberNonNull(coordinator, "getExpandedStateHandler", "expandedStateHandler") ||
            tempHidden(coordinator) ||
            callShowing(coordinator)

    private fun memberNonNull(target: Any, methodName: String, fieldName: String): Boolean {
        val handler = member(target, methodName, fieldName) ?: return false
        val current = handler.javaClass.methods.firstOrNull { it.name == "getCurrent" && it.parameterCount == 0 }?.invoke(handler)
        return current != null
    }

    private fun callShowing(coordinator: Any): Boolean {
        val handler = member(coordinator, "getBigIslandStateHandler", "bigIslandStateHandler") ?: return false
        val current = runCatching {
            handler.javaClass.methods.firstOrNull { it.name == "getCurrent" && it.parameterCount == 0 }?.invoke(handler)
        }.getOrNull() ?: return false
        return current.javaClass.name.contains("call", ignoreCase = true)
    }

    private fun tempHidden(coordinator: Any): Boolean {
        val state = runCatching {
            coordinator.javaClass.methods.firstOrNull { it.name == "getWindowState" && it.parameterCount == 0 }?.invoke(coordinator)
        }.getOrNull() ?: return false
        val hidden = runCatching {
            state.javaClass.methods.firstOrNull { it.name == "getTempHidden" && it.parameterCount == 0 }?.invoke(state)
        }.getOrNull() ?: return false
        val value = runCatching {
            hidden.javaClass.methods.firstOrNull { it.name == "getValue" && it.parameterCount == 0 }?.invoke(hidden)
        }.getOrNull()
        return value == true
    }

    private fun member(target: Any, methodName: String, fieldName: String): Any? {
        val method = target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
        if (method != null) return runCatching { method.invoke(target) }.getOrNull()
        val field = findField(target.javaClass, fieldName) ?: return null
        field.isAccessible = true
        return runCatching { field.get(target) }.getOrNull()
    }

    private fun findCoordinator(window: View): Any? {
        val controller = window.javaClass.methods.firstOrNull { it.name == "getWindowViewController" && it.parameterCount == 0 }
            ?.let { runCatching { it.invoke(window) }.getOrNull() }
            ?: return null
        val field = controller.javaClass.declaredFields.firstOrNull {
            it.type.name.endsWith("DynamicIslandEventCoordinator")
        } ?: return null
        field.isAccessible = true
        return runCatching { field.get(controller) }.getOrNull()
    }

    private fun readMonitor(monitor: Any) {
        boolField(monitor, "mPrimaryBouncerIsOrWillBeShowing")?.let { bouncerShowing = it }
        boolField(monitor, "mKeyguardShowing")?.let { keyguardShowing = it }
        boolField(monitor, "mKeyguardOccluded")?.let { keyguardOccluded = it }
        intField(monitor, "mFingerprintRunningState")?.let { fingerprintRunning = it == 1 }
        runCatching {
            val method = monitor.javaClass.methods.first { it.name == "isFingerprintLockedOut" && it.parameterCount == 0 }
            lockedOut = method.invoke(monitor) as Boolean
        }
    }

    private fun watchDisplay(context: Context) {
        if (displayListener != null) return
        val manager = context.getSystemService(DisplayManager::class.java) ?: return
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                main.post { readDisplay(manager) }
            }
        }
        runCatching { manager.registerDisplayListener(listener, main) }.onSuccess {
            displayListener = listener
            readDisplay(manager)
        }
    }

    private fun readDisplay(manager: DisplayManager) {
        val state = manager.getDisplay(Display.DEFAULT_DISPLAY)?.state ?: return
        val wasOn = displayOn
        displayOn = state == Display.STATE_ON
        val nowDozing = state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND
        dozing = nowDozing
        if (wasOn && !displayOn) {
            val reason = if (nowDozing) ResetReason.Doze else ResetReason.ScreenOff
            if (FingerprintPresentationPolicy.resetsNow(machine.snapshot.phase, reason)) {
                dispatch(FingerprintSignal.Reset(reason))
                return
            }
        }
        publish()
    }

    private fun ensureSensor(context: Context) {
        if (mapper != null) return
        val rect = FingerprintSensorBounds.resolve(context)
        if (rect == null) {
            if (!loggedSensor) {
                loggedSensor = true
                module?.log("HyperPop: fp probe sensor bounds unavailable")
            }
            return
        }
        mapper = FingerprintContactMapper(rect[0], rect[1], rect[2], rect[3])
        module?.log("HyperPop: fp probe sensor rect=${rect[0].toInt()},${rect[1].toInt()},${rect[2].toInt()},${rect[3].toInt()}")
    }

    private fun ensureArtwork(context: Context) {
        if (!featurePref && !HookConfig.fingerprintIslandEnabled()) return
        FingerprintArtworkLoader.ensure(context, module) { publish() }
    }

    private fun animationsEnabled(context: Context): Boolean =
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) > 0f

    private fun contextOf(monitor: Any): Context? {
        val field = findField(monitor.javaClass, "mContext") ?: return null
        field.isAccessible = true
        return field.get(monitor) as? Context
    }

    private fun boolField(target: Any, name: String): Boolean? {
        val field = findField(target.javaClass, name) ?: return null
        field.isAccessible = true
        return runCatching { field.getBoolean(target) }.getOrNull()
    }

    private fun intField(target: Any, name: String): Int? {
        val field = findField(target.javaClass, name) ?: return null
        field.isAccessible = true
        return runCatching { field.getInt(target) }.getOrNull()
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            val found = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (found != null) return found
            current = current.superclass
        }
        return null
    }

    private data class IslandChrome(
        val heightPx: Float,
        val radiusPx: Float,
        val strokePx: Float,
        val strokeColor: Int,
    )
}

internal object FingerprintSensorBounds {
    fun resolve(context: Context): FloatArray? = runCatching {
        val manager = context.getSystemService("fingerprint") ?: return null
        val props = manager.javaClass.methods.firstOrNull {
            it.name == "getSensorPropertiesInternal" && it.parameterCount == 0
        }?.invoke(manager) as? List<*> ?: return null
        val first = props.firstOrNull() ?: return null
        val location = first.javaClass.methods.firstOrNull { it.name == "getLocation" && it.parameterCount == 0 }?.invoke(first)
            ?: (first.javaClass.methods.firstOrNull { it.name == "getAllLocations" && it.parameterCount == 0 }?.invoke(first) as? List<*>)?.firstOrNull()
            ?: return null
        val x = intMember(location, "sensorLocationX") ?: return null
        val y = intMember(location, "sensorLocationY") ?: return null
        val radius = intMember(location, "sensorRadius") ?: return null
        if (radius <= 0) return null
        floatArrayOf((x - radius).toFloat(), (y - radius).toFloat(), (x + radius).toFloat(), (y + radius).toFloat())
    }.getOrNull()

    private fun intMember(target: Any, name: String): Int? {
        val field = target.javaClass.declaredFields.firstOrNull { it.name == name } ?: return null
        field.isAccessible = true
        return runCatching { field.getInt(target) }.getOrNull()
    }
}
