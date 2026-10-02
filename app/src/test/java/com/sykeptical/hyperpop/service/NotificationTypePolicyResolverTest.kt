package com.sykeptical.hyperpop.service

import com.sykeptical.hyperpop.models.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTypePolicyResolverTest {
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
    fun appOverrideBeatsGlobal() {
        assertEquals(
            setOf("MESSAGE"),
            EffectiveNotificationTypePolicy.resolve(
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
