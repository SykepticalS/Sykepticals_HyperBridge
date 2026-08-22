package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiredIslandRegistryTest {
    @Test
    fun identicalExpiredGenerationRemainsSuppressed() {
        val registry = ExpiredIslandRegistry(retentionMs = 1_000)
        registry.record(ExpiredIslandRecord("conversation", "source", 7, expiredAt = 100))

        assertEquals(ExpiredSourceDecision.SUPPRESS_IDENTICAL, registry.evaluate("source", 7, now = 200))
    }

    @Test
    fun changedGenerationBecomesEligibleAndClearsTombstone() {
        val registry = ExpiredIslandRegistry(retentionMs = 1_000)
        registry.record(ExpiredIslandRecord("conversation", "source", 7, expiredAt = 100))

        assertEquals(ExpiredSourceDecision.NEW_GENERATION, registry.evaluate("source", 8, now = 200))
        assertEquals(ExpiredSourceDecision.SUPPRESS_IDENTICAL, registry.evaluate("source", 7, now = 201))
        registry.acceptNewGeneration("source", 8)
        assertEquals(ExpiredSourceDecision.NOT_EXPIRED, registry.evaluate("source", 8, now = 201))
    }

    @Test
    fun tombstonesExpireAndRemainBounded() {
        val registry = ExpiredIslandRegistry(maxEntries = 1, retentionMs = 100)
        registry.record(ExpiredIslandRecord("one", "source-one", 1, expiredAt = 0))
        registry.record(ExpiredIslandRecord("two", "source-two", 2, expiredAt = 10))
        assertEquals(1, registry.size())
        registry.prune(now = 111)
        assertEquals(0, registry.size())
    }

    @Test
    fun staleTimeoutCannotDeleteNewerUpdate() {
        assertFalse(IslandTimeoutPolicy.isCurrent(2, 42, scheduledGeneration = 1, scheduledBridgeId = 42))
        assertTrue(IslandTimeoutPolicy.isCurrent(2, 42, scheduledGeneration = 2, scheduledBridgeId = 42))
    }
}
