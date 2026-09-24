package com.d4viddf.hyperbridge.service

import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.models.theme.HyperTheme

data class SemanticSignature(
    val primaryType: NotificationType,
    val fallbackType: NotificationType? = null
) {
    fun isReplacedBy(effectiveTypes: Set<String>): Boolean =
        NotificationTypeEnablementPolicy.isEnabled(effectiveTypes, primaryType.name) ||
            fallbackType?.let { NotificationTypeEnablementPolicy.isEnabled(effectiveTypes, it.name) } == true
}

data class SemanticNotificationResult(
    val rawType: NotificationType,
    val detectedType: NotificationType,
    val signature: SemanticSignature,
    val enabledType: NotificationType?
)

data class RawNotificationTypeSignals(
    val isScreenRecording: Boolean,
    val isCall: Boolean,
    val isNavigation: Boolean,
    val isTimer: Boolean,
    val isMedia: Boolean,
    val isMessage: Boolean,
    val hasProgress: Boolean,
    val isDownload: Boolean,
    val isVoice: Boolean = false,
)

object RawNotificationTypeClassifier {
    fun classify(signals: RawNotificationTypeSignals): NotificationType = when {
        signals.isScreenRecording -> NotificationType.SCREEN_RECORDING
        signals.isCall -> NotificationType.CALL
        signals.isNavigation -> NotificationType.NAVIGATION
        signals.isTimer -> NotificationType.TIMER
        signals.isMedia -> NotificationType.MEDIA
        signals.isMessage -> NotificationType.MESSAGE
        signals.isVoice -> NotificationType.VOICE_MESSAGE
        signals.hasProgress && signals.isDownload -> NotificationType.DOWNLOAD
        signals.hasProgress -> NotificationType.PROGRESS
        else -> NotificationType.STANDARD
    }
}

object EffectiveNotificationTypePolicy {
    fun resolve(
        theme: HyperTheme?,
        packageName: String,
        appTypes: Set<String>?,
        globalTypes: Set<String>
    ): Set<String> {
        val raw = theme?.apps?.get(packageName)?.activeNotificationTypes ?: appTypes ?: globalTypes
        return normalize(raw)
    }

    fun normalize(rawTypes: Set<String>): Set<String> =
        if ("PROGRESS" in rawTypes && "DOWNLOAD" !in rawTypes) rawTypes + "DOWNLOAD" else rawTypes
}

class EffectiveNotificationTypeResolver(
    private val preferences: AppPreferences,
    private val activeTheme: () -> HyperTheme?
) {
    fun getEffectiveTypes(packageName: String): Set<String> =
        EffectiveNotificationTypePolicy.resolve(
            theme = activeTheme(),
            packageName = packageName,
            appTypes = preferences.getAppConfigSync(packageName),
            globalTypes = preferences.getGlobalNotificationTypesSync()
        )

    suspend fun getEffectiveTypesFresh(packageName: String): Set<String> =
        EffectiveNotificationTypePolicy.resolve(
            theme = activeTheme(),
            packageName = packageName,
            appTypes = preferences.getAppConfigFresh(packageName),
            globalTypes = preferences.getGlobalNotificationTypes()
        )
}

object SemanticNotificationResolver {
    fun resolve(
        rawType: NotificationType,
        ruleTargetLayout: String?,
        hasDirectMessagingStyle: Boolean,
        effectiveTypes: Set<String>
    ): SemanticNotificationResult {
        val detected = ruleTargetLayout
            ?.let { runCatching { NotificationType.valueOf(it) }.getOrNull() }
            ?: rawType
        val fallback = NotificationType.STANDARD.takeIf {
            detected == NotificationType.MESSAGE && hasDirectMessagingStyle
        }
        val enabled = NotificationTypeEnablementPolicy.resolveEnabledType(
            effectiveTypes = effectiveTypes,
            detectedType = detected.name,
            hasDirectMessagingStyle = hasDirectMessagingStyle
        )?.let(NotificationType::valueOf)
        return SemanticNotificationResult(
            rawType = rawType,
            detectedType = detected,
            signature = SemanticSignature(detected, fallback),
            enabledType = enabled
        )
    }
}
