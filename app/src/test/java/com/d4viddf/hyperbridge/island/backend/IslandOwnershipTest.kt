package com.d4viddf.hyperbridge.island.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandOwnershipTest {
    @Test fun tagsAreStableAndNamespaced() {
        assertEquals(IslandOwnership.tag("conversation:42"), IslandOwnership.tag("conversation:42"))
        assertTrue(IslandOwnership.tag("conversation:42").startsWith("hyperbridge:"))
        assertNotEquals(IslandOwnership.tag("conversation:42"), IslandOwnership.tag("conversation:43"))
    }

    @Test fun staleCancelCannotRemoveReplacement() {
        assertFalse(IslandOwnership.acceptsCancel(currentGeneration = 8, requestedGeneration = 7))
        assertTrue(IslandOwnership.acceptsCancel(currentGeneration = 8, requestedGeneration = 8))
        assertTrue(IslandOwnership.acceptsCancel(currentGeneration = 8, requestedGeneration = Long.MAX_VALUE))
    }
}
