package com.sykeptical.hyperpop.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.UserManager
import android.service.notification.StatusBarNotification
import com.sykeptical.hyperpop.island.backend.IslandProtocol
import com.sykeptical.hyperpop.island.backend.SystemUiIslandBackend
import com.sykeptical.hyperpop.processing.INotificationProcessingService
import com.sykeptical.hyperpop.processing.IIslandDispatcher
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the notification semantics engine in HyperPop's process, where its Room database,
 * preferences and application resources are valid. SystemUI remains the sole source
 * intake and calls here before Xiaomi snapshots the source. The attached return Binder posts
 * directly as SystemUI; the Boolean result marks SystemUI's original SBN before it proceeds.
 */
class NotificationProcessingService : Service() {
    private val activeSources = ConcurrentHashMap<String, StatusBarNotification>()
    private lateinit var engine: NotificationProcessingEngine
    private var unlockReceiver: BroadcastReceiver? = null
    @Volatile private var pendingPreserveVisibleIslands = false

    private val binder = object : INotificationProcessingService.Stub() {
        override fun attachDispatcher(dispatcher: IIslandDispatcher?) {
            enforceSystemUiCaller()
            SystemUiIslandBackend.get(this@NotificationProcessingService).attachDispatcher(dispatcher)
        }

        override fun processPosted(request: Bundle?): Boolean {
            enforceSystemUiCaller()
            val posted = request ?: return false
            val sbn = posted.statusBarNotification() ?: return false
            sbn.notification.extras.remove(IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP)
            activeSources[sbn.key] = sbn
            if (!::engine.isInitialized) return false
            engine.onNotificationPosted(sbn)
            sbn.notification.extras.getBundle(IslandProtocol.EXTRA_SOURCE_FOCUS_DECORATION)?.let { decoration ->
                posted.putBundle(IslandProtocol.EXTRA_SOURCE_FOCUS_DECORATION, decoration)
                sbn.notification.extras.remove(IslandProtocol.EXTRA_SOURCE_FOCUS_DECORATION)
            }
            if (sbn.notification.extras.getBoolean(IslandProtocol.EXTRA_CALL_SHADE_DISMISSIBLE, false)) {
                posted.putBoolean(IslandProtocol.EXTRA_CALL_SHADE_DISMISSIBLE, true)
                sbn.notification.extras.remove(IslandProtocol.EXTRA_CALL_SHADE_DISMISSIBLE)
            }
            return sbn.notification.extras.getBoolean(
                IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP,
                false,
            )
        }

        override fun processRemoved(request: Bundle?) {
            enforceSystemUiCaller()
            val sbn = request?.statusBarNotification() ?: return
            activeSources.computeIfPresent(sbn.key) { _, current ->
                current.takeUnless { it.postTime == sbn.postTime }
            }
            if (!::engine.isInitialized) return
            engine.onNotificationRemoved(sbn, request.getInt(KEY_REASON, 0))
        }

        override fun reconcile(request: Bundle?) {
            enforceSystemUiCaller()
            val snapshot = request?.statusBarNotifications().orEmpty()
            activeSources.clear()
            snapshot.forEach { activeSources[it.key] = it }
            pendingPreserveVisibleIslands =
                request?.getBoolean(KEY_PRESERVE_VISIBLE_ISLANDS, false) == true
            if (!::engine.isInitialized) return
            engine.onIngressConnected(
                preserveVisibleIslands = pendingPreserveVisibleIslands,
            )
        }

        override fun reload() {
            enforceSystemUiCaller()
            if (!::engine.isInitialized) return
            engine.onIngressConnected(preserveVisibleIslands = true)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (getSystemService(UserManager::class.java)?.isUserUnlocked == false) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
                    unlockReceiver = null
                    initializeEngine()
                }
            }
            unlockReceiver = receiver
            registerReceiver(
                receiver,
                IntentFilter(Intent.ACTION_USER_UNLOCKED),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            initializeEngine()
        }
    }

    @Synchronized
    private fun initializeEngine() {
        if (::engine.isInitialized) return
        engine = NotificationProcessingEngine.create(
            appContext = this,
            activeNotificationsProvider = { activeSources.values.toTypedArray() },
            cancelSourceNotificationByKey = ::requestSourceCancellation,
            islandBackend = SystemUiIslandBackend.get(this),
        )
        engine.onIngressConnected(preserveVisibleIslands = pendingPreserveVisibleIslands)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        unlockReceiver?.let { runCatching { unregisterReceiver(it) } }
        unlockReceiver = null
        if (::engine.isInitialized) engine.shutdown()
        super.onDestroy()
    }

    private fun requestSourceCancellation(key: String) {
        val intent = Intent(IslandProtocol.ACTION_CANCEL_SOURCE).apply {
            setPackage(IslandProtocol.SYSTEM_UI_PACKAGE)
            putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            putExtra(IslandProtocol.EXTRA_SOURCE_KEY, key)
        }
        sendBroadcast(intent)
    }

    private fun enforceSystemUiCaller() {
        val callingUid = Binder.getCallingUid()
        val packages = packageManager.getPackagesForUid(callingUid).orEmpty()
        check(IslandProtocol.SYSTEM_UI_PACKAGE in packages) {
            "Notification ingress caller is not SystemUI (uid=$callingUid)"
        }
    }

    private fun Bundle.statusBarNotification(): StatusBarNotification? =
        getParcelable(KEY_NOTIFICATION, StatusBarNotification::class.java)

    private fun Bundle.statusBarNotifications(): List<StatusBarNotification> =
        getParcelableArrayList(KEY_NOTIFICATIONS, StatusBarNotification::class.java).orEmpty()

    companion object {
        const val KEY_NOTIFICATION = "notification"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_REASON = "reason"
        const val KEY_PRESERVE_VISIBLE_ISLANDS = "preserveVisibleIslands"
    }
}
