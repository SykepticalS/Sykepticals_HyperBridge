package com.d4viddf.hyperbridge.xposed.hooks

import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

object XmsfFocusAuthHook {
    private const val AUTH_SESSION = "com.xiaomi.xms.auth.AuthSession"

    fun install(module: XposedModule, param: PackageLoadedParam): Boolean {
        val type = try {
            param.defaultClassLoader.loadClass(AUTH_SESSION)
        } catch (_: ClassNotFoundException) {
            // Global XMSF builds (for example 6.2.x-G) omit the Xiaomi Focus
            // authorization subsystem entirely. There is no rejection path to bypass,
            // but the injected process must still participate in health handshakes.
            module.log("HyperBridge: XMSF Focus auth gate is absent; compatible no-op")
            return true
        }
        return runCatching {
            val method = type.declaredMethods.firstOrNull { it.name == "b" && it.parameterCount == 1 }
                ?: error("unique AuthSession.b(error) signature not found")
            module.hook(method).intercept { chain ->
                val error = chain.args.firstOrNull()
                if (!HookConfig.focusEnabled() || error == null) return@intercept chain.proceed()
                runCatching {
                    findField(error.javaClass, "a").set(error, 0)
                    findNoArg(chain.thisObject?.javaClass ?: error("missing session"), "h")
                        .invoke(chain.thisObject)
                }.getOrElse { chain.proceed() }
            }
            true
        }.getOrElse {
            module.log("HyperBridge: XMSF Focus auth hook unavailable: ${it.message}")
            false
        }
    }

    private fun findField(type: Class<*>, name: String): java.lang.reflect.Field {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { return current.getDeclaredField(name).apply { isAccessible = true } }
            current = current.superclass
        }
        throw NoSuchFieldException(name)
    }

    private fun findNoArg(type: Class<*>, name: String): java.lang.reflect.Method {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }?.let {
                return it.apply { isAccessible = true }
            }
            current = current.superclass
        }
        throw NoSuchMethodException(name)
    }
}
