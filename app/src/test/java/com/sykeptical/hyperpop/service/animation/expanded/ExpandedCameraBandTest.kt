package com.sykeptical.hyperpop.service.animation.expanded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedCameraBandTest {
    private val density = 3f
    private val compact = IslandRect(460, 36, 740, 108)
    private val cutout = IslandRect(566, 47, 634, 115)

    @Test
    fun aCutoutCenteredOnTheCompactIslandIsKept() {
        assertTrue(CameraBandGeometry.valid(cutout, compact, displayCutoutWidth = 68))
        assertEquals(cutout, CameraBandGeometry.resolve(cutout, compact, 68))
    }

    @Test
    fun aCutoutWhoseCenterMissesTheIslandIsRebuilt() {
        val reported = IslandRect(566, 0, 634, 68)
        assertFalse(CameraBandGeometry.valid(reported, compact, 68))
        val resolved = CameraBandGeometry.resolve(reported, compact, 68)
        assertNotNull(resolved)
        assertTrue(compact.contains(resolved!!.centerX, resolved.centerY))
        assertTrue(resolved.width in 60..76)
    }

    @Test
    fun bothCutoutSourcesMissingFailsOpenToNull() {
        assertNull(CameraBandGeometry.resolve(null, IslandRect(0, 0, 0, 0), 0))
    }

    @Test
    fun titleFitCoversTheFourCasesAndBothDirections() {
        val exclusion = CameraBandGeometry.exclusion(cutout, density)
        val fits = CutoutTextClipPolicy.evaluate(80f, 200, 400, exclusion, density, rtl = false)
        assertEquals(CutoutTextClipPolicy.Fit.FITS, fits.fit)
        assertEquals(0, fits.fadePx)

        val container = CutoutTextClipPolicy.evaluate(400f, 80, 200, exclusion, density, rtl = false)
        assertEquals(CutoutTextClipPolicy.Fit.CONTAINER, container.fit)
        assertTrue(container.fadePx > 0)
        assertFalse(container.cutoutLimited)

        val cutoutFit = CutoutTextClipPolicy.evaluate(900f, 180, 900, exclusion, density, rtl = false)
        assertEquals(CutoutTextClipPolicy.Fit.CUTOUT, cutoutFit.fit)
        assertTrue(cutoutFit.visiblePx >= (ExpandedVisualTokens.MIN_BAND_TEXT_DP * density).toInt())
        assertTrue(cutoutFit.fadePx <= cutoutFit.visiblePx / 2)
        assertTrue(cutoutFit.safeEnd(180) <= exclusion.left)

        val rtl = CutoutTextClipPolicy.evaluate(900f, 300, 1100, exclusion, density, rtl = true)
        assertEquals(CutoutTextClipPolicy.Fit.CUTOUT, rtl.fit)
        assertTrue(rtl.visiblePx > 0)

        val fallback = CutoutTextClipPolicy.evaluate(40f, exclusion.left - 20, exclusion.right + 20, exclusion, density, rtl = false)
        assertEquals(CutoutTextClipPolicy.Fit.FALLBACK, fallback.fit)
        assertEquals(0, fallback.fadePx)
    }

    @Test
    fun fadeLengthScalesWithTheSpanAndDensity() {
        listOf(2.75f, 3f, 3.5f).forEach { density ->
            val span = (ExpandedVisualTokens.MIN_BAND_TEXT_DP * density).toInt() + 40
            val fade = CutoutTextClipPolicy.fadePx(span, density)
            val minPx = (ExpandedVisualTokens.FADE_MIN_DP * density).toInt()
            val maxPx = (ExpandedVisualTokens.FADE_MAX_DP * density).toInt()
            assertTrue(fade in minPx..maxPx)
            assertTrue(fade <= span / 2)
        }
        assertEquals(0, CutoutTextClipPolicy.startFadePx(0f, density))
        val cap = (ExpandedVisualTokens.START_FADE_MAX_DP * density).toInt()
        assertEquals(cap, CutoutTextClipPolicy.startFadePx(10_000f, density))
    }

    @Test
    fun pillLiftsAClippableTitleBesideTheCameraAndKeepsTheHangUpClear() {
        val profile = ExpandedContentProfile(
            nativeTopMarginPx = 0,
            contentWidthPx = 1087,
            contentHeightPx = 251,
            leaves = listOf(
                ContentLeaf(IslandRect(54, 54, 197, 197), ContentLeafKind.PLAIN, ContentLeafRole.AVATAR),
                ContentLeaf(IslandRect(233, 60, 854, 133), ContentLeafKind.TEXT, ContentLeafRole.PRIMARY_TITLE),
                ContentLeaf(IslandRect(233, 133, 510, 190), ContentLeafKind.TEXT, ContentLeafRole.SECONDARY_TEXT),
                ContentLeaf(IslandRect(884, 48, 1039, 203), ContentLeafKind.INTERACTIVE, ContentLeafRole.CALL_CONTROL),
            ),
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
                compact = IslandRect(520, 30, 680, 132),
                nativeExpanded = IslandRect(56, 156, 1143, 407),
                statusBarHeight = 144,
                density = density,
                style = ExpandedVisualStyle(blackBackground = true, roundedPill = true),
                nativeRadiusPx = 90f,
                content = profile,
            ),
        ) as ExpandedLayoutDecision.Takeover
        val gap = ExpandedVisualTokens.px(ExpandedVisualTokens.CAMERA_VERTICAL_GAP_DP, density)
        assertTrue(decision.bodyTop + 60 >= cutout.bottom + gap)
        assertEquals(1f, decision.contentScale)
        assertTrue(decision.textClips.isEmpty())
        val topGap = decision.bodyTop + 48 - 30
        val bottomGap = decision.card.bottom - (decision.bodyTop + 203)
        assertEquals(topGap, bottomGap)
        val buttonTop = decision.bodyTop + 48
        val button = IslandRect(56 + 884, buttonTop, 56 + 1039, buttonTop + 155)
        assertFalse(button.intersects(CameraBandGeometry.exclusion(cutout, density)))
        assertNotNull(decision.flowMask)
        assertTrue(decision.flowMask!!.blackUntilY >= cutout.bottom)
    }

    @Test
    fun pillOffStillDropsACrossingTitleBelowTheCutout() {
        val profile = ExpandedContentProfile(
            nativeTopMarginPx = 0,
            contentWidthPx = 1087,
            contentHeightPx = 251,
            leaves = listOf(
                ContentLeaf(IslandRect(233, 60, 854, 133), ContentLeafKind.TEXT, ContentLeafRole.PRIMARY_TITLE),
            ),
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
                compact = IslandRect(520, 30, 680, 132),
                nativeExpanded = IslandRect(56, 156, 1143, 407),
                statusBarHeight = 144,
                density = density,
                content = profile,
            ),
        ) as ExpandedLayoutDecision.Takeover
        assertTrue(decision.bodyTop + 60 >= cutout.bottom)
        assertTrue(decision.textClips.isEmpty())
    }

    @Test
    fun callControlsDoNotShrinkAndPillsGrowTheirHitArea() {
        assertEquals(156, CallControlPolicy.visualPx(156))
        assertTrue(CallControlPolicy.touchPx(156, density) >= 156)
        assertTrue(CallControlPolicy.touchPx(40, density) >= (ExpandedVisualTokens.MIN_TOUCH_DP * density).toInt())

        val visual = IslandRect(100, 40, 220, 80)
        val touch = ActionButtonPolicy.touchRect(visual, density, leftLimit = 80, rightLimit = 240)
        assertTrue(touch.contains(visual))
        assertTrue(touch.height >= (ExpandedVisualTokens.MIN_TOUCH_DP * density).toInt())
        assertTrue(touch.left >= 80)
        assertTrue(touch.right <= 240)

        val plan = FocusTemplatePolicy.plan(density, pill = true)
        assertTrue(plan.retune)
        assertTrue(plan.pillMarginVerticalPx < ExpandedVisualTokens.px(ExpandedVisualTokens.FOCUS_PILL_MARGIN_VERTICAL_DP, density))
        assertFalse(FocusTemplatePolicy.plan(density, pill = false).retune)
    }

    @Test
    fun mediaSystemMarginsMoveArtInwardWithoutScalingActions() {
        val margins = MediaSystemPolicy.margins(density)
        assertTrue(margins.artStartPx > (15f * density).toInt())
        assertTrue(margins.artTopPx < (15f * density).toInt())
        assertTrue(margins.actionTopPx < (11f * density).toInt())
        assertTrue(margins.progressBottomPx < (16f * density).toInt())
        assertEquals(CallControlPolicy.visualPx((ExpandedVisualTokens.PRIMARY_CONTROL_DP * density).toInt()), (ExpandedVisualTokens.PRIMARY_CONTROL_DP * density).toInt())
    }

    @Test
    fun familiesKeepTheirOwnCameraRules() {
        assertTrue(MessagePolicy.avatarUsesBand())
        assertFalse(MessagePolicy.bodyScrolls())
        assertEquals(ContentLeafRole.PRIMARY_TITLE, MessagePolicy.titleRole())
        assertEquals(ContentLeafRole.CALL_CONTROL, VoiceMessagePolicy.playControlRole())
        assertEquals(ContentLeafRole.SECONDARY_TEXT, VoiceMessagePolicy.durationRole())
        assertTrue(ProgressPolicy.staysBelowCamera())
        assertFalse(ProgressPolicy.labelScrolls())
        assertFalse(NavigationPolicy.specialTitleUsesBand())
        assertFalse(ContentLeaf(IslandRect(0, 0, 10, 10), ContentLeafKind.PLAIN, ProgressPolicy.role()).sharesCameraBand())
        assertTrue(ContentLeaf(IslandRect(0, 0, 10, 10), ContentLeafKind.TEXT, MessagePolicy.titleRole()).sharesCameraBand())
    }
}

private fun CutoutTextClipPolicy.Result.safeEnd(viewLeft: Int): Int = viewLeft + visiblePx
