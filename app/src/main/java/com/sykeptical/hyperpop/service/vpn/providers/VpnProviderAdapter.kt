package com.sykeptical.hyperpop.service.vpn.providers

import android.app.PendingIntent
import android.content.Context
import com.sykeptical.hyperpop.service.vpn.VpnConnectionState
import com.sykeptical.hyperpop.service.vpn.VpnControlProvenance
import com.sykeptical.hyperpop.service.vpn.VpnNotificationSignals

data class VpnAdapterCapabilities(
    val notificationMetadata: Boolean,
    val notificationState: Boolean,
    val notificationTraffic: Boolean,
    val notificationTimestamp: Boolean,
    val directDisconnect: VpnControlProvenance? = null,
    val requiredPermission: String? = null
)

data class VpnAdapterNotificationResult(
    val state: VpnConnectionState? = null,
    val disconnectActionIndex: Int? = null
)

interface VpnProviderAdapter {
    val id: String
    val packageNames: Set<String>
    val capabilities: VpnAdapterCapabilities
    val stateStringResources: Map<VpnConnectionState, Set<String>> get() = emptyMap()
    val destinationStringResources: Set<String> get() = emptySet()
    val trustsContentAsConnectedDestination: Boolean get() = false

    fun matches(packageName: String): Boolean = packageName in packageNames
    fun analyzeNotification(signals: VpnNotificationSignals): VpnAdapterNotificationResult =
        VpnAdapterNotificationResult()

    fun createDisconnectPendingIntent(context: Context, packageName: String): PendingIntent? = null
}

