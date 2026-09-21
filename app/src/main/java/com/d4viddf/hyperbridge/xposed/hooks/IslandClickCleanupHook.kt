package com.d4viddf.hyperbridge.xposed.hooks

import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.xposed.dispatch.SystemUiDispatcher
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Removes an owned proxy after HyperOS has dispatched its big-island app launch. */
object IslandClickCleanupHook {
    private const val CONTROLLER =
        "com.android.systemui.statusbar.notification.DynamicIslandController"
    private const val OPEN_APP = "onDynamicPluginCallback_openApp"

    private val loaders = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
    )
    @Volatile private var active = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { loader ->
            hook(module, loader)
        }
    }

    private fun hook(module: XposedModule, loader: ClassLoader) {
        if (!loaders.add(loader)) return
        runCatching {
            val controller = loader.loadClass(CONTROLLER)
            val callback = findCallback(controller)
            module.hook(callback).intercept { chain ->
                val callbackName = chain.args.getOrNull(0) as? String
                val bundle = chain.args.getOrNull(1) as? Bundle
                val sbn = if (callbackName == OPEN_APP) {
                    // WhatsApp's grouped MessagingStyle callback can contain a parcelable
                    // bundle with a different class-loader/state than ordinary notifications.
                    // Never let diagnostic cleanup break Xiaomi's actual app-launch callback.
                    runCatching {
                        bundle?.getParcelable("miui.sbn", StatusBarNotification::class.java)
                    }.onFailure {
                        module.log("HyperBridge: open-app SBN extraction skipped: ${it.message}")
                    }.getOrNull()
                } else {
                    null
                }
                val result = chain.proceed()
                if (sbn != null) {
                    module.log("HyperBridge: open-app cleanup callback key=${sbn.key}")
                    runCatching { SystemUiDispatcher.cancelClickedOwnedIsland(sbn) }
                        .onFailure {
                            module.log("HyperBridge: open-app island cleanup skipped: ${it.message}")
                        }
                }
                result
            }
            active = true
            module.log("HyperBridge: hooked DynamicIsland open-app cleanup loader=${loader.hashCode()}")
        }.onFailure {
            loaders.remove(loader)
            module.log("HyperBridge: DynamicIsland click cleanup unavailable loader=${loader.hashCode()}: ${it.message}")
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
