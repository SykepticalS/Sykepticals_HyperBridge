package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceProcessingGenerationTest {
    @Test
    fun staleGenerationCannotPostAfterNewerCallback() {
        val generations = SourceProcessingGeneration()
        val old = generations.next("source")
        val latest = generations.next("source")

        assertFalse(generations.isCurrent("source", old))
        assertTrue(generations.isCurrent("source", latest))
    }

    @Test
    fun everyCallbackClaimsGenerationRegardlessOfInitialContent() {
        val generations = SourceProcessingGeneration()
        val initiallyUseful = generations.next("source")
        val initiallySparse = generations.next("source")

        assertTrue(initiallySparse > initiallyUseful)
        assertFalse(generations.isCurrent("source", initiallyUseful))
        assertTrue(generations.isCurrent("source", initiallySparse))
    }

    @Test
    fun generationsAreTrackedIndependentlyPerSource() {
        val generations = SourceProcessingGeneration()
        val first = generations.next("first")
        val second = generations.next("second")

        assertTrue(generations.isCurrent("first", first))
        assertTrue(generations.isCurrent("second", second))
    }
}
