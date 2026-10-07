package com.sykeptical.hyperpop.xposed.hooks.fingerprint

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.sykeptical.hyperpop.service.animation.fingerprint.AcquiredCodeClassifier
import com.sykeptical.hyperpop.service.animation.fingerprint.AcquiredKind
import com.sykeptical.hyperpop.xposed.hooks.DynamicClassLoaderHooks
import com.sykeptical.hyperpop.xposed.log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Read-only observers for the lockscreen fingerprint session. Nothing here
 * changes the authentication result, the HAL, or the keyguard.
 */
object FingerprintSignalBridge {
    private const val MONITOR = "com.android.keyguard.KeyguardUpdateMonitor"
    private const val CALLBACK = "com.android.keyguard.KeyguardUpdateMonitor\$10"
    private const val GXZW = "com.miui.keyguard.biometrics.fod.MiuiGxzwManager"
    private const val GESTURE = "com.miui.keyguard.biometrics.fod.MiuiGestureEventDispatcher"
    private const val KEYGUARD_STATE = "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl"
    private const val STATUS_BAR_STATE = "com.android.systemui.statusbar.StatusBarStateControllerImpl"
    private const val WINDOW = "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val COORDINATOR = "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val SHADE_LOCKED = 2

    private val signalLoaders = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))
    private val windowLoaders = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))
    private val seenAcquired = Collections.synchronizedSet(HashSet<Int>())
    private val main = Handler(Looper.getMainLooper())
    private val contactPosted = AtomicBoolean(false)
    private val pendingContact = FloatArray(2)
    private var moduleRef: XposedModule? = null
    private var gestureSamples = 0
    private var pending114Ms = 0L
    private var loggedGesture = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        moduleRef = module
        FingerprintIslandController.attachModule(module)
        hookSignals(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hookWindow(module, loader)
        }
    }

    private fun hookSignals(module: XposedModule, loader: ClassLoader) {
        if (!signalLoaders.add(loader)) return
        val failures = ArrayList<String>()
        runCatching { hookMonitor(module, loader) }.onFailure { failures += "monitor:${it.message}" }
        runCatching { hookCallback(module, loader) }.onFailure { failures += "callback:${it.message}" }
        runCatching { hookGxzw(module, loader) }.onFailure { failures += "gxzw:${it.message}" }
        runCatching { hookGesture(module, loader) }.onFailure { failures += "gesture:${it.message}" }
        runCatching { hookKeyguardState(module, loader) }.onFailure { failures += "keyguard:${it.message}" }
        runCatching { hookShade(module, loader) }.onFailure { failures += "shade:${it.message}" }
        if (failures.isEmpty()) {
            module.log("HyperPop: fingerprint signal bridge installed")
        } else {
            module.log("HyperPop: fingerprint signal bridge partial: ${failures.joinToString("; ")}")
        }
    }

    private fun hookMonitor(module: XposedModule, loader: ClassLoader) {
        val monitor = loader.loadClass(MONITOR)
        hookAfter(module, method(monitor, "onFingerprintAuthenticated", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)) { chain ->
            FingerprintIslandController.noteMonitor(chain.thisObject)
            if (enabled()) {
                module.log("HyperPop: fp probe success user=${chain.args.getOrNull(0)} strong=${chain.args.getOrNull(1)}")
                FingerprintIslandController.onSuccess()
            }
        }
        hookAfter(module, method(monitor, "setFingerprintRunningState", Int::class.javaPrimitiveType!!)) { chain ->
            FingerprintIslandController.noteMonitor(chain.thisObject)
            val state = chain.args.getOrNull(0) as? Int ?: return@hookAfter
            if (enabled()) FingerprintIslandController.onRunning(state)
        }
        hookAfter(module, method(monitor, "setKeyguardGoingAway", Boolean::class.javaPrimitiveType!!)) { chain ->
            FingerprintIslandController.noteMonitor(chain.thisObject)
            val going = chain.args.getOrNull(0) as? Boolean ?: return@hookAfter
            if (enabled()) FingerprintIslandController.onGoingAway(going)
        }
    }

    private fun hookCallback(module: XposedModule, loader: ClassLoader) {
        val callback = loader.loadClass(CALLBACK)
        module.log("HyperPop: fp probe callback class=${callback.name}")
        hookAfter(module, method(callback, "onAuthenticationAcquired", Int::class.javaPrimitiveType!!)) { chain ->
            val code = chain.args.getOrNull(0) as? Int ?: return@hookAfter
            if (!enabled()) return@hookAfter
            val kind = AcquiredCodeClassifier.classify(code)
            if (seenAcquired.add(code)) module.log("HyperPop: fp probe acquired code=$code kind=$kind")
            when {
                AcquiredCodeClassifier.isFingerDown(kind) -> {
                    noteDownAfter114(module)
                    FingerprintIslandController.onFingerDown()
                }
                AcquiredCodeClassifier.isFingerUp(kind) -> FingerprintIslandController.onFingerUp()
                kind == AcquiredKind.Help -> FingerprintIslandController.onHelp()
            }
        }
        hookAfter(module, method(callback, "onAuthenticationFailed")) { _ ->
            if (enabled()) FingerprintIslandController.onFailure()
        }
        hookAfter(module, method(callback, "onAuthenticationHelp", Int::class.javaPrimitiveType!!, CharSequence::class.java)) { _ ->
            if (enabled()) FingerprintIslandController.onHelp()
        }
        hookAfter(module, method(callback, "onAuthenticationError", Int::class.javaPrimitiveType!!, CharSequence::class.java)) { chain ->
            val code = chain.args.getOrNull(0) as? Int ?: return@hookAfter
            if (!enabled()) return@hookAfter
            module.log("HyperPop: fp probe error code=$code")
            FingerprintIslandController.onError(code)
        }
    }

    private fun hookGxzw(module: XposedModule, loader: ClassLoader) {
        val manager = loader.loadClass(GXZW)
        hookAfter(module, method(manager, "dealCallback", Int::class.javaPrimitiveType!!)) { chain ->
            val code = chain.args.getOrNull(0) as? Int ?: return@hookAfter
            if (!enabled()) return@hookAfter
            when (code) {
                101, 112 -> Unit
                102, 105, 106, 113 -> {
                    report114IfStillPending(module)
                    FingerprintIslandController.onSessionEnded()
                }
                114 -> {
                    pending114Ms = SystemClock.uptimeMillis()
                    module.log("HyperPop: fp probe dealCallback 114")
                }
            }
        }
    }

    private fun hookGesture(module: XposedModule, loader: ClassLoader) {
        val dispatcher = loader.loadClass(GESTURE)
        val input = method(dispatcher, "onInputEvent", InputEvent::class.java)
        runCatching { module.deoptimize(input) }
        module.hook(input).intercept { chain ->
            val event = chain.args.getOrNull(0) as? MotionEvent
            val x = event?.x
            val y = event?.y
            val action = event?.actionMasked
            val result = chain.proceed()
            if (event != null && x != null && y != null && enabled() &&
                (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_DOWN)
            ) {
                if (!loggedGesture) {
                    loggedGesture = true
                    module.log("HyperPop: fp probe gesture class=${dispatcher.name}")
                }
                if (gestureSamples < 3) {
                    gestureSamples += 1
                    module.log("HyperPop: fp probe gesture sample x=${x.toInt()} y=${y.toInt()} action=$action")
                }
                postContact(x, y)
            }
            result
        }
    }

    private fun hookKeyguardState(module: XposedModule, loader: ClassLoader) {
        val state = loader.loadClass(KEYGUARD_STATE)
        hookAfter(module, method(state, "notifyKeyguardState", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!)) { chain ->
            if (!enabled()) return@hookAfter
            val showing = chain.args.getOrNull(0) as? Boolean ?: return@hookAfter
            val occluded = chain.args.getOrNull(1) as? Boolean ?: false
            FingerprintIslandController.onKeyguard(showing, occluded)
        }
    }

    private fun hookShade(module: XposedModule, loader: ClassLoader) {
        val state = loader.loadClass(STATUS_BAR_STATE)
        val method = state.declaredMethods.firstOrNull {
            it.name == "setState" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType
        } ?: error("setState(int) missing")
        hookAfter(module, method) { chain ->
            if (!enabled()) return@hookAfter
            val value = chain.args.getOrNull(0) as? Int ?: return@hookAfter
            FingerprintIslandController.onShadeExpanded(value == SHADE_LOCKED)
        }
    }

    private fun hookWindow(module: XposedModule, loader: ClassLoader) {
        if (!windowLoaders.add(loader)) return
        var hooked = false
        runCatching {
            val window = loader.loadClass(WINDOW)
            window.declaredConstructors.forEach { constructor ->
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    (chain.thisObject as? View)?.let { view ->
                        view.post {
                            val group = view as? ViewGroup ?: return@post
                            FingerprintIslandController.attachWindow(group)
                            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: View) = Unit
                                override fun onViewDetachedFromWindow(v: View) {
                                    FingerprintIslandController.onDetached()
                                }
                            })
                        }
                    }
                    result
                }
            }
            hooked = true
        }.onFailure {
            module.log("HyperPop: fp window host unavailable on loader=${loader.hashCode()}: ${it.message}")
        }
        runCatching {
            val coordinator = loader.loadClass(COORDINATOR)
            coordinator.declaredConstructors.forEach { constructor ->
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    FingerprintIslandController.offerCoordinator(chain.thisObject)
                    result
                }
            }
            val update = coordinator.declaredMethods.first { it.name == "updateWindowHeight" && it.parameterCount == 0 }
            update.isAccessible = true
            runCatching { module.deoptimize(update) }
            module.hook(update).intercept { chain ->
                val result = chain.proceed()
                if (enabled()) FingerprintIslandController.onWindowHeightWritten(chain.thisObject)
                result
            }
            hooked = true
        }.onFailure {
            module.log("HyperPop: fp window height unavailable on loader=${loader.hashCode()}: ${it.message}")
        }
        if (!hooked) windowLoaders.remove(loader)
    }

    private fun postContact(x: Float, y: Float) {
        synchronized(pendingContact) {
            pendingContact[0] = x
            pendingContact[1] = y
        }
        if (contactPosted.compareAndSet(false, true)) {
            main.post {
                contactPosted.set(false)
                val sampleX: Float
                val sampleY: Float
                synchronized(pendingContact) {
                    sampleX = pendingContact[0]
                    sampleY = pendingContact[1]
                }
                FingerprintIslandController.onContact(sampleX, sampleY)
            }
        }
    }

    private fun noteDownAfter114(module: XposedModule) {
        val started = pending114Ms
        if (started == 0L) return
        pending114Ms = 0L
        module.log("HyperPop: fp probe 114 then down after ${SystemClock.uptimeMillis() - started}ms")
    }

    private fun report114IfStillPending(module: XposedModule) {
        val started = pending114Ms
        if (started == 0L) return
        pending114Ms = 0L
        module.log("HyperPop: fp probe 114 without a new down after ${SystemClock.uptimeMillis() - started}ms")
    }

    private fun enabled(): Boolean = com.sykeptical.hyperpop.xposed.HookConfig.fingerprintIslandEnabled()

    private fun hookAfter(module: XposedModule, method: Method, block: (Chain) -> Unit) {
        runCatching { module.deoptimize(method) }
        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            runCatching { block(chain) }.onFailure {
                module.log("HyperPop: fingerprint observer failed open: ${it.message}")
            }
            result
        }
    }

    private fun method(type: Class<*>, name: String, vararg params: Class<*>): Method {
        var current: Class<*>? = type
        while (current != null) {
            val found = runCatching { current.getDeclaredMethod(name, *params) }.getOrNull()
            if (found != null) return found
            current = current.superclass
        }
        error("$name missing on ${type.name}")
    }
}
