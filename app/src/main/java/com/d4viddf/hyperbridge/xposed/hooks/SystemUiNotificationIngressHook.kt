package com.d4viddf.hyperbridge.xposed.hooks

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.processing.INotificationProcessingService
import com.d4viddf.hyperbridge.service.NotificationProcessingService
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/** HyperIsland-style source intake hosted by SystemUI before Xiaomi snapshots its extras. */
object SystemUiNotificationIngressHook {
    private const val LISTENER = "com.android.systemui.statusbar.notification.MiuiNotificationListener"
    private const val MIUI_NOTIF_UTIL = "com.miui.systemui.notification.MiuiBaseNotifUtil"

    private val activeSources = ConcurrentHashMap<String, StatusBarNotification>()
    @Volatile private var listener = WeakReference<Any>(null)
    @Volatile private var processor: INotificationProcessingService? = null
    @Volatile private var binding = false
    @Volatile private var systemUiContext = WeakReference<Context>(null)
    @Volatile private var logger = WeakReference<XposedModule>(null)
    @Volatile private var active = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            processor = INotificationProcessingService.Stub.asInterface(service)
            binding = false
            reconcileRemote()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            processor = null
            binding = false
        }

        override fun onBindingDied(name: ComponentName?) {
            processor = null
            binding = false
            systemUiContext.get()?.let { connect(it, logger.get()) }
        }
    }

    fun isActive(): Boolean = active

    fun reloadEngine() {
        runCatching { processor?.reload() }
    }

    fun connect(context: Context, module: XposedModule?) {
        systemUiContext = WeakReference(context.applicationContext)
        if (module != null) logger = WeakReference(module)
        if (processor != null || binding) return
        synchronized(this) {
            if (processor != null || binding) return
            binding = true
            val intent = Intent().setClassName(
                IslandProtocol.APP_PACKAGE,
                "com.d4viddf.hyperbridge.service.NotificationProcessingService",
            )
            val bound = runCatching {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.onFailure {
                module?.log("HyperBridge: notification processor bind failed open: ${it.message}")
            }.getOrDefault(false)
            if (!bound) {
                binding = false
                module?.log("HyperBridge: notification processor service was unavailable")
            }
        }
    }

    fun install(module: XposedModule, param: PackageLoadedParam) {
        val loader = param.defaultClassLoader
        runCatching {
            val listenerClass = loader.loadClass(LISTENER)

            module.hook(listenerClass.getDeclaredMethod("onListenerConnected")).intercept { chain ->
                listener = WeakReference(chain.thisObject)
                val result = chain.proceed()
                snapshotActive(chain.thisObject)
                connectFromCurrentContext(module)
                reconcileRemote()
                result
            }

            val posted = listenerClass.declaredMethods.first {
                it.name == "onNotificationPosted" && it.parameterTypes.firstOrNull() == StatusBarNotification::class.java
            }
            module.hook(posted).intercept { chain ->
                listener = WeakReference(chain.thisObject)
                (chain.args.firstOrNull() as? StatusBarNotification)?.let { activeSources[it.key] = it }
                chain.proceed()
            }

            val removed = listenerClass.declaredMethods.first {
                it.name == "onNotificationRemoved" && it.parameterTypes.firstOrNull() == StatusBarNotification::class.java &&
                    it.parameterTypes.lastOrNull() == Int::class.javaPrimitiveType
            }
            module.hook(removed).intercept { chain ->
                listener = WeakReference(chain.thisObject)
                val result = chain.proceed()
                val sbn = chain.args[0] as? StatusBarNotification
                if (sbn != null) {
                    activeSources.computeIfPresent(sbn.key) { _, current ->
                        current.takeUnless { sameGeneration(it, sbn) }
                    }
                    processRemoved(sbn, chain.args.lastOrNull() as? Int ?: 0, module)
                }
                result
            }
            module.log("HyperBridge: hooked MiuiNotificationListener posted/removed lifecycle")

            val util = loader.loadClass(MIUI_NOTIF_UTIL)
            val generate = util.getDeclaredMethod(
                "generateInnerNotifBean",
                StatusBarNotification::class.java,
            )
            module.hook(generate).intercept { chain ->
                val sbn = chain.args.firstOrNull() as? StatusBarNotification
                if (sbn != null) {
                    val isProxy = sbn.packageName == IslandProtocol.SYSTEM_UI_PACKAGE &&
                        sbn.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
                    if (!isProxy) {
                        // Notification instances can be reused; success in a previous pass must
                        // never authorize suppression in this pass.
                        sbn.notification.extras.remove(IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP)
                        activeSources[sbn.key] = sbn
                        val posted = processPosted(sbn, module)
                        if (posted && sbn.notification.fullScreenIntent == null) {
                            sbn.notification.extras.putBoolean(
                                IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP,
                                true,
                            )
                        }
                    }
                }
                chain.proceed()
            }
            active = true
            module.log("HyperBridge: hooked MiuiBaseNotifUtil.generateInnerNotifBean before extras snapshot")
        }.onFailure {
            active = false
            module.log("HyperBridge: SystemUI notification ingress hook failed open: ${it.message}")
        }
    }

    private fun processPosted(sbn: StatusBarNotification, module: XposedModule): Boolean {
        val remote = processor ?: run {
            connectFromCurrentContext(module)
            return false
        }
        return runCatching {
            remote.processPosted(Bundle().apply {
                putParcelable(NotificationProcessingService.KEY_NOTIFICATION, sbn)
            })
        }.onFailure {
            module.log("HyperBridge: notification processing failed open: ${it.message}")
        }.getOrDefault(false)
    }

    private fun processRemoved(sbn: StatusBarNotification, reason: Int, module: XposedModule) {
        val remote = processor ?: run {
            connectFromCurrentContext(module)
            return
        }
        runCatching {
            remote.processRemoved(Bundle().apply {
                putParcelable(NotificationProcessingService.KEY_NOTIFICATION, sbn)
                putInt(NotificationProcessingService.KEY_REASON, reason)
            })
        }.onFailure {
            module.log("HyperBridge: notification removal processing failed open: ${it.message}")
        }
    }

    private fun reconcileRemote() {
        val remote = processor ?: return
        runCatching {
            remote.reconcile(Bundle().apply {
                putParcelableArrayList(
                    NotificationProcessingService.KEY_NOTIFICATIONS,
                    ArrayList(activeSources.values),
                )
            })
        }.onFailure {
            logger.get()?.log("HyperBridge: active notification reconciliation failed open: ${it.message}")
        }
    }

    private fun connectFromCurrentContext(module: XposedModule) {
        val context = systemUiContext.get() ?: currentApplication() ?: return
        connect(context, module)
    }

    private fun snapshotActive(target: Any?) {
        val values = runCatching {
            target?.javaClass?.getMethod("getActiveNotifications")?.invoke(target) as? Array<*>
        }.getOrNull().orEmpty()
        activeSources.clear()
        values.filterIsInstance<StatusBarNotification>().forEach { activeSources[it.key] = it }
    }

    fun cancelSource(key: String) {
        val target = listener.get() ?: return
        runCatching { target.javaClass.getMethod("cancelNotification", String::class.java).invoke(target, key) }
    }

    private fun currentApplication(): android.app.Application? = runCatching {
        val thread = Class.forName("android.app.ActivityThread")
        thread.getDeclaredMethod("currentApplication").apply { isAccessible = true }
            .invoke(null) as? android.app.Application
    }.getOrNull()

    private fun sameGeneration(left: StatusBarNotification, right: StatusBarNotification): Boolean =
        left.key == right.key && left.postTime == right.postTime
}
