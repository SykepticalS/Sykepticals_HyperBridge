package com.d4viddf.hyperbridge.island.backend

import android.app.BroadcastOptions
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Parcel
import android.os.ResultReceiver
import android.os.SystemClock
import android.util.Log
import com.d4viddf.hyperbridge.models.IslandVisualMetadata
import com.d4viddf.hyperbridge.processing.IIslandDispatcher
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class SystemUiIslandBackend private constructor(private val context: Context) : IslandBackend {
    @Volatile private var dispatcher: IIslandDispatcher? = null

    fun attachDispatcher(value: IIslandDispatcher?) {
        dispatcher = value
    }
    private val nonce = SecureRandom().nextLong()
    private val lastHandshakeElapsed = AtomicLong(0L)
    private val lastXmsfHandshakeElapsed = AtomicLong(0L)
    @Volatile private var hookProtocol: Int? = null
    @Volatile private var capabilities: Int = 0

    private val pongReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != IslandProtocol.ACTION_PONG ||
                intent.getLongExtra(IslandProtocol.EXTRA_NONCE, Long.MIN_VALUE) != nonce ||
                !trustedHookSender(context, sentFromUid)
            ) return
            val hookPackage = intent.getStringExtra(IslandProtocol.EXTRA_HOOK_PACKAGE) ?: return
            if (hookPackage == IslandProtocol.SYSTEM_UI_PACKAGE) {
                hookProtocol = intent.getIntExtra(IslandProtocol.EXTRA_PROTOCOL, -1)
                capabilities = intent.getIntExtra(IslandProtocol.EXTRA_CAPABILITIES, 0)
                lastHandshakeElapsed.set(SystemClock.elapsedRealtime())
            } else if (hookPackage == IslandProtocol.XMSF_PACKAGE) {
                lastXmsfHandshakeElapsed.set(SystemClock.elapsedRealtime())
            }
            HookConfigSync.updateBackendHealth(context, health())
        }
    }

    init {
        context.registerReceiver(
            pongReceiver,
            IntentFilter(IslandProtocol.ACTION_PONG),
            Context.RECEIVER_EXPORTED,
        )
        ping()
    }

    override fun post(id: Int, notification: Notification, metadata: IslandMetadata): Result<Unit> = runCatching {
        notification.extras.apply {
            putString(IslandProtocol.EXTRA_OWNER, IslandProtocol.OWNER)
            putString(IslandProtocol.EXTRA_SOURCE_KEY, metadata.sourceKey ?: metadata.logicalToken)
            metadata.sourcePackage?.let { putString(IslandProtocol.EXTRA_SOURCE_PACKAGE, it) }
            metadata.sourceChannel?.let { putString(IslandProtocol.EXTRA_SOURCE_CHANNEL, it) }
            metadata.semanticType?.let { putString(IslandProtocol.EXTRA_SEMANTIC_TYPE, it) }
            putLong(IslandProtocol.EXTRA_GENERATION, metadata.generation)
            putInt(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            // All posts converge here, including VPN, widgets, and migration progress. Keep
            // Xiaomi's native compact and expanded text transitions consistent on every owned
            // island instead of relying on each producer to remember the hidden protocol flag.
            putBoolean(IslandProtocol.EXTRA_TEXT_UPDATE_ANIMATION, true)
            getString("miui.focus.param")?.let { json ->
                putString(
                    "miui.focus.param",
                    IslandVisualMetadata.injectTextUpdateAnimation(json),
                )
            }
        }
        check(parcelSize(notification) <= IslandProtocol.MAX_PARCEL_BYTES) {
            "Island payload exceeds ${IslandProtocol.MAX_PARCEL_BYTES} byte IPC limit"
        }
        // The ingress service is bound by SystemUI. Return directly over that connection:
        // a broadcast here races the source heads-up and queues behind unrelated broadcasts.
        dispatcher?.let { target ->
            check(target.post(IslandOwnership.tag(metadata.logicalToken), id, notification, metadata.generation)) {
                "SystemUI rejected island post"
            }
            return@runCatching
        }
        val latch = CountDownLatch(1)
        val resultCode = AtomicReference<Int>()
        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(code: Int, resultData: android.os.Bundle?) {
                resultCode.set(code)
                latch.countDown()
            }
        }
        send(Intent(IslandProtocol.ACTION_POST).apply {
            putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            putExtra(IslandProtocol.EXTRA_NOTIFICATION, notification)
            putExtra(IslandProtocol.EXTRA_TAG, IslandOwnership.tag(metadata.logicalToken))
            putExtra(IslandProtocol.EXTRA_ID, id)
            putExtra(IslandProtocol.EXTRA_GENERATION, metadata.generation)
            putExtra(IslandProtocol.EXTRA_RESULT_RECEIVER, receiver)
        })
        check(latch.await(3, TimeUnit.SECONDS)) { "SystemUI island post acknowledgement timed out" }
        check(resultCode.get() == IslandProtocol.RESULT_POSTED) { "SystemUI rejected island post" }
    }.onFailure { Log.e(TAG, "post rejected id=$id", it) }

    override fun cancel(id: Int, logicalToken: String, generation: Long): Result<Unit> = runCatching {
        send(Intent(IslandProtocol.ACTION_CANCEL).apply {
            putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            putExtra(IslandProtocol.EXTRA_TAG, IslandOwnership.tag(logicalToken))
            putExtra(IslandProtocol.EXTRA_ID, id)
            putExtra(IslandProtocol.EXTRA_GENERATION, generation)
        })
    }

    override fun cancelAllOwned(): Result<Unit> = runCatching {
        send(Intent(IslandProtocol.ACTION_CANCEL_ALL).putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION))
    }

    override fun ping() {
        send(Intent(IslandProtocol.ACTION_PING).apply {
            putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            putExtra(IslandProtocol.EXTRA_NONCE, nonce)
        })
        sendTo(IslandProtocol.XMSF_PACKAGE, Intent(IslandProtocol.ACTION_PING_XMSF).apply {
            putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
            putExtra(IslandProtocol.EXTRA_NONCE, nonce)
        })
    }

    override fun health(): IslandBackendHealth {
        val elapsed = lastHandshakeElapsed.get()
        val fresh = elapsed != 0L && SystemClock.elapsedRealtime() - elapsed <= IslandProtocol.HEARTBEAT_LEASE_MS
        val xmsfElapsed = lastXmsfHandshakeElapsed.get()
        val xmsfFresh = xmsfElapsed != 0L && SystemClock.elapsedRealtime() - xmsfElapsed <= IslandProtocol.HEARTBEAT_LEASE_MS
        val compatible = IslandProtocol.compatible(hookProtocol ?: -1)
        val capable = capabilities and IslandProtocol.REQUIRED_CAPABILITIES == IslandProtocol.REQUIRED_CAPABILITIES
        return IslandBackendHealth(
            available = fresh && xmsfFresh && compatible && capable,
            protocolVersion = hookProtocol,
            capabilities = capabilities,
            lastHandshakeMillis = elapsed.takeIf { it != 0L },
            systemUiHookAlive = fresh,
            xmsfHookAlive = xmsfFresh,
            detail = when {
                !fresh -> "SystemUI handshake is missing or stale"
                !xmsfFresh -> "XMSF hook handshake is missing or stale"
                !compatible -> "Hook protocol is incompatible"
                !capable -> "SystemUI hook lacks required capabilities"
                else -> "SystemUI backend ready"
            },
        )
    }

    private fun send(intent: Intent) {
        sendTo(IslandProtocol.SYSTEM_UI_PACKAGE, intent)
    }

    private fun sendTo(packageName: String, intent: Intent) {
        intent.setPackage(packageName)
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendBroadcast(intent, null, options)
        } else {
            @Suppress("DEPRECATION") context.sendBroadcast(intent)
        }
    }

    private fun trustedHookSender(context: Context, uid: Int): Boolean =
        uid >= 0 && context.packageManager.getPackagesForUid(uid).orEmpty().any {
            it == IslandProtocol.SYSTEM_UI_PACKAGE || it == IslandProtocol.XMSF_PACKAGE
        }

    private fun parcelSize(notification: Notification): Int {
        val parcel = Parcel.obtain()
        return try {
            notification.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    companion object {
        private const val TAG = "SystemUiIslandBackend"
        @Volatile private var instance: SystemUiIslandBackend? = null

        fun get(context: Context): SystemUiIslandBackend = instance ?: synchronized(this) {
            instance ?: SystemUiIslandBackend(context.applicationContext).also { instance = it }
        }
    }
}
