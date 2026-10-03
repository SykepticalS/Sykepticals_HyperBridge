package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExpandedPillPolicyTest {
    private val density = 3f
    private val nativeRadius = 90f

    @Test
    fun pillOffPreservesBottomAndNativeRadius() {
        val result = apply(enabled = false, provisionalBottom = 640, profile = shortProfile())
        assertEquals(640, result.cardBottom)
        assertEquals(nativeRadius, result.radiusPx)
        assertEquals(1f, result.contentScale)
    }

    @Test
    fun emptyBottomIsTrimmedAndTheLeafStaysInside() {
        val result = apply(enabled = true, provisionalBottom = 720, profile = shortProfile())
        val origin = 120
        val leafBottom = 180
        val pad = (ExpandedPillPolicy.BOTTOM_PAD_DP * density).toInt()
        assertTrue(result.cardBottom < 720)
        assertTrue(result.cardBottom >= origin + leafBottom)
        assertTrue(result.cardBottom <= origin + leafBottom + pad)
        assertTrue(result.radiusPx >= nativeRadius)
        assertTrue(result.contentScale >= ExpandedPillPolicy.MIN_SCALE)
    }

    @Test
    fun radiusFollowsHeightAndCapsForATallCard() {
        val result = apply(enabled = true, provisionalBottom = 980, profile = tallProfile())
        val cap = ExpandedPillPolicy.CAP_DP * density
        assertTrue(result.cardBottom >= 120 + 800)
        assertTrue(abs(result.radiusPx - cap) < 1f)
        assertTrue(result.radiusPx <= cap + 0.01f)
        assertTrue(result.radiusPx >= nativeRadius)
    }

    @Test
    fun shortCardUsesHalfItsHeightWhenThatExceedsTheNativeRadius() {
        val result = apply(enabled = true, provisionalBottom = 420, profile = shortProfile())
        val height = result.cardBottom - 36
        assertTrue(result.radiusPx <= height / 2f + 0.5f)
        assertTrue(result.radiusPx >= nativeRadius)
    }

    @Test
    fun touchFloorRejectsAScaleThatWouldUndershootTheMinimum() {
        val barely = ContentLeaf(IslandRect(0, 0, 140, 140), ContentLeafKind.INTERACTIVE)
        assertEquals(1f, ExpandedPillPolicy.boundedScale(listOf(barely), density))
        val roomy = ContentLeaf(IslandRect(0, 0, 220, 220), ContentLeafKind.INTERACTIVE)
        assertEquals(ExpandedPillPolicy.MIN_SCALE, ExpandedPillPolicy.boundedScale(listOf(roomy), density))
    }

    @Test
    fun normalContentIsNotScaled() {
        val result = apply(enabled = true, provisionalBottom = 720, profile = shortProfile())
        assertEquals(1f, result.contentScale)
    }

    private fun apply(
        enabled: Boolean,
        provisionalBottom: Int,
        profile: ExpandedContentProfile,
    ) = ExpandedPillPolicy.apply(
        enabled = enabled,
        cardLeft = 48,
        cardRight = 1152,
        cardTop = 36,
        provisionalBottom = provisionalBottom,
        contentOriginY = 120,
        contentLeft = 48,
        nativeRadiusPx = nativeRadius,
        density = density,
        profile = profile,
        displayHeight = 2608,
    )

    private fun shortProfile() = ExpandedContentProfile(
        nativeTopMarginPx = 0,
        contentWidthPx = 1104,
        contentHeightPx = 400,
        leaves = listOf(ContentLeaf(IslandRect(80, 40, 1020, 180), ContentLeafKind.TEXT)),
    )

    private fun tallProfile() = ExpandedContentProfile(
        nativeTopMarginPx = 0,
        contentWidthPx = 1104,
        contentHeightPx = 900,
        leaves = listOf(ContentLeaf(IslandRect(80, 40, 1020, 800), ContentLeafKind.TEXT)),
    )
}
