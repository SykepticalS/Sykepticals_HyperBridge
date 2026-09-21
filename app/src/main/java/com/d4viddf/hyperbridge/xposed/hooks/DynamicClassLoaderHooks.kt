package com.d4viddf.hyperbridge.xposed.hooks

import android.content.Context
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

object DynamicClassLoaderHooks {
    private const val FACTORY = "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory"
    private val pluginFactories = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))
    private val callbacks = CopyOnWriteArrayList<(ClassLoader) -> Unit>()
    @Volatile private var dexHooksInstalled = false

    fun observe(module: XposedModule, classLoader: ClassLoader, callback: (ClassLoader) -> Unit) {
        callbacks += callback
        hookPluginFactory(module, classLoader)
        hookDexLoaders(module, classLoader)
    }

    private fun dispatch(loader: ClassLoader) {
        callbacks.forEach { callback -> runCatching { callback(loader) } }
    }

    private fun hookPluginFactory(module: XposedModule, classLoader: ClassLoader) {
        if (!pluginFactories.add(classLoader)) return
        runCatching {
            val factory = classLoader.loadClass(FACTORY)
            factory.declaredMethods.filter { it.name == "createPluginContext" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (result as? Context)?.classLoader?.let(::dispatch)
                    result
                }
            }
        }.onFailure { module.log("HyperBridge: plugin classloader hook unavailable: ${it.message}") }
    }

    private fun hookDexLoaders(module: XposedModule, classLoader: ClassLoader) {
        if (dexHooksInstalled) return
        synchronized(this) {
            if (dexHooksInstalled) return
            dexHooksInstalled = true
        }
        val names = arrayOf(
            "dalvik.system.BaseDexClassLoader",
            "dalvik.system.PathClassLoader",
            "dalvik.system.DexClassLoader",
            "dalvik.system.DelegateLastClassLoader",
        )
        for (name in names) {
            runCatching {
                val clazz = Class.forName(name, false, classLoader)
                clazz.declaredConstructors.forEach { constructor ->
                    runCatching {
                        module.hook(constructor).intercept { chain ->
                            val result = chain.proceed()
                            (chain.thisObject as? ClassLoader)?.let(::dispatch)
                            result
                        }
                    }
                }
            }
        }
    }
}
