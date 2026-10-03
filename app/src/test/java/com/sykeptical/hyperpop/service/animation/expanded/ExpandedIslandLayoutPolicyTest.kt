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
    )
}
