package com.d4viddf.hyperbridge.xposed

import com.d4viddf.hyperbridge.island.backend.HookConfigSync
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import io.github.libxposed.api.XposedModule

object HookConfig {
    @Volatile private var module: XposedModule? = null
    @Volatile private var prefs: android.content.SharedPreferences? = null

    @Synchronized
    fun initialize(value: XposedModule) {
        if (prefs != null) return
        module = value
        prefs = runCatching { value.getRemotePreferences(IslandProtocol.REMOTE_PREFS) }
            .onFailure { value.log("HyperBridge: RemotePreferences unavailable: ${it.message}") }
            .getOrNull()
    }

    fun focusEnabled(): Boolean = prefs?.getBoolean(HookConfigSync.KEY_FOCUS_ENABLED, true) == true
    fun suppressSourceHeadsUp(): Boolean =
        prefs?.getBoolean(HookConfigSync.KEY_SUPPRESS_SOURCE_HEADS_UP, true) ?: true
}
