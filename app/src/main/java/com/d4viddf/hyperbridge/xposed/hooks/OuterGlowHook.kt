package com.d4viddf.hyperbridge.xposed.hooks

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Enables Xiaomi's shader path; HyperBridge's existing payload remains the source of glow flags/colors. */
object OuterGlowHook {
    private const val FEATURE = "miui.systemui.dynamicisland.DynamicFeatureConfig"
    private val loaders = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))

    fun install(module: XposedModule, param: PackageLoadedParam) {
        enable(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { enable(module, it) }
    }

    private fun enable(module: XposedModule, loader: ClassLoader) {
        if (!loaders.add(loader)) return
        runCatching {
            val method = loader.loadClass(FEATURE).declaredMethods.singleOrNull {
                it.name == "getFEATURE_DYNAMIC_ISLAND_SHADER" && it.parameterCount == 0 &&
                    it.returnType == Boolean::class.javaPrimitiveType
            } ?: error("shader feature method missing")
            module.hook(method).intercept { true }
        }.onFailure { loaders.remove(loader) }
    }
}
