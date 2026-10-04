package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpandedMediaSurfacePolicyTest {
    @Test
    fun extensionIsTheGapBelowTheMediaSurface() {
        assertEquals(180, ExpandedMediaSurfacePolicy.extensionPx(cardBottom = 700, surfaceBottom = 520))
    }

    @Test
    fun noExtensionWhenTheSurfaceAlreadyReachesTheCard() {
        assertEquals(0, ExpandedMediaSurfacePolicy.extensionPx(cardBottom = 520, surfaceBottom = 520))
        assertEquals(0, ExpandedMediaSurfacePolicy.extensionPx(cardBottom = 400, surfaceBottom = 520))
    }

    @Test
    fun dragKeepsAResolvedTailAndStillAllowsItToGrow() {
        assertEquals(180, ExpandedMediaSurfacePolicy.heldExtension(recorded = 180, measured = 40, dragging = true))
        assertEquals(200, ExpandedMediaSurfacePolicy.heldExtension(recorded = 180, measured = 200, dragging = true))
        assertEquals(40, ExpandedMediaSurfacePolicy.heldExtension(recorded = 180, measured = 40, dragging = false))
    }

    @Test
    fun bottomMarginCarriesTheExtensionBelowThePlayer() {
        assertEquals(-180, ExpandedMediaSurfacePolicy.bottomMargin(baseMargin = 0, extensionPx = 180))
        assertEquals(4, ExpandedMediaSurfacePolicy.bottomMargin(baseMargin = 16, extensionPx = 12))
    }

    @Test
    fun sharedAncestorGrowsOnlyUntilTheCardBottom() {
        assertEquals(700, ExpandedMediaSurfacePolicy.hostHeight(0, 700, 520, -2, owned = false))
        assertNull(ExpandedMediaSurfacePolicy.hostHeight(0, 700, 700, 700, owned = false))
        assertNull(ExpandedMediaSurfacePolicy.hostHeight(0, 700, 2400, -2, owned = false))
        assertNull(ExpandedMediaSurfacePolicy.hostHeight(40, 700, 0, -2, owned = false))
    }

    @Test
    fun ownedHostIsPinnedToTheCardWhateverItsStaleLaidOutSize() {
        // A media refresh restored the params; layout has not run yet, so the old bounds remain.
        assertEquals(597, ExpandedMediaSurfacePolicy.hostHeight(30, 627, 597, -1, owned = true))
        assertEquals(597, ExpandedMediaSurfacePolicy.hostHeight(30, 627, 711, -2, owned = true))
        assertEquals(597, ExpandedMediaSurfacePolicy.hostHeight(30, 627, 544, -2, owned = true))
    }

    @Test
    fun hostAlreadyPinnedToTheCardIsNotWrittenAgain() {
        assertNull(ExpandedMediaSurfacePolicy.hostHeight(30, 627, 544, 597, owned = true))
        assertNull(ExpandedMediaSurfacePolicy.hostHeight(30, 627, 597, 597, owned = true))
    }
}
