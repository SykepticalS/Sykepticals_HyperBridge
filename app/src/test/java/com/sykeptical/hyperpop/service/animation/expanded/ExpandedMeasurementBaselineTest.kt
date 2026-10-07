package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedMeasurementBaselineTest {
    private val density = 3f
    private val cutout = IslandRect(566, 36, 634, 108)
    private val compact = IslandRect(460, 36, 740, 108)
    private val nativeExpanded = IslandRect(48, 160, 1152, 800)
    private val width = nativeExpanded.width

    @Test
    fun repeatedRefreshesKeepTheCardOffsetAndContentTop() {
        val profile = profile(
            leaves = listOf(ContentLeaf(IslandRect(48, 36, width - 48, 220), ContentLeafKind.TEXT)),
            contentHeight = nativeExpanded.height,
            margin = 48,
        )
        val savedHeight = nativeExpanded.height
        var height = ExpandedMeasurementBaseline.mergeHeight(
            savedNativeHeightPx = 0,
            requiredHeightPx = ExpandedMeasurementBaseline.requiredHeight(profile),
            xiaomiFieldHeightPx = savedHeight,
            appliedBodyOffsetPx = 0,
        )
        assertEquals(savedHeight, height)
        val first = decide(profile, height)
        repeat(10) { step ->
            val ratcheted = savedHeight + first.bodyOffsetPx * (step + 1)
            val liveMargin = profile.nativeTopMarginPx + first.bodyOffsetPx
            val margin = ExpandedMeasurementBaseline.nativeMargin(
                savedNativeMarginPx = profile.nativeTopMarginPx,
                liveMarginPx = liveMargin,
                appliedBodyOffsetPx = first.bodyOffsetPx,
            )
            height = ExpandedMeasurementBaseline.mergeHeight(
                savedNativeHeightPx = height,
                requiredHeightPx = ExpandedMeasurementBaseline.requiredHeight(profile),
                xiaomiFieldHeightPx = ratcheted,
                appliedBodyOffsetPx = first.bodyOffsetPx,
            )
            val again = decide(profile.copy(nativeTopMarginPx = margin), height)
            assertEquals(first.card, again.card)
            assertEquals(first.bodyOffsetPx, again.bodyOffsetPx)
            assertEquals(first.bodyTop, again.bodyTop)
            assertEquals(first.contentOffsetPx, again.contentOffsetPx)
        }
    }

    @Test
    fun everyReArmAfterACollapseStartsFromTheSameNativeBaseline() {
        val profile = profile(
            leaves = listOf(ContentLeaf(IslandRect(48, 36, width - 48, 220), ContentLeafKind.TEXT)),
            contentHeight = nativeExpanded.height,
            margin = 48,
        )
        val required = ExpandedMeasurementBaseline.requiredHeight(profile)
        val native = nativeExpanded.height
        // Auto expansion: Xiaomi's field is still native when the first session arms.
        var saved = ExpandedMeasurementBaseline.mergeHeight(0, required, native, appliedBodyOffsetPx = 0)
        val first = decide(profile, saved)
        var carried = first.bodyOffsetPx
        var field = native
        repeat(6) {
            // The session runs; Xiaomi measures after the margin and stores the taller number.
            field = saved + carried
            // Manual re-expansion: collapse leaves the field taller; the arm must not adopt it.
            val rearmed = ExpandedMeasurementBaseline.mergeHeight(saved, required, field, carried)
            assertEquals(native, rearmed)
            val again = decide(profile, rearmed)
            assertEquals(first.card, again.card)
            assertEquals(first.bodyTop, again.bodyTop)
            assertEquals(first.bodyOffsetPx, again.bodyOffsetPx)
            assertEquals(first.contentOffsetPx, again.contentOffsetPx)
            saved = rearmed
            carried = again.bodyOffsetPx
        }
        // Without the carried offset the inflated field wins and the next arm drifts.
        val drifted = ExpandedMeasurementBaseline.mergeHeight(saved, required, saved + first.bodyOffsetPx, 0)
        assertTrue(first.bodyOffsetPx <= 0 || drifted > native)
    }

    @Test
    fun aFieldXiaomiRewroteWithNothingAppliedIsTrustedAgain() {
        val grown = 520
        // The collapsed island got a taller template; no offset is carried any more.
        assertEquals(grown, ExpandedMeasurementBaseline.mergeHeight(400, 380, grown, 0))
    }

    @Test
    fun aCallControlPastAShortRootGrowsTheCardAndAParkedOneDoesNot() {
        val title = ContentLeaf(
            IslandRect(48, 36, width - 48, 140),
            ContentLeafKind.TEXT,
            ContentLeafRole.PRIMARY_TITLE,
        )
        val control = ContentLeaf(
            IslandRect(48, 200, 220, 340),
            ContentLeafKind.INTERACTIVE,
            ContentLeafRole.CALL_CONTROL,
        )
        val kept = control.visibleWithin(width, 251)
        assertEquals(340, kept?.bounds?.bottom)
        val below = ContentLeaf(
            IslandRect(48, 400, 220, 520),
            ContentLeafKind.INTERACTIVE,
            ContentLeafRole.CALL_CONTROL,
        ).visibleWithin(width, 251)
        assertEquals(520, below?.bounds?.bottom)
        assertNull(
            ContentLeaf(
                IslandRect(48, 400, 220, 520),
                ContentLeafKind.TEXT,
                ContentLeafRole.SECONDARY_TEXT,
            ).visibleWithin(width, 251),
        )
        assertNull(
            ContentLeaf(
                IslandRect(1069, 48, 1224, 203),
                ContentLeafKind.INTERACTIVE,
                ContentLeafRole.CALL_CONTROL,
            ).visibleWithin(1087, 251),
        )

        val short = decide(
            profile(leaves = listOf(title), contentHeight = 200, margin = 0),
            height = 200,
        )
        val call = decide(
            profile(leaves = listOf(title, kept!!), contentHeight = 251, margin = 0),
            height = ExpandedMeasurementBaseline.mergeHeight(
                savedNativeHeightPx = 251,
                requiredHeightPx = 340,
                xiaomiFieldHeightPx = 251 + 90,
                appliedBodyOffsetPx = 40,
            ),
        )
        val pad = (ExpandedVisualTokens.PILL_BOTTOM_PAD_DP * density).toInt()
        assertTrue(call.card.height < short.card.height + 800)
        assertTrue(call.card.bottom > short.card.bottom)
        assertTrue(call.card.bottom >= call.bodyTop + 340 + pad)
        assertEquals(340, ExpandedMeasurementBaseline.mergeHeight(251, 340, 900, 40))
        assertEquals(340, ExpandedMeasurementBaseline.mergeHeight(340, 340, 1200, 40))
        assertEquals(480, ExpandedMeasurementBaseline.mergeHeight(200, 180, 480, 0))
        assertEquals(560, ExpandedMeasurementBaseline.mergeHeight(480, 560, 700, 40))
        val hung = decide(
            profile(leaves = listOf(title, below!!), contentHeight = 251, margin = 0),
            height = 520,
        )
        assertTrue(hung.card.bottom >= hung.bodyTop + 520 + pad)
        assertTrue(hung.card.bottom > call.card.bottom)
    }

    private fun decide(profile: ExpandedContentProfile, height: Int): ExpandedLayoutDecision.Takeover {
        val native = IslandRect(
            nativeExpanded.left,
            nativeExpanded.top,
            nativeExpanded.right,
            nativeExpanded.top + height,
        )
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
                style = ExpandedVisualStyle(roundedPill = true),
                nativeRadiusPx = 90f,
                content = profile,
            ),
        )
        assertTrue(decision is ExpandedLayoutDecision.Takeover)
        return decision as ExpandedLayoutDecision.Takeover
    }

    private fun profile(
        leaves: List<ContentLeaf>,
        contentHeight: Int,
        margin: Int,
    ) = ExpandedContentProfile(
        nativeTopMarginPx = margin,
        contentWidthPx = width,
        contentHeightPx = contentHeight,
        leaves = leaves,
    )
}
