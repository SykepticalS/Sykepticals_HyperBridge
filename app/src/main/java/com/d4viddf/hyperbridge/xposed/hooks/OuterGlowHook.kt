package com.d4viddf.hyperbridge.xposed.hooks

import android.graphics.Color
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.View
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.island.backend.IslandVisualExtras
import com.d4viddf.hyperbridge.models.IslandGlowIsolation
import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Real Xiaomi shader/glow integration adapted from HyperIsland's IslandOuterGlowHook (MIT).
 *
 * Glow on/off and colors are already resolved onto owned-proxy extras. This hook only drives
 * SystemUI animation/shader objects and never queries app databases per frame.
 */
object OuterGlowHook {
    private const val FEATURE = "miui.systemui.dynamicisland.DynamicFeatureConfig"
    private const val ANIMATION = "miui.systemui.dynamicisland.anim.DynamicIslandAnimationController"
    private const val GLOW_VIEW = "miui.systemui.dynamicisland.view.DynamicGlowEffectView"
    private const val FOCUS_CONTROLLER = "miui.systemui.notification.focus.FocusNotificationController"
    private const val AVOID_BURN_IN = "miui.systemui.dynamicisland.display.AvoidScreenBurnInHelper"
    private const val ABS_SHADER = "com.mi.widget.core.AbsShader"
    private const val DEFAULT_TEXTURE_BASE_COLOR = "vec3 currentColor = vec3(0.0, 0.5884, 1.0);"
    private const val BIG_VIEW = "DynamicIslandBigIslandView"
    private const val SMALL_VIEW = "DynamicIslandSmallIslandView"
    private const val EXPANDED_VIEW = "DynamicIslandExpandedView"
    private const val MODE_AUTO = 0
    private const val MODE_STATUS = 1
    private const val MODE_EXPAND = 2

    private data class OwnedGlowTarget(
        val snapshot: OwnedIslandSnapshot,
        val mode: Int,
        val createdAt: Long,
        val key: String? = snapshot.islandKey,
    )

    private val featureLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val animationLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val glowLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val focusLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val burnInLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val shaderLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val defaultShaderColors = WeakHashMap<Class<*>, FloatArray>()
    private val glowTargets = WeakHashMap<Any, OwnedGlowTarget>()
    private val runningGlowViews = WeakHashMap<Any, Boolean>()
    private val defaultGlowRanges = WeakHashMap<Any, Float>()

    @Volatile private var recentOwnedTarget: OwnedGlowTarget? = null
    @Volatile private var statusGlowShowing = false
    @Volatile private var active = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hookAll(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { hookAll(module, it) }
    }

    fun isActive(): Boolean = active

    private fun hookAll(module: XposedModule, loader: ClassLoader) {
        var hooked = 0
        hooked += hookFeature(module, loader)
        hooked += hookAnimation(module, loader)
        hooked += hookGlowView(module, loader)
        hooked += hookFocusBridge(module, loader)
        hooked += hookAvoidBurnIn(module, loader)
        hooked += hookShaderSource(module, loader)
        if (hooked > 0) active = true
    }

    private fun hookFeature(module: XposedModule, loader: ClassLoader): Int {
        val id = System.identityHashCode(loader)
        if (!featureLoaders.add(id)) return 0
        return runCatching {
            val method = loader.loadClass(FEATURE).declaredMethods.firstOrNull {
                it.name == "getFEATURE_DYNAMIC_ISLAND_SHADER" && it.parameterCount == 0
            } ?: return 0
            module.hook(method).intercept { true }
            1
        }.getOrElse {
            featureLoaders.remove(id)
            0
        }
    }

