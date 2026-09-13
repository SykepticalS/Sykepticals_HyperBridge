package com.d4viddf.hyperbridge.service.popup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestGenerationTest {
    @Test
    fun onlyNewestGenerationForEachPackageMayWrite() {
        val gate = LatestGeneration()
        val firstA = gate.next("app.a")
        val firstB = gate.next("app.b")
        val secondA = gate.next("app.a")

        assertFalse(gate.isLatest("app.a", firstA))
        assertTrue(gate.isLatest("app.a", secondA))
        assertTrue(gate.isLatest("app.b", firstB))
    }
}
