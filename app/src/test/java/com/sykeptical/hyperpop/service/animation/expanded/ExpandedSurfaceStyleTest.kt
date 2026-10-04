package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class ExpandedSurfaceStyleTest {
    private val nativeFill = 0xFF1A1A1A.toInt()
    private val cutout = IslandRect(566, 36, 634, 108)
    private val compact = IslandRect(460, 36, 740, 108)
    private val nativeExpanded = IslandRect(48, 160, 1152, 560)

    @Test
    fun plateCornerFollowsTheClipAndLeavesTheNativeOutline() {
        assertEquals(180f, ExpandedSurfaceStyle.plateCornerRadius(180f), 0.01f)
        assertEquals(0f, ExpandedSurfaceStyle.plateCornerRadius(-4f), 0.01f)
        assertEquals(ExpandedSurfaceStyle.BLACK, ExpandedSurfaceStyle.ownedPlateFill())
    }

    @Test
    fun blackOffKeepsTheNativeFill() {
        assertEquals(nativeFill, ExpandedSurfaceStyle.fill(nativeFill, black = false, progress = 1f))
        assertEquals(nativeFill, ExpandedSurfaceStyle.fill(nativeFill, black = true, progress = 0f))
    }

    @Test
    fun blackOnAtFullProgressIsTrueBlack() {
        assertEquals(ExpandedSurfaceStyle.BLACK, ExpandedSurfaceStyle.fill(nativeFill, black = true, progress = 1f))
    }

    @Test
    fun ownedPlateIsBlackWithoutLerpingTheNativeFill() {
        assertEquals(ExpandedSurfaceStyle.BLACK, ExpandedSurfaceStyle.ownedPlateFill())
        assertEquals(nativeFill, ExpandedSurfaceStyle.fill(nativeFill, black = true, progress = 0f))
    }

    @Test
    fun drawnHeightFollowsTheOutlineWhileTheViewIsStillCompact() {
        assertEquals(
            535,
            ExpandedSurfaceStyle.drawnHeight(
                clipPx = 72,
                expandedHeight = 0,
                backgroundHeight = 72,
                actualHeight = 535,
            ),
        )
        assertEquals(200, ExpandedSurfaceStyle.drawnHeight(200, 80, 40, 10))
        assertEquals(0, ExpandedSurfaceStyle.drawnHeight(-5, -1, 0, 0))
    }

    @Test
    fun clipRadiusMatchesXiaomiAtTheStartAndThePillAtTheEnd() {
        val nativeCap = 90f
        val pillCap = 168f
        assertEquals(nativeCap, ExpandedSurfaceStyle.clipRadius(200f, nativeCap, pillCap, pill = true, progress = 0f))
        assertEquals(nativeCap, ExpandedSurfaceStyle.clipRadius(200f, nativeCap, pillCap, pill = false, progress = 1f))
        assertEquals(min(100f, pillCap), ExpandedSurfaceStyle.clipRadius(200f, nativeCap, pillCap, pill = true, progress = 1f))
    }

    @Test
    fun clipBottomOvershootStopsAtTheCardBottom() {
        assertEquals(565f, ExpandedSurfaceStyle.cappedClipBottom(568f, cardBottom = 565), 0f)
        assertEquals(400f, ExpandedSurfaceStyle.cappedClipBottom(400f, cardBottom = 565), 0f)
        assertEquals(565f, ExpandedSurfaceStyle.cappedClipBottom(565f, cardBottom = 565), 0f)
    }

    @Test
    fun fakeFlowMaskKeepsTheFadeAtTheSameDepthInTheCard() {
        val relative = ExpandedSurfaceStyle.flowMaskFromCardTop(FlowMask(blackUntilY = 160, fadeEndY = 280), cardTop = 30)
        assertEquals(130, relative.blackUntilY)
        assertEquals(250, relative.fadeEndY)
    }

    @Test
    fun flowMaskIsBlackThroughTheCutoutAndSmoothAfterwards() {
        val mask = ExpandedSurfaceStyle.flowMask(cutout, cardBottom = 700, density = 3f)
        assertNotNull(mask)
        mask!!
        val safety = (CutoutSafeLayout.SAFETY_DP * 3f).toInt()
        assertTrue(mask.blackUntilY >= cutout.bottom + safety)
        assertEquals(0f, mask.alphaAt(mask.blackUntilY.toFloat()))
        assertEquals(0f, mask.alphaAt((mask.blackUntilY - 20).toFloat()))
        assertEquals(1f, mask.alphaAt(mask.fadeEndY.toFloat()))
        var previous = -1f
        var y = mask.blackUntilY
        while (y <= mask.fadeEndY) {
            val alpha = mask.alphaAt(y.toFloat())
            assertTrue(alpha >= previous)
            previous = alpha
            y += 1
        }
        val span = (mask.fadeEndY - mask.blackUntilY).toFloat()
        val startSlope = mask.alphaAt(mask.blackUntilY + span * 0.02f) / (span * 0.02f)
        val midSlope = (
            mask.alphaAt(mask.blackUntilY + span * 0.52f) -
                mask.alphaAt(mask.blackUntilY + span * 0.48f)
            ) / (span * 0.04f)
        assertTrue(startSlope < midSlope)
    }

    @Test
    fun styleCombinationsKeepGeometryAndSurfaceIndependent() {
        val plain = decide(ExpandedVisualStyle())
        assertFalse(plain.blackBackground)
        assertFalse(plain.pillEnabled)
        assertNull(plain.flowMask)
        assertEquals(132, plain.bodyTop)

        val black = decide(ExpandedVisualStyle(blackBackground = true))
        assertTrue(black.blackBackground)
        assertFalse(black.pillEnabled)
        assertEquals(plain.card, black.card)
        assertNotNull(black.flowMask)
        assertEquals(ExpandedSurfaceStyle.BLACK, ExpandedSurfaceStyle.fill(nativeFill, black = true, progress = 1f))

        val pill = decide(ExpandedVisualStyle(roundedPill = true), profile = shortContent(), radius = 90f)
        assertFalse(pill.blackBackground)
        assertTrue(pill.pillEnabled)
        assertNull(pill.flowMask)
        assertTrue(pill.radiusPx > 90f)
        assertTrue(pill.card.bottom <= plain.card.bottom)

        val both = decide(
            ExpandedVisualStyle(blackBackground = true, roundedPill = true),
            profile = shortContent(),
            radius = 90f,
        )
        assertTrue(both.blackBackground)
        assertTrue(both.pillEnabled)
        assertNotNull(both.flowMask)
        assertEquals(pill.radiusPx, both.radiusPx)
        assertEquals(pill.card, both.card)
        assertTrue(both.flowMask!!.blackUntilY >= cutout.bottom)
    }

    private fun decide(
        style: ExpandedVisualStyle,
        profile: ExpandedContentProfile? = null,
        radius: Float = 0f,
    ): ExpandedLayoutDecision.Takeover {
        val decision = ExpandedIslandLayoutPolicy.decide(
            ExpandedLayoutRequest(
                enabled = true,
                portrait = true,
                keyguard = false,
                tablet = false,
                displayWidth = 1200,
                displayHeight = 2608,
                cutout = cutout,
                compact = compact,
                nativeExpanded = nativeExpanded,
                statusBarHeight = 144,
                density = 3f,
                style = style,
                nativeRadiusPx = radius,
                content = profile,
            ),
        )
        assertTrue(decision is ExpandedLayoutDecision.Takeover)
        return decision as ExpandedLayoutDecision.Takeover
    }

    private fun shortContent() = ExpandedContentProfile(
        nativeTopMarginPx = 0,
        contentWidthPx = nativeExpanded.width,
        contentHeightPx = nativeExpanded.height,
        leaves = listOf(ContentLeaf(IslandRect(80, 24, nativeExpanded.width - 80, 200), ContentLeafKind.TEXT)),
    )
}
