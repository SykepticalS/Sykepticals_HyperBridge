package com.d4viddf.hyperbridge.xposed.dispatch

import android.app.BroadcastOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.d4viddf.hyperbridge.island.backend.IslandOwnership
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.xposed.log
import com.d4viddf.hyperbridge.xposed.hooks.FocusWhitelistHook
import com.d4viddf.hyperbridge.xposed.hooks.HeadsUpSuppressionHook
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap

object SystemUiDispatcher {
    private data class OwnedKey(val tag: String, val id: Int)
    private val generations = ConcurrentHashMap<OwnedKey, Long>()
    @Volatile private var registered = false

    fun register(context: Context, module: XposedModule) {
        if (registered) return
        synchronized(this) {
            if (registered) return@synchronized
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!trustedSender(context, sentFromUid)) return
                    if (!IslandProtocol.compatible(intent.getIntExtra(IslandProtocol.EXTRA_PROTOCOL, -1))) return
                    when (intent.action) {
                        IslandProtocol.ACTION_POST -> post(context, intent, module)
                        IslandProtocol.ACTION_CANCEL -> cancel(context, intent)
                        IslandProtocol.ACTION_CANCEL_ALL -> cancelAll(context)
                        IslandProtocol.ACTION_PING -> pong(context, intent)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(IslandProtocol.ACTION_POST)
                addAction(IslandProtocol.ACTION_CANCEL)
                addAction(IslandProtocol.ACTION_CANCEL_ALL)
                addAction(IslandProtocol.ACTION_PING)
            }
            context.registerReceiver(
                receiver,
                filter,
                IslandProtocol.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED,
            )
            registered = true
            module.log("HyperBridge: SystemUI dispatcher registered")
        }
    }

    private fun post(context: Context, intent: Intent, module: XposedModule) {
        val notification = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(IslandProtocol.EXTRA_NOTIFICATION, Notification::class.java)
        } else @Suppress("DEPRECATION") intent.getParcelableExtra(IslandProtocol.EXTRA_NOTIFICATION)
        if (notification == null || notification.extras.getString(IslandProtocol.EXTRA_OWNER) != IslandProtocol.OWNER) return
        val tag = intent.getStringExtra(IslandProtocol.EXTRA_TAG) ?: return
        if (!tag.startsWith("hyperbridge:")) return
        val id = intent.getIntExtra(IslandProtocol.EXTRA_ID, Int.MIN_VALUE)
        if (id == Int.MIN_VALUE) return
        val generation = intent.getLongExtra(IslandProtocol.EXTRA_GENERATION, 0L)
        val key = OwnedKey(tag, id)
        val current = generations[key]
        if (current != null && generation < current) return
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: error("NotificationManager unavailable")
            ensureChannel(manager, notification.channelId)
            manager.notify(tag, id, notification)
            generations[key] = generation
        }.onFailure { module.log("HyperBridge: dispatcher post failed id=$id: ${it.message}") }
    }

    private fun cancel(context: Context, intent: Intent) {
        val tag = intent.getStringExtra(IslandProtocol.EXTRA_TAG) ?: return
        val id = intent.getIntExtra(IslandProtocol.EXTRA_ID, Int.MIN_VALUE)
        val requested = intent.getLongExtra(IslandProtocol.EXTRA_GENERATION, Long.MAX_VALUE)
        val key = OwnedKey(tag, id)
        val current = generations[key] ?: findOwnedGeneration(context, tag, id) ?: return
        if (!IslandOwnership.acceptsCancel(current, requested)) return
        context.getSystemService(NotificationManager::class.java)?.cancel(tag, id)
        generations.remove(key)
    }

    private fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications.orEmpty().filter {
            it.tag?.startsWith("hyperbridge:") == true &&
                it.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
        }.forEach { manager.cancel(it.tag, it.id) }
        generations.clear()
    }

    private fun findOwnedGeneration(context: Context, tag: String, id: Int): Long? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        return manager.activeNotifications.orEmpty().firstOrNull {
            it.tag == tag && it.id == id && it.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
        }?.notification?.extras?.getLong(IslandProtocol.EXTRA_GENERATION, 0L)
    }

    private fun ensureChannel(manager: NotificationManager, id: String?) {
        val channelId = id?.takeIf(String::isNotBlank) ?: "hyperbridge_systemui"
        if (manager.getNotificationChannel(channelId) != null) return
        manager.createNotificationChannel(
            NotificationChannel(channelId, "HyperBridge Islands", NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun pong(context: Context, intent: Intent) {
        val reply = Intent(IslandProtocol.ACTION_PONG).apply {
            setPackage(IslandProtocol.APP_PACKAGE)
            putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            putExtra(IslandProtocol.EXTRA_NONCE, intent.getLongExtra(IslandProtocol.EXTRA_NONCE, 0L))
            putExtra(IslandProtocol.EXTRA_HOOK_PACKAGE, IslandProtocol.SYSTEM_UI_PACKAGE)
            val focus = if (FocusWhitelistHook.isActive()) IslandProtocol.CAP_FOCUS_BYPASS else 0
            val headsUp = if (HeadsUpSuppressionHook.isActive()) IslandProtocol.CAP_HEADS_UP else 0
            putExtra(IslandProtocol.EXTRA_CAPABILITIES,
                IslandProtocol.CAP_POST or IslandProtocol.CAP_TAGGED_CANCEL or
                    focus or headsUp)
        }
        val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        context.sendBroadcast(reply, null, options)
    }

    private fun trustedSender(context: Context, uid: Int): Boolean {
        if (uid < 0) return false
        val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
        return IslandProtocol.APP_PACKAGE in packages &&
            context.checkPermission(IslandProtocol.PERMISSION, -1, uid) == PackageManager.PERMISSION_GRANTED
    }
}
