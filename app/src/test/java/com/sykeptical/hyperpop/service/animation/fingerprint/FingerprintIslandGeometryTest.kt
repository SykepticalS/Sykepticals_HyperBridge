package com.sykeptical.hyperpop.service.animation.fingerprint

import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintIslandGeometryTest {
    @Test
    fun holeStaysInsideThePlateAtFullShakeAcrossDensities() {
        val holes = listOf(
            FingerprintIslandGeometry.Rect(480f, 24f, 560f, 96f),
            FingerprintIslandGeometry.Rect(180f, 8f, 230f, 70f),
        )
        val densities = floatArrayOf(2.75f, 3f, 3.5f)
        val heights = floatArrayOf(80f, 102f, 140f)
        for (hole in holes) {
            for (density in densities) {
                for (height in heights) {
                    val layout = FingerprintIslandGeometry.layout(hole, height, density)
                    assertTrue(layout.square.contains(hole, layout.holeMarginPx))
                    assertTrue(layout.pill.left <= hole.left)
                    assertTrue(layout.pill.right >= hole.right)
                    assertTrue(layout.glyph.top >= hole.bottom)
                    assertTrue(layout.square.contains(layout.glyph, 0f))
                    val amp = layout.shakeAmplitudePx
                    assertTrue(amp <= FingerprintIslandGeometry.SHAKE_TO_SIDE * layout.square.width + 0.01f)
                    val movedRight = FingerprintIslandGeometry.Rect(
                        layout.square.left + amp,
                        layout.square.top,
                        layout.square.right + amp,
                        layout.square.bottom,
                    )
                    val movedLeft = FingerprintIslandGeometry.Rect(
                        layout.square.left - amp,
                        layout.square.top,
                        layout.square.right - amp,
                        layout.square.bottom,
                    )
                    assertTrue(movedRight.contains(hole, layout.holeMarginPx))
                    assertTrue(movedLeft.contains(hole, layout.holeMarginPx))
                    assertEquals(
                        FingerprintIslandGeometry.RADIUS_TO_SIDE * layout.square.width,
                        layout.squareRadius,
                        0.01f,
                    )
                }
            }
        }
    }

    @Test
    fun lockPillMatchesTheCompactIslandAndKeepsThePadlockInside() {
        val hole = FingerprintIslandGeometry.Rect(500f, 40f, 580f, 100f)
        val density = 3f
        val height = 34f * density
        val radius = FingerprintIslandGeometry.ISLAND_RADIUS_DP * density
        val layout = FingerprintIslandGeometry.layout(hole, height, density, islandRadiusPx = radius)
        assertEquals(height, layout.pill.height, 0.01f)
        assertEquals(min(height / 2f, radius), layout.pillRadius, 0.01f)
        assertEquals(hole.centerX, layout.pill.centerX, 0.01f)
        assertEquals(hole.centerY, layout.pill.centerY, 0.01f)
        assertTrue(layout.pill.left <= layout.padlock.left + 0.01f)
        assertTrue(layout.pill.top <= layout.padlock.top + 0.01f)
        assertTrue(layout.pill.right + 0.01f >= layout.padlock.right)
        assertTrue(layout.pill.bottom + 0.01f >= layout.padlock.bottom)
        assertTrue(layout.pill.left <= hole.left)
        assertTrue(layout.pill.right >= hole.right)
    }

    @Test
    fun hiddenReservesNoWindowHeight() {
        val hole = FingerprintIslandGeometry.Rect(100f, 20f, 160f, 80f)
        val layout = FingerprintIslandGeometry.layout(hole, 100f, 3f)
        assertEquals(0, FingerprintIslandGeometry.reservedHeightPx(layout, FingerprintPhase.Hidden, 3f))
        assertTrue(FingerprintIslandGeometry.reservedHeightPx(layout, FingerprintPhase.LockPill, 3f) > layout.pill.bottom)
        assertTrue(
            FingerprintIslandGeometry.reservedHeightPx(layout, FingerprintPhase.Scanning, 3f) >=
                layout.square.bottom,
        )
    }
}
