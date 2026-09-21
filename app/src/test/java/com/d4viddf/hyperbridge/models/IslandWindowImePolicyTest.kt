package com.d4viddf.hyperbridge.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandWindowImePolicyTest {
    @Test
    fun idleIslandDropsImeInversionAndStaysUntouchModal() {
        val stolen = IslandWindowImePolicy.FLAG_NOT_FOCUSABLE or
            IslandWindowImePolicy.FLAG_ALT_FOCUSABLE_IM
        val idle = IslandWindowImePolicy.idleFlags(stolen)
        assertEquals(0, idle and IslandWindowImePolicy.FLAG_ALT_FOCUSABLE_IM)
        assertEquals(IslandWindowImePolicy.FLAG_NOT_FOCUSABLE, idle and IslandWindowImePolicy.FLAG_NOT_FOCUSABLE)
        assertEquals(IslandWindowImePolicy.FLAG_NOT_TOUCH_MODAL, idle and IslandWindowImePolicy.FLAG_NOT_TOUCH_MODAL)
    }

    @Test
    fun expandedFocusableIslandStillCannotTakeImeWhileIdle() {
        val expanded = 0
        val idle = IslandWindowImePolicy.apply(expanded, composerOpen = false)
        assertTrue(idle and IslandWindowImePolicy.FLAG_NOT_FOCUSABLE != 0)
        assertFalse(idle and IslandWindowImePolicy.FLAG_ALT_FOCUSABLE_IM != 0)
    }

    @Test
    fun composerClearsNotFocusableSoIslandCanHostIme() {
        val stolen = IslandWindowImePolicy.FLAG_NOT_FOCUSABLE or
            IslandWindowImePolicy.FLAG_ALT_FOCUSABLE_IM or
            IslandWindowImePolicy.FLAG_NOT_TOUCH_MODAL
        val composer = IslandWindowImePolicy.apply(stolen, composerOpen = true)
        assertEquals(0, composer and IslandWindowImePolicy.FLAG_NOT_FOCUSABLE)
        assertEquals(0, composer and IslandWindowImePolicy.FLAG_ALT_FOCUSABLE_IM)
        assertEquals(IslandWindowImePolicy.FLAG_NOT_TOUCH_MODAL, composer and IslandWindowImePolicy.FLAG_NOT_TOUCH_MODAL)
    }

    @Test
    fun idleFlagsAreIdempotent() {
        val first = IslandWindowImePolicy.idleFlags(IslandWindowImePolicy.FLAG_ALT_FOCUSABLE_IM)
        assertEquals(first, IslandWindowImePolicy.idleFlags(first))
    }

    @Test
    fun composerImeDoesNotPanOrResizeTheIslandWindow() {
        val resized = 0x00000010
        val mode = IslandWindowImePolicy.composerSoftInputMode(resized)
        assertEquals(
            IslandWindowImePolicy.SOFT_INPUT_ADJUST_NOTHING,
            mode and IslandWindowImePolicy.SOFT_INPUT_MASK_ADJUST,
        )
        assertEquals(
            IslandWindowImePolicy.SOFT_INPUT_STATE_VISIBLE,
            mode and IslandWindowImePolicy.SOFT_INPUT_MASK_STATE,
        )
    }
}
