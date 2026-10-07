package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedDecisionRefreshPolicyTest {
    private val density = 3f
    private val cutout = IslandRect(566, 36, 634, 108)
    private val compact = IslandRect(460, 36, 740, 108)
    private val staleNative = IslandRect(48, 160, 1152, 330)
    private val settledNative = IslandRect(48, 160, 1152, 640)

    @Test
    fun aSessionWithoutAProfileKeepsReadingUntilItHasOne() {
        assertTrue(ExpandedDecisionRefreshPolicy.shouldRefresh(tight = false, provisional = true))
        assertTrue(ExpandedDecisionRefreshPolicy.shouldRefresh(tight = true, provisional = false))
        assertFalse(ExpandedDecisionRefreshPolicy.shouldRefresh(tight = false, provisional = false))
    }

    @Test
    fun aTargetIsReReadOnlyWhenTheSessionIsProvisionalOrXiaomiBoundSinceTheLastRead() {
        assertTrue(ExpandedDecisionRefreshPolicy.needsSettle(provisional = true, nativeVersion = 4, settledVersion = 4))
        assertTrue(ExpandedDecisionRefreshPolicy.needsSettle(provisional = false, nativeVersion = 5, settledVersion = 4))
        assertFalse(ExpandedDecisionRefreshPolicy.needsSettle(provisional = false, nativeVersion = 4, settledVersion = 4))
    }

    @Test
    fun theFirstCompleteReadingReplacesAProvisionalDecisionEvenWhenTheChangeIsSmall() {
        val provisional = takeover(content = null, native = settledNative)
        val complete = takeover(content = profile(lowest = 312), native = settledNative)
        assertTrue(complete.tightLayout)
        assertTrue(ExpandedDecisionRefreshPolicy.adopt(provisional, complete, provisional = true))
    }

    @Test
    fun aProvisionalDecisionNeverAdoptsAnotherProfilelessReading() {
        val first = takeover(content = null, native = staleNative)
        val second = takeover(content = null, native = settledNative)
        assertFalse(ExpandedDecisionRefreshPolicy.adopt(first, second, provisional = true))
    }

    @Test
    fun aSettledDecisionKeepsTheExistingSlack() {
        val pill = ExpandedVisualStyle(roundedPill = true)
        val base = takeover(content = profile(lowest = 312), native = settledNative, style = pill)
        val same = takeover(content = profile(lowest = 312), native = settledNative, style = pill)
        val nudged = takeover(content = profile(lowest = 316), native = settledNative, style = pill)
        val taller = takeover(content = profile(lowest = 380), native = settledNative, style = pill)
        assertFalse(ExpandedDecisionRefreshPolicy.adopt(base, same, provisional = false))
        assertFalse(ExpandedDecisionRefreshPolicy.adopt(base, nudged, provisional = false))
        assertTrue(ExpandedDecisionRefreshPolicy.adopt(base, taller, provisional = false))
    }

    @Test
    fun aStuckOffsetCanBeCorrected() {
        val pill = ExpandedVisualStyle(roundedPill = true)
        val high = takeover(content = profile(lowest = 312).copy(nativeTopMarginPx = 20), native = settledNative, style = pill)
        val corrected = takeover(content = profile(lowest = 312).copy(nativeTopMarginPx = 80), native = settledNative, style = pill)
        assertTrue(high.bodyOffsetPx > corrected.bodyOffsetPx + ExpandedDecisionRefreshPolicy.OFFSET_SLACK_PX)
        assertTrue(ExpandedDecisionRefreshPolicy.adopt(high, corrected, provisional = false))
    }

    @Test
    fun theCompleteTemplateWithActionsGivesATallerCardThanTheEarlyReading() {
        val pill = ExpandedVisualStyle(roundedPill = true)
        val titleOnly = takeover(content = profile(lowest = 150, actions = false), native = staleNative, style = pill)
        val complete = takeover(content = profile(lowest = 312, actions = true), native = settledNative, style = pill)
        assertTrue(complete.card.bottom > titleOnly.card.bottom)
        assertTrue(complete.card.bottom >= complete.bodyTop + 312)
        assertEquals(complete.card.top, titleOnly.card.top)
        assertTrue(ExpandedDecisionRefreshPolicy.adopt(titleOnly, complete, provisional = false))
    }

    @Test
    fun aZeroActionTemplateResolvesWithoutAnActionRow() {
        val pill = ExpandedVisualStyle(roundedPill = true)
        val noActions = takeover(content = profile(lowest = 150, actions = false), native = settledNative, style = pill)
        val withActions = takeover(content = profile(lowest = 312, actions = true), native = settledNative, style = pill)
        assertTrue(noActions.card.height < withActions.card.height)
        assertTrue(noActions.card.bottom >= noActions.bodyTop + 150)
    }

    private fun takeover(
        content: ExpandedContentProfile?,
        native: IslandRect,
        style: ExpandedVisualStyle = ExpandedVisualStyle(),
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
                nativeExpanded = native,
                statusBarHeight = 144,
                density = density,
                style = style,
                nativeRadiusPx = 90f,
                content = content,
            ),
        )
        assertTrue(decision is ExpandedLayoutDecision.Takeover)
        return decision as ExpandedLayoutDecision.Takeover
    }

    /** Title row on top; with [actions], a pill row ending at [lowest]. */
    private fun profile(lowest: Int, actions: Boolean = lowest > 200): ExpandedContentProfile {
        val width = settledNative.width
        val leaves = buildList {
            add(ContentLeaf(IslandRect(48, 36, width - 48, 110), ContentLeafKind.TEXT, ContentLeafRole.PRIMARY_TITLE))
            if (actions) {
                add(ContentLeaf(IslandRect(48, lowest - 72, 300, lowest), ContentLeafKind.TEXT, ContentLeafRole.ACTION_PILL))
            } else {
                add(ContentLeaf(IslandRect(48, 100, width - 48, lowest), ContentLeafKind.TEXT, ContentLeafRole.SECONDARY_TEXT))
            }
        }
        return ExpandedContentProfile(
            nativeTopMarginPx = 48,
            contentWidthPx = width,
            contentHeightPx = lowest + 60,
            leaves = leaves,
            clusters = listOf(ContentCluster(0, IslandRect(0, 0, width, lowest), decorative = false)),
        )
    }
}
