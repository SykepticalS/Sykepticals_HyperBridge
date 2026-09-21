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
import android.os.Bundle
import android.os.ResultReceiver
import android.service.notification.StatusBarNotification
import android.util.Log
import com.d4viddf.hyperbridge.island.backend.IslandOwnership
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.xposed.log
import com.d4viddf.hyperbridge.xposed.hooks.FocusWhitelistHook
import com.d4viddf.hyperbridge.xposed.hooks.HeadsUpSuppressionHook
import com.d4viddf.hyperbridge.xposed.hooks.SystemUiNotificationIngressHook
import com.d4viddf.hyperbridge.xposed.hooks.MarqueeHook
import com.d4viddf.hyperbridge.xposed.hooks.ActiveIslandDismissHook
import com.d4viddf.hyperbridge.xposed.hooks.OuterGlowHook
import com.d4viddf.hyperbridge.xposed.hooks.IslandTextUpdateAnimationHook
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference

object SystemUiDispatcher {
    private data class OwnedKey(val tag: String, val id: Int)
    private val generations = ConcurrentHashMap<OwnedKey, Long>()
    @Volatile private var systemUiContext = WeakReference<Context>(null)
    @Volatile private var registered = false

    fun register(context: Context, module: XposedModule) {
        systemUiContext = WeakReference(context.applicationContext)
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
                        IslandProtocol.ACTION_RELOAD_ENGINE -> SystemUiNotificationIngressHook.reloadEngine()
                        IslandProtocol.ACTION_CANCEL_SOURCE -> intent.getStringExtra(IslandProtocol.EXTRA_SOURCE_KEY)
                            ?.let(SystemUiNotificationIngressHook::cancelSource)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(IslandProtocol.ACTION_POST)
                addAction(IslandProtocol.ACTION_CANCEL)
                addAction(IslandProtocol.ACTION_CANCEL_ALL)
                addAction(IslandProtocol.ACTION_PING)
                addAction(IslandProtocol.ACTION_RELOAD_ENGINE)
                addAction(IslandProtocol.ACTION_CANCEL_SOURCE)
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

    fun cancelClickedOwnedIsland(sbn: StatusBarNotification) {
        runCatching {
            val context = systemUiContext.get() ?: return@runCatching
            val owned = if (
                sbn.packageName == IslandProtocol.SYSTEM_UI_PACKAGE &&
                sbn.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
            ) {
                sbn
            } else {
                val sourceKey = sbn.key
                context.getSystemService(NotificationManager::class.java)
                    ?.activeNotifications
                    ?.firstOrNull {
                        it.packageName == IslandProtocol.SYSTEM_UI_PACKAGE &&
                            it.notification.extras.getString(IslandProtocol.EXTRA_OWNER) ==
                                IslandProtocol.OWNER &&
                            it.notification.extras.getString(IslandProtocol.EXTRA_SOURCE_KEY) == sourceKey
                    }
            } ?: return@runCatching
            val tag = owned.tag ?: return@runCatching
            cancelOwned(
                context = context,
                tag = tag,
                id = owned.id,
                requestedGeneration = owned.notification.extras.getLong(
                    IslandProtocol.EXTRA_GENERATION,
                    Long.MAX_VALUE,
                ),
            )
        }
    }

    private fun post(context: Context, intent: Intent, module: XposedModule) {
        val acknowledgement = intent.getParcelableExtra(
            IslandProtocol.EXTRA_RESULT_RECEIVER,
            ResultReceiver::class.java,
        )
        val result = runCatching {
            val notification = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(IslandProtocol.EXTRA_NOTIFICATION, Notification::class.java)
            } else @Suppress("DEPRECATION") intent.getParcelableExtra(IslandProtocol.EXTRA_NOTIFICATION)
            require(notification != null) { "Missing notification" }
            require(notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER) {
                "Unowned notification"
            }
            val tag = requireNotNull(intent.getStringExtra(IslandProtocol.EXTRA_TAG)) { "Missing tag" }
            require(tag.startsWith("hyperbridge:")) { "Unowned tag" }
            val id = intent.getIntExtra(IslandProtocol.EXTRA_ID, Int.MIN_VALUE)
            require(id != Int.MIN_VALUE) { "Missing notification id" }
            val generation = intent.getLongExtra(IslandProtocol.EXTRA_GENERATION, 0L)
            postOwned(context, tag, id, notification, generation).getOrThrow()
        }
        result.onFailure { module.log("HyperBridge: dispatcher post failed: ${it.message}") }
        acknowledgement?.send(
            if (result.isSuccess) IslandProtocol.RESULT_POSTED else IslandProtocol.RESULT_REJECTED,
            Bundle.EMPTY,
        )
    }

