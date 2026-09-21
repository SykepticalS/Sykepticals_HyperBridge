package com.d4viddf.hyperbridge.xposed.hooks

import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps Xiaomi's native text switcher enabled while an owned island is expanded.
 *
 * Compact holders are activated by `TextInfo.turnAnim` in the JSON payload. Expanded island
 * cards use Focus module holders, whose TimerTextEffectViews already own Xiaomi's vertical
 * fade/spring implementation. Enabling those views before updatePartial() preserves the old
 * glyph snapshot so Xiaomi can animate it into the new content instead of replacing it.
 */
object IslandTextUpdateAnimationHook {
    private const val FOCUS_MODULE_HOLDER =
        "miui.systemui.notification.focus.moduleV3.ModuleViewHolder"
    private const val TIMER_TEXT_EFFECT_VIEW = "miuix.colorful.texteffect.TimerTextEffectView"

    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val loggedOwners = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Any, Boolean>()),
    )
    @Volatile private var active = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { hook(module, it) }
    }

    fun isActive(): Boolean = active

    private fun hook(module: XposedModule, loader: ClassLoader) {
        val loaderId = System.identityHashCode(loader)
        if (!hookedLoaders.add(loaderId)) return
        runCatching {
            val holderClass = loader.loadClass(FOCUS_MODULE_HOLDER)
            val methods = holderClass.declaredMethods.filter {
                it.name == "updatePartial" && it.parameterCount == 2
            }
            check(methods.isNotEmpty()) { "updatePartial missing" }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val sbn = chain.args.getOrNull(1) as? StatusBarNotification
                    if (shouldAnimate(sbn)) {
                        val holder = chain.thisObject
                        val enabled = enableNativeTextEffects(holder)
                        if (enabled > 0 && holder?.let(loggedOwners::add) == true) {
                            module.log(
                                "HyperBridge: native expanded text updates enabled " +
                                    "views=$enabled loader=$loaderId",
                            )
                        }
                    }
                    chain.proceed()
                }
            }
            active = true
            module.log("HyperBridge: hooked native expanded text updates loader=$loaderId")
        }.onFailure { error ->
            // Dynamic plugin loaders are probed opportunistically. A missing class here is
            // expected until MIUISystemUIPlugin is created.
            if (error !is ClassNotFoundException) {
                module.log(
                    "HyperBridge: native expanded text update hook unavailable " +
                        "loader=$loaderId: ${error.message}",
                )
            }
        }
    }

    private fun shouldAnimate(sbn: StatusBarNotification?): Boolean {
        val extras = sbn?.notification?.extras ?: return false
        return extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER &&
            extras.getBoolean(IslandProtocol.EXTRA_TEXT_UPDATE_ANIMATION, false)
    }

    private fun enableNativeTextEffects(holder: Any?): Int {
        val owner = holder ?: return 0
        val root = IslandHookReflection.invokeNoArg(owner, "getView") as? View
            ?: IslandHookReflection.readField(owner, "view") as? View
            ?: return 0
        var enabled = 0
        traverse(root) { view ->
            if (view.javaClass.name != TIMER_TEXT_EFFECT_VIEW) return@traverse
            val method = runCatching {
                view.javaClass.getMethod("enableEffect", Boolean::class.javaPrimitiveType)
            }.getOrNull() ?: return@traverse
            if (runCatching { method.invoke(view, true) }.isSuccess) enabled++
        }
        return enabled
    }

    private fun traverse(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) traverse(view.getChildAt(index), action)
        }
    }
}
