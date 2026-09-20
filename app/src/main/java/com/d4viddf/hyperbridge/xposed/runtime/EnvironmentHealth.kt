package com.d4viddf.hyperbridge.xposed.runtime

import android.content.Context
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend
import com.d4viddf.hyperbridge.service.NotificationReaderService
import com.d4viddf.hyperbridge.util.DeviceUtils
import com.d4viddf.hyperbridge.util.isNotificationServiceEnabled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EnvironmentHealth(
    val supportedDevice: Boolean,
    val rootAvailable: Boolean,
    val libxposedServiceAvailable: Boolean,
    val moduleApiCompatible: Boolean,
    val systemUiScope: Boolean,
    val xmsfScope: Boolean,
    val systemUiHookAlive: Boolean,
    val xmsfHookConfigured: Boolean,
    val focusCompatible: Boolean,
    val notificationListenerProvisioned: Boolean,
    val notificationEngineReady: Boolean,
    val backendProtocolCompatible: Boolean,
) {
    val privilegedReady: Boolean get() = supportedDevice && rootAvailable && moduleApiCompatible &&
        systemUiScope && xmsfScope && systemUiHookAlive && xmsfHookConfigured && focusCompatible &&
        notificationListenerProvisioned && notificationEngineReady && backendProtocolCompatible
}

object EnvironmentRuntime {
    private val root = MutableStateFlow(false)
    val rootAvailable = root.asStateFlow()
    fun setRootAvailable(value: Boolean) { root.value = value }

    fun snapshot(context: Context): EnvironmentHealth {
        val module = ModuleServiceState.state.value
        val backend = SystemUiIslandBackend.get(context).health()
        return EnvironmentHealth(
            supportedDevice = DeviceUtils.isXiaomi && DeviceUtils.isCompatibleOS(),
            rootAvailable = root.value,
            libxposedServiceAvailable = module.available,
            moduleApiCompatible = module.available && module.apiVersion >= 101,
            systemUiScope = IslandProtocol.SYSTEM_UI_PACKAGE in module.scopes,
            xmsfScope = IslandProtocol.XMSF_PACKAGE in module.scopes,
            systemUiHookAlive = backend.systemUiHookAlive,
            xmsfHookConfigured = backend.xmsfHookAlive,
            focusCompatible = backend.capabilities and IslandProtocol.CAP_FOCUS_BYPASS != 0,
            notificationListenerProvisioned = isNotificationServiceEnabled(context),
            notificationEngineReady = NotificationReaderService.isConnected,
            backendProtocolCompatible = IslandProtocol.compatible(backend.protocolVersion ?: -1),
        )
    }
}
