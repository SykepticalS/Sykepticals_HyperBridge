package com.d4viddf.hyperbridge.xposed.hooks

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Binder
import android.app.Notification
import android.os.IBinder
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.processing.INotificationProcessingService
import com.d4viddf.hyperbridge.processing.IIslandDispatcher
import com.d4viddf.hyperbridge.xposed.dispatch.SystemUiDispatcher
import com.d4viddf.hyperbridge.service.FocusShadeUpdate
import com.d4viddf.hyperbridge.service.NotificationProcessingService
import com.d4viddf.hyperbridge.service.NotificationLifecyclePolicy
import com.d4viddf.hyperbridge.service.ShadeEntryIdentity
import com.d4viddf.hyperbridge.service.ShadeReplayPolicy
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** HyperIsland-style source intake hosted by SystemUI before Xiaomi snapshots its extras. */
object SystemUiNotificationIngressHook {
    private const val LISTENER = "com.android.systemui.statusbar.notification.MiuiNotificationListener"
    private const val MIUI_NOTIF_UTIL = "com.miui.systemui.notification.MiuiBaseNotifUtil"
    private const val BULK_REPLAY_WINDOW_MS = 2_000L
    private val activeSources = ConcurrentHashMap<String, StatusBarNotification>()
    /** Shade entries already known to SystemUI. A rebuild must not present them again. */
    private val resident = ConcurrentHashMap<String, ShadeEntryIdentity>()
    @Volatile private var replayFreezeUntil = 0L
    /** Set once the listener has supplied a shade snapshot, so a later rebind keeps islands. */
    @Volatile private var listenerSnapshotReady = false
    @Volatile private var liveSession = false
    /**
     * Notifications can arrive before the cross-process service binding completes.  Dropping
     * that first callback means there is nothing to post until the source app happens to update
     * it again.  Keep only the newest SBN per key and drain it as soon as Binder is ready.
     */
    private val pendingPosts = ConcurrentHashMap<String, StatusBarNotification>()
    private val processingExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HyperBridge-NotificationProcessing").apply { isDaemon = true }
    }
    @Volatile private var connectingListener = false
    @Volatile private var listener = WeakReference<Any>(null)
    @Volatile private var processor: INotificationProcessingService? = null
    @Volatile private var binding = false
    @Volatile private var systemUiContext = WeakReference<Context>(null)
    @Volatile private var logger = WeakReference<XposedModule>(null)
    @Volatile private var active = false
    @Volatile private var preSnapshotActive = false

    private val dispatcher = object : IIslandDispatcher.Stub() {
        override fun post(tag: String, id: Int, notification: Notification, generation: Long): Boolean {
            val context = systemUiContext.get() ?: return false
            val uid = Binder.getCallingUid()
            check(IslandProtocol.APP_PACKAGE in context.packageManager.getPackagesForUid(uid).orEmpty()) {
                "Island dispatcher caller is not HyperBridge"
            }
            // notify must execute as SystemUI, not under the incoming app Binder identity.
            val identity = Binder.clearCallingIdentity()
            return try {
                SystemUiDispatcher.postOwned(context, tag, id, notification, generation).isSuccess
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            processingExecutor.execute {
                val remote = INotificationProcessingService.Stub.asInterface(service)
                runCatching { remote.attachDispatcher(dispatcher) }.onFailure {
                    binding = false
                    logger.get()?.log("HyperBridge: dispatcher attachment failed: ${it.message}")
                    return@execute
                }
                processor = remote
                binding = false
                drainPendingPosts()
                reconcileRemote()
            }
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

    /** WhatsApp can float its separate group summary after its child was replaced. */
    fun hasReplacedGroupChild(summary: StatusBarNotification): Boolean {
        if (summary.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) return false
        return activeSources.values.any { child ->
            child.key != summary.key && child.packageName == summary.packageName &&
                child.user == summary.user && child.groupKey == summary.groupKey &&
                child.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
                child.notification.extras.getBoolean(IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP, false)
        }
    }

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
        installPreSnapshotMarker(module, loader)
        runCatching {
            val listenerClass = loader.loadClass(LISTENER)
            val rankingMap = loader.loadClass(
                "android.service.notification.NotificationListenerService\$RankingMap"
            )

            module.hook(listenerClass.getDeclaredMethod("onListenerConnected")).intercept { chain ->
                listener = WeakReference(chain.thisObject)
                connectingListener = true
                val result = try {
                    chain.proceed()
                } finally {
                    connectingListener = false
                }
                snapshotActive(chain.thisObject)
                listenerSnapshotReady = true
                noteBulkReplay()
                connectFromCurrentContext(module)
                processingExecutor.execute(::reconcileRemote)
                result
            }

            val posted = listenerClass.declaredMethods.first {
                it.name == "onNotificationPosted" &&
                    it.parameterTypes.firstOrNull() == StatusBarNotification::class.java &&
                    it.parameterTypes.getOrNull(1) == rankingMap
            }
            module.hook(posted).intercept { chain ->
                listener = WeakReference(chain.thisObject)
                (chain.args.firstOrNull() as? StatusBarNotification)?.let { sbn ->
                    activeSources[sbn.key] = sbn
                    if (!preSnapshotActive && !connectingListener && !isOwnedProxy(sbn)) {
                        processingExecutor.execute { processPosted(sbn, module) }
                    }
                }
                chain.proceed()
            }

            val removed = listenerClass.declaredMethods.first {
                it.name == "onNotificationRemoved" &&
                    it.parameterTypes.firstOrNull() == StatusBarNotification::class.java &&
                    it.parameterTypes.lastOrNull() == Int::class.javaPrimitiveType
            }
            module.hook(removed).intercept { chain ->
                listener = WeakReference(chain.thisObject)
                val sbn = chain.args[0] as? StatusBarNotification
                val reason = chain.args.lastOrNull() as? Int ?: 0
                if (sbn != null && (
                        NotificationLifecyclePolicy.preservesActiveIsland(reason) ||
                            reason == NotificationLifecyclePolicy.REASON_CANCEL_ALL ||
                            reason == NotificationLifecyclePolicy.REASON_LISTENER_CANCEL_ALL
                        )
                ) {
                    // Shade clear-all and recents both rebuild the shade. Ignore replayed
                    // posts of entries that were already showing.
                    noteBulkReplay()
                }
                if (sbn != null && NotificationLifecyclePolicy.preservesActiveIsland(reason)) {
                    // Recents cleanup is not a user dismissal. Swallowing the proxy removal
                    // keeps the island view; HyperOS drops it inside proceed.
                    if (isOwnedProxy(sbn)) return@intercept null
                }
                val result = chain.proceed()
                if (sbn != null) {
                    pendingPosts.remove(sbn.key)
                    activeSources.computeIfPresent(sbn.key) { _, current ->
                        current.takeUnless { sameGeneration(it, sbn) }
                    }
                    if (!NotificationLifecyclePolicy.preservesActiveIsland(reason)) {
                        resident.remove(sbn.key)
                    }
                    if (isOwnedProxy(sbn) &&
                        NotificationLifecyclePolicy.isUserInitiatedRemoval(reason) &&
                        !NotificationLifecyclePolicy.preservesActiveIsland(reason)
                    ) {
                        ActiveIslandDismissHook.dismissKey(sbn.key)
                    }
                    processingExecutor.execute {
                        processRemoved(sbn, reason, module)
                    }
                }
                result
            }
            module.log("HyperBridge: hooked MiuiNotificationListener posted/removed lifecycle")

            active = true
            module.log("HyperBridge: ingress ready preSnapshot=$preSnapshotActive directDispatcher=true")
        }.onFailure {
            active = false
            module.log("HyperBridge: SystemUI notification ingress hook failed open: ${it.message}")
        }
    }

    private fun installPreSnapshotMarker(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val util = loader.loadClass(MIUI_NOTIF_UTIL)
            val method = util.getDeclaredMethod(
                "generateInnerNotifBean",
                StatusBarNotification::class.java,
            )
            module.hook(method).intercept { chain ->
                (chain.args.firstOrNull() as? StatusBarNotification)?.let { sbn ->
                    if (!isOwnedProxy(sbn)) {
                        sbn.notification.extras.remove(IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP)
                        // HyperIsland posts here, on Xiaomi's background notification thread,
                        // before InnerNotifBean and heads-up decisions snapshot the source.
                        // The direct return Binder avoids a main-thread broadcast deadlock.
                        if (!connectingListener) processPosted(sbn, module)
                    }
                }
                chain.proceed()
            }
            preSnapshotActive = true
            module.log("HyperBridge: hooked pre-snapshot source suppression marker")
        }.onFailure {
            module.log("HyperBridge: pre-snapshot source marker unavailable: ${it.message}")
        }
    }

    private fun processPosted(sbn: StatusBarNotification, module: XposedModule) {
        if (isOwnedProxy(sbn)) return
        val incoming = identityOf(sbn)
        if (ShadeReplayPolicy.shouldIgnore(resident[sbn.key], incoming, bulkReplayActive())) {
            resident[sbn.key] = incoming
            pendingPosts.remove(sbn.key)
            return
        }
        val remote = processor ?: run {
            pendingPosts[sbn.key] = sbn
            connectFromCurrentContext(module)
            return
        }
        runCatching {
            val started = android.os.SystemClock.elapsedRealtime()
            val request = Bundle().apply {
                putParcelable(NotificationProcessingService.KEY_NOTIFICATION, sbn)
            }
            val replaced = remote.processPosted(request)
            resident[sbn.key] = incoming
            applyCallFocusDecoration(sbn, request)
            allowCallShadeDismissal(sbn, request)
            if (replaced) markSourceHeadsUpSuppressed(sbn)
            if (replaced) module.log(
                "HyperBridge: pre-snapshot replacement package=${sbn.packageName} " +
                    "elapsedMs=${android.os.SystemClock.elapsedRealtime() - started}",
            )
        }.onFailure {
            pendingPosts[sbn.key] = sbn
            module.log("HyperBridge: notification processing failed open: ${it.message}")
            processor = null
            connectFromCurrentContext(module)
        }
    }

    private fun drainPendingPosts() {
        val remote = processor ?: return
        pendingPosts.entries.toList().forEach { (key, sbn) ->
            val incoming = identityOf(sbn)
            if (ShadeReplayPolicy.shouldIgnore(resident[key], incoming, bulkReplayActive())) {
                resident[key] = incoming
                pendingPosts.remove(key, sbn)
                return@forEach
            }
            runCatching {
                val request = Bundle().apply {
                    putParcelable(NotificationProcessingService.KEY_NOTIFICATION, sbn)
                }
                val replaced = remote.processPosted(request)
                resident[key] = incoming
                applyCallFocusDecoration(sbn, request)
                allowCallShadeDismissal(sbn, request)
                if (replaced) markSourceHeadsUpSuppressed(sbn)
            }.onSuccess {
                pendingPosts.remove(key, sbn)
            }.onFailure {
                logger.get()?.log("HyperBridge: queued notification processing failed: ${it.message}")
                return
            }
        }
    }

    private fun applyCallFocusDecoration(sbn: StatusBarNotification, request: Bundle) {
        val decoration = request.getBundle(IslandProtocol.EXTRA_CALL_FOCUS_DECORATION) ?: return
        sbn.notification.extras.putAll(decoration)
        if (decoration.getString(IslandProtocol.EXTRA_SEMANTIC_TYPE) == "VOICE_MESSAGE" &&
            !FocusShadeUpdate.cancels(decoration.getString("miui.focus.param"))
        ) {
            // Playback rows are low-importance and silent. Ongoing keeps the island alive.
            // Leave contentView / bigContentView alone so WhatsApp and Instagram keep
            // posting their own shade row.
            sbn.notification.flags = sbn.notification.flags or android.app.Notification.FLAG_ONGOING_EVENT
        }
    }

    private fun allowCallShadeDismissal(sbn: StatusBarNotification, request: Bundle) {
        if (!request.getBoolean(IslandProtocol.EXTRA_CALL_SHADE_DISMISSIBLE, false)) return
        val locked = android.app.Notification.FLAG_ONGOING_EVENT or
            android.app.Notification.FLAG_NO_CLEAR or
            android.app.Notification.FLAG_FOREGROUND_SERVICE
        sbn.notification.flags = sbn.notification.flags and locked.inv()
    }

    private fun markSourceHeadsUpSuppressed(sbn: StatusBarNotification) {
        // Incoming calls keep their full-screen intent. The engine only reports replacement
        // for those when the incoming island is posted, so the banner can be hidden without
        // cancelling the notification that vibrates and owns the call.
        sbn.notification.extras.putBoolean(
            IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP,
            true,
        )
        activeSources.compute(sbn.key) { _, current ->
            if (current == null || sameGeneration(current, sbn)) sbn else current
        }
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
        val preserveVisibleIslands = liveSession
        runCatching {
            remote.reconcile(Bundle().apply {
                putParcelableArrayList(
                    NotificationProcessingService.KEY_NOTIFICATIONS,
                    ArrayList(activeSources.values),
                )
                putBoolean(
                    NotificationProcessingService.KEY_PRESERVE_VISIBLE_ISLANDS,
                    preserveVisibleIslands,
                )
            })
        }.onSuccess {
            if (listenerSnapshotReady) liveSession = true
        }.onFailure {
            logger.get()?.log("HyperBridge: active notification reconciliation failed open: ${it.message}")
        }
    }

    private fun noteBulkReplay() {
        replayFreezeUntil = android.os.SystemClock.elapsedRealtime() + BULK_REPLAY_WINDOW_MS
    }

    private fun bulkReplayActive(): Boolean =
        android.os.SystemClock.elapsedRealtime() < replayFreezeUntil

    private fun identityOf(sbn: StatusBarNotification): ShadeEntryIdentity =
        ShadeEntryIdentity(visibleHash(sbn), sbn.postTime)

    private fun visibleHash(sbn: StatusBarNotification): Int {
        val extras = sbn.notification.extras
        val remote = sbn.notification?.let { notification ->
            runCatching { com.d4viddf.hyperbridge.service.NotificationRemoteViewsParser.collect(notification) }.getOrNull()
        }
        return listOf(
            extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras?.getInt(Notification.EXTRA_PROGRESS, 0),
            extras?.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            latestMessageText(extras),
            remote?.progress,
            remote?.progressMax,
            remote?.texts?.joinToString("|"),
            remote?.clicks?.joinToString("|") { it.label },
        ).hashCode()
    }

    @Suppress("DEPRECATION")
    private fun latestMessageText(extras: android.os.Bundle?): String? {
        val messages = extras?.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val last = messages.lastOrNull() as? android.os.Bundle ?: return null
        return last.getCharSequence("text")?.toString()
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
        values.filterIsInstance<StatusBarNotification>().forEach { sbn ->
            activeSources[sbn.key] = sbn
            if (!isOwnedProxy(sbn)) resident[sbn.key] = identityOf(sbn)
        }
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

    private fun isOwnedProxy(sbn: StatusBarNotification): Boolean =
        sbn.packageName == IslandProtocol.SYSTEM_UI_PACKAGE &&
            sbn.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
}
