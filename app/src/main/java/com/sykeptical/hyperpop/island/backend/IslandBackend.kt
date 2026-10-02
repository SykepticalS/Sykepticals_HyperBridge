package com.sykeptical.hyperpop.island.backend

import android.app.Notification

data class IslandMetadata(
    val logicalToken: String,
    val sourceKey: String? = null,
    val sourcePackage: String? = null,
    val sourceChannel: String? = null,
    val semanticType: String? = null,
    val generation: Long = 0L,
)

data class IslandBackendHealth(
    val available: Boolean,
    val protocolVersion: Int?,
    val capabilities: Int,
    val lastHandshakeMillis: Long?,
    val systemUiHookAlive: Boolean,
    val xmsfHookAlive: Boolean,
    val detail: String,
)

interface IslandBackend {
    fun post(id: Int, notification: Notification, metadata: IslandMetadata): Result<Unit>
    fun update(id: Int, notification: Notification, metadata: IslandMetadata): Result<Unit> =
        post(id, notification, metadata)
    fun cancel(id: Int, logicalToken: String = id.toString(), generation: Long = Long.MAX_VALUE): Result<Unit>
    fun cancelAllOwned(): Result<Unit>
    fun ping()
    fun health(): IslandBackendHealth
    /** Copies sensitive text from SystemUI, which may write the clipboard while this app is in the background. */
    fun copyToClipboard(label: String, text: String, confirmation: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Clipboard relay unavailable"))
}
