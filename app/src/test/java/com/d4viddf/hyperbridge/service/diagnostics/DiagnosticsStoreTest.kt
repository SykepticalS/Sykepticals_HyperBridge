package com.d4viddf.hyperbridge.service.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosticsStoreTest {
    @Test
    fun eventBufferIsBoundedAndContainsMetadataOnly() {
        DiagnosticsStore.resetForTest()
        repeat(DiagnosticsStore.MAX_EVENTS + 7) { index ->
            DiagnosticsStore.record("MESSAGE", "updated", "example.app", "reason-$index", timestamp = index.toLong())
        }

        val events = DiagnosticsStore.state.value.events
        assertEquals(DiagnosticsStore.MAX_EVENTS, events.size)
        assertEquals(7L, events.first().timestamp)
        assertFalse(events.joinToString().contains("message body", ignoreCase = true))
    }

    @Test
    fun activeIslandsUpdateState() {
        DiagnosticsStore.resetForTest()
        assertEquals(0, DiagnosticsStore.state.value.activeIslands)

        DiagnosticsStore.setActiveIslands(3)
        assertEquals(3, DiagnosticsStore.state.value.activeIslands)
    }
}
