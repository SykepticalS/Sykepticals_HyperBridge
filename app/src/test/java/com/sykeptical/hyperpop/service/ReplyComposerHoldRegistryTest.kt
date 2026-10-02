package com.sykeptical.hyperpop.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyComposerHoldRegistryTest {
    @Test
    fun scopedComposerOnlyPausesItsOwnIsland() {
        val holds = ReplyComposerHoldRegistry()
        holds.update(open = true, sourceKey = "message-a")

        assertTrue(holds.holds("message-a"))
        assertFalse(holds.holds("message-b"))

        holds.update(open = false, sourceKey = "message-a")
        assertFalse(holds.holds("message-a"))
    }

    @Test
    fun missingLegacyKeyRemainsFailSafeGlobal() {
        val holds = ReplyComposerHoldRegistry()
        holds.update(open = true, sourceKey = null)
        assertTrue(holds.holds("any-island"))
        holds.update(open = false, sourceKey = null)
        assertFalse(holds.holds("any-island"))
    }
}
