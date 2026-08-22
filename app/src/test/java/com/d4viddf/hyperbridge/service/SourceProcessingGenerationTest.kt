package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceProcessingGenerationTest {
    @Test
    fun staleGenerationCannotPostAfterNewerCallback() {
        val generations = SourceProcessingGeneration()
        val old = requireNotNull(generations.next("source"))
        val latest = requireNotNull(generations.next("source"))

        assertFalse(generations.isCurrent("source", old))
        assertTrue(generations.isCurrent("source", latest))
    }

    @Test
    fun emptyCallbackCannotSupersedeQueuedUsableCallback() {
        val generations = SourceProcessingGeneration()
        val usable = requireNotNull(generations.next("source", SourceCandidateQuality.USABLE))

        val empty = generations.next("source", SourceCandidateQuality.EMPTY_AUXILIARY)

        assertTrue(empty == null)
        assertTrue(generations.isCurrent("source", usable))
    }

    @Test
    fun usableCallbackAfterEmptyProcessesNormally() {
        val generations = SourceProcessingGeneration()
        assertTrue(generations.next("source", SourceCandidateQuality.EMPTY_AUXILIARY) == null)

        val usable = requireNotNull(generations.next("source", SourceCandidateQuality.USABLE))

        assertTrue(generations.isCurrent("source", usable))
    }

    @Test
    fun emptyCallbackAloneDoesNotCreateGeneration() {
        val generations = SourceProcessingGeneration()

        assertTrue(generations.next("source", SourceCandidateQuality.EMPTY_AUXILIARY) == null)
        assertTrue(generations.current("source") == null)
    }
}
