package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val leafTop = 40
        val leafBottom = 180
        val pad = (ExpandedPillPolicy.BOTTOM_PAD_DP * density).toInt()
        val slack = (ExpandedPillPolicy.MIRROR_SLACK_DP * density).toInt()
        val topGap = origin + leafTop - 36
        val bottomGap = maxOf(pad, topGap - slack)
        assertTrue(result.cardBottom < 720)
        assertEquals(origin + leafBottom + bottomGap, result.cardBottom)
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
    fun mediaTrimRaisesOnlyTheBottomAndLeavesOtherFamiliesAlone() {
        val plain = apply(enabled = true, provisionalBottom = 720, profile = shortProfile())
        val mediaLeaf = shortProfile().leaves.single().copy(role = ContentLeafRole.PROGRESS)
        val media = apply(
            enabled = true,
            provisionalBottom = 720,
            profile = shortProfile().copy(leaves = listOf(mediaLeaf)),
        )
        val trim = (ExpandedVisualTokens.MEDIA_OUTER_BOTTOM_TRIM_DP * density).toInt()
        assertEquals(0, plain.bottomTrimPx)
        assertEquals(trim, media.bottomTrimPx)
        assertEquals(plain.cardBottom - trim, media.cardBottom)
        assertTrue(media.cardBottom > 120 + mediaLeaf.bounds.bottom)
    }

    @Test
    fun normalContentIsNotScaled() {
        val result = apply(enabled = true, provisionalBottom = 720, profile = shortProfile())
        assertEquals(1f, result.contentScale)
    }

    @Test
    fun aLeafThatMissesTheCornerRelaxesTheRadiusInsteadOfScaling() {
        val edge = ContentLeaf(IslandRect(0, 0, 20, 30), ContentLeafKind.INTERACTIVE)
        val profile = shortProfile().copy(leaves = listOf(edge))
        val result = apply(enabled = true, provisionalBottom = 720, profile = profile)
        assertEquals(1f, result.contentScale)
        assertTrue(result.radiusPx <= ExpandedPillPolicy.radiusCap(result.cardBottom - 36, nativeRadius, density))
        assertTrue(result.radiusPx >= 0f)
    }

    @Test
    fun aButtonParkedOffTheEdgeDoesNotCountAsContent() {
        val parked = ContentLeaf(IslandRect(1069, 48, 1224, 203), ContentLeafKind.INTERACTIVE)
        val shown = ContentLeaf(IslandRect(884, 48, 1039, 203), ContentLeafKind.INTERACTIVE)
        val straddling = ContentLeaf(IslandRect(1000, 48, 1120, 203), ContentLeafKind.INTERACTIVE)
        assertNull(parked.visibleWithin(1087, 251))
        assertEquals(shown, shown.visibleWithin(1087, 251))
        assertEquals(IslandRect(1000, 48, 1087, 203), straddling.visibleWithin(1087, 251)?.bounds)
    }

    @Test
    fun outgoingCallAfterItsStateChangeStillGetsThePill() {
        val probed = listOf(
            ContentLeaf(IslandRect(884, 48, 1039, 203), ContentLeafKind.INTERACTIVE),
            ContentLeaf(IslandRect(1069, 48, 1224, 203), ContentLeafKind.INTERACTIVE),
            ContentLeaf(IslandRect(54, 54, 197, 197), ContentLeafKind.PLAIN),
            ContentLeaf(IslandRect(161, 161, 209, 209), ContentLeafKind.PLAIN),
            ContentLeaf(IslandRect(233, 60, 854, 133), ContentLeafKind.TEXT),
            ContentLeaf(IslandRect(233, 133, 510, 190), ContentLeafKind.TEXT),
        )
        assertEquals(89.625f, callRadius(probed).radiusPx, 0.01f)
        val pill = callRadius(probed.mapNotNull { it.visibleWithin(1087, 251) })
        assertEquals(
            ExpandedPillPolicy.radiusCap(pill.cardBottom - 30, 89.625f, density),
            pill.radiusPx,
            0.01f,
        )
        assertTrue(pill.radiusPx > 110f)
    }

    private fun callRadius(leaves: List<ContentLeaf>) = ExpandedPillPolicy.apply(
        enabled = true,
        cardLeft = 56,
        cardRight = 1143,
        cardTop = 30,
        provisionalBottom = 351,
        contentOriginY = 30,
        contentLeft = 56,
        nativeRadiusPx = 89.625f,
        density = density,
        profile = ExpandedContentProfile(
            nativeTopMarginPx = 0,
            contentWidthPx = 1087,
            contentHeightPx = 251,
            leaves = leaves,
        ),
        displayHeight = 2608,
    )

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
