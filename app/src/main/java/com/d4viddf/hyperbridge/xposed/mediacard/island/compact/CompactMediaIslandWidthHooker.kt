package com.d4viddf.hyperbridge.xposed.mediacard.island.compact

import android.view.View
import com.d4viddf.hyperbridge.xposed.hooks.IslandOwnedNotification
import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardLog
import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardRuntimeConfig
import com.d4viddf.hyperbridge.xposed.mediacard.compat.IslandProbeUtils
import com.d4viddf.hyperbridge.xposed.mediacard.managedHook
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap

/**
 * Length slider for native compact media islands only.
 * 0 leaves Xiaomi's max width alone; each step adds a little room up to the slider maximum.
 */
internal object CompactMediaIslandWidthHooker {
    private const val TAG = "CompactMediaIslandWidth"
    private const val CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"
    private const val BASE_CONTENT_VIEW =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val PHONE_HELPER =
        "miui.systemui.dynamicisland.window.content.helpers.DynamicIslandContentViewPhoneHelper"

    private val hookedLoaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    private val activeMedia = ThreadLocal<View>()

    fun hook(module: XposedModule, loader: ClassLoader) {
        if (!hookedLoaders.add(loader)) return
        var installed = 0
        installed += hookClass(loader, CONTENT_VIEW) { clazz ->
            val method = clazz.methods.firstOrNull {
                it.name == "calculateBigIslandWidth" &&
                    it.parameterCount == 0 &&
                    it.returnType == Void.TYPE
            } ?: return@hookClass 0
            module.managedHook(method, "media.island.compact.width.scope", ScopeHook())
            1
        }
        installed += hookClass(loader, BASE_CONTENT_VIEW) { clazz ->
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "getMaxWidth" && it.parameterCount == 0
            } ?: return@hookClass 0
            module.managedHook(method, "media.island.compact.width.max", MaxWidthHook())
            1
        }
        installed += hookClass(loader, PHONE_HELPER) { clazz ->
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "calculateMaxWidthWithSmall" &&
                    it.returnType == Float::class.javaPrimitiveType
            } ?: return@hookClass 0
            module.managedHook(method, "media.island.compact.width.small", MaxWidthWithSmallHook())
            1
        }
        if (installed == 0) {
            hookedLoaders.remove(loader)
            MediaCardLog.w(TAG, "Compact media width hook was not installed")
        } else {
            MediaCardLog.d(TAG, "Compact media width hook installed: methods=$installed")
        }
    }

    private fun hookClass(loader: ClassLoader, name: String, install: (Class<*>) -> Int): Int {
        val clazz = runCatching { loader.loadClass(name) }.getOrNull() ?: return 0
        return runCatching { install(clazz) }
            .onFailure { error -> MediaCardLog.w(TAG, "Skipped $name", error) }
            .getOrDefault(0)
    }

    private class ScopeHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val view = chain.thisObject as? View
            val track = view != null && shouldWiden(view)
            if (track) activeMedia.set(view)
            return try {
                chain.proceed()
            } finally {
                if (track) activeMedia.remove()
            }
        }
    }

    private class MaxWidthHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val system = chain.proceed()
            val view = chain.thisObject as? View ?: return system
            if (!shouldWiden(view)) return system
            val systemPx = (system as? Number)?.toFloat() ?: return system
            val override = CompactMediaIslandPolicy.islandMaxOverridePx(
                percent = MediaCardRuntimeConfig.current.compactIsland.widthPercent,
                systemMaxPx = systemPx,
                density = view.resources.displayMetrics.density,
            ) ?: return system
            return when (system) {
                is Float -> override
                is Double -> override.toDouble()
                is Int -> override.toInt()
                else -> system
            }
        }
    }

    private class MaxWidthWithSmallHook : Hooker {
        override fun intercept(chain: Chain): Any? {
            val system = chain.proceed()
            val view = activeMedia.get() ?: return system
            if (!shouldWiden(view)) return system
            val systemPx = (system as? Number)?.toFloat() ?: return system
            return CompactMediaIslandPolicy.islandMaxOverridePx(
                percent = MediaCardRuntimeConfig.current.compactIsland.widthPercent,
                systemMaxPx = systemPx,
                density = view.resources.displayMetrics.density,
            ) ?: system
        }
    }

    private fun shouldWiden(view: View): Boolean {
        if (view.resources.configuration.smallestScreenWidthDp >= 600) return false
        if (MediaCardRuntimeConfig.current.compactIsland.widthPercent <= 0) return false
        val data = islandData(view) ?: return false
        if (!IslandProbeUtils.isMediaIsland(data)) return false
        return IslandOwnedNotification.fromIslandData(data)?.owned != true
    }

    private fun islandData(view: View): Any? {
        var current: View? = view
        while (current != null) {
            IslandProbeUtils.getCurrentIslandData(current)?.let { return it }
            current = current.parent as? View
        }
        return null
    }
}
