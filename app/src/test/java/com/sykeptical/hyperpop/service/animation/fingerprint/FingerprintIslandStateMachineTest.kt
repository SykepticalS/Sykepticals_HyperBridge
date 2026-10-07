package com.sykeptical.hyperpop.service.animation.fingerprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintIslandStateMachineTest {
    private val eligible = LockWorld(
        featureEnabled = true,
        lockPillSetting = true,
        keyguardShowing = true,
        fingerprintRunning = true,
        displayOn = true,
        portrait = true,
    )

    @Test
    fun failWhileTheFingerIsDownStaysSquareUntilLift() {
        val machine = armed()
        down(machine, 0L)
        val failed = machine.dispatch(FingerprintSignal.Failure, 100L)
        assertEquals(FingerprintPhase.Failed, failed.phase)
        assertTrue(failed.shaking)
        assertTrue(failed.fingerDown)
        assertFalse(failed.showBlue)
        val held = machine.dispatch(FingerprintSignal.Tick(500L), 500L)
        assertEquals(FingerprintPhase.Failed, held.phase)
        assertFalse(held.showBlue)
        val lifted = machine.dispatch(FingerprintSignal.FingerUp, 600L)
        assertEquals(600L + FingerprintTiming.FAIL_RETURN_MS, lifted.deadlineMs)
        val back = machine.dispatch(FingerprintSignal.Tick(600L + FingerprintTiming.FAIL_RETURN_MS), 1_000L)
        assertEquals(FingerprintPhase.LockPill, back.phase)
        assertFalse(back.shaking)
    }

    @Test
    fun successAfterFingerUpPlaysTheCheck() {
        val machine = armed()
        down(machine, 0L)
        machine.dispatch(FingerprintSignal.FingerUp, 200L)
        val success = machine.dispatch(FingerprintSignal.Success, 430L)
        assertEquals(FingerprintPhase.Success, success.phase)
        assertTrue(success.playingSuccess)
        assertFalse(success.showBlue)
        val hold = machine.dispatch(FingerprintSignal.SuccessSettled, 700L)
        assertEquals(FingerprintPhase.SuccessHold, hold.phase)
        assertEquals(700L + FingerprintTiming.SUCCESS_HOLD_MS, hold.deadlineMs)
        val collapsing = machine.dispatch(
            FingerprintSignal.Tick(700L + FingerprintTiming.SUCCESS_HOLD_MS),
            700L + FingerprintTiming.SUCCESS_HOLD_MS,
        )
        assertEquals(FingerprintPhase.Collapsing, collapsing.phase)
    }

    @Test
    fun awaitTimeoutReturnsToThePillAndArmsLateSuccess() {
        val machine = armed()
        down(machine, 0L)
        val waiting = machine.dispatch(FingerprintSignal.FingerUp, 100L)
        assertEquals(FingerprintPhase.AwaitingResult, waiting.phase)
        val early = machine.dispatch(FingerprintSignal.Tick(100L + 599L), 699L)
        assertEquals(FingerprintPhase.AwaitingResult, early.phase)
        val timed = machine.dispatch(FingerprintSignal.Tick(100L + 600L), 700L)
        assertEquals(FingerprintPhase.LockPill, timed.phase)
        assertFalse(timed.showBlue)
        val lateFail = machine.dispatch(FingerprintSignal.Failure, 800L)
        assertEquals(FingerprintPhase.LockPill, lateFail.phase)
        assertFalse(lateFail.shaking)
        val lateSuccess = machine.dispatch(FingerprintSignal.Success, 900L)
        assertEquals(FingerprintPhase.Success, lateSuccess.phase)
        assertTrue(lateSuccess.playingSuccess)
    }

    @Test
    fun lockoutCollapsesAndStaysHidden() {
        val machine = armed()
        down(machine, 0L)
        machine.dispatch(FingerprintSignal.Failure, 50L)
        val collapsing = machine.dispatch(FingerprintSignal.Lockout, 80L)
        assertEquals(FingerprintPhase.Collapsing, collapsing.phase)
        val hidden = machine.dispatch(FingerprintSignal.CollapseFinished, 200L)
        assertEquals(FingerprintPhase.Hidden, hidden.phase)
        val still = machine.dispatch(FingerprintSignal.WorldChanged(eligible.copy(lockedOut = true)), 220L)
        assertEquals(FingerprintPhase.Hidden, still.phase)
    }

    @Test
    fun screenOffAndKeyguardGoneWithoutSuccessResetImmediately() {
        val machine = armed()
        val scanning = down(machine, 0L)
        val reset = machine.dispatch(FingerprintSignal.Reset(ResetReason.ScreenOff), 40L)
        assertEquals(FingerprintPhase.Hidden, reset.phase)
        assertTrue(reset.generation > scanning.generation)
        down(machine, 100L)
        machine.dispatch(FingerprintSignal.Success, 150L)
        val kept = machine.dispatch(FingerprintSignal.Reset(ResetReason.KeyguardGone), 160L)
        assertEquals(FingerprintPhase.Success, kept.phase)
        val rotated = machine.dispatch(FingerprintSignal.Reset(ResetReason.Rotation), 170L)
        assertEquals(FingerprintPhase.Hidden, rotated.phase)
    }

    @Test
    fun nativeExpandCutsTheHoldShort() {
        val machine = armed()
        down(machine, 0L)
        machine.dispatch(FingerprintSignal.FingerUp, 10L)
        machine.dispatch(FingerprintSignal.Success, 20L)
        machine.dispatch(FingerprintSignal.SuccessSettled, 40L)
        val cut = machine.dispatch(FingerprintSignal.HoldInterrupted, 50L)
        assertEquals(FingerprintPhase.Collapsing, cut.phase)
    }

    @Test
    fun helpDoesNotShakeOrLeaveTheSession() {
        val machine = armed()
        val scanning = down(machine, 0L)
        val helped = machine.dispatch(FingerprintSignal.Help, 30L)
        assertEquals(FingerprintPhase.Scanning, helped.phase)
        assertEquals(scanning.generation, helped.generation)
        assertFalse(helped.shaking)
        assertTrue(helped.showBlue)
    }

    @Test
    fun retouchDuringFailureReturnsToScanning() {
        val machine = armed()
        val first = down(machine, 0L)
        machine.dispatch(FingerprintSignal.Failure, 40L)
        val again = machine.dispatch(FingerprintSignal.FingerDown, 80L)
        assertEquals(FingerprintPhase.Scanning, again.phase)
        assertTrue(again.generation > first.generation)
        assertTrue(again.showBlue)
        assertFalse(again.shaking)
        assertTrue(again.fingerDown)
    }

    @Test
    fun repeatedFailuresThenLockoutCollapseToHidden() {
        val machine = armed()
        down(machine, 0L)
        machine.dispatch(FingerprintSignal.Failure, 10L)
        machine.dispatch(FingerprintSignal.FingerUp, 20L)
        machine.dispatch(FingerprintSignal.Tick(20L + FingerprintTiming.FAIL_RETURN_MS), 420L)
        down(machine, 500L)
        val second = machine.dispatch(FingerprintSignal.Failure, 510L)
        assertTrue(second.shaking)
        assertTrue(second.shakeSerial >= 2)
        machine.dispatch(FingerprintSignal.Lockout, 520L)
        val hidden = machine.dispatch(FingerprintSignal.CollapseFinished, 600L)
        assertEquals(FingerprintPhase.Hidden, hidden.phase)
    }

    @Test
    fun sessionEndDuringTheGracePeriodStillAcceptsSuccess() {
        val machine = armed()
        down(machine, 0L)
        machine.dispatch(FingerprintSignal.FingerUp, 10L)
        val ended = machine.dispatch(FingerprintSignal.SessionEnded, 20L)
        assertEquals(FingerprintPhase.AwaitingResult, ended.phase)
        val success = machine.dispatch(FingerprintSignal.Success, 200L)
        assertEquals(FingerprintPhase.Success, success.phase)
    }

    @Test
    fun idleLockPillDoesNotOccludeNativeIslands() {
        val machine = armed()
        assertEquals(FingerprintPhase.LockPill, machine.snapshot.phase)
        assertFalse(machine.snapshot.suspendNatives)
        assertFalse(FingerprintPresentationPolicy.occludeNatives(FingerprintPhase.LockPill))
        assertFalse(FingerprintPresentationPolicy.occludeNatives(FingerprintPhase.Hidden))
        down(machine, 0L)
        assertTrue(machine.snapshot.suspendNatives)
        assertTrue(FingerprintPresentationPolicy.occludeNatives(FingerprintPhase.Scanning))
        assertTrue(FingerprintPresentationPolicy.occludeNatives(FingerprintPhase.Success))
        assertTrue(FingerprintPresentationPolicy.occludeNatives(FingerprintPhase.Collapsing))
    }

    @Test
    fun pillHidesWhenTheSettingOrTheSessionIsOff() {
        val machine = armed()
        assertEquals(FingerprintPhase.LockPill, machine.snapshot.phase)
        val noPill = machine.dispatch(
            FingerprintSignal.WorldChanged(eligible.copy(lockPillSetting = false)),
            10L,
        )
        assertEquals(FingerprintPhase.Hidden, noPill.phase)
        val scanning = machine.dispatch(FingerprintSignal.FingerDown, 20L)
        assertEquals(FingerprintPhase.Scanning, scanning.phase)
    }

    private fun armed(): FingerprintIslandStateMachine {
        val machine = FingerprintIslandStateMachine()
        machine.dispatch(FingerprintSignal.WorldChanged(eligible), 0L)
        assertEquals(FingerprintPhase.LockPill, machine.snapshot.phase)
        return machine
    }

    private fun down(machine: FingerprintIslandStateMachine, now: Long): FingerprintSnapshot {
        val snapshot = machine.dispatch(FingerprintSignal.FingerDown, now)
        assertEquals(FingerprintPhase.Scanning, snapshot.phase)
        return snapshot
    }
}
