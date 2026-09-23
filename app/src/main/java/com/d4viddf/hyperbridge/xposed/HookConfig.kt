package com.d4viddf.hyperbridge.xposed

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.island.backend.HookConfigSync
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import io.github.libxposed.api.XposedModule
import org.json.JSONObject

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

    internal fun remotePreferences(): android.content.SharedPreferences? = prefs

    fun focusEnabled(): Boolean = prefs?.getBoolean(HookConfigSync.KEY_FOCUS_ENABLED, true) == true
    fun suppressSourceHeadsUp(): Boolean =
        prefs?.getBoolean(HookConfigSync.KEY_SUPPRESS_SOURCE_HEADS_UP, true) ?: true
    fun marqueeSpeed(): Int = prefs?.getInt(HookConfigSync.KEY_MARQUEE_SPEED, 100)?.coerceIn(20, 500) ?: 100
    fun glowRange(): Int = prefs?.getInt(HookConfigSync.KEY_GLOW_RANGE, 100)?.coerceIn(0, 100) ?: 100
    fun singleColorGlow(): Boolean = prefs?.getBoolean(HookConfigSync.KEY_SINGLE_COLOR_GLOW, false) ?: false
    fun glowBaseColor(): String? = prefs?.getString(HookConfigSync.KEY_GLOW_BASE_COLOR, "")
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("#FFFFFF", ignoreCase = true) && !it.equals("#FFFFFFFF", ignoreCase = true) }
    fun replaceScreenRecorder(): Boolean =
        prefs?.getBoolean(HookConfigSync.KEY_SCREEN_RECORDER_REPLACE, true) ?: true
    fun screenRecorderImmediateStart(): Boolean =
        prefs?.getBoolean(HookConfigSync.KEY_SCREEN_RECORDER_IMMEDIATE_START, false) ?: false
    fun screenRecorderIconStyle(): String =
        prefs?.getString(HookConfigSync.KEY_SCREEN_RECORDER_ICON_STYLE, "screen_recorder")
            ?.takeIf { it.isNotBlank() }
            ?: "screen_recorder"

    /** Fast, local and fail-open prediction used before SystemUI evaluates heads-up state. */
    fun expectsReplacement(sbn: StatusBarNotification): Boolean {
        if (!suppressSourceHeadsUp()) return false
        val p = prefs ?: return false
        if (p.getInt(HookConfigSync.KEY_PROTOCOL, -1) != IslandProtocol.VERSION ||
            !p.getBoolean(HookConfigSync.KEY_ENGINE_ENABLED, false)
        ) return false
        val notification = sbn.notification ?: return false
        if (notification.fullScreenIntent != null) return false
        val packageName = sbn.packageName ?: return false
        val allowed = p.getString(HookConfigSync.KEY_ALLOWED_PACKAGES, "")
            .orEmpty().split(',').any { it == packageName }
        if (!allowed) return false
        val semanticType = classify(sbn) ?: return false
        val policy = runCatching {
            JSONObject(p.getString(HookConfigSync.KEY_TYPE_POLICY, "{}") ?: "{}")
        }.getOrNull() ?: return false
        val configured = policy.optJSONObject("overrides")
            ?.optString(packageName)?.takeIf(String::isNotBlank)
            ?: policy.optString("global")
        val enabledTypes = configured.split(',').filter(String::isNotBlank).toMutableSet()
        if ("DOWNLOAD" in enabledTypes) enabledTypes += "PROGRESS"
        enabledTypes += "SCREEN_RECORDING"
        val directMessagingStyle = notification.extras
            ?.getString(Notification.EXTRA_TEMPLATE)
            .orEmpty()
            .contains("MessagingStyle")
        return SourceHeadsUpReplacementPolicy.expectsReplacement(
            packageName = packageName,
            semanticType = semanticType,
            enabledTypes = enabledTypes,
            directMessagingStyle = directMessagingStyle,
        )
    }

    private fun classify(sbn: StatusBarNotification): String? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: Bundle.EMPTY
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return when {
            sbn.packageName == IslandProtocol.SCREEN_RECORDER_PACKAGE && sbn.id == 110 -> "SCREEN_RECORDING"
            notification.category == Notification.CATEGORY_CALL || template.contains("CallStyle") -> "CALL"
            notification.category == Notification.CATEGORY_MESSAGE || template.contains("MessagingStyle") -> "MESSAGE"
            template.contains("MediaStyle") || notification.category == Notification.CATEGORY_TRANSPORT -> "MEDIA"
            extras.containsKey(Notification.EXTRA_PROGRESS_MAX) &&
                (extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 ||
                    extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) -> "PROGRESS"
            notification.category == Notification.CATEGORY_ALARM &&
                extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) -> "TIMER"
            notification.category == Notification.CATEGORY_NAVIGATION -> "NAVIGATION"
            else -> "STANDARD"
        }
    }
}
