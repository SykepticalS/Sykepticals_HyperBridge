package com.d4viddf.hyperbridge.xposed.hooks

import android.content.Context
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field

object HeadsUpSuppressionHook {
    private const val ENTRY = "com.android.systemui.statusbar.notification.collection.NotificationEntry"
    private const val OS4 = "com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionProviderImplInjector"
    private const val OS4_OLD = "com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImplInjectorImpl"
    private const val OS3 = "com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl"
    @Volatile private var sbnField: Field? = null
    @Volatile private var oldInjectorField: Field? = null
    @Volatile private var contextField: Field? = null
    @Volatile private var active = false

    fun isActive(): Boolean = active

    fun install(module: XposedModule, param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        val entry = runCatching { loader.loadClass(ENTRY) }.getOrElse {
            module.log("HyperBridge: NotificationEntry unavailable; heads-up suppression disabled")
            return
        }
        sbnField = findField(entry, "mSbn")
        val os4 = runCatching {
            val injector = loader.loadClass(OS4)
            val old = loader.loadClass(OS4_OLD)
            oldInjectorField = findField(injector, "oldInjector")
            contextField = findField(old, "context")
            val method = injector.getDeclaredMethod("shouldPeek", entry)
            module.hook(method).intercept { chain ->
                val sbn = source(chain.args.firstOrNull())
                if (sbn != null && interactive(chain.thisObject) == true) false else chain.proceed()
            }
        }.isSuccess
        if (os4) {
            active = true
            return
        }
        runCatching {
            val provider = loader.loadClass(OS3)
            val method = provider.getDeclaredMethod("checkHeadsUp", entry, Boolean::class.javaPrimitiveType!!)
            module.hook(method).intercept { chain ->
                if (source(chain.args.firstOrNull()) != null) false else chain.proceed()
            }
            active = true
        }.onFailure { module.log("HyperBridge: no compatible heads-up hook; failing open: ${it.message}") }
    }

    private fun source(entry: Any?): StatusBarNotification? {
        val sbn = runCatching { sbnField?.get(entry) as? StatusBarNotification }.getOrNull() ?: return null
        if (sbn.packageName == "com.android.systemui" || sbn.notification?.fullScreenIntent != null) return null
        return sbn.takeIf(HookConfig::expectsReplacement)
    }

    private fun interactive(injector: Any?): Boolean? = runCatching {
        val old = oldInjectorField?.get(injector) ?: return@runCatching null
        val context = contextField?.get(old) as? Context ?: return@runCatching null
        context.getSystemService(PowerManager::class.java)?.isInteractive
    }.getOrNull()

    private fun findField(type: Class<*>, name: String): Field {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { return current.getDeclaredField(name).apply { isAccessible = true } }
            current = current.superclass
        }
        throw NoSuchFieldException("$name in ${type.name}")
    }
}
