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
}
