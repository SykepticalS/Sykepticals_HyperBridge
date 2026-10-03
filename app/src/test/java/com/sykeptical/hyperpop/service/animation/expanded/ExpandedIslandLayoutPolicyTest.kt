package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedIslandLayoutPolicyTest {
    private val density = 3f
    private val cutout = IslandRect(566, 36, 634, 108)
    private val compact = IslandRect(460, 36, 740, 108)
    private val nativeExpanded = IslandRect(48, 160, 1152, 560)
    private val leadingEar = IslandRect(460, 36, 540, 108)
    private val trailingEar = IslandRect(660, 36, 740, 108)

    @Test
    fun disabledReturnsNative() {
        assertTrue(ExpandedIslandLayoutPolicy.decide(request(enabled = false)) is ExpandedLayoutDecision.Native)
    }

    @Test
    fun landscapeReturnsNative() {
        assertTrue(ExpandedIslandLayoutPolicy.decide(request(portrait = false)) is ExpandedLayoutDecision.Native)
    }

    @Test
    fun keyguardAndTabletReturnNative() {
        assertTrue(ExpandedIslandLayoutPolicy.decide(request(keyguard = true)) is ExpandedLayoutDecision.Native)
        assertTrue(ExpandedIslandLayoutPolicy.decide(request(tablet = true)) is ExpandedLayoutDecision.Native)
    }

    @Test
    fun invalidCutoutOrMissingNativeGeometryFailsOpen() {
        assertTrue(ExpandedIslandLayoutPolicy.decide(request(cutout = IslandRect(0, 0, 0, 0))) is ExpandedLayoutDecision.Native)
        assertTrue(ExpandedIslandLayoutPolicy.decide(request(nativeExpanded = IslandRect(0, 0, 0, 0))) is ExpandedLayoutDecision.Native)
        assertTrue(ExpandedIslandLayoutPolicy.decide(request(density = 0f)) is ExpandedLayoutDecision.Native)
        val beside = IslandRect(0, 400, 40, 480)
        assertTrue(ExpandedIslandLayoutPolicy.decide(request(cutout = beside)) is ExpandedLayoutDecision.Native)
    }

    @Test
    fun portraitTakeoverGrowsDownFromTheCompactTop() {
        val takeover = takeover()
        assertEquals(compact.top, takeover.card.top)
        assertEquals(nativeExpanded.left, takeover.card.left)
        assertEquals(nativeExpanded.right, takeover.card.right)
        assertTrue(takeover.card.bottom > compact.bottom)
        assertTrue(takeover.card.top < 144)
        assertTrue(takeover.card.bottom > 144)
        assertEquals(600, takeover.card.centerX)
        assertTrue(takeover.card.centerY > cutout.centerY)
        assertEquals(takeover.bodyTop - takeover.card.top, takeover.bodyOffsetPx)
        assertTrue(takeover.bodyTop >= cutout.bottom + 24)
        assertTrue(takeover.bodyTop >= compact.bottom + 12)
        assertTrue(takeover.belowCutout.top == takeover.bodyTop)
        assertFalse(takeover.belowCutout.intersects(cutout.inflate(24)))
    }

    @Test
    fun earsStayOutsideTheCutoutAndAnOversizedEarIsHidden() {
        val takeover = takeover()
        val exclusion = cutout.inflate(24)
        val leading = takeover.leadingEar
        val trailing = takeover.trailingEar
        assertTrue(leading != null && trailing != null)
        assertFalse(leading!!.intersects(exclusion))
        assertFalse(trailing!!.intersects(exclusion))
        assertTrue(takeover.leadingSafe.contains(leading))
        assertTrue(takeover.trailingSafe.contains(trailing))

        val oversized = takeover(request(leadingEar = IslandRect(100, 36, 700, 108)))
        assertNull(oversized.leadingEar)
        assertTrue(oversized.trailingEar != null)
    }

    @Test
    fun collapseTargetIsTheNativeCompactRect() {
        assertEquals(compact, ExpandedIslandLayoutPolicy.collapseTarget(compact))
    }

    @Test
    fun missingProfileKeepsTheConservativeGap() {
        val takeover = takeover()
        assertFalse(takeover.tightLayout)
        assertEquals(132, takeover.bodyTop)
        assertEquals(96, takeover.bodyOffsetPx)
        assertNull(takeover.flowMask)
        assertEquals(0f, takeover.radiusPx)
        assertEquals(1f, takeover.contentScale)
    }

    @Test
    fun measuredContentStartsCloserWithoutCrossingTheCutout() {
        val takeover = takeover(request(content = centerProfile(), nativeRadiusPx = 90f))
        val legacy = takeover()
        assertTrue(takeover.tightLayout)
        val visualTop = takeover.bodyTop + 36
        assertTrue(visualTop < legacy.bodyTop + 48 + 36)
        assertTrue(visualTop >= cutout.bottom + (CutoutSafeLayout.SAFETY_DP * density).toInt())
        assertFalse(IslandRect(96, visualTop, 1104, visualTop + 80).intersects(cutout))
    }

    @Test
    fun sideContentCanSitBesideTheCutout() {
        val takeover = takeover(request(content = sideProfile(), nativeRadiusPx = 90f))
        assertTrue(takeover.bodyTop < cutout.bottom)
        assertTrue(takeover.bodyTop >= compact.top)
        val leaf = IslandRect(148, takeover.bodyTop, 248, takeover.bodyTop + 60)
        assertFalse(leaf.intersects(cutout))
    }

    @Test
    fun mediaChildrenKeepTheirRelativeOrder() {
        val takeover = takeover(request(content = mixedProfile(), nativeRadiusPx = 90f))
        assertTrue(takeover.sideLifts.isEmpty())
        assertEquals(1f, takeover.contentScale)
        assertTrue(takeover.bodyOffsetPx >= 0)
        assertTrue(takeover.bodyTop >= cutout.bottom + (CutoutSafeLayout.SAFETY_DP * density).toInt())
        val side = mixedProfile().clusters[0].bounds
        val center = mixedProfile().clusters[1].bounds
        assertEquals(side.top, center.top)
    }

    @Test
    fun visiblePillIncludesTheShoulderBesideTheCamera() {
        val takeover = takeover()
        assertTrue(takeover.ownsTouch(takeover.card.left + 8, takeover.card.top + 8))
        assertTrue(takeover.ownsTouch(takeover.belowCutout.centerX, takeover.belowCutout.centerY))
        assertFalse(takeover.ownsTouch(8, 8))
    }

    @Test
    fun aCompactRectLeftBelowTheCutoutSeatsOnIt() {
        val low = request(content = centerProfile()).copy(compact = IslandRect(460, 700, 740, 772))
        val seated = ExpandedIslandLayoutPolicy.seatOnCutout(low)
        assertTrue(seated !== low)
        assertTrue(seated.compact.top < cutout.bottom)
        assertTrue(ExpandedIslandLayoutPolicy.decide(seated) is ExpandedLayoutDecision.Takeover)
        val alreadySeated = request()
        assertTrue(ExpandedIslandLayoutPolicy.seatOnCutout(alreadySeated) === alreadySeated)
    }

    @Test
    fun cutoutGapScalesWithDensityAndCutoutDepth() {
        listOf(2f, 2.75f, 3f, 3.5f).forEach { density ->
            val deep = IslandRect(560, 20, 640, 140)
            val shallow = IslandRect(580, 40, 620, 80)
            val deepTop = takeover(request(cutout = deep, density = density, content = centerProfile())).bodyTop
            val shallowTop = takeover(request(cutout = shallow, density = density, content = centerProfile())).bodyTop
            val safety = (CutoutSafeLayout.SAFETY_DP * density).toInt()
            assertTrue(deepTop + 36 >= deep.bottom + safety)
            assertTrue(shallowTop + 36 >= shallow.bottom + safety)
            assertTrue(deepTop > shallowTop)
        }
    }

    @Test
    fun passthroughBandIncludesTheTopLeft() {
        val takeover = takeover()
        assertTrue(takeover.acceptsShadePull(8, 8, statusBarHeight = 144))
        assertFalse(takeover.ownsTouch(8, 8))
        assertTrue(takeover.ownsTouch(takeover.belowCutout.centerX, takeover.belowCutout.centerY))
    }

    private fun takeover(request: ExpandedLayoutRequest = request()): ExpandedLayoutDecision.Takeover {
        val decision = ExpandedIslandLayoutPolicy.decide(request)
        assertTrue(decision is ExpandedLayoutDecision.Takeover)
        return decision as ExpandedLayoutDecision.Takeover
    }

    private fun request(
        enabled: Boolean = true,
        portrait: Boolean = true,
        keyguard: Boolean = false,
        tablet: Boolean = false,
        cutout: IslandRect = this.cutout,
        nativeExpanded: IslandRect = this.nativeExpanded,
        density: Float = this.density,
        leadingEar: IslandRect? = this.leadingEar,
        nativeRadiusPx: Float = 0f,
        content: ExpandedContentProfile? = null,
        style: ExpandedVisualStyle = ExpandedVisualStyle(),
    ) = ExpandedLayoutRequest(
        enabled = enabled,
        portrait = portrait,
        keyguard = keyguard,
        tablet = tablet,
        displayWidth = 1200,
        displayHeight = 2608,
        cutout = cutout,
        compact = compact,
        nativeExpanded = nativeExpanded,
        statusBarHeight = 144,
        density = density,
        leadingEar = leadingEar,
        trailingEar = trailingEar,
        style = style,
        nativeRadiusPx = nativeRadiusPx,
        content = content,
    )

    private fun centerProfile() = ExpandedContentProfile(
        nativeTopMarginPx = 48,
        contentWidthPx = nativeExpanded.width,
        contentHeightPx = nativeExpanded.height,
        leaves = listOf(ContentLeaf(IslandRect(48, 36, nativeExpanded.width - 48, 160), ContentLeafKind.TEXT)),
        clusters = listOf(ContentCluster(0, IslandRect(48, 36, nativeExpanded.width - 48, 160), decorative = false)),
    )

    private fun sideProfile() = ExpandedContentProfile(
        nativeTopMarginPx = 0,
        contentWidthPx = nativeExpanded.width,
        contentHeightPx = 200,
        leaves = listOf(ContentLeaf(IslandRect(100, 0, 200, 60), ContentLeafKind.PLAIN)),
        clusters = listOf(ContentCluster(0, IslandRect(100, 0, 200, 60), decorative = false)),
    )

    private fun mixedProfile() = ExpandedContentProfile(
        nativeTopMarginPx = 0,
        contentWidthPx = nativeExpanded.width,
        contentHeightPx = nativeExpanded.height,
        leaves = listOf(
            ContentLeaf(IslandRect(100, 0, 200, 60), ContentLeafKind.PLAIN),
            ContentLeaf(IslandRect(48, 0, nativeExpanded.width - 48, 80), ContentLeafKind.TEXT),
        ),
        clusters = listOf(
            ContentCluster(0, IslandRect(100, 0, 200, 60), decorative = false),
            ContentCluster(1, IslandRect(48, 0, nativeExpanded.width - 48, 80), decorative = false),
        ),
    )
}
