package com.d4viddf.hyperbridge.island.backend

import android.app.Notification
import android.content.Context
import com.d4viddf.hyperbridge.xposed.dispatch.SystemUiDispatcher
import com.d4viddf.hyperbridge.xposed.hooks.ActiveIslandDismissHook
import com.d4viddf.hyperbridge.xposed.hooks.MarqueeHook
import com.d4viddf.hyperbridge.xposed.hooks.OuterGlowHook

/** Direct backend used by the processing engine already running inside SystemUI. */
class InjectedSystemUiIslandBackend(private val systemUiContext: Context) : IslandBackend {
    override fun post(id: Int, notification: Notification, metadata: IslandMetadata): Result<Unit> {
        notification.extras.apply {
            putString(IslandProtocol.EXTRA_OWNER, IslandProtocol.OWNER)
            putString(IslandProtocol.EXTRA_SOURCE_KEY, metadata.sourceKey ?: metadata.logicalToken)
            metadata.sourcePackage?.let { putString(IslandProtocol.EXTRA_SOURCE_PACKAGE, it) }
            metadata.sourceChannel?.let { putString(IslandProtocol.EXTRA_SOURCE_CHANNEL, it) }
            metadata.semanticType?.let { putString(IslandProtocol.EXTRA_SEMANTIC_TYPE, it) }
            putLong(IslandProtocol.EXTRA_GENERATION, metadata.generation)
            putInt(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
        }
        return SystemUiDispatcher.postOwned(
            systemUiContext,
            IslandOwnership.tag(metadata.logicalToken),
            id,
            notification,
            metadata.generation,
        )
    }

    override fun cancel(id: Int, logicalToken: String, generation: Long): Result<Unit> =
        SystemUiDispatcher.cancelOwned(
            systemUiContext,
            IslandOwnership.tag(logicalToken),
            id,
            generation,
        )

    override fun cancelAllOwned(): Result<Unit> = SystemUiDispatcher.cancelAllOwned(systemUiContext)
    override fun ping() = Unit

    override fun health() = IslandBackendHealth(
        available = true,
        protocolVersion = IslandProtocol.VERSION,
        capabilities = IslandProtocol.REQUIRED_CAPABILITIES or
            (if (MarqueeHook.isActive()) IslandProtocol.CAP_MARQUEE else 0) or
            (if (ActiveIslandDismissHook.isActive()) IslandProtocol.CAP_ISLAND_DISMISS else 0) or
            (if (OuterGlowHook.isActive()) IslandProtocol.CAP_FULL_GLOW else 0),
        lastHandshakeMillis = android.os.SystemClock.elapsedRealtime(),
        systemUiHookAlive = true,
        xmsfHookAlive = true,
        detail = "Injected SystemUI backend ready",
    )
}
