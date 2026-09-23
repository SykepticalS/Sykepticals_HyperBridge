package com.d4viddf.hyperbridge.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend
import com.d4viddf.hyperbridge.processing.INotificationProcessingService
import com.d4viddf.hyperbridge.processing.IIslandDispatcher
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the notification semantics engine in HyperBridge's process, where its Room database,
 * preferences, themes, and application resources are valid. SystemUI remains the sole source
 * intake and calls here before Xiaomi snapshots the source. The attached return Binder posts
 * directly as SystemUI; the Boolean result marks SystemUI's original SBN before it proceeds.
 */
class NotificationProcessingService : Service() {
    private val activeSources = ConcurrentHashMap<String, StatusBarNotification>()
    private lateinit var engine: NotificationProcessingEngine

    private val binder = object : INotificationProcessingService.Stub() {
        override fun attachDispatcher(dispatcher: IIslandDispatcher?) {
            enforceSystemUiCaller()
            SystemUiIslandBackend.get(this@NotificationProcessingService).attachDispatcher(dispatcher)
        }

        override fun processPosted(request: Bundle?): Boolean {
            enforceSystemUiCaller()
            val sbn = request?.statusBarNotification() ?: return false
            sbn.notification.extras.remove(IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP)
            activeSources[sbn.key] = sbn
            engine.onNotificationPosted(sbn)
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
            engine.onNotificationRemoved(sbn, request.getInt(KEY_REASON, 0))
        }

        override fun reconcile(request: Bundle?) {
            enforceSystemUiCaller()
            val snapshot = request?.statusBarNotifications().orEmpty()
            activeSources.clear()
            snapshot.forEach { activeSources[it.key] = it }
            engine.onIngressConnected(
                preserveVisibleIslands = request?.getBoolean(KEY_PRESERVE_VISIBLE_ISLANDS, false) == true,
            )
        }

        override fun reload() {
            enforceSystemUiCaller()
            engine.handleCommand(Intent(NotificationProcessingEngine.ACTION_RELOAD_THEME))
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = NotificationProcessingEngine.create(
            appContext = this,
            activeNotificationsProvider = { activeSources.values.toTypedArray() },
            cancelSourceNotificationByKey = ::requestSourceCancellation,
            islandBackend = SystemUiIslandBackend.get(this),
        )
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
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
