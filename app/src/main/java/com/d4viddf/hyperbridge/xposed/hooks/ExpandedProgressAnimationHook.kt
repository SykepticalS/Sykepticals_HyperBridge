package com.d4viddf.hyperbridge.xposed.hooks

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.LayerDrawable
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ProgressBar
import com.d4viddf.hyperbridge.models.ExpandedProgressAnimationPolicy
import com.d4viddf.hyperbridge.models.ExpandedProgressHistory
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Smooths Xiaomi's determinate progressInfo module without changing its layout or data model. */
object ExpandedProgressAnimationHook {
    private const val DURATION_MS = 380L
    private const val MAX_DIAGNOSTIC_LOGS = 12
    private val holderClassNames = listOf(
        "miui.systemui.notification.focus.moduleV3.ModuleProgressViewHolder",
        "miui.systemui.notification.focus.moduleV3.ModuleDecoPortProgressViewHolder",
        "miui.systemui.notification.focus.moduleV3.ModuleDecoLandProgressViewHolder",
        "miui.systemui.notification.focus.moduleV3.ModuleTinyProgressViewHolder",
        "miui.systemui.notification.focus.moduleV3.ModuleMultiProgressViewHolder",
        "miui.systemui.notification.focus.moduleV3.ModuleDecoMultiProgressViewHolder",
        "miui.systemui.notification.focus.moduleV3.ModuleTinyMultiProgressViewHolder",
    )
    private val interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    )
    private val animationStates = Collections.synchronizedMap(
        WeakHashMap<ProgressBar, AnimationState>()
    )
    private val history = ExpandedProgressHistory()
    private val diagnosticLogs = AtomicInteger()

    private data class AnimationState(
        val sourceKey: String,
        val token: Any,
        var currentLevel: Int,
        val targetLevel: Int,
        var animator: ValueAnimator? = null,
    )

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hook(module, loader)
        }
    }

    private fun hook(module: XposedModule, loader: ClassLoader) {
        var installed = 0
        holderClassNames.forEach { className ->
            val holderClass = runCatching { loader.loadClass(className) }.getOrNull()
                ?: return@forEach
            if (!hookedClasses.add(holderClass)) return@forEach
            val bindMethods = holderClass.declaredMethods.filter { method ->
                method.name == "bind" &&
                    method.parameterCount == 2 &&
                    StatusBarNotification::class.java.isAssignableFrom(method.parameterTypes[1])
            }
            bindMethods.forEach { method ->
                runCatching {
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        val holder = chain.thisObject
                        val sbn = chain.args.getOrNull(1) as? StatusBarNotification
                        val beforeBar = holder?.let(::progressBar)
                        val beforeProgress = beforeBar?.progress
                        val beforeState = beforeBar?.let { animationStates[it] }
                        val result = chain.proceed()
                        if (holder != null && sbn != null) {
                            runCatching {
                                onBound(
                                    module = module,
                                    holder = holder,
                                    sbn = sbn,
                                    beforeBar = beforeBar,
                                    beforeProgress = beforeProgress,
                                    beforeState = beforeState,
                                )
                            }.onFailure { error ->
                                module.log(
                                    "HyperBridge: expanded progress update failed: " +
                                        "${error.javaClass.simpleName}: ${error.message}"
                                )
                            }
                        }
                        result
                    }
                    installed++
                }.onFailure { error ->
                    module.log(
                        "HyperBridge: expanded progress hook failed " +
                            "class=$className method=${method.name}: ${error.message}"
                    )
                }
            }
        }
        if (installed > 0) {
            module.log(
                "HyperBridge: hooked Xiaomi expanded progress loader=" +
                    "${System.identityHashCode(loader)} methods=$installed"
            )
        }
    }

    private fun onBound(
        module: XposedModule,
        holder: Any,
        sbn: StatusBarNotification,
        beforeBar: ProgressBar?,
        beforeProgress: Int?,
        beforeState: AnimationState?,
    ) {
        val bar = progressBar(holder) ?: return
        val sourceKey = sbn.key?.takeIf { it.isNotBlank() } ?: return
        val targetProgress = bar.progress
        val maxProgress = bar.max
        val updateId = sbn.postTime
        val historicalProgress = history.observe(sourceKey, updateId, targetProgress)
        val currentState = animationStates[bar]
        val fromProgress = when {
            currentState?.sourceKey == sourceKey ->
                levelToProgress(currentState.currentLevel, maxProgress)
            beforeBar === bar && beforeState?.sourceKey == sourceKey ->
                levelToProgress(beforeState.currentLevel, maxProgress)
            beforeBar === bar -> beforeProgress
            else -> historicalProgress
        }
        val shouldAnimate = ExpandedProgressAnimationPolicy.shouldAnimate(
            fromProgress = fromProgress,
            targetProgress = targetProgress,
            maxProgress = maxProgress,
        )
        if (!shouldAnimate) {
            finishImmediately(bar, targetProgress, maxProgress)
            return
        }

        val fromLevel = currentState
            ?.takeIf { it.sourceKey == sourceKey }
            ?.currentLevel
            ?: ExpandedProgressAnimationPolicy.drawableLevel(fromProgress!!, maxProgress)
        val targetLevel = ExpandedProgressAnimationPolicy.drawableLevel(targetProgress, maxProgress)
        animate(bar, sourceKey, fromLevel, targetLevel)

        if (diagnosticLogs.getAndIncrement() < MAX_DIAGNOSTIC_LOGS) {
            module.log(
                "HyperBridge: expanded progress animated " +
                    "source=${sbn.packageName} from=$fromProgress to=$targetProgress " +
                    "view=${resourceName(bar)} class=${bar.javaClass.name}"
            )
        }
    }

    private fun animate(
        bar: ProgressBar,
        sourceKey: String,
        fromLevel: Int,
        targetLevel: Int,
    ) {
        val token = Any()
        val oldState = animationStates[bar]
        val state = AnimationState(
            sourceKey = sourceKey,
            token = token,
            currentLevel = fromLevel,
            targetLevel = targetLevel,
        )
        animationStates[bar] = state
        oldState?.animator?.cancel()
        setVisualLevel(bar, fromLevel)

        val animator = ValueAnimator.ofInt(fromLevel, targetLevel).apply {
            duration = DURATION_MS
            interpolator = ExpandedProgressAnimationHook.interpolator
            addUpdateListener { valueAnimator ->
                val active = animationStates[bar]
                if (active?.token !== token) return@addUpdateListener
                val level = valueAnimator.animatedValue as Int
                active.currentLevel = level
                setVisualLevel(bar, level)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
                override fun onAnimationCancel(animation: Animator) = finish()

                private fun finish() {
                    val active = animationStates[bar]
                    if (active?.token !== token) return
                    active.currentLevel = targetLevel
                    setVisualLevel(bar, targetLevel)
                    animationStates.remove(bar)
                }
            })
        }
        state.animator = animator
        animator.start()
    }

    private fun finishImmediately(bar: ProgressBar, targetProgress: Int, maxProgress: Int) {
        val targetLevel = ExpandedProgressAnimationPolicy.drawableLevel(targetProgress, maxProgress)
        val previous = animationStates.remove(bar)
        previous?.animator?.cancel()
        setVisualLevel(bar, targetLevel)
    }

    private fun setVisualLevel(bar: ProgressBar, level: Int) {
        val drawable = bar.progressDrawable ?: return
        val progressDrawable = (drawable as? LayerDrawable)
            ?.findDrawableByLayerId(android.R.id.progress)
            ?: drawable
        progressDrawable.level = level.coerceIn(0, 10_000)
        bar.invalidate()
    }

    private fun progressBar(holder: Any): ProgressBar? =
        IslandHookReflection.readField(holder, "progressBar") as? ProgressBar

    private fun levelToProgress(level: Int, maxProgress: Int): Int =
        ((level.coerceIn(0, 10_000).toLong() * maxProgress.coerceAtLeast(0)) / 10_000L)
            .toInt()

    private fun resourceName(view: View): String = runCatching {
        view.resources.getResourceEntryName(view.id)
    }.getOrDefault("no-id")
}
