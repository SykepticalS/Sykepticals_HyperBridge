package com.d4viddf.hyperbridge.island.backend

import android.content.Context
import android.os.SystemClock
import com.d4viddf.hyperbridge.HyperBridgeApplication
import org.json.JSONObject

object HookConfigSync {
    const val KEY_PROTOCOL = "protocol"
    const val KEY_ENGINE_ENABLED = "engine_enabled"
    const val KEY_BACKEND_READY = "backend_ready"
    const val KEY_HEARTBEAT = "heartbeat_elapsed"
    const val KEY_ALLOWED_PACKAGES = "allowed_packages"
    const val KEY_TYPE_POLICY = "type_policy"
    const val KEY_CALL_STAGE_POLICY = "call_stage_policy"
    const val KEY_FOCUS_ENABLED = "focus_enabled"
    const val KEY_SUPPRESS_SOURCE_HEADS_UP = "suppress_source_heads_up"
    const val KEY_MARQUEE_SPEED = "marquee_speed"
    const val KEY_GLOW_RANGE = "glow_range"
    const val KEY_SINGLE_COLOR_GLOW = "single_color_glow"
    const val KEY_GLOW_BASE_COLOR = "glow_base_color"
    const val KEY_SCREEN_RECORDER_REPLACE = "screen_recorder_replace"
    const val KEY_SCREEN_RECORDER_IMMEDIATE_START = "screen_recorder_immediate_start"
    const val KEY_SCREEN_RECORDER_ICON_STYLE = "screen_recorder_icon_style"

    private fun local(context: Context) = context.getSharedPreferences(
        IslandProtocol.REMOTE_PREFS,
        Context.MODE_PRIVATE,
    )

    fun initialize(context: Context) {
        local(context).edit()
            .remove("nls_ready")
            .putInt(KEY_PROTOCOL, IslandProtocol.VERSION)
            .putBoolean(KEY_ENGINE_ENABLED, true)
            .putBoolean(KEY_FOCUS_ENABLED, true)
            .putBoolean(KEY_SUPPRESS_SOURCE_HEADS_UP,
                local(context).getBoolean(KEY_SUPPRESS_SOURCE_HEADS_UP, true))
            .putInt(KEY_MARQUEE_SPEED, local(context).getInt(KEY_MARQUEE_SPEED, 100).coerceIn(20, 500))
            .putInt(KEY_GLOW_RANGE, local(context).getInt(KEY_GLOW_RANGE, 100).coerceIn(0, 100))
            .putBoolean(KEY_SINGLE_COLOR_GLOW, local(context).getBoolean(KEY_SINGLE_COLOR_GLOW, false))
            .putString(KEY_GLOW_BASE_COLOR, migratedGlowBaseColor(local(context).getString(KEY_GLOW_BASE_COLOR, "")))
            .putBoolean(KEY_SCREEN_RECORDER_REPLACE, local(context).getBoolean(KEY_SCREEN_RECORDER_REPLACE, true))
            .putBoolean(
                KEY_SCREEN_RECORDER_IMMEDIATE_START,
                local(context).getBoolean(KEY_SCREEN_RECORDER_IMMEDIATE_START, false),
            )
            .putString(
                KEY_SCREEN_RECORDER_ICON_STYLE,
                local(context).getString(KEY_SCREEN_RECORDER_ICON_STYLE, "screen_recorder"),
            )
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

    fun updateCallStagePolicy(
        context: Context,
        globalStages: Set<String>,
        overrides: Map<String, Set<String>>,
    ) {
        val policy = JSONObject().apply {
            put("global", globalStages.sorted().joinToString(","))
            put("overrides", JSONObject().apply {
                overrides.toSortedMap().forEach { (pkg, stages) -> put(pkg, stages.sorted().joinToString(",")) }
            })
        }.toString()
        local(context).edit().putString(KEY_CALL_STAGE_POLICY, policy).apply()
        sync(context)
    }

    fun heartbeat(context: Context) {
        val backend = SystemUiIslandBackend.get(context)
        backend.ping()
        local(context).edit()
            .putBoolean(KEY_BACKEND_READY, backend.health().available)
            .putLong(KEY_HEARTBEAT, SystemClock.elapsedRealtime())
            .apply()
        sync(context)
    }

    fun updateBackendHealth(context: Context, health: IslandBackendHealth) {
        local(context).edit().putBoolean(KEY_BACKEND_READY, health.available).apply()
        sync(context)
    }

    fun setSuppressSourceHeadsUp(context: Context, enabled: Boolean) {
        local(context).edit().putBoolean(KEY_SUPPRESS_SOURCE_HEADS_UP, enabled).apply()
        sync(context)
    }

    fun suppressSourceHeadsUp(context: Context): Boolean =
        local(context).getBoolean(KEY_SUPPRESS_SOURCE_HEADS_UP, true)

    fun marqueeSpeed(context: Context): Int = local(context).getInt(KEY_MARQUEE_SPEED, 100).coerceIn(20, 500)
    fun glowRange(context: Context): Int = local(context).getInt(KEY_GLOW_RANGE, 100).coerceIn(0, 100)
    fun singleColorGlow(context: Context): Boolean = local(context).getBoolean(KEY_SINGLE_COLOR_GLOW, false)
    fun glowBaseColor(context: Context): String = migratedGlowBaseColor(local(context).getString(KEY_GLOW_BASE_COLOR, ""))
    fun replaceScreenRecorder(context: Context): Boolean =
        local(context).getBoolean(KEY_SCREEN_RECORDER_REPLACE, true)
    fun screenRecorderImmediateStart(context: Context): Boolean =
        local(context).getBoolean(KEY_SCREEN_RECORDER_IMMEDIATE_START, false)
    fun screenRecorderIconStyle(context: Context): String =
        local(context).getString(KEY_SCREEN_RECORDER_ICON_STYLE, "screen_recorder") ?: "screen_recorder"

    fun setScreenRecorderReplacement(
        context: Context,
        replaceFloatingWindow: Boolean,
        immediateStart: Boolean,
        iconStyle: String,
    ) {
        local(context).edit()
            .putBoolean(KEY_SCREEN_RECORDER_REPLACE, replaceFloatingWindow)
            .putBoolean(KEY_SCREEN_RECORDER_IMMEDIATE_START, immediateStart)
            .putString(KEY_SCREEN_RECORDER_ICON_STYLE, iconStyle)
            .apply()
        sync(context)
    }

    fun setVisualTuning(context: Context, speed: Int, range: Int, singleColor: Boolean, baseColor: String) {
        local(context).edit()
            .putInt(KEY_MARQUEE_SPEED, speed.coerceIn(20, 500))
            .putInt(KEY_GLOW_RANGE, range.coerceIn(0, 100))
            .putBoolean(KEY_SINGLE_COLOR_GLOW, singleColor)
            .putString(KEY_GLOW_BASE_COLOR, migratedGlowBaseColor(baseColor))
            .apply()
        sync(context)
    }

    private fun migratedGlowBaseColor(raw: String?): String {
        val value = raw?.trim().orEmpty()
        return if (value.equals("#FFFFFF", ignoreCase = true) || value.equals("#FFFFFFFF", ignoreCase = true)) {
            ""
        } else {
            value
        }
    }

    private fun sync(context: Context) {
        (context.applicationContext as? HyperBridgeApplication)?.syncHookConfig()
    }
}
