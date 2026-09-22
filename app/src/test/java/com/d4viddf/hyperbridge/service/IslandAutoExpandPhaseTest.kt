package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandAutoExpandPhaseTest {
    @Test
    fun taggedEntranceForcesMaxWidthUntilExpandIsCaptured() {
        val phase = IslandAutoExpandPhaseMachine.start(tagged = true)

        assertEquals(IslandAutoExpandPhase.Entrance, phase)
        assertTrue(IslandAutoExpandPhaseMachine.forcesMaxWidth(phase))

        val expanding = IslandAutoExpandPhaseMachine.onAppearFinished(
            phase = phase,
            appearPending = true,
            settledInCutout = true,
        )
        assertTrue(expanding.requestExpand)
        assertFalse(expanding.releaseWidth)
        assertTrue(IslandAutoExpandPhaseMachine.forcesMaxWidth(expanding.phase))

        val again = IslandAutoExpandPhaseMachine.onAppearFinished(
            phase = expanding.phase,
            appearPending = true,
            settledInCutout = true,
        )
        assertFalse(again.requestExpand)

        val measured = IslandAutoExpandPhaseMachine.onExpandCaptured(expanding.phase)
        assertTrue(measured.releaseWidth)
        assertEquals(IslandAutoExpandPhase.Measured, measured.phase)
        assertFalse(IslandAutoExpandPhaseMachine.forcesMaxWidth(measured.phase))
        assertFalse(IslandAutoExpandPhaseMachine.onExpandCaptured(measured.phase).releaseWidth)
        assertEquals(750L, IslandAutoExpandPhaseMachine.EXPAND_DELAY_MS)
    }

    @Test
    fun manualExpandDuringTheWaitReleasesTheMaxWidth() {
        val measured = IslandAutoExpandPhaseMachine.onExpandCaptured(IslandAutoExpandPhase.Entrance)
        assertTrue(measured.releaseWidth)
        assertEquals(IslandAutoExpandPhase.Measured, measured.phase)
        assertFalse(IslandAutoExpandPhaseMachine.forcesMaxWidth(measured.phase))
    }

    @Test
    fun entranceWidthCannotUsePortraitHeightAsHorizontalWidth() {
        assertEquals(1200, IslandAutoExpandPhaseMachine.entranceWidth(2608, 1200))
        assertEquals(806, IslandAutoExpandPhaseMachine.entranceWidth(806, 1200))
        assertEquals(1200, IslandAutoExpandPhaseMachine.entranceWidth(0, 1200))
        assertEquals(806, IslandAutoExpandPhaseMachine.entranceWidth(806, 0))
        assertEquals(0, IslandAutoExpandPhaseMachine.entranceWidth(0, 0))
    }

    @Test
    fun untaggedIslandsNeverEnterTheStagedSequence() {
        val phase = IslandAutoExpandPhaseMachine.start(tagged = false)

        assertEquals(IslandAutoExpandPhase.Inactive, phase)
        assertFalse(IslandAutoExpandPhaseMachine.forcesMaxWidth(phase))
        val finished = IslandAutoExpandPhaseMachine.onAppearFinished(
            phase = phase,
            appearPending = true,
            settledInCutout = true,
        )
        assertFalse(finished.requestExpand)
        assertEquals(IslandAutoExpandPhase.Inactive, finished.phase)
    }

    @Test
    fun appearFinishDoesNotExpandBeforeTheCutoutSettles() {
        val waiting = IslandAutoExpandPhaseMachine.onAppearFinished(
            phase = IslandAutoExpandPhase.Entrance,
            appearPending = true,
            settledInCutout = false,
        )
        val unrelated = IslandAutoExpandPhaseMachine.onAppearFinished(
            phase = IslandAutoExpandPhase.Entrance,
            appearPending = false,
            settledInCutout = true,
        )

        assertFalse(waiting.requestExpand)
        assertEquals(IslandAutoExpandPhase.Entrance, waiting.phase)
        assertFalse(unrelated.requestExpand)
    }

    @Test
    fun updateAlreadyInTheCutoutExpandsOnceWithoutAnotherAppearAnimation() {
        val decision = IslandAutoExpandPhaseMachine.onAlreadyInCutout(
            phase = IslandAutoExpandPhase.Entrance,
            suppressesAppearAnimation = true,
            settledInCutout = true,
        )
        assertTrue(decision.requestExpand)
        assertEquals(IslandAutoExpandPhase.Expanding, decision.phase)

        val second = IslandAutoExpandPhaseMachine.onAlreadyInCutout(
            phase = decision.phase,
            suppressesAppearAnimation = true,
            settledInCutout = true,
        )
        assertFalse(second.requestExpand)
    }

    @Test
    fun freshPostDoesNotSkipTheAppearAnimation() {
        val decision = IslandAutoExpandPhaseMachine.onAlreadyInCutout(
            phase = IslandAutoExpandPhase.Entrance,
            suppressesAppearAnimation = false,
            settledInCutout = true,
        )
        assertFalse(decision.requestExpand)
        assertEquals(IslandAutoExpandPhase.Entrance, decision.phase)
    }

    @Test
    fun onlyTheLiveCutoutAppearAnimationsQualify() {
        assertTrue(IslandAutoExpandPhaseMachine.isCutoutAppearAnimation("hiddenToBigIslandAnimation"))
        assertTrue(IslandAutoExpandPhaseMachine.isCutoutAppearAnimation("initToBigIslandAnimation"))
        assertFalse(IslandAutoExpandPhaseMachine.isCutoutAppearAnimation("expandedToBigIslandAnimation"))
        assertFalse(IslandAutoExpandPhaseMachine.isCutoutAppearAnimation("bigIslandToExpandedAnimation"))
    }

    @Test
    fun cutoutBigStatesExcludeTheExpandedCard() {
        assertTrue(IslandAutoExpandPhaseMachine.isCutoutBigState("DynamicIslandState\$BigIsland"))
        assertTrue(IslandAutoExpandPhaseMachine.isCutoutBigState("DynamicIslandState.ShowOnceBigIsland"))
        assertFalse(IslandAutoExpandPhaseMachine.isCutoutBigState("DynamicIslandState\$Expanded"))
        assertFalse(IslandAutoExpandPhaseMachine.isCutoutBigState("DynamicIslandState\$Hidden"))
        assertTrue(IslandAutoExpandPhaseMachine.isExpandedState("DynamicIslandState\$Expanded"))
        assertTrue(IslandAutoExpandPhaseMachine.isExpandedState("DynamicIslandState\$AppExpanded"))
        assertFalse(IslandAutoExpandPhaseMachine.isExpandedState("DynamicIslandState\$BigIsland"))
    }
}
