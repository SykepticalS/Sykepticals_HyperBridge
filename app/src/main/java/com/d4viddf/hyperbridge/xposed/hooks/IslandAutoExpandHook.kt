package com.d4viddf.hyperbridge.xposed.hooks

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import com.d4viddf.hyperbridge.service.IslandAutoExpandPhase
import com.d4viddf.hyperbridge.service.IslandAutoExpandPhaseMachine
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Auto-expanding islands appear at the cutout's maximum width, then open about
 * [IslandAutoExpandPhaseMachine.EXPAND_DELAY_MS] after the notification arrives.
 * Once the expand animation has captured that wide pill, the cutout is measured
 * back to its content width so dismiss does not keep the entrance size.
 */
object IslandAutoExpandHook {
    private const val TAG = "HyperBridge"
    private const val CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val ANIMATION_CONTROLLER =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationController"
    private const val CLICK_EVENT =
        "miui.systemui.dynamicisland.event.DynamicIslandEvent\$ClickDynamicIsland"
    private const val EXPANDED_STATE =
        "miui.systemui.dynamicisland.event.DynamicIslandState\$Expanded"
    private const val EXPAND_ANIMATION = "bigIslandToExpandedAnimation"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val widthLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val animationLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val phases = ConcurrentHashMap<String, IslandAutoExpandPhase>()
    private val views = ConcurrentHashMap<String, WeakReference<Any>>()
    private val scheduled = ConcurrentHashMap<String, Runnable>()
    private val expandAttempted = ConcurrentHashMap.newKeySet<String>()
    private val expandStarted = ConcurrentHashMap.newKeySet<String>()
    private val compactGeometry = ConcurrentHashMap<String, CompactGeometry>()

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { hook(module, it) }
    }

    private fun hook(module: XposedModule, loader: ClassLoader) {
        hookWidth(module, loader)
        hookAnimations(module, loader)
    }

    private fun hookWidth(module: XposedModule, loader: ClassLoader) {
        val loaderId = System.identityHashCode(loader)
        if (!widthLoaders.add(loaderId)) return
        val hooked = runCatching {
            val clazz = loader.loadClass(CONTENT_VIEW)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "calculateBigIslandWidth" && it.parameterCount == 0
            } ?: return@runCatching false
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val view = chain.thisObject ?: return@intercept result
                onWidthCalculated(view)
                result
            }
            true
        }.getOrDefault(false)
        if (!hooked) widthLoaders.remove(loaderId)
        if (hooked) module.log("HyperBridge: hooked cutout width for staged auto-expand")
    }

    private fun hookAnimations(module: XposedModule, loader: ClassLoader) {
        val loaderId = System.identityHashCode(loader)
        if (!animationLoaders.add(loaderId)) return
        val hooked = runCatching {
            val clazz = loader.loadClass(ANIMATION_CONTROLLER)
            var installed = 0
            clazz.declaredMethods.forEach { method ->
                when {
                    method.name == EXPAND_ANIMATION && method.parameterCount == 1 -> {
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            (chain.args.getOrNull(0))?.let(::onExpandCaptured)
                            result
                        }
                        installed++
                    }
                    isDismissToCutout(method.name) && method.parameterCount == 1 -> {
                        module.hook(method).intercept { chain ->
                            (chain.args.getOrNull(0))?.let(::prepareDismissWidth)
                            chain.proceed()
                        }
                        installed++
                    }
                }
            }
            installed > 0
        }.getOrDefault(false)
        if (!hooked) animationLoaders.remove(loaderId)
        if (hooked) module.log("HyperBridge: hooked staged expand and cutout dismiss")
    }

    private fun isDismissToCutout(methodName: String): Boolean =
        methodName == "expandedToBigIslandAnimation" ||
            methodName == "expandedToBigIslandNoAnimation"

    private fun onWidthCalculated(view: Any) {
        val session = session(view) ?: return
        if (!session.snapshot.autoExpandEntrance) return
        views[session.id] = WeakReference(view)
        val phase = phases.getOrPut(session.id) { IslandAutoExpandPhase.Entrance }
        val alreadyExpanded = IslandAutoExpandPhaseMachine.isExpandedState(stateName(view))
        if (alreadyExpanded && phase == IslandAutoExpandPhase.Entrance) {
            // Expanded before the delay, so the wide entrance size must not survive dismiss.
            releaseCutoutWidth(view)
            return
        }
        if (IslandAutoExpandPhaseMachine.forcesMaxWidth(phase) && !alreadyExpanded) {
            compactGeometry.putIfAbsent(session.id, snapshotCompactGeometry(view))
            forceMaxCutoutWidth(view)
        }
        if (phase == IslandAutoExpandPhase.Entrance && !expandStarted.contains(session.id)) {
            scheduleExpand(session.id)
        }
    }

    private fun scheduleExpand(id: String) {
        if (scheduled.containsKey(id) || expandAttempted.contains(id) || expandStarted.contains(id)) return
        val task = Runnable { runScheduledExpand(id) }
        scheduled[id] = task
        mainHandler.postDelayed(task, IslandAutoExpandPhaseMachine.EXPAND_DELAY_MS)
    }

    private fun cancelExpand(id: String) {
        scheduled.remove(id)?.let(mainHandler::removeCallbacks)
    }

    private fun runScheduledExpand(id: String) {
        scheduled.remove(id)
        expandAttempted.add(id)
        if (expandStarted.contains(id)) return
        val view = views[id]?.get() ?: return
        val session = session(view) ?: return
        if (session.id != id || !session.snapshot.autoExpandEntrance) return
        if (phases[id] != IslandAutoExpandPhase.Entrance) return
        if (IslandAutoExpandPhaseMachine.isExpandedState(stateName(view))) {
            releaseCutoutWidth(view)
            return
        }
        Log.i(TAG, "staged auto-expand after ${IslandAutoExpandPhaseMachine.EXPAND_DELAY_MS}ms")
        phases[id] = IslandAutoExpandPhase.Expanding
        if (!startExpand(id, view) && phases[id] == IslandAutoExpandPhase.Expanding) {
            phases[id] = IslandAutoExpandPhase.Entrance
        }
    }

    private fun onExpandCaptured(view: Any) {
        releaseCutoutWidth(view)
    }

    private fun prepareDismissWidth(view: Any) {
        val session = session(view) ?: return
        if (!session.snapshot.autoExpandEntrance) return
        cancelExpand(session.id)
        if (phases[session.id] != IslandAutoExpandPhase.Measured) {
            phases[session.id] = IslandAutoExpandPhase.Measured
        }
        restoreCompactGeometry(session.id, view)
        // Pure calculation only: refresh the cached cutout geometry before Xiaomi captures the
        // collapse target. Do not call updateBigIslandViewWidth() here; it emits another state
        // change and recursively re-enters expandedToBigIslandAnimation.
        calculateNativeCompactGeometry(view)
    }

    private fun releaseCutoutWidth(view: Any) {
        val session = session(view) ?: return
        val phase = phases[session.id] ?: return
        val decision = IslandAutoExpandPhaseMachine.onExpandCaptured(phase)
        if (!decision.releaseWidth) return
        phases[session.id] = decision.phase
        cancelExpand(session.id)
        restoreCompactGeometry(session.id, view)
        scheduleNativeCompactMeasurement(session.id, view)
    }

    private fun startExpand(id: String, view: Any): Boolean {
        if (!expandStarted.add(id)) return true
        val started = dispatchClickExpand(view) || invokeBigToExpanded(view) || moveStateToExpanded(view)
        if (!started) {
            expandStarted.remove(id)
            Log.w(TAG, "staged auto-expand could not open the cutout island")
            return false
        }
        if (phases[id] == IslandAutoExpandPhase.Expanding) {
            releaseCutoutWidth(view)
        }
        return true
    }

    private fun dispatchClickExpand(view: Any): Boolean = runCatching {
        val loader = view.javaClass.classLoader ?: return false
        val eventClass = loader.loadClass(CLICK_EVENT)
        val instance = eventClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        val coordinator = IslandHookReflection.invokeNoArg(view, "getDynamicIslandEventCoordinator")
            ?: return false
        val method = coordinator.javaClass.methods.firstOrNull {
            it.name == "dispatchEvent" && it.parameterCount == 2
        } ?: return false
        method.isAccessible = true
        method.invoke(coordinator, instance, view)
        IslandAutoExpandPhaseMachine.isExpandedState(stateName(view))
    }.getOrElse { error ->
        Log.w(TAG, "staged click-expand failed", error)
        false
    }

    private fun invokeBigToExpanded(view: Any): Boolean = runCatching {
        val coordinator = IslandHookReflection.invokeNoArg(view, "getDynamicIslandEventCoordinator")
            ?: return false
        val controller = IslandHookReflection.invokeNoArg(coordinator, "getAnimationController")
            ?: return false
        val method = controller.javaClass.methods.firstOrNull {
            it.name == EXPAND_ANIMATION && it.parameterCount == 1
        } ?: return false
        method.isAccessible = true
        method.invoke(controller, view)
        true
    }.getOrElse { error ->
        Log.w(TAG, "staged big-to-expanded animation failed", error)
        false
    }

    private fun moveStateToExpanded(view: Any): Boolean = runCatching {
        val loader = view.javaClass.classLoader ?: return false
        val expanded = loader.loadClass(EXPANDED_STATE).getDeclaredConstructor().newInstance()
        val setState = IslandHookReflection.allMethods(view.javaClass).firstOrNull {
            it.name == "setState" && it.parameterCount == 1
        } ?: return false
        setState.isAccessible = true
        setState.invoke(view, expanded)
        val coordinator = IslandHookReflection.invokeNoArg(view, "getDynamicIslandEventCoordinator")
            ?: return false
        val controller = IslandHookReflection.invokeNoArg(coordinator, "getAnimationController")
            ?: return false
        val onStateChange = controller.javaClass.methods.firstOrNull {
            it.name == "onStateChange" && it.parameterCount == 1
        } ?: return false
        onStateChange.isAccessible = true
        onStateChange.invoke(controller, view)
        true
    }.getOrElse { error ->
        Log.w(TAG, "staged state expand failed", error)
        false
    }

    private fun forceMaxCutoutWidth(view: Any) {
        val reportedMax = readInt(view, "getMaxWidth") ?: 0
        val horizontalBound = (view as? View)?.let {
            maxOf(it.width, it.resources.displayMetrics.widthPixels)
        } ?: 0
        val max = IslandAutoExpandPhaseMachine.entranceWidth(reportedMax, horizontalBound)
        if (max <= 0) return
        val small = readInt(view, "getSmallIslandViewWidth") ?: 0
        val space = readInt(view, "getSpace") ?: 0
        val besideSmall = if (small > 0) (max - small - space).coerceAtLeast(1) else max
        widen(view, "getBigIslandViewWidth", "setBigIslandViewWidth", "getBigIslandX", "setBigIslandX", max)
        if (small > 0) {
            widen(
                view,
                "getBigIslandViewWidthHasSmallIsland",
                "setBigIslandViewWidthHasSmallIsland",
                "getBigIslandXHasSmallIsland",
                "setBigIslandXHasSmallIsland",
                besideSmall,
            )
        }
        val target = if (small > 0) besideSmall else max
        val left = readInt(view, "getBigIslandLeftWidth") ?: 0
        val right = readInt(view, "getBigIslandRightWidth") ?: 0
        val margin = readInt(view, "getBigIslandMarginWidth") ?: 0
        val sum = left + right + margin
        if (sum in 1 until target) {
            writeNumber(view, "setBigIslandMarginWidth", margin + (target - sum))
        }
    }

    private fun snapshotCompactGeometry(view: Any): CompactGeometry = CompactGeometry(
        bigWidth = readInt(view, "getBigIslandViewWidth"),
        bigWidthWithSmall = readInt(view, "getBigIslandViewWidthHasSmallIsland"),
        bigX = readInt(view, "getBigIslandX"),
        bigXWithSmall = readInt(view, "getBigIslandXHasSmallIsland"),
        marginWidth = readInt(view, "getBigIslandMarginWidth"),
    )

    /**
     * Restore Xiaomi's own content-measured compact geometry without asking it to relayout.
     * Calling updateBigIslandViewWidth() from an expanded-to-big transition emits another state
     * change and recursively starts the same transition, eventually hanging SystemUI's main
     * thread in MIUIX animation bookkeeping.
     */
    private fun restoreCompactGeometry(id: String, view: Any) {
        val geometry = compactGeometry.remove(id) ?: return
        geometry.bigWidth?.let { writeNumber(view, "setBigIslandViewWidth", it) }
        geometry.bigWidthWithSmall?.let {
            writeNumber(view, "setBigIslandViewWidthHasSmallIsland", it)
        }
        geometry.bigX?.let { writeNumber(view, "setBigIslandX", it) }
        geometry.bigXWithSmall?.let { writeNumber(view, "setBigIslandXHasSmallIsland", it) }
        geometry.marginWidth?.let { writeNumber(view, "setBigIslandMarginWidth", it) }
        Log.i(
            TAG,
            "restored native compact geometry width=${geometry.bigWidth} x=${geometry.bigX}",
        )
    }

    /**
     * Once the expanded transition has captured its start geometry, silently ask Xiaomi's pure
     * calculator to rebuild the cached compact width from the current island content. The next
     * expanded-to-big animation then consumes the correct native width without a visible update.
     */
    private fun scheduleNativeCompactMeasurement(id: String, view: Any) {
        val task = Runnable {
            if (phases[id] != IslandAutoExpandPhase.Measured) return@Runnable
            val current = session(view) ?: return@Runnable
            if (current.id != id) return@Runnable
            calculateNativeCompactGeometry(view)
        }
        (view as? View)?.postOnAnimation(task) ?: mainHandler.post(task)
    }

    private fun calculateNativeCompactGeometry(view: Any) {
        runCatching {
            val method = IslandHookReflection.findNoArgMethod(
                view.javaClass,
                "calculateBigIslandWidth",
            ) ?: return
            method.isAccessible = true
            method.invoke(view)
            Log.i(
                TAG,
                "silently measured native compact geometry width=" +
                    readInt(view, "getBigIslandViewWidth") +
                    " x=" + readInt(view, "getBigIslandX"),
            )
        }.onFailure { error ->
            Log.w(TAG, "native compact geometry measurement failed", error)
        }
    }

    private fun widen(
        view: Any,
        readWidth: String,
        writeWidth: String,
        readX: String,
        writeX: String,
        target: Int,
    ) {
        val current = readInt(view, readWidth) ?: 0
        if (current >= target) return
        val x = readInt(view, readX)
        writeNumber(view, writeWidth, target)
        if (x != null && current > 0) {
            writeNumber(view, writeX, x - (target - current) / 2)
        }
    }

    private fun readInt(target: Any, name: String): Int? {
        val value = IslandHookReflection.invokeNoArg(target, name) as? Number ?: return null
        return value.toFloat().roundToInt()
    }

    private fun writeNumber(target: Any, name: String, value: Int) {
        if (IslandHookReflection.invokeIntSetter(target, name, value)) return
        IslandHookReflection.invokeFloatSetter(target, name, value.toFloat())
    }

    private fun stateName(view: Any): String? =
        IslandHookReflection.invokeNoArg(view, "getState")?.javaClass?.name

    private fun session(view: Any): TrackedIsland? {
        val data = IslandHookReflection.invokeNoArg(view, "getCurrentIslandData")
        val snapshot = IslandOwnedNotification.fromIslandData(data) ?: return null
        if (!snapshot.owned) return null
        val key = snapshot.islandKey?.takeIf { it.isNotBlank() } ?: return null
        return TrackedIsland("$key#${snapshot.generation}", snapshot)
    }

    private data class TrackedIsland(
        val id: String,
        val snapshot: OwnedIslandSnapshot,
    )

    private data class CompactGeometry(
        val bigWidth: Int?,
        val bigWidthWithSmall: Int?,
        val bigX: Int?,
        val bigXWithSmall: Int?,
        val marginWidth: Int?,
    )
}
