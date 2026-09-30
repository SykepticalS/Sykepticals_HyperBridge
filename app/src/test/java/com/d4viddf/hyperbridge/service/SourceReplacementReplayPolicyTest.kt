package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceReplacementReplayPolicyTest {
    @Test
    fun repeatedXiaomiSnapshotRestoresSuppressionForTheReplacedSource() {
        val replaced = ShadeEntryIdentity(visibleHash = 42, postTime = 100)
        val repeatedSnapshot = ShadeEntryIdentity(visibleHash = 42, postTime = 101)

        assertTrue(
            SourceReplacementReplayPolicy.shouldRestoreSuppression(replaced, repeatedSnapshot)
        )
    }

    @Test
    fun changedContentIsNotMistakenForThePreviouslyReplacedSource() {
        val replaced = ShadeEntryIdentity(visibleHash = 42, postTime = 100)

        assertFalse(
            SourceReplacementReplayPolicy.shouldRestoreSuppression(
                replaced,
                ShadeEntryIdentity(visibleHash = 43, postTime = 101),
            )
        )
    }

    @Test
    fun callLifecycleChangesCannotInheritAnOlderSuppressionDecision() {
        val replaced = ShadeEntryIdentity(visibleHash = 42, postTime = 100, callLifecycleHash = 1)

        assertFalse(
            SourceReplacementReplayPolicy.shouldRestoreSuppression(
                replaced,
                ShadeEntryIdentity(visibleHash = 42, postTime = 101, callLifecycleHash = 2),
            )
        )
    }

    @Test
    fun sourceThatWasNeverReplacedRemainsUnsuppressed() {
        assertFalse(
            SourceReplacementReplayPolicy.shouldRestoreSuppression(
                null,
                ShadeEntryIdentity(visibleHash = 42, postTime = 100),
            )
        )
    }
}