    private fun cancel(context: Context, intent: Intent) {
        val tag = intent.getStringExtra(IslandProtocol.EXTRA_TAG) ?: return
        val id = intent.getIntExtra(IslandProtocol.EXTRA_ID, Int.MIN_VALUE)
        val requested = intent.getLongExtra(IslandProtocol.EXTRA_GENERATION, Long.MAX_VALUE)
        cancelOwned(context, tag, id, requested)
    }

    private fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications.orEmpty().filter {
            it.tag?.startsWith("hyperbridge:") == true &&
                it.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
        }.forEach { sbn ->
            manager.cancel(sbn.tag, sbn.id)
            sbn.key?.let(ActiveIslandDismissHook::dismissKey)
        }
        generations.clear()
    }

    fun postOwned(
        context: Context,
        tag: String,
        id: Int,
        notification: Notification,
        generation: Long,
    ): Result<Unit> = runCatching {
        require(tag.startsWith("hyperbridge:")) { "Unowned island tag" }
        require(notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER) {
            "Unowned island notification"
        }
        val key = OwnedKey(tag, id)
        val current = generations[key]
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: error("NotificationManager unavailable")
        ensureChannel(manager, notification.channelId)
        manager.notify(tag, id, notification)
        generations[key] = maxOf(current ?: Long.MIN_VALUE, generation)
        Log.i("HyperBridge", "notify tag=$tag id=$id gen=$generation prevGen=$current")
    }

    fun cancelOwned(
        context: Context,
        tag: String,
        id: Int,
        requestedGeneration: Long,
    ): Result<Unit> = runCatching {
        val key = OwnedKey(tag, id)
        val current = generations[key] ?: findOwnedGeneration(context, tag, id) ?: return@runCatching
        if (!IslandOwnership.acceptsCancel(current, requestedGeneration)) return@runCatching
        val manager = context.getSystemService(NotificationManager::class.java)
        val islandKey = manager?.activeNotifications.orEmpty().firstOrNull {
            it.tag == tag && it.id == id &&
                it.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
        }?.key
        manager?.cancel(tag, id)
        generations.remove(key)
        islandKey?.let(ActiveIslandDismissHook::dismissKey)
    }

    fun cancelAllOwned(context: Context): Result<Unit> = runCatching { cancelAll(context) }

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
            val ingress = if (SystemUiNotificationIngressHook.isActive()) IslandProtocol.CAP_NOTIFICATION_INGRESS else 0
            val marquee = if (MarqueeHook.isActive()) IslandProtocol.CAP_MARQUEE else 0
            val dismiss = if (ActiveIslandDismissHook.isActive()) IslandProtocol.CAP_ISLAND_DISMISS else 0
            val glow = if (OuterGlowHook.isActive()) IslandProtocol.CAP_FULL_GLOW else 0
            val textUpdate = if (IslandTextUpdateAnimationHook.isActive()) {
                IslandProtocol.CAP_TEXT_UPDATE_ANIMATION
            } else 0
            putExtra(IslandProtocol.EXTRA_CAPABILITIES,
                IslandProtocol.CAP_POST or IslandProtocol.CAP_TAGGED_CANCEL or
                    focus or headsUp or ingress or marquee or dismiss or glow or textUpdate)
        }
        val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        context.sendBroadcast(reply, null, options)
    }

    fun notifyReplyComposer(open: Boolean) {
        val context = systemUiContext.get() ?: return
        val intent = Intent(IslandProtocol.ACTION_REPLY_COMPOSER).apply {
            setPackage(IslandProtocol.APP_PACKAGE)
            putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            putExtra(IslandProtocol.EXTRA_REPLY_COMPOSER_OPEN, open)
        }
        val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        context.sendBroadcast(intent, null, options)
    }

    private fun trustedSender(context: Context, uid: Int): Boolean {
        if (uid < 0) return false
        val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
        return IslandProtocol.APP_PACKAGE in packages &&
            context.checkPermission(IslandProtocol.PERMISSION, -1, uid) == PackageManager.PERMISSION_GRANTED
    }
}
