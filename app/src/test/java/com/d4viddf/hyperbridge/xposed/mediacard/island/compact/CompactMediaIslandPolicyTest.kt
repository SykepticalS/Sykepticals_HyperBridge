package com.d4viddf.hyperbridge.xposed.mediacard.island.compact

import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactMediaIslandPolicyTest {
    @Test fun defaultsShowTheTitleCycleAndKeepScrolling() {
        val settings = CompactMediaIslandSettings.defaults()
        assertTrue(settings.showTitle)
        assertTrue(settings.cycleTitleArtist)
        assertTrue(settings.cycleActive)
        assertEquals(MediaCardConstants.COMPACT_TITLE_SCROLL_FOREVER, settings.titleScrollMode)
        assertEquals(100, settings.titleScrollSpeed)
        assertTrue(settings.titleScrollBounce)
        assertEquals(0, settings.widthPercent)
        assertNull(CompactMediaIslandPolicy.scrollLoopLimit(settings.titleScrollMode, settings.titleScrollBounce))
    }

    @Test fun rawValuesClampIntoTheSupportedRange() {
        val settings = CompactMediaIslandSettings.fromRaw(
            showTitle = true,
            scrollMode = 9,
            speed = 1,
            bounce = false,
            cycle = true,
            width = 100,
        )
        assertEquals(MediaCardConstants.COMPACT_TITLE_SCROLL_FOREVER, settings.titleScrollMode)
        assertEquals(MediaCardConstants.MIN_HOOK_ISLAND_COMPACT_TITLE_SCROLL_SPEED, settings.titleScrollSpeed)
        assertEquals(30, settings.widthPercent)
        assertTrue(settings.cycleActive)
        assertEquals(0, CompactMediaIslandPolicy.scrollLoopLimit(settings.titleScrollMode, settings.titleScrollBounce))
    }

    @Test fun cycleRequiresTheTitleToBeVisible() {
        assertFalse(CompactMediaIslandPolicy.cycleActive(showTitle = false, cycleTitleArtist = true))
        assertTrue(CompactMediaIslandPolicy.cycleActive(showTitle = true, cycleTitleArtist = true))
    }

    @Test fun scrollTitleLoopsForTheChosenRepeatCount() {
        assertEquals(1, CompactMediaIslandPolicy.scrollLoopLimit(1, scrollTitle = true))
        assertEquals(2, CompactMediaIslandPolicy.scrollLoopLimit(2, scrollTitle = true))
        assertNull(CompactMediaIslandPolicy.scrollLoopLimit(3, scrollTitle = true))
        assertNull(CompactMediaIslandPolicy.scrollLoopLimit(0, scrollTitle = true))
        assertEquals(0, CompactMediaIslandPolicy.scrollLoopLimit(3, scrollTitle = false))
    }

    @Test fun cycleScrollFinishesInsideTheBudgetButNeverCrawls() {
        assertEquals(100L, CompactMediaIslandPolicy.CYCLE_SCROLL_DELAY_MS)
        assertEquals(100f, CompactMediaIslandPolicy.cycleScrollSpeedPxPerSec(300f, 3_000L, 20), 0.01f)
        assertEquals(150f, CompactMediaIslandPolicy.cycleScrollSpeedPxPerSec(300f, 3_000L, 150), 0.01f)
        assertEquals(40f, CompactMediaIslandPolicy.cycleScrollSpeedPxPerSec(200f, 5_000L, 20), 0.01f)
        assertEquals(0f, CompactMediaIslandPolicy.cycleScrollSpeedPxPerSec(0f, 3_000L, 100), 0.01f)
        assertEquals(0f, CompactMediaIslandPolicy.cycleScrollSpeedPxPerSec(80f, 0L, 100), 0.01f)
    }

    @Test fun firstVisibleCycleLineIsRecheckedAndSettledQuickly() {
        assertEquals(50L, CompactMediaIslandPolicy.APPEARANCE_RECHECK_MS)
        assertEquals(100L, CompactMediaIslandPolicy.APPEARANCE_SETTLE_MS)
        assertEquals(
            250L,
            CompactMediaIslandPolicy.APPEARANCE_RECHECK_MS +
                CompactMediaIslandPolicy.APPEARANCE_SETTLE_MS +
                CompactMediaIslandPolicy.CYCLE_SCROLL_DELAY_MS,
        )
    }

    @Test fun scrolledLinesWaitOneSecondAndFittingLinesMoveOnQuickly() {
        assertEquals(1_000L, CompactMediaIslandPolicy.cycleHoldMs(overflowPx = 40f, budgetMs = 3_000L))
        assertEquals(1_200L, CompactMediaIslandPolicy.cycleHoldMs(overflowPx = 0f, budgetMs = 3_000L))
        assertEquals(1_200L, CompactMediaIslandPolicy.cycleHoldMs(overflowPx = 0f, budgetMs = 5_000L))
        assertEquals(800L, CompactMediaIslandPolicy.cycleHoldMs(overflowPx = 0f, budgetMs = 800L))
    }

    @Test fun cycleRunsTitleThenArtistThenStaysOnTheTitle() {
        assertEquals(
            CompactMediaIslandPolicy.CyclePhase.ARTIST,
            CompactMediaIslandPolicy.nextCyclePhase(CompactMediaIslandPolicy.CyclePhase.TITLE),
        )
        assertEquals(
            CompactMediaIslandPolicy.CyclePhase.SETTLED,
            CompactMediaIslandPolicy.nextCyclePhase(CompactMediaIslandPolicy.CyclePhase.ARTIST),
        )
        assertEquals(
            CompactMediaIslandPolicy.CyclePhase.SETTLED,
            CompactMediaIslandPolicy.nextCyclePhase(CompactMediaIslandPolicy.CyclePhase.SETTLED),
        )
        assertEquals(
            CompactMediaIslandPolicy.CyclePhase.SETTLED,
            CompactMediaIslandPolicy.nextCyclePhase(
                CompactMediaIslandPolicy.CyclePhase.TITLE,
                artistBlank = true,
            ),
        )
        val artist = CompactMediaIslandPolicy.artistLine("Band")
        assertEquals("Song", CompactMediaIslandPolicy.cycleText(CompactMediaIslandPolicy.CyclePhase.TITLE, "Song", artist))
        assertEquals("By: Band", CompactMediaIslandPolicy.cycleText(CompactMediaIslandPolicy.CyclePhase.ARTIST, "Song", artist))
        assertEquals("Song", CompactMediaIslandPolicy.cycleText(CompactMediaIslandPolicy.CyclePhase.SETTLED, "Song", artist))
        assertEquals("Song", CompactMediaIslandPolicy.cycleText(CompactMediaIslandPolicy.CyclePhase.ARTIST, "Song", ""))
    }

    @Test fun artistLineIsPrefixedOnlyWhenThereIsAnArtist() {
        assertEquals("By: Daft Punk", CompactMediaIslandPolicy.artistLine("  Daft Punk "))
        assertEquals("", CompactMediaIslandPolicy.artistLine("   "))
    }

    @Test fun trackChangeResetsTheCycle() {
        assertTrue(CompactMediaIslandPolicy.shouldResetCycle(null, "a\u0000b"))
        assertFalse(CompactMediaIslandPolicy.shouldResetCycle("a\u0000b", "a\u0000b"))
        assertTrue(CompactMediaIslandPolicy.shouldResetCycle("a\u0000b", "c\u0000b"))
    }

    @Test fun turnSpringStartsAtZeroAndSettlesAtOne() {
        assertEquals(0f, CompactMediaIslandPolicy.turnProgress(0f), 0.0001f)
        assertEquals(1f, CompactMediaIslandPolicy.turnProgress(1f), 0.0001f)
        assertTrue(CompactMediaIslandPolicy.turnProgress(0.3f) > 0.5f)
        assertEquals(1f, CompactMediaIslandPolicy.turnProgress(0.99f), 0.01f)
    }

    @Test fun widthSliderStopsAtThirtyWithoutJumpingToTheDisplay() {
        assertNull(CompactMediaIslandPolicy.islandMaxOverridePx(0, 400f, density = 2f))
        assertNull(CompactMediaIslandPolicy.islandMaxOverridePx(-10, 400f, density = 2f))
        assertEquals(416.8f, CompactMediaIslandPolicy.islandMaxOverridePx(15, 400f, density = 2f)!!, 0.01f)
        assertEquals(433.6f, CompactMediaIslandPolicy.islandMaxOverridePx(30, 400f, density = 2f)!!, 0.01f)
        assertEquals(433.6f, CompactMediaIslandPolicy.islandMaxOverridePx(100, 400f, density = 2f)!!, 0.01f)
    }

    @Test fun textViewportLeavesRoomForTheAlbumCover() {
        assertEquals(228, CompactMediaIslandPolicy.leftSlotMaxPx(0, density = 2f))
        assertEquals(261, CompactMediaIslandPolicy.leftSlotMaxPx(30, density = 2f))
        assertEquals(164, CompactMediaIslandPolicy.textViewportPx(0, density = 2f, leadingPx = 64))
        assertEquals(80, CompactMediaIslandPolicy.textViewportPx(0, density = 2f, leadingPx = 500))
    }

    @Test fun lengthWithoutATitleAddsEmptyRoomThatGrowsWithTheSlider() {
        assertEquals(1, CompactMediaIslandPolicy.spacerWidthPx(0, density = 2f, leadingPx = 60))
        assertEquals(100, CompactMediaIslandPolicy.spacerWidthPx(15, density = 2f, leadingPx = 60))
        assertEquals(201, CompactMediaIslandPolicy.spacerWidthPx(30, density = 2f, leadingPx = 60))
    }

    @Test fun slotWidthFollowsTheWidestLineUpToTheViewport() {
        assertEquals(124, CompactMediaIslandPolicy.slotWidthPx(listOf(80.2f, 119.4f), 4, 300))
        assertEquals(300, CompactMediaIslandPolicy.slotWidthPx(listOf(80f, 900f), 4, 300))
        assertEquals(1, CompactMediaIslandPolicy.slotWidthPx(emptyList(), 0, 300))
    }

    @Test fun defaultLengthFollowsTheDisplayedLineButExplicitLengthStaysStable() {
        val lines = listOf(80.2f, 119.4f)
        assertEquals(85, CompactMediaIslandPolicy.displayedSlotWidthPx(0, 80.2f, lines, 4, 300))
        assertEquals(124, CompactMediaIslandPolicy.displayedSlotWidthPx(0, 119.4f, lines, 4, 300))
        assertEquals(124, CompactMediaIslandPolicy.displayedSlotWidthPx(15, 80.2f, lines, 4, 300))
        assertEquals(124, CompactMediaIslandPolicy.displayedSlotWidthPx(15, 119.4f, lines, 4, 300))
    }

    @Test fun onlyDefaultLengthAddsCameraFadeClearance() {
        assertEquals(12, CompactMediaIslandPolicy.defaultEndClearancePx(0, density = 3f))
        assertEquals(0, CompactMediaIslandPolicy.defaultEndClearancePx(1, density = 3f))
        assertEquals(0, CompactMediaIslandPolicy.defaultEndClearancePx(30, density = 3f))
    }
}
