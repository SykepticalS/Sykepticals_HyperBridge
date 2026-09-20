package com.d4viddf.hyperbridge.xposed

import android.app.Notification
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.island.backend.HookConfigSync
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.xposed.runtime.HealthLease
import com.d4viddf.hyperbridge.xposed.runtime.SuppressionEligibility
import com.d4viddf.hyperbridge.xposed.runtime.SuppressionSignals
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

    fun healthy(): Boolean {
        val p = prefs ?: return false
        val heartbeat = p.getLong(HookConfigSync.KEY_HEARTBEAT, 0L)
        return p.getInt(HookConfigSync.KEY_PROTOCOL, -1) == IslandProtocol.VERSION &&
            p.getBoolean(HookConfigSync.KEY_ENGINE_ENABLED, false) &&
            p.getBoolean(HookConfigSync.KEY_NLS_READY, false) &&
            p.getBoolean(HookConfigSync.KEY_BACKEND_READY, false) &&
            HealthLease.fresh(SystemClock.elapsedRealtime(), heartbeat, IslandProtocol.HEARTBEAT_LEASE_MS)
    }

    fun focusEnabled(): Boolean = prefs?.getBoolean(HookConfigSync.KEY_FOCUS_ENABLED, true) == true

    fun expectsReplacement(sbn: StatusBarNotification): Boolean {
        val packageName = sbn.packageName ?: return false
        val allowed = prefs?.getString(HookConfigSync.KEY_ALLOWED_PACKAGES, "")
            .orEmpty().split(',').any { it == packageName }
        val type = classify(sbn.notification ?: return false) ?: return false
        val policy = runCatching { JSONObject(prefs?.getString(HookConfigSync.KEY_TYPE_POLICY, "{}") ?: "{}") }
            .getOrNull() ?: return false
        val overrides = policy.optJSONObject("overrides")
        val enabled = overrides?.optString(packageName)?.takeIf(String::isNotBlank)
            ?: policy.optString("global")
        val enabledTypes = enabled.split(',').filter(String::isNotBlank).toMutableSet()
        if ("DOWNLOAD" in enabledTypes) enabledTypes += "PROGRESS"
        return SuppressionEligibility.shouldSuppress(
            SuppressionSignals(
                engineHealthy = healthy(),
                fullScreenIntent = sbn.notification.fullScreenIntent != null,
                packageEnabled = allowed,
                semanticType = type,
                enabledTypes = enabledTypes,
            )
        )
    }

    private fun classify(notification: Notification): String? {
        if (notification.fullScreenIntent != null) return null
        val extras = notification.extras ?: Bundle.EMPTY
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return when {
            notification.category == Notification.CATEGORY_CALL || template.contains("CallStyle") -> "CALL"
            notification.category == Notification.CATEGORY_MESSAGE || template.contains("MessagingStyle") -> "MESSAGE"
            template.contains("MediaStyle") || notification.category == Notification.CATEGORY_TRANSPORT -> "MEDIA"
            extras.containsKey(Notification.EXTRA_PROGRESS_MAX) &&
                (extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 || extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) -> "PROGRESS"
            notification.category == Notification.CATEGORY_ALARM && extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) -> "TIMER"
            notification.category == "navigation" -> "NAVIGATION"
            else -> null
        }
    }
}
