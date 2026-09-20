package com.d4viddf.hyperbridge.xposed.hooks

import android.app.Application
import android.content.Context
import com.d4viddf.hyperbridge.xposed.dispatch.SystemUiDispatcher
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

object SystemUiBootstrapHook {
    @Volatile private var installed = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        if (installed) return
        installed = true
        runCatching {
            registerCurrentApplication(module)
            val application = param.defaultClassLoader.loadClass("android.app.Application")
            val attach = application.getDeclaredMethod("attach", Context::class.java).apply { isAccessible = true }
            module.hook(attach).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Application)?.let { SystemUiDispatcher.register(it, module) }
                result
            }
            val onCreate = application.getDeclaredMethod("onCreate")
            module.hook(onCreate).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Application)?.let { SystemUiDispatcher.register(it, module) }
                result
            }
        }.onFailure {
            installed = false
            module.log("HyperBridge: SystemUI bootstrap hook failed: ${it.message}")
        }
    }

    private fun registerCurrentApplication(module: XposedModule) {
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentApplication").apply { isAccessible = true }
            (method.invoke(null) as? Application)?.let { SystemUiDispatcher.register(it, module) }
        }.onFailure {
            module.log("HyperBridge: current SystemUI application unavailable: ${it.message}")
        }
    }
}
