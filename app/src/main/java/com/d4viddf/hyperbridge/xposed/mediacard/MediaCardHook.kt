package com.d4viddf.hyperbridge.xposed.mediacard

import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.hooks.DynamicClassLoaderHooks
import com.d4viddf.hyperbridge.xposed.log
import com.d4viddf.hyperbridge.xposed.mediacard.island.IslandExpandedMediaAmbientFlowHooker
import com.d4viddf.hyperbridge.xposed.mediacard.island.layout.IslandExpandedMediaLayoutHooker
import com.d4viddf.hyperbridge.xposed.mediacard.notification.NotificationMediaAmbientFlowHooker
import com.d4viddf.hyperbridge.xposed.mediacard.notification.NotificationMediaCoverStyleHooker
import com.d4viddf.hyperbridge.xposed.mediacard.notification.switcher.NotificationMediaSingleCardSwitcherHooker
import com.d4viddf.hyperbridge.xposed.mediacard.progress.MediaProgressStyleHooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Installs HyperLyric's media-card engine on SystemUI and its island plugin loader. */
object MediaCardHook {
    private val visited = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )

    fun install(module: XposedModule, param: PackageLoadedParam) {
        val prefs = HookConfig.remotePreferences() ?: run {
            module.log("MediaCard: remote preferences unavailable; skipping")
            return
        }
        MediaCardLog.module = module
        MediaCardRuntimeConfig.load(prefs)
        installForLoader(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            installForLoader(module, loader)
        }
    }

    private fun installForLoader(module: XposedModule, loader: ClassLoader) {
        if (!visited.add(loader)) return
        var installed = 0
        val installers: List<Pair<String, () -> Unit>> = listOf(
            "configuration refresh" to { MediaCardConfigurationRefreshHooker.hook(module, loader) },
            "progress styling" to { MediaProgressStyleHooker.hook(module, loader) },
            "element behavior" to { MediaCardElementBehaviorHooker.hook(module, loader) },
            "island expanded styling" to { IslandExpandedMediaAmbientFlowHooker.hook(module, loader) },
            "island expanded layout" to { IslandExpandedMediaLayoutHooker.hook(module, loader) },
            "shade background" to { NotificationMediaAmbientFlowHooker.hook(module, loader) },
            "shade elements" to { NotificationMediaCoverStyleHooker.hook(module, loader) },
        )
        installers.forEach { (name, action) ->
            runCatching(action).onSuccess { installed++ }.onFailure {
                module.log("MediaCard: $name unavailable on ${loader.javaClass.simpleName}: ${it.message}")
            }
        }
        if (MediaCardRuntimeConfig.current.notification.cardSwitcherEnabled) {
            runCatching { NotificationMediaSingleCardSwitcherHooker.hook(module, loader) }
                .onSuccess { installed++ }
                .onFailure { module.log("MediaCard: card switcher unavailable: ${it.message}") }
        }
        module.log("MediaCard: processed ${loader.javaClass.name}; installers=$installed")
    }
}
