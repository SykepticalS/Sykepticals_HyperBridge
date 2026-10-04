package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedFakeMirrorPolicyTest {
    @Test
    fun stampChangesWhenTheResolvedLayoutChanges() {
        val settled = ExpandedFakeMirrorPolicy.stamp(bodyOffsetPx = 48, cardBottom = 620, contentScale = 1f)
        assertEquals(settled, ExpandedFakeMirrorPolicy.stamp(48, 620, 1f))
        assertTrue(settled != ExpandedFakeMirrorPolicy.stamp(56, 620, 1f))
        assertTrue(settled != ExpandedFakeMirrorPolicy.stamp(48, 680, 1f))
        assertTrue(settled != ExpandedFakeMirrorPolicy.stamp(48, 620, 0.92f))
    }

    @Test
    fun tokenChangesWhenAChildMarginChanges() {
        val tuned = ExpandedFakeMirrorPolicy.layoutToken(intArrayOf(24, 80, 16))
        assertEquals(tuned, ExpandedFakeMirrorPolicy.layoutToken(intArrayOf(24, 80, 16)))
        assertTrue(tuned != ExpandedFakeMirrorPolicy.layoutToken(intArrayOf(12, 80, 16)))
        assertTrue(tuned != ExpandedFakeMirrorPolicy.layoutToken(intArrayOf(24, 80)))
    }

    @Test
    fun syncIsNeededUntilGenerationStampAndTokenMatch() {
        assertTrue(ExpandedFakeMirrorPolicy.needsSync(null, generation = 3, stamp = 7, token = 11))
        val marker = ExpandedFakeMirrorPolicy.marker(generation = 3, stamp = 7, token = 11)
        assertFalse(ExpandedFakeMirrorPolicy.needsSync(marker, 3, 7, 11))
        assertTrue(ExpandedFakeMirrorPolicy.needsSync(marker, generation = 4, stamp = 7, token = 11))
        assertTrue(ExpandedFakeMirrorPolicy.needsSync(marker, generation = 3, stamp = 8, token = 11))
        assertTrue(ExpandedFakeMirrorPolicy.needsSync(marker, generation = 3, stamp = 7, token = 12))
    }

    @Test
    fun restoreWaitsWhileTheFakeCopyIsStillVisible() {
        assertTrue(ExpandedFakeMirrorPolicy.deferRestore(fakeVisible = true))
        assertFalse(ExpandedFakeMirrorPolicy.deferRestore(fakeVisible = false))
    }
}