    private fun hookShaderSource(module: XposedModule, loader: ClassLoader): Int {
        val id = System.identityHashCode(loader)
        if (!shaderLoaders.add(id)) return 0
        return runCatching {
            val method = loader.loadClass(ABS_SHADER).declaredMethods.firstOrNull {
                it.name == "readRawString" && it.parameterCount == 1
            } ?: return 0
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val source = result as? String ?: return@intercept result
                if (!source.contains("uniform vec3 uLightColors[11]") ||
                    !source.contains(DEFAULT_TEXTURE_BASE_COLOR)
                ) return@intercept source
                source
                    .replaceFirst(
                        "uniform vec2 uResolution;",
                        "uniform vec2 uResolution;\nuniform vec3 uBaseColor;\nuniform float uUseBaseColor;",
                    )
                    .replaceFirst(
                        DEFAULT_TEXTURE_BASE_COLOR,
                        "vec3 currentColor = mix(vec3(0.0, 0.5884, 1.0), uBaseColor, uUseBaseColor);",
                    )
            }
            1
        }.getOrElse {
            shaderLoaders.remove(id)
            0
        }
    }

    private fun hookAvoidBurnIn(module: XposedModule, loader: ClassLoader): Int {
        val id = System.identityHashCode(loader)
        if (!burnInLoaders.add(id)) return 0
        return runCatching {
            val methods = loader.loadClass(AVOID_BURN_IN).declaredMethods.filter {
                it.name == "updateViewForAvoidingScreenBurnIn" && it.parameterCount == 2
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val mode = glowModeFromContent(chain.args.getOrNull(0))
                    if (mode == MODE_STATUS && statusGlowShowing) null else chain.proceed()
                }
            }
            methods.size
        }.getOrElse { 0 }
    }

    private fun hookFocusBridge(module: XposedModule, loader: ClassLoader): Int {
        val id = System.identityHashCode(loader)
        if (!focusLoaders.add(id)) return 0
        return runCatching {
            val methods = loader.loadClass(FOCUS_CONTROLLER).declaredMethods.filter { method ->
                method.parameterTypes.any { it.name == "android.service.notification.StatusBarNotification" } &&
                    (method.name == "setUpDynamicIslandDataBundle" ||
                        method.name.contains("DynamicIslandData", ignoreCase = true))
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val sbn = chain.args.firstOrNull { it is StatusBarNotification } as? StatusBarNotification
                        ?: return@intercept result
                    val source = sbn.notification?.extras ?: return@intercept result
                    if (source.getString(IslandProtocol.EXTRA_OWNER) != IslandProtocol.OWNER) return@intercept result
                    val target = (result as? Bundle) ?: chain.args.firstOrNull { it is Bundle } as? Bundle
                    if (target != null) IslandVisualExtras.bridgeOwnedExtras(source, target)
                    result
                }
            }
            methods.size
        }.getOrElse {
            focusLoaders.remove(id)
            0
        }
    }

    private fun hookAnimation(module: XposedModule, loader: ClassLoader): Int {
        val id = System.identityHashCode(loader)
        if (!animationLoaders.add(id)) return 0
        return runCatching {
            val methods = loader.loadClass(ANIMATION).declaredMethods.filter {
                it.name == "onStateChange" && it.parameterCount >= 1
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val state = chain.args.getOrNull(0)
                    val mode = strictGlowMode(state)
                    val snapshot = IslandOwnedNotification.fromAnimationState(state)
                    rememberOwnedTarget(snapshot, mode, state)
                    val glowRequest = IslandGlowIsolation.shouldStartGlow(
                        currentOwned = snapshot?.owned == true,
                        currentRequestsGlow = glowRequestedFor(snapshot, mode),
                    )
                    val stopGlow = IslandGlowIsolation.shouldStopGlow(
                        currentKnown = snapshot != null,
                        currentOwned = snapshot?.owned == true,
                        currentRequestsGlow = glowRequest,
                    )
                    if (stopGlow) {
                        recentOwnedTarget = null
                        resolveGlowViews(state, mode).forEach { glowView ->
                            synchronized(glowTargets) { glowTargets.remove(glowView) }
                            restoreDefaultGlowColor(glowView)
                            if (mode != MODE_AUTO) invokeGlowEffect(glowView, "stopGlowEffect")
                        }
                    }
                    val result = chain.proceed()
                    val glowViews = resolveGlowViews(state, mode)
                    if (glowRequest) {
                        recentOwnedTarget?.let { bound ->
                            val attached = if (mode == MODE_AUTO) bound else bound.copy(mode = mode)
                            glowViews.forEach { glowView ->
                                synchronized(glowTargets) { glowTargets[glowView] = attached }
                            }
                        }
                    }
                    when {
                        glowRequest && mode == MODE_STATUS ->
                            startStatusGlow(glowViews)
                        glowRequest && mode == MODE_EXPAND ->
                            glowViews.forEach { invokeGlowEffect(it, "startGlowEffect") }
                        isStateTag(state, "Deleted") -> {
                            invokeGlowEffect(IslandHookReflection.invokeNoArg(state, "getBigIslandView"), "stopGlowEffect")
                            invokeGlowEffect(IslandHookReflection.invokeNoArg(state, "getSmallIslandView"), "stopGlowEffect")
                            invokeGlowEffect(IslandHookReflection.invokeNoArg(state, "getExpandedView"), "stopGlowEffect")
                            statusGlowShowing = false
                            recentOwnedTarget = null
                        }
                    }
                    result
                }
            }
            if (methods.isNotEmpty()) active = true
            methods.size
        }.onFailure {
            animationLoaders.remove(id)
            module.log("HyperBridge: glow animation hook unavailable: ${it.message}")
        }.getOrDefault(0)
    }

    private fun hookGlowView(module: XposedModule, loader: ClassLoader): Int {
        val id = System.identityHashCode(loader)
        if (!glowLoaders.add(id)) return 0
        return runCatching {
            val clazz = loader.loadClass(GLOW_VIEW)
            val usesOs4Shared = clazz.declaredMethods.any {
                (it.name == "stopGlowEffect" || it.name == "stopGlowEffect\$miui_dynamicisland_release") &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            val methods = clazz.declaredMethods.filter { method ->
                val base = method.name.substringBefore('$')
                val isStart = base == "startGlowEffect"
                val isStop = base == "stopGlowEffect"
                val isDetach = method.name == "onDetachedFromWindow"
                (isStart && method.parameterCount == 0) ||
                    (isStop && (method.parameterCount == 0 ||
                        (method.parameterCount == 1 && method.parameterTypes[0] == Boolean::class.javaPrimitiveType))) ||
                    (usesOs4Shared && isDetach && method.parameterCount == 0)
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val isStart = method.name.startsWith("startGlowEffect")
                    val isStop = method.name.startsWith("stopGlowEffect")
                    val isDetach = method.name == "onDetachedFromWindow"
                    if (isStart) applyGlowAppearance(chain.thisObject)
                    val result = chain.proceed()
                    val mode = glowModeFromGlowView(chain.thisObject)
                    if (isStart) {
                        applyGlowAppearance(chain.thisObject)
                        applyOwnedGlowColor(chain.thisObject, mode)
                        if (IslandHookReflection.readField(chain.thisObject, "enabledGlowEffect") == true) {
                            synchronized(runningGlowViews) { runningGlowViews[chain.thisObject] = true }
                        }
                        if (mode == MODE_STATUS && isOwnedGlowActive(mode)) statusGlowShowing = true
                    } else if (isStop || isDetach) {
                        synchronized(runningGlowViews) { runningGlowViews.remove(chain.thisObject) }
                        if (usesOs4Shared) restoreSharedGlowContainers(chain.thisObject)
                        if (mode == MODE_STATUS && !usesOs4Shared) statusGlowShowing = false
                    }
                    if (usesOs4Shared) statusGlowShowing = hasRunningOwnedStatusGlow()
                    result
                }
            }
            if (methods.isNotEmpty()) active = true
            methods.size
        }.onFailure {
            glowLoaders.remove(id)
            module.log("HyperBridge: glow view hook unavailable: ${it.message}")
        }.getOrDefault(0)
    }

    private fun glowRequested(snapshot: OwnedIslandSnapshot, mode: Int): Boolean = when (mode) {
        MODE_STATUS -> snapshot.owned && snapshot.islandGlowEnabled
        MODE_EXPAND -> snapshot.owned && (snapshot.focusGlowEnabled || snapshot.islandGlowEnabled)
        else -> false
    }

    private fun glowRequestedFor(snapshot: OwnedIslandSnapshot?, mode: Int): Boolean =
        snapshot != null && glowRequested(snapshot, mode)

    private fun rememberOwnedTarget(snapshot: OwnedIslandSnapshot?, mode: Int, state: Any?) {
        val now = System.currentTimeMillis()
        if (snapshot != null && snapshot.owned &&
            (glowRequested(snapshot, MODE_STATUS) || glowRequested(snapshot, MODE_EXPAND))
        ) {
            recentOwnedTarget = OwnedGlowTarget(
                snapshot,
                mode.takeIf { it != MODE_AUTO } ?: MODE_STATUS,
                now,
                snapshot.islandKey,
            )
            return
        }
        val previous = recentOwnedTarget ?: return
        if (isStateTag(state, "Deleted")) {
            recentOwnedTarget = null
            return
        }
        val currentKey = snapshot?.islandKey
        if (IslandGlowIsolation.canReuseRecentTarget(
                currentKey = currentKey,
                recentKey = previous.key,
                currentRequestsGlow = false,
            )
        ) {
            recentOwnedTarget = previous.copy(
                mode = mode.takeIf { it != MODE_AUTO } ?: previous.mode,
                createdAt = now,
            )
            return
        }
        recentOwnedTarget = null
    }

    private fun startStatusGlow(glowViews: List<Any>) {
        glowViews.forEach { invokeGlowEffect(it, "startGlowEffect") }
        recentOwnedTarget?.let { bound ->
            glowViews.forEach { view ->
                synchronized(glowTargets) { glowTargets[view] = bound.copy(mode = MODE_STATUS) }
                applyOwnedGlowColor(view, MODE_STATUS)
            }
        }
    }

    private fun applyOwnedGlowColor(glowView: Any?, mode: Int) {
        if (glowView == null) return
        if (resolveLightBgShader(glowView) == null) {
            val nested = IslandHookReflection.invokeNoArg(glowView, "getGlowEffectView")
                ?.takeUnless { it === glowView }
            if (nested != null) {
                synchronized(glowTargets) { glowTargets[glowView]?.let { glowTargets[nested] = it } }
                applyOwnedGlowColor(nested, mode)
            }
            return
        }
        val bound = synchronized(glowTargets) { glowTargets[glowView] }
        val recent = recentOwnedTarget
        val target = when {
            bound != null && recent != null -> {
                val sameIsland = !bound.key.isNullOrBlank() && bound.key == recent.key
                when {
                    !sameIsland -> bound
                    recent.createdAt >= bound.createdAt -> recent
                    else -> bound
                }
            }
            bound != null -> bound
            recent != null -> recent
            else -> return
        }
        val resolvedMode = if (mode == MODE_AUTO) target.mode.takeIf { it != MODE_AUTO } ?: return else mode
        val snapshot = target.snapshot
        val enabled = glowRequested(snapshot, resolvedMode) ||
            (resolvedMode == MODE_STATUS && snapshot.islandGlowEnabled) ||
            (resolvedMode == MODE_EXPAND && (snapshot.focusGlowEnabled || snapshot.islandGlowEnabled))
        val color = if (resolvedMode == MODE_EXPAND) snapshot.focusColorArgb() else snapshot.islandColorArgb()
        val shader = resolveLightBgShader(glowView) ?: return
        val runtime = resolveRuntimeShader(shader) ?: return
        val shaderClass = shader.javaClass
        val base = obtainDefaultLightColors(shaderClass) ?: readInstanceLightColors(shader) ?: return
        cacheDefaultLightColors(shaderClass, base)
        val single = HookConfig.singleColorGlow()
        val colors = when {
            !enabled || color == null -> base
            else -> GlowShaderPalette.rebuild(base, color, single)
        }
        setFloatUniform(runtime, "uLightColors", colors)
        val configuredBase = HookConfig.glowBaseColor()?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        val paletteColor = color.takeIf { enabled }
        val baseColor = when {
            single && paletteColor != null -> paletteColor
            configuredBase != null -> configuredBase
            paletteColor != null -> paletteColor
            else -> Color.rgb(0, 150, 255)
        }
        val useBase = paletteColor != null || configuredBase != null
        setFloatUniform(runtime, "uBaseColor", GlowShaderPalette.rgb(baseColor))
        setFloatUniform(runtime, "uUseBaseColor", floatArrayOf(if (useBase) 1f else 0f))
        if (!enabled) restoreDefaultGlowColor(glowView)
    }

    private fun restoreDefaultGlowColor(glowView: Any) {
        val shader = resolveLightBgShader(glowView) ?: return
        val runtime = resolveRuntimeShader(shader) ?: return
        val colors = obtainDefaultLightColors(shader.javaClass) ?: readInstanceLightColors(shader) ?: return
        cacheDefaultLightColors(shader.javaClass, colors)
        setFloatUniform(runtime, "uLightColors", colors)
        setFloatUniform(runtime, "uUseBaseColor", floatArrayOf(0f))
    }

    private fun applyGlowAppearance(glowView: Any) {
        val range = HookConfig.glowRange()
        val container = IslandHookReflection.invokeNoArg(glowView, "getMContainer") ?: return
        val defaultRange = synchronized(defaultGlowRanges) {
            defaultGlowRanges.getOrPut(container) {
                (IslandHookReflection.invokeNoArg(container, "getSizeOfGlowArea") as? Number)?.toFloat() ?: Float.NaN
            }
        }
        val applied = if (range == 0 || defaultRange.isNaN()) defaultRange else defaultRange * range / 100f
        if (!applied.isNaN()) IslandHookReflection.invokeFloatSetter(container, "setSizeOfGlowArea", applied)
    }

    private fun restoreSharedGlowContainers(stoppedView: Any) {
        synchronized(runningGlowViews) {
            val iterator = runningGlowViews.keys.iterator()
            while (iterator.hasNext()) {
                val glowView = iterator.next()
                if (glowView === stoppedView) {
                    iterator.remove()
                    continue
                }
                val host = glowView as? View
                val running = IslandHookReflection.readField(glowView, "enabledGlowEffect") == true
                val shown = host?.isShown == true && (host.alpha > 0f)
                if (!running || !shown) {
                    iterator.remove()
                    continue
                }
                (IslandHookReflection.invokeNoArg(glowView, "getMGlowEffectUpperContainer") as? View)?.visibility = View.VISIBLE
                (IslandHookReflection.invokeNoArg(glowView, "getMGlowEffectBottomContainer") as? View)?.visibility = View.VISIBLE
                val upperEffect = IslandHookReflection.invokeNoArg(glowView, "getMGlowEffectUpperView") as? View
                val bottomEffect = IslandHookReflection.invokeNoArg(glowView, "getMGlowEffectBottomView") as? View
                upperEffect?.visibility = View.VISIBLE
                bottomEffect?.visibility = View.VISIBLE
                val mode = glowModeFromGlowView(glowView)
                val effectAlpha = if (mode == MODE_STATUS) {
                    host.alpha
                } else {
                    (IslandHookReflection.invokeNoArg(glowView, "getAlphaOfGlowEffect\$miui_dynamicisland_release") as? Number)?.toFloat()
                }
                if (effectAlpha != null) {
                    IslandHookReflection.invokeFloatSetter(glowView, "setAlphaOfGlowEffect\$miui_dynamicisland_release", effectAlpha) ||
                        IslandHookReflection.invokeFloatSetter(glowView, "setAlphaOfGlowEffect", effectAlpha)
                    upperEffect?.alpha = effectAlpha
                    bottomEffect?.alpha = effectAlpha
                }
                (IslandHookReflection.invokeNoArg(glowView, "getMContainer") as? View)?.invalidate()
            }
        }
    }

    private fun isOwnedGlowActive(mode: Int): Boolean {
        val target = synchronized(glowTargets) {
            glowTargets.values.firstOrNull { glowRequested(it.snapshot, mode) }
        } ?: recentOwnedTarget ?: return false
        return glowRequested(target.snapshot, mode)
    }

    private fun hasRunningOwnedStatusGlow(): Boolean = synchronized(runningGlowViews) {
        runningGlowViews.keys.any { glowView ->
            IslandHookReflection.readField(glowView, "enabledGlowEffect") == true &&
                synchronized(glowTargets) { glowTargets[glowView] }?.let { glowRequested(it.snapshot, MODE_STATUS) } == true
        }
    }

    private fun resolveGlowViews(state: Any?, mode: Int): List<Any> {
        val expanded = IslandHookReflection.invokeNoArg(state, "getExpandedView")
        val big = IslandHookReflection.invokeNoArg(state, "getBigIslandView")
        val small = IslandHookReflection.invokeNoArg(state, "getSmallIslandView")
        return when (mode) {
            MODE_STATUS -> if (isStateTag(state, "SmallIsland")) {
                listOfNotNull(small, big)
            } else {
                listOfNotNull(big, small)
            }
            MODE_EXPAND -> listOfNotNull(expanded, big)
            else -> listOfNotNull(expanded, big, small)
        }.distinct()
    }

    private fun strictGlowMode(state: Any?): Int {
        val value = IslandHookReflection.invokeNoArg(state ?: return MODE_AUTO, "getState") ?: return MODE_AUTO
        val name = value.javaClass.simpleName
        return when {
            name.contains("Expand", ignoreCase = true) -> MODE_EXPAND
            name.contains("BigIsland") || name.contains("SmallIsland") -> MODE_STATUS
            else -> MODE_AUTO
        }
    }

    private fun glowModeFromGlowView(glowView: Any): Int {
        val name = glowView.javaClass.name
        synchronized(glowTargets) { glowTargets[glowView]?.mode }?.takeIf { it != MODE_AUTO }?.let { return it }
        return when {
            name.contains(EXPANDED_VIEW) || name.contains("Expanded") -> MODE_EXPAND
            name.contains(BIG_VIEW) || name.contains(SMALL_VIEW) -> MODE_STATUS
            isOwnedGlowActive(MODE_STATUS) -> MODE_STATUS
            isOwnedGlowActive(MODE_EXPAND) -> MODE_EXPAND
            else -> recentOwnedTarget?.mode ?: MODE_AUTO
        }
    }

    private fun glowModeFromContent(view: Any?): Int {
        val state = IslandHookReflection.invokeNoArg(view ?: return MODE_AUTO, "getState")?.toString()
        return when {
            state?.contains("BigIsland") == true || state?.contains("SmallIsland") == true -> MODE_STATUS
            state?.contains("Expand") == true -> MODE_EXPAND
            else -> MODE_AUTO
        }
    }

    private fun isStateTag(state: Any?, tag: String): Boolean =
        IslandHookReflection.invokeNoArg(state, "getState")?.toString()?.contains(tag) == true

    private fun invokeGlowEffect(view: Any?, baseName: String) {
        if (view == null) return
        IslandHookReflection.findNoArgMethod(view.javaClass, baseName)
            ?.let { runCatching { it.isAccessible = true; it.invoke(view) }; return }
        IslandHookReflection.findNoArgMethod(view.javaClass, "$baseName\$miui_dynamicisland_release")
            ?.let { runCatching { it.isAccessible = true; it.invoke(view) }; return }
        IslandHookReflection.invokeNoArg(view, "getGlowEffectView")?.let { invokeGlowEffect(it, baseName) }
    }

    private fun resolveLightBgShader(glowView: Any): Any? {
        val container = IslandHookReflection.invokeNoArg(glowView, "getMContainer") ?: return null
        IslandHookReflection.invokeNoArg(container, "getMShader\$hyper_widget_1_0_8_pluginRelease")?.let { return it }
        return IslandHookReflection.allMethods(container.javaClass).firstOrNull {
            it.parameterCount == 0 && it.name.contains("getMShader")
        }?.let { runCatching { it.isAccessible = true; it.invoke(container) }.getOrNull() }
    }

    private fun resolveRuntimeShader(shader: Any): Any? {
        IslandHookReflection.invokeNoArg(shader, "getMTextureShader")?.let { return it }
        IslandHookReflection.allMethods(shader.javaClass).firstOrNull {
            it.parameterCount == 0 && it.name.contains("getMTextureShader")
        }?.let { runCatching { it.isAccessible = true; it.invoke(shader) }.getOrNull() }?.let { return it }
        IslandHookReflection.invokeNoArg(shader, "getRuntimeShader")?.let { return it }
        IslandHookReflection.invokeNoArg(shader, "getMRuntimeShader")?.let { return it }
        return IslandHookReflection.readField(shader, "mRuntimeShader")
    }

    private fun obtainDefaultLightColors(shaderClass: Class<*>): FloatArray? {
        synchronized(defaultShaderColors) { defaultShaderColors[shaderClass]?.let { return it.copyOf() } }
        val field = IslandHookReflection.findLightColorField(shaderClass, preferStatic = true) ?: return null
        return runCatching { (field.get(if (Modifier.isStatic(field.modifiers)) null else null) as? FloatArray)?.copyOf() }
            .getOrNull()
            ?.also { cacheDefaultLightColors(shaderClass, it) }
    }

    private fun cacheDefaultLightColors(shaderClass: Class<*>, colors: FloatArray) {
        synchronized(defaultShaderColors) {
            if (!defaultShaderColors.containsKey(shaderClass)) defaultShaderColors[shaderClass] = colors.copyOf()
        }
    }

    private fun readInstanceLightColors(shader: Any): FloatArray? {
        val field = IslandHookReflection.findLightColorField(shader.javaClass, preferStatic = false) ?: return null
        return runCatching { (field.get(shader) as? FloatArray)?.copyOf() }.getOrNull()
    }

    private fun setFloatUniform(runtime: Any, name: String, values: FloatArray): Boolean {
        val method = (runtime.javaClass.methods + runtime.javaClass.declaredMethods).firstOrNull {
            it.name == "setFloatUniform" &&
                it.parameterCount == 2 &&
                it.parameterTypes.getOrNull(0) == String::class.java &&
                it.parameterTypes.getOrNull(1)?.isArray == true
        } ?: return false
        return runCatching {
            method.isAccessible = true
            method.invoke(runtime, name, values)
            true
        }.getOrDefault(false)
    }
}
