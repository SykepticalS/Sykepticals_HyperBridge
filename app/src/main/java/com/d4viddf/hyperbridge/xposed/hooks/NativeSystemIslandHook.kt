package com.d4viddf.hyperbridge.xposed.hooks

import android.app.BroadcastOptions
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.service.NativeSystemIslandEvent
import com.d4viddf.hyperbridge.service.NativeSystemIslandPolicy
import com.d4viddf.hyperbridge.service.PermanentIslandManager
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * HyperOS device islands (charging, bluetooth, …) are ShowOnce overlays from
 * [DeviceNotificationListenerImpl.handleDeviceNotification]. They dismiss the current island,
 * play, then restore it.
 *
 * When only HyperBridge's permanent stub is on screen, skip that overlay and tell the engine
 * to update 9999 in place instead — the keep-island charging-profile pattern from HyperIsland.
 * Occupied or extra bridged islands keep the native dismiss/restore path.
 */
object NativeSystemIslandHook {
    private val DEVICE_NOTIFICATION_CLASS_NAMES = listOf(
        "com.android.systemui.devicenotification.listener.DeviceNotificationListenerImpl",
        "com.android.systemui.statusbar.notification.DeviceNotificationListenerImpl",
    )
    private val TITLE_KEYS = listOf(
        "left", "leftText", "title", "status", "contentTitle", "text",
    )
    private val RIGHT_KEYS = listOf(
        "right", "rightText", "levelText", "percent", "battery", "content", "subText",
    )
    private val loaders = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>()))
    private val hookedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    @Volatile private var active = false
    @Volatile private var powerReceiverRegistered = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        hook(module, param.defaultClassLoader)
        DynamicClassLoaderHooks.observe(module, param.defaultClassLoader) { hook(module, it) }
        registerPowerDisconnect(module, param.defaultClassLoader)
    }

    fun isActive(): Boolean = active

    private fun hook(module: XposedModule, loader: ClassLoader) {
        if (!loaders.add(loader)) return
        val clazz = DEVICE_NOTIFICATION_CLASS_NAMES.firstNotNullOfOrNull { name ->
            runCatching { loader.loadClass(name) }.getOrNull()
        }
        if (clazz == null) {
            loaders.remove(loader)
            return
        }
        val methods = clazz.declaredMethods.filter { method ->
            method.name == "handleDeviceNotification" &&
                method.parameterTypes.firstOrNull() == Bundle::class.java
        }
        if (methods.isEmpty()) {
            loaders.remove(loader)
            module.log("HyperBridge: native system island handleDeviceNotification missing in ${clazz.name}")
            return
        }
        methods.forEach { method ->
            synchronized(hookedMethods) {
                if (!hookedMethods.add(method)) return@forEach
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val bundle = chain.args.getOrNull(0) as? Bundle
                val notifyId = notifyId(bundle)
                if (notifyId.isNullOrBlank()) return@intercept chain.proceed()
                val context = currentApplication() ?: return@intercept chain.proceed()
                if (!shouldAbsorb(context)) return@intercept chain.proceed()
                val event = eventFrom(chain.thisObject, bundle, notifyId)
                runCatching { dispatch(context, event) }.onFailure {
                    return@intercept chain.proceed()
                }
                null
            }
        }
        active = true
        module.log("HyperBridge: native system island hooked ${methods.size} device notification methods")
    }

    private fun registerPowerDisconnect(module: XposedModule, loader: ClassLoader) {
        if (powerReceiverRegistered) return
        runCatching {
            val method = loader.loadClass("android.app.Application").getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val context = chain.thisObject as? Context ?: return@intercept result
                if (context.packageName != IslandProtocol.SYSTEM_UI_PACKAGE) return@intercept result
                registerPowerReceiver(context)
                result
            }
        }
        currentApplication()?.let(::registerPowerReceiver)
    }

    private fun registerPowerReceiver(context: Context) {
        if (powerReceiverRegistered) return
        val app = context.applicationContext ?: context
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                if (intent?.action != Intent.ACTION_POWER_DISCONNECTED) return
                if (!shouldAbsorb(context)) return
                dispatch(
                    context,
                    NativeSystemIslandEvent(
                        notifyId = "charge",
                        left = "",
                        right = "",
                        durationMs = 0L,
                        hide = true,
                    ),
                )
            }
        }
        val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                app.registerReceiver(receiver, filter)
            }
        }.isSuccess
        if (registered) powerReceiverRegistered = true
    }

    private fun shouldAbsorb(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val owned = manager.activeNotifications.orEmpty().filter { sbn ->
            sbn.packageName == IslandProtocol.SYSTEM_UI_PACKAGE &&
                sbn.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
        }
        val permanent = owned.firstOrNull { it.id == PermanentIslandManager.PERMANENT_BRIDGE_ID }
        return NativeSystemIslandPolicy.shouldAbsorbFromPosted(
            permanentPosted = permanent != null,
            permanentSemanticType = permanent?.notification?.extras
                ?.getString(IslandProtocol.EXTRA_SEMANTIC_TYPE),
            extraOwnedIslandCount = owned.count { it.id != PermanentIslandManager.PERMANENT_BRIDGE_ID },
        )
    }

    private fun eventFrom(listener: Any?, bundle: Bundle?, notifyId: String): NativeSystemIslandEvent {
        val duration = NativeSystemIslandPolicy.durationMs(bundleDuration(bundle))
        val hide = NativeSystemIslandPolicy.isHide(duration, explicitHide = isChargeHide(bundle, notifyId))
        val model = if (notifyId.equals("charge", ignoreCase = true) && listener != null) {
            invokeChargeModel(listener, bundle)
        } else {
            null
        }
        val left = sideText(model, left = true)
            .ifBlank { bundleString(bundle, TITLE_KEYS) }
            .ifBlank { prettyNotifyId(notifyId) }
        val right = sideText(model, left = false)
            .ifBlank { bundleString(bundle, RIGHT_KEYS) }
        return NativeSystemIslandEvent(
            notifyId = notifyId,
            left = left,
            right = right,
            durationMs = duration,
            hide = hide,
        )
    }

    private fun dispatch(context: Context, event: NativeSystemIslandEvent) {
        val extras = Bundle().apply {
            putInt(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            event.putExtras(this)
        }
        val intent = Intent(IslandProtocol.ACTION_NATIVE_SYSTEM_ISLAND).apply {
            setPackage(IslandProtocol.APP_PACKAGE)
            replaceExtras(extras)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendBroadcast(intent, null, options)
        } else {
            @Suppress("DEPRECATION")
            context.sendBroadcast(intent)
        }
    }

    private fun notifyId(bundle: Bundle?): String? {
        if (bundle == null) return null
        return sequenceOf("notifyId", "id", "islandId")
            .mapNotNull { bundle.getString(it)?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
    }

    private fun bundleDuration(bundle: Bundle?): Long? {
        if (bundle == null) return null
        if (bundle.containsKey("duration")) return bundle.getLong("duration", -1L)
        if (bundle.containsKey("showDuration")) return bundle.getLong("showDuration", -1L)
        return null
    }

    private fun isChargeHide(bundle: Bundle?, notifyId: String): Boolean {
        if (!notifyId.equals("charge", ignoreCase = true) || bundle == null) return false
        return booleanExtra(bundle, "charging") == false ||
            booleanExtra(bundle, "isCharging") == false ||
            booleanExtra(bundle, "plugged") == false
    }

    @Suppress("DEPRECATION")
    private fun booleanExtra(bundle: Bundle, key: String): Boolean? {
        if (!bundle.containsKey(key)) return null
        return when (val value = bundle.get(key)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", ignoreCase = true) || value == "1"
            else -> null
        }
    }

    private fun bundleString(bundle: Bundle?, keys: List<String>): String {
        if (bundle == null) return ""
        keys.forEach { key ->
            bundle.getString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            bundle.getCharSequence(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        if (bundle.containsKey("level")) {
            val level = bundle.getInt("level", Int.MIN_VALUE)
            if (level in 0..100) return "$level%"
        }
        return ""
    }

    private fun prettyNotifyId(notifyId: String): String =
        notifyId.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun invokeChargeModel(listener: Any, bundle: Bundle?): Any? {
        val method = listener.javaClass.declaredMethods.firstOrNull { it.name == "structModelForCharge" }
            ?: return null
        method.isAccessible = true
        val args = method.parameterTypes.map { type ->
            when {
                type == Bundle::class.java -> bundle
                type == java.lang.Boolean.TYPE -> false
                type == java.lang.Integer.TYPE -> 0
                type == java.lang.Long.TYPE -> 0L
                type == java.lang.Float.TYPE -> 0f
                else -> null
            }
        }.toTypedArray()
        return runCatching { method.invoke(listener, *args) }.getOrNull()
    }

    private fun sideText(model: Any?, left: Boolean): String {
        if (model == null) return ""
        val side = IslandHookReflection.invokeNoArg(model, if (left) "getLeft" else "getRight") ?: return ""
        val textParams = IslandHookReflection.invokeNoArg(side, "getTextParams") ?: return ""
        val text = IslandHookReflection.invokeNoArg(textParams, "getText")?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) return text
        val title = IslandHookReflection.invokeNoArg(textParams, "getTitle")?.toString()?.trim().orEmpty()
        val content = IslandHookReflection.invokeNoArg(textParams, "getContent")?.toString()?.trim().orEmpty()
        return title.ifBlank { content }
    }

    private fun currentApplication(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()
}
