package com.d4viddf.hyperbridge.xposed.hooks

import android.content.Context
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import java.util.Collections
import java.util.WeakHashMap

object DynamicClassLoaderHooks {
    private const val FACTORY = "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory"
    private val installed = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))

    fun observe(module: XposedModule, classLoader: ClassLoader, callback: (ClassLoader) -> Unit) {
        if (!installed.add(classLoader)) return
        runCatching {
            val factory = classLoader.loadClass(FACTORY)
            factory.declaredMethods.filter { it.name == "createPluginContext" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    (result as? Context)?.classLoader?.let(callback)
                    result
                }
            }
        }.onFailure { module.log("HyperBridge: plugin classloader hook unavailable: ${it.message}") }
    }
}
