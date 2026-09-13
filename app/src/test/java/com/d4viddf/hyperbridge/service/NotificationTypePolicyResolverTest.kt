package com.d4viddf.hyperbridge.service

import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.models.theme.AppThemeOverride
import com.d4viddf.hyperbridge.models.theme.GlobalConfig
import com.d4viddf.hyperbridge.models.theme.HyperTheme
import com.d4viddf.hyperbridge.models.theme.ThemeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTypePolicyResolverTest {
    private fun theme(types: Set<String>?) = HyperTheme(
        id = "test",
        meta = ThemeMetadata("Test", "HyperBridge"),
        global = GlobalConfig(),
        apps = mapOf("com.example" to AppThemeOverride(activeNotificationTypes = types))
    )

    @Test
    fun progressCompatibilityAlsoEnablesDownload() {
        assertEquals(setOf("PROGRESS", "DOWNLOAD"), EffectiveNotificationTypePolicy.normalize(setOf("PROGRESS")))
    }

    @Test
    fun semanticResultCarriesRawRuleAdjustedAndEnabledTypes() {
        val result = SemanticNotificationResolver.resolve(
            rawType = NotificationType.STANDARD,
            ruleTargetLayout = "CALL",
            hasDirectMessagingStyle = false,
            effectiveTypes = setOf("CALL")
        )
        assertEquals(NotificationType.STANDARD, result.rawType)
        assertEquals(NotificationType.CALL, result.detectedType)
        assertEquals(NotificationType.CALL, result.enabledType)
        assertEquals(NotificationType.CALL, result.signature.primaryType)
    }

    @Test
    fun disabledSemanticTypeStillProducesObservationSignature() {
        val result = SemanticNotificationResolver.resolve(
            rawType = NotificationType.DOWNLOAD,
            ruleTargetLayout = null,
            hasDirectMessagingStyle = false,
            effectiveTypes = setOf("MESSAGE")
        )
        assertNull(result.enabledType)
        assertEquals(NotificationType.DOWNLOAD, result.signature.primaryType)
    }

    @Test
    fun directMessageFallbackIsRepresentedInCanonicalResult() {
        val result = SemanticNotificationResolver.resolve(
            rawType = NotificationType.MESSAGE,
            ruleTargetLayout = null,
            hasDirectMessagingStyle = true,
            effectiveTypes = setOf("STANDARD")
        )
        assertEquals(NotificationType.STANDARD, result.enabledType)
        assertEquals(NotificationType.STANDARD, result.signature.fallbackType)
    }

    @Test
    fun themeOverrideBeatsAppAndGlobalTypes() {
        assertEquals(
            setOf("CALL"),
            EffectiveNotificationTypePolicy.resolve(
                theme(setOf("CALL")),
                "com.example",
                appTypes = setOf("MESSAGE"),
                globalTypes = setOf("DOWNLOAD")
            )
        )
    }

    @Test
    fun appOverrideBeatsGlobalWhenThemeHasNoTypeOverride() {
        assertEquals(
            setOf("MESSAGE"),
            EffectiveNotificationTypePolicy.resolve(
                theme(null),
                "com.example",
                appTypes = setOf("MESSAGE"),
                globalTypes = setOf("DOWNLOAD")
            )
        )
    }

    @Test
    fun rawClassifierUsesCanonicalPrecedence() {
        val result = RawNotificationTypeClassifier.classify(
            RawNotificationTypeSignals(
                isScreenRecording = false,
                isCall = true,
                isNavigation = false,
                isTimer = false,
                isMedia = false,
                isMessage = true,
                hasProgress = true,
                isDownload = true
            )
        )
        assertEquals(NotificationType.CALL, result)
    }
}
