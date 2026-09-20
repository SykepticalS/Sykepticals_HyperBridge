package com.d4viddf.hyperbridge.island.backend

import android.content.Context
import android.os.SystemClock
import com.d4viddf.hyperbridge.HyperBridgeApplication
import org.json.JSONObject

object HookConfigSync {
    const val KEY_PROTOCOL = "protocol"
    const val KEY_ENGINE_ENABLED = "engine_enabled"
    const val KEY_NLS_READY = "nls_ready"
    const val KEY_BACKEND_READY = "backend_ready"
    const val KEY_HEARTBEAT = "heartbeat_elapsed"
    const val KEY_ALLOWED_PACKAGES = "allowed_packages"
    const val KEY_TYPE_POLICY = "type_policy"
    const val KEY_FOCUS_ENABLED = "focus_enabled"

    private fun local(context: Context) = context.getSharedPreferences(
        IslandProtocol.REMOTE_PREFS,
        Context.MODE_PRIVATE,
    )

    fun initialize(context: Context) {
        local(context).edit()
            .putInt(KEY_PROTOCOL, IslandProtocol.VERSION)
            .putBoolean(KEY_ENGINE_ENABLED, true)
            .putBoolean(KEY_FOCUS_ENABLED, true)
            .apply()
        sync(context)
    }

    fun updatePolicy(context: Context, packages: Set<String>, globalTypes: Set<String>, overrides: Map<String, Set<String>>) {
        val policy = JSONObject().apply {
            put("global", globalTypes.sorted().joinToString(","))
            put("overrides", JSONObject().apply {
                overrides.toSortedMap().forEach { (pkg, types) -> put(pkg, types.sorted().joinToString(",")) }
            })
        }.toString()
        local(context).edit()
            .putString(KEY_ALLOWED_PACKAGES, packages.sorted().joinToString(","))
            .putString(KEY_TYPE_POLICY, policy)
            .apply()
        sync(context)
    }

    fun heartbeat(context: Context, nlsReady: Boolean) {
        val backend = SystemUiIslandBackend.get(context)
        backend.ping()
        local(context).edit()
            .putBoolean(KEY_NLS_READY, nlsReady)
            .putBoolean(KEY_BACKEND_READY, backend.health().available)
            .putLong(KEY_HEARTBEAT, SystemClock.elapsedRealtime())
            .apply()
        sync(context)
    }

    fun updateBackendHealth(context: Context, health: IslandBackendHealth) {
        local(context).edit().putBoolean(KEY_BACKEND_READY, health.available).apply()
        sync(context)
    }

    fun markStopped(context: Context) {
        local(context).edit()
            .putBoolean(KEY_NLS_READY, false)
            .putBoolean(KEY_BACKEND_READY, false)
            .putLong(KEY_HEARTBEAT, 0L)
            .apply()
        sync(context)
    }

    private fun sync(context: Context) {
        (context.applicationContext as? HyperBridgeApplication)?.syncHookConfig()
    }
}
