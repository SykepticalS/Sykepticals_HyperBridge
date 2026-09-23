package com.d4viddf.hyperbridge.xposed.hooks

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.IncomingCallBannerPolicy
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field

object HeadsUpSuppressionHook {
    private const val ENTRY = "com.android.systemui.statusbar.notification.collection.NotificationEntry"
    private const val OS4 = "com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionProviderImplInjector"
    private const val OS4_OLD = "com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImplInjectorImpl"
    private const val OS3 = "com.android.systemui.statusbar.notification.interruption.NotificationInterruptStateProviderImpl"
    private const val OS4_PROVIDER = "com.android.systemui.statusbar.notification.interruption.VisualInterruptionDecisionProviderImpl"
    private const val ACTIVITY_STARTER = "com.android.systemui.statusbar.phone.StatusBarNotificationActivityStarter"
    @Volatile private var sbnField: Field? = null
    @Volatile private var oldInjectorField: Field? = null
    @Volatile private var contextField: Field? = null
    @Volatile private var systemContext: Context? = null
    @Volatile private var active = false

    fun isActive(): Boolean = active

    fun install(module: XposedModule, param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        val entry = runCatching { loader.loadClass(ENTRY) }.getOrElse {
            module.log("HyperBridge: NotificationEntry unavailable; heads-up suppression disabled")
            return
        }
        sbnField = findField(entry, "mSbn")
        rememberContext(currentApplication())
        hookFullScreenLaunch(module, loader)
        val os4 = runCatching {
            val injector = loader.loadClass(OS4)
            val old = loader.loadClass(OS4_OLD)
            oldInjectorField = findField(injector, "oldInjector")
            contextField = findField(old, "context")
            val method = injector.getDeclaredMethod("shouldPeek", entry)
            module.hook(method).intercept { chain ->
                val sbn = source(chain.args.firstOrNull())
                if (sbn != null && interactive(chain.thisObject) == true) {
                    module.log("HyperBridge: suppressed source heads-up platform=OS4 key=${sbn.key}")
                    false
                } else {
                    chain.proceed()
                }
            }
            module.log("HyperBridge: hooked OS4 source heads-up suppression")
        }.isSuccess
        if (os4) {
            active = true
            return
        }
        runCatching {
            val provider = loader.loadClass(OS3)
            val method = provider.getDeclaredMethod("checkHeadsUp", entry, Boolean::class.javaPrimitiveType!!)
            module.hook(method).intercept { chain ->
                val sbn = source(chain.args.firstOrNull())
                if (sbn != null && deviceInteractive() == true) {
                    module.log("HyperBridge: suppressed source heads-up platform=OS3 key=${sbn.key}")
                    false
                } else {
                    chain.proceed()
                }
            }
            module.log("HyperBridge: hooked OS3 source heads-up suppression")
            active = true
        }.onFailure { module.log("HyperBridge: no compatible heads-up hook; failing open: ${it.message}") }
    }

    private fun source(entry: Any?): StatusBarNotification? {
        val sbn = runCatching { sbnField?.get(entry) as? StatusBarNotification }.getOrNull() ?: return null
        if (sbn.packageName == "com.android.systemui") return null
        if (!HookConfig.suppressSourceHeadsUp()) return null
        val notification = sbn.notification ?: return null
        val marked = notification.extras.getBoolean(IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP, false) ||
            SystemUiNotificationIngressHook.hasReplacedGroupChild(sbn)
        val engineMarked = notification.extras.getBoolean(IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP, false)
        val incomingBanner = notification.fullScreenIntent != null &&
            (engineMarked || HookConfig.expectsIncomingCallBannerSuppression(sbn))
        if (notification.fullScreenIntent != null && !incomingBanner) return null
        if (!marked && !incomingBanner) return null
        return sbn
    }

    private fun hookFullScreenLaunch(module: XposedModule, loader: ClassLoader) {
        listOf(OS4, OS4_OLD, OS3, OS4_PROVIDER, ACTIVITY_STARTER).forEach { name ->
            val type = runCatching { loader.loadClass(name) }.getOrNull() ?: return@forEach
            type.methods.filter { method ->
                method.name == "shouldLaunchFullScreenIntentWhenAdded" &&
                    method.returnType == Boolean::class.javaPrimitiveType
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val sbn = chain.args.firstNotNullOfOrNull { source(it) }
                    if (sbn?.notification?.fullScreenIntent != null && inUnlockedApp()) {
                        module.log("HyperBridge: kept incoming call full-screen intent parked key=${sbn.key}")
                        false
                    } else {
                        chain.proceed()
                    }
                }
            }
            type.methods.filter { method ->
                method.name == "launchFullScreenIntent" &&
                    method.parameterTypes.firstOrNull()?.name == ENTRY
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val sbn = chain.args.firstNotNullOfOrNull { source(it) }
                    if (sbn?.notification?.fullScreenIntent != null && inUnlockedApp()) {
                        module.log("HyperBridge: skipped in-app incoming call full-screen launch key=${sbn.key}")
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }
        }
    }

    private fun interactive(injector: Any?): Boolean? = runCatching {
        val old = oldInjectorField?.get(injector) ?: return@runCatching null
        val context = contextField?.get(old) as? Context ?: return@runCatching null
        rememberContext(context)
        context.getSystemService(PowerManager::class.java)?.isInteractive
    }.getOrNull()

    private fun deviceInteractive(): Boolean? {
        val context = systemContext ?: currentApplication() ?: return null
        rememberContext(context)
        return context.getSystemService(PowerManager::class.java)?.isInteractive
    }

    /** True only while the person is on the home screen or in an app, not on the lock screen. */
    private fun inUnlockedApp(): Boolean {
        val interactive = deviceInteractive()
        val context = systemContext ?: return false
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked
        return IncomingCallBannerPolicy.inUnlockedApp(interactive, locked)
    }

    private fun rememberContext(context: Context?) {
        if (context == null) return
        if (systemContext == null) {
            systemContext = context.applicationContext
            HookConfig.refreshCallKeywords(context)
        }
    }

    private fun currentApplication(): Context? = runCatching {
        val thread = Class.forName("android.app.ActivityThread")
        thread.getMethod("currentApplication").invoke(null) as? Context
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
