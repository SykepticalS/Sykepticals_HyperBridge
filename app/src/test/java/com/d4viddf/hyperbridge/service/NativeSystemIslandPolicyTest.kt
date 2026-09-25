package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSystemIslandPolicyTest {
    @Test
    fun stubOnlyAbsorbsNativeSystemIslands() {
        assertTrue(
            NativeSystemIslandPolicy.shouldAbsorb(
                permanentPosted = true,
                occupyingLogicalId = null,
                realIslandCount = 0,
            )
        )
        assertTrue(
            NativeSystemIslandPolicy.shouldAbsorbFromPosted(
                permanentPosted = true,
                permanentSemanticType = "PERMANENT",
                extraOwnedIslandCount = 0,
            )
        )
    }

    @Test
    fun occupiedPermanentIslandKeepsNativeDismissRestore() {
        assertFalse(
            NativeSystemIslandPolicy.shouldAbsorb(
                permanentPosted = true,
                occupyingLogicalId = "whatsapp:1",
                realIslandCount = 1,
            )
        )
        assertFalse(
            NativeSystemIslandPolicy.shouldAbsorbFromPosted(
                permanentPosted = true,
                permanentSemanticType = "MESSAGE",
                extraOwnedIslandCount = 0,
            )
        )
    }

    @Test
    fun extraBridgedIslandsKeepNativeDismissRestore() {
        assertFalse(
            NativeSystemIslandPolicy.shouldAbsorb(
                permanentPosted = true,
                occupyingLogicalId = "island-a",
                realIslandCount = 2,
            )
        )
        assertFalse(
            NativeSystemIslandPolicy.shouldAbsorbFromPosted(
                permanentPosted = true,
                permanentSemanticType = "MESSAGE",
                extraOwnedIslandCount = 1,
            )
        )
    }

    @Test
    fun alreadyAbsorbedNativeIslandCanUpdateInPlace() {
        val logicalId = NativeSystemIslandPolicy.logicalId("charge")
        assertTrue(NativeSystemIslandPolicy.isSoftOccupant(logicalId))
        assertTrue(
            NativeSystemIslandPolicy.shouldAbsorb(
                permanentPosted = true,
                occupyingLogicalId = logicalId,
                realIslandCount = 0,
            )
        )
        assertTrue(
            NativeSystemIslandPolicy.shouldAbsorbFromPosted(
                permanentPosted = true,
                permanentSemanticType = "NATIVE_SYSTEM",
                extraOwnedIslandCount = 0,
            )
        )
    }

    @Test
    fun missingPermanentIslandDoesNotAbsorb() {
        assertFalse(
            NativeSystemIslandPolicy.shouldAbsorb(
                permanentPosted = false,
                occupyingLogicalId = null,
                realIslandCount = 0,
            )
        )
        assertFalse(
            NativeSystemIslandPolicy.shouldAbsorbFromPosted(
                permanentPosted = false,
                permanentSemanticType = "PERMANENT",
                extraOwnedIslandCount = 0,
            )
        )
    }

    @Test
    fun incomingIslandsTreatNativeOccupancyAsTheStub() {
        assertNull(
            NativeSystemIslandPolicy.effectiveOccupant(
                NativeSystemIslandPolicy.logicalId("charge")
            )
        )
        assertEquals("island-a", NativeSystemIslandPolicy.effectiveOccupant("island-a"))
    }

    @Test
    fun missingDurationUsesTheShowOnceDefault() {
        assertEquals(3_500L, NativeSystemIslandPolicy.durationMs(null))
        assertEquals(3_500L, NativeSystemIslandPolicy.durationMs(-1L))
        assertEquals(8_000L, NativeSystemIslandPolicy.durationMs(8_000L))
        assertTrue(NativeSystemIslandPolicy.isHide(0L, explicitHide = false))
        assertTrue(NativeSystemIslandPolicy.isHide(3_500L, explicitHide = true))
        assertFalse(NativeSystemIslandPolicy.isHide(3_500L, explicitHide = false))
    }
}
