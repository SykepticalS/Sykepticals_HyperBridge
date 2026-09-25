package com.d4viddf.hyperbridge.xposed.hooks

import com.d4viddf.hyperbridge.xposed.HookConfig
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

object FocusWhitelistHook {
    private const val SETTINGS = "miui.systemui.notification.NotificationSettingsManager"
    private const val SIGNATURE = "miui.systemui.notification.focus.SignatureChecker"
    private val loaders = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))
    @Volatile private var active = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { hook(module, it) }
    }

    fun isActive(): Boolean = active

    private fun hook(module: XposedModule, loader: ClassLoader): Boolean {
        if (!loaders.add(loader)) return true
        var found = false
        runCatching {
            loader.loadClass(SETTINGS).declaredMethods.filter {
                (it.name == "canShowFocus" || it.name == "canCustomFocus") && it.returnType == Boolean::class.javaPrimitiveType
            }.forEach { method ->
                module.hook(method).intercept { chain -> if (HookConfig.focusEnabled()) true else chain.proceed() }
                found = true
            }
        }
        runCatching {
            loader.loadClass(SIGNATURE).declaredMethods.filter {
                it.name == "checkSignatures" && it.returnType == Boolean::class.javaPrimitiveType
            }.forEach { method ->
                module.hook(method).intercept { chain -> if (HookConfig.focusEnabled()) true else chain.proceed() }
                found = true
            }
        }
        if (found) active = true else loaders.remove(loader)
        return found
    }
}
