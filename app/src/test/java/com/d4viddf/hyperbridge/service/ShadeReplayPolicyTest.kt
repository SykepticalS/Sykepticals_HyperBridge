package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadeReplayPolicyTest {
    @Test
    fun firstSightOfANotificationIsPresented() {
        assertFalse(
            ShadeReplayPolicy.shouldIgnore(
                previous = null,
                incoming = ShadeEntryIdentity(visibleHash = 1, postTime = 10),
                bulkReplayActive = true,
            )
        )
    }

    @Test
    fun unchangedShadeEntryIsNotPresentedAgainWhenPostTimeDrifts() {
        val previous = ShadeEntryIdentity(visibleHash = 1, postTime = 10)
        val refreshed = ShadeEntryIdentity(visibleHash = 1, postTime = 99)
        assertTrue(
            ShadeReplayPolicy.shouldIgnore(previous, refreshed, bulkReplayActive = false)
        )
    }

    @Test
    fun contentUpdateOutsideABulkRebuildIsPresented() {
        val previous = ShadeEntryIdentity(visibleHash = 1, postTime = 10)
        val updated = ShadeEntryIdentity(visibleHash = 2, postTime = 10)
        assertFalse(
            ShadeReplayPolicy.shouldIgnore(previous, updated, bulkReplayActive = false)
        )
    }

    @Test
    fun bulkRebuildDoesNotPresentRewrittenExtrasThatKeepTheirPostTime() {
        val previous = ShadeEntryIdentity(visibleHash = 1, postTime = 10)
        val rewritten = ShadeEntryIdentity(visibleHash = 2, postTime = 10)
        assertTrue(
            ShadeReplayPolicy.shouldIgnore(previous, rewritten, bulkReplayActive = true)
        )
    }

    @Test
    fun appUpdateDuringBulkRebuildStillPresents() {
        val previous = ShadeEntryIdentity(visibleHash = 1, postTime = 10)
        val updated = ShadeEntryIdentity(visibleHash = 2, postTime = 40)
        assertFalse(
            ShadeReplayPolicy.shouldIgnore(previous, updated, bulkReplayActive = true)
        )
    }
}
