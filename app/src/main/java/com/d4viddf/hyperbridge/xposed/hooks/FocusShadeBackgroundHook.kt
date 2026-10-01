package com.d4viddf.hyperbridge.xposed.hooks

import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.xposed.FocusShadeBackgroundPolicy
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/** Makes Xiaomi's row-background selector treat Focus rows exactly like ordinary rows. */
object FocusShadeBackgroundHook {
    private const val INJECTOR =
        "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector"
    private const val FOCUS_FIELD = "mIsFocusNotification"

    private val loggedKeys = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var active = false

    fun isActive(): Boolean = active

    fun install(module: XposedModule, param: PackageLoadedParam) {
        runCatching {
            val injector = param.defaultClassLoader.loadClass(INJECTOR)
            val updateBackground = injector.getDeclaredMethod("updateBackground\$1")
            module.hook(updateBackground).intercept { chain ->
                val sbn = notificationFromInjector(chain.thisObject)
                    ?: return@intercept chain.proceed()
                val focusField = findField(sbn.javaClass, FOCUS_FIELD)
                    ?: return@intercept chain.proceed()
                val isFocus = runCatching { focusField.getBoolean(sbn) }.getOrDefault(false)
                if (!FocusShadeBackgroundPolicy.shouldUseRegularRowSelector(isFocus)) {
                    return@intercept chain.proceed()
                }

                // Xiaomi branches on this field to choose notification_focus_* drawables. The
                // flag is suppressed only for the synchronous background-selection call so all
                // other Focus behavior and content remain untouched.
                focusField.setBoolean(sbn, false)
                try {
                    chain.proceed().also {
                        if (loggedKeys.add(sbn.key)) {
                            module.log(
                                "HyperBridge: applied regular shade background to Focus row ${sbn.key}",
                            )
                        }
                    }
                } finally {
                    focusField.setBoolean(sbn, true)
                }
            }
            active = true
            module.log("HyperBridge: hooked Xiaomi Focus row background selector")
        }.onFailure {
            module.log("HyperBridge: Focus row background hook unavailable; failing open: ${it.message}")
        }
    }

    private fun notificationFromInjector(injector: Any?): StatusBarNotification? {
        val view = IslandHookReflection.readField(injector ?: return null, "view") ?: return null
        val entry = IslandHookReflection.invokeNoArg(view, "getEntry")
            ?: IslandHookReflection.readField(view, "mEntry")
            ?: return null
        return IslandHookReflection.readField(entry, "mSbn") as? StatusBarNotification
            ?: IslandHookReflection.invokeNoArg(entry, "getSbn") as? StatusBarNotification
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            val field = runCatching { current.getDeclaredField(name) }.getOrNull()
            if (field != null) return field.apply { isAccessible = true }
            current = current.superclass
        }
        return null
    }
}
