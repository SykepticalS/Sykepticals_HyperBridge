package com.d4viddf.hyperbridge.service.popup

import android.app.NotificationManager
import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.service.SemanticSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupSuppressionPolicyTest {
    private fun signature(
        type: NotificationType,
        fallback: NotificationType? = null
    ) = SemanticSignature(type, fallback)

    @Test
    fun messageOnlyChannelIsManagedWhenMessageEnabled() {
        assertEquals(
            ChannelSemanticState.TARGET_ONLY,
            PopupSuppressionPolicy.semanticState(setOf(signature(NotificationType.MESSAGE)), setOf("MESSAGE"))
        )
    }

    @Test
    fun downloadOnlyChannelIsNormalWhenOnlyMessageEnabled() {
        assertEquals(
            ChannelSemanticState.NON_TARGET_ONLY,
            PopupSuppressionPolicy.semanticState(setOf(signature(NotificationType.DOWNLOAD)), setOf("MESSAGE"))
        )
    }

    @Test
    fun callOnlyChannelIsManagedWhenCallEnabled() {
        assertEquals(
            ChannelSemanticState.TARGET_ONLY,
            PopupSuppressionPolicy.semanticState(setOf(signature(NotificationType.CALL)), setOf("CALL"))
        )
    }

    @Test
    fun messageOnlyChannelIsNormalWhenOnlyCallEnabled() {
        assertEquals(
            ChannelSemanticState.NON_TARGET_ONLY,
            PopupSuppressionPolicy.semanticState(setOf(signature(NotificationType.MESSAGE)), setOf("CALL"))
        )
    }

    @Test
    fun partiallyTargetedChannelIsMixedAndConservative() {
        val observed = setOf(signature(NotificationType.MESSAGE), signature(NotificationType.DOWNLOAD))
        assertEquals(
            ChannelSemanticState.MIXED,
            PopupSuppressionPolicy.semanticState(observed, setOf("MESSAGE"))
        )
    }

    @Test
    fun multiTypeChannelIsTargetOnlyWhenEveryKnownTypeEnabled() {
        val observed = setOf(signature(NotificationType.MESSAGE), signature(NotificationType.DOWNLOAD))
        assertEquals(
            ChannelSemanticState.TARGET_ONLY,
            PopupSuppressionPolicy.semanticState(observed, setOf("MESSAGE", "DOWNLOAD"))
        )
    }

    @Test
    fun directMessageStandardFallbackUsesSameTranslationPolicy() {
        assertEquals(
            ChannelSemanticState.TARGET_ONLY,
            PopupSuppressionPolicy.semanticState(
                setOf(signature(NotificationType.MESSAGE, NotificationType.STANDARD)),
                setOf("STANDARD")
            )
        )
    }

    @Test
    fun directAndAggregateMessagesBecomeMixedWithStandardOnly() {
        val observed = setOf(
            signature(NotificationType.MESSAGE, NotificationType.STANDARD),
            signature(NotificationType.MESSAGE)
        )
        assertEquals(ChannelSemanticState.MIXED, PopupSuppressionPolicy.semanticState(observed, setOf("STANDARD")))
    }

    @Test
    fun unknownChannelIsNeverTargeted() {
        assertEquals(ChannelSemanticState.UNKNOWN, PopupSuppressionPolicy.semanticState(emptySet(), setOf("MESSAGE")))
    }

    @Test
    fun onlyHighAndMaxAreDemoted() {
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, PopupSuppressionPolicy.appliedImportance(NotificationManager.IMPORTANCE_HIGH))
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, PopupSuppressionPolicy.appliedImportance(NotificationManager.IMPORTANCE_MAX))
        assertNull(PopupSuppressionPolicy.appliedImportance(NotificationManager.IMPORTANCE_DEFAULT))
        assertNull(PopupSuppressionPolicy.appliedImportance(NotificationManager.IMPORTANCE_LOW))
        assertNull(PopupSuppressionPolicy.appliedImportance(NotificationManager.IMPORTANCE_MIN))
        assertNull(PopupSuppressionPolicy.appliedImportance(NotificationManager.IMPORTANCE_NONE))
    }

    @Test
    fun restorationRequiresCurrentValueToEqualAppliedValue() {
        assertTrue(PopupSuppressionPolicy.canSafelyRestore(NotificationManager.IMPORTANCE_DEFAULT, NotificationManager.IMPORTANCE_DEFAULT))
        assertFalse(PopupSuppressionPolicy.canSafelyRestore(NotificationManager.IMPORTANCE_LOW, NotificationManager.IMPORTANCE_DEFAULT))
    }
}
