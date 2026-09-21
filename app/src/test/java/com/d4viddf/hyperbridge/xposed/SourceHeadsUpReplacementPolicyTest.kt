package com.d4viddf.hyperbridge.xposed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHeadsUpReplacementPolicyTest {
    @Test
    fun `whatsapp message summary is covered by standard replacement`() {
        assertTrue(
            SourceHeadsUpReplacementPolicy.expectsReplacement(
                packageName = "com.whatsapp",
                semanticType = "MESSAGE",
                enabledTypes = setOf("STANDARD"),
                directMessagingStyle = false,
            ),
        )
    }

    @Test
    fun `whatsapp business message summary is covered by standard replacement`() {
        assertTrue(
            SourceHeadsUpReplacementPolicy.expectsReplacement(
                packageName = "com.whatsapp.w4b",
                semanticType = "MESSAGE",
                enabledTypes = setOf("STANDARD"),
                directMessagingStyle = false,
            ),
        )
    }

    @Test
    fun `unrelated inbox summary is not suppressed without a matching replacement`() {
        assertFalse(
            SourceHeadsUpReplacementPolicy.expectsReplacement(
                packageName = "example.app",
                semanticType = "MESSAGE",
                enabledTypes = setOf("STANDARD"),
                directMessagingStyle = false,
            ),
        )
    }
}
