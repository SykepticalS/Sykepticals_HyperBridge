package com.d4viddf.hyperbridge.xposed

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.island.backend.HookConfigSync
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.models.CallStage
import com.d4viddf.hyperbridge.service.call.CallActionSignal
import com.d4viddf.hyperbridge.service.call.CallNotificationClassifier
import com.d4viddf.hyperbridge.service.call.CallNotificationSignals
import com.d4viddf.hyperbridge.service.call.CallState
import io.github.libxposed.api.XposedModule
import org.json.JSONObject

object HookConfig {
    @Volatile private var module: XposedModule? = null
    @Volatile private var prefs: android.content.SharedPreferences? = null
    @Volatile private var callClassifier: CallNotificationClassifier? = null
    @Volatile private var answerKeywords = listOf("answer", "accept")
    @Volatile private var declineKeywords = listOf("hang", "end", "decline", "reject")

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

    /**
     * Fast prediction for the incoming-call banner. Full-screen intent normally bypasses
     * heads-up suppression; this allows that banner only when an incoming call island
     * will replace it.
     */
    fun expectsIncomingCallBannerSuppression(sbn: StatusBarNotification): Boolean {
        if (!suppressSourceHeadsUp()) return false
        val preferences = prefs ?: return false
        if (preferences.getInt(HookConfigSync.KEY_PROTOCOL, -1) != IslandProtocol.VERSION ||
            !preferences.getBoolean(HookConfigSync.KEY_ENGINE_ENABLED, false)
        ) return false
        val packageName = sbn.packageName ?: return false
        if (!packageAllowed(preferences, packageName)) return false
        val notification = sbn.notification ?: return false
        return IncomingCallBannerPolicy.shouldSuppress(
            suppressSourceEnabled = true,
            engineReady = true,
            packageAllowed = true,
            callIslandsEnabled = callIslandsEnabled(preferences, packageName),
            incomingStageEnabled = incomingStageEnabled(preferences, packageName),
            hasFullScreenIntent = notification.fullScreenIntent != null,
            isIncomingCall = isIncomingCall(notification),
        )
    }

    fun refreshCallKeywords(context: Context) {
        val resources = runCatching {
            context.createPackageContext(
                IslandProtocol.APP_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY,
            ).resources
        }.getOrNull() ?: return
        val answer = runCatching { resources.getStringArray(R.array.call_keywords_answer).toList() }.getOrNull()
        val decline = runCatching { resources.getStringArray(R.array.call_keywords_hangup).toList() }.getOrNull()
        if (!answer.isNullOrEmpty()) answerKeywords = answer
        if (!decline.isNullOrEmpty()) declineKeywords = decline
        callClassifier = null
    }

    private fun packageAllowed(preferences: android.content.SharedPreferences, packageName: String): Boolean {
        return preferences.getString(HookConfigSync.KEY_ALLOWED_PACKAGES, "")
            .orEmpty()
            .split(',')
            .any { it == packageName }
    }

    private fun callIslandsEnabled(preferences: android.content.SharedPreferences, packageName: String): Boolean {
        val policy = runCatching {
            JSONObject(preferences.getString(HookConfigSync.KEY_TYPE_POLICY, "{}") ?: "{}")
        }.getOrNull() ?: return false
        val configured = policy.optJSONObject("overrides")
            ?.optString(packageName)?.takeIf(String::isNotBlank)
            ?: policy.optString("global")
        if (configured.isBlank() && !policy.has("global") && policy.optJSONObject("overrides")?.has(packageName) != true) {
            return false
        }
        return configured.split(',').any { it == "CALL" }
    }

    private fun incomingStageEnabled(preferences: android.content.SharedPreferences, packageName: String): Boolean {
        val raw = preferences.getString(HookConfigSync.KEY_CALL_STAGE_POLICY, null)
        if (raw.isNullOrBlank()) return true
        val policy = runCatching { JSONObject(raw) }.getOrNull() ?: return true
        val overrides = policy.optJSONObject("overrides")
        val configured = if (overrides?.has(packageName) == true) {
            overrides.optString(packageName)
        } else {
            policy.optString("global", CallStage.entries.joinToString(",") { it.name })
        }
        return configured.split(',').any { it == CallStage.INCOMING.name }
    }

    private fun isIncomingCall(notification: Notification): Boolean {
        val extras = notification.extras ?: Bundle.EMPTY
        val callType = if (extras.containsKey(Notification.EXTRA_CALL_TYPE)) {
            extras.getInt(Notification.EXTRA_CALL_TYPE, CallNotificationClassifier.CALL_TYPE_UNKNOWN)
        } else {
            null
        }
        val actions = notification.actions?.map { action ->
            CallActionSignal(
                title = action.title?.toString().orEmpty(),
                semanticAction = action.semanticAction,
                hasPendingIntent = action.actionIntent != null,
            )
        }.orEmpty()
        val classification = classifier().classify(
            CallNotificationSignals(
                category = notification.category,
                template = extras.getString(Notification.EXTRA_TEMPLATE),
                callType = callType,
                showsChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
                whenTime = notification.`when`,
                actions = actions,
                isOngoingEvent = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
                isForegroundService = notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
                isVideoCall = extras.getBoolean(Notification.EXTRA_CALL_IS_VIDEO, false),
            )
        )
        return classification.isCall && classification.state == CallState.INCOMING_RINGING
    }

    private fun classifier(): CallNotificationClassifier {
        callClassifier?.let { return it }
        return CallNotificationClassifier(
            answerKeywords = answerKeywords,
            declineKeywords = declineKeywords,
            hangUpKeywords = declineKeywords,
            muteKeywords = emptyList(),
            unmuteKeywords = emptyList(),
            speakerKeywords = emptyList(),
        ).also { callClassifier = it }
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
