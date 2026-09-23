package com.d4viddf.hyperbridge.service.translators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandFloatingPresentationPolicyTest {
    @Test
    fun newConfiguredEventMayFloatAndExpand() {
        val presentation = IslandFloatingPresentationPolicy.resolve(
            firstFloat = true,
            floatOnUpdate = false,
            isUpdate = false
        )

        assertTrue(presentation.enableFloat)
        assertTrue(presentation.islandFirstFloat)
        assertTrue(presentation.reopen)
    }

    @Test
    fun updateCannotFloatOrReExpand() {
        val presentation = IslandFloatingPresentationPolicy.resolve(
            firstFloat = true,
            floatOnUpdate = false,
            isUpdate = true
        )

        assertFalse(presentation.enableFloat)
        assertFalse(presentation.islandFirstFloat)
        assertFalse(presentation.reopen)
        assertEquals(0, presentation.expandedTimeMs(2_000))
    }

    @Test
    fun disabledFloatingRemainsDisabledForNewEvents() {
        val presentation = IslandFloatingPresentationPolicy.resolve(
            firstFloat = false,
            floatOnUpdate = false,
            isUpdate = false
        )

        assertFalse(presentation.enableFloat)
        assertFalse(presentation.islandFirstFloat)
        assertFalse(presentation.reopen)
    }

    @Test
    fun updateMayFloatWhenExplicitlyEnabled() {
        val presentation = IslandFloatingPresentationPolicy.resolve(true, true, isUpdate = true)
        assertTrue(presentation.enableFloat)
        assertTrue(presentation.islandFirstFloat)
        assertTrue(presentation.reopen)
    }

    @Test
    fun chromeProgressUpdateDoesNotReExpandByDefault() {
        val presentation = IslandFloatingPresentationPolicy.resolve(
            firstFloat = true,
            floatOnUpdate = false,
            isUpdate = true,
        )
        assertFalse(presentation.enableFloat)
        assertFalse(presentation.islandFirstFloat)
        assertFalse(presentation.reopen)
    }

}
