package com.sykeptical.hyperpop.service.animation.fingerprint

enum class FingerprintPhase {
    Hidden,
    LockPill,
    Scanning,
    AwaitingResult,
    Failed,
    Success,
    SuccessHold,
    Collapsing,
}

data class FingerprintSnapshot(
    val phase: FingerprintPhase = FingerprintPhase.Hidden,
    val generation: Long = 0L,
    val fingerDown: Boolean = false,
    val showBlue: Boolean = false,
    val shaking: Boolean = false,
    val playingSuccess: Boolean = false,
    val shakeSerial: Int = 0,
    val deadlineMs: Long? = null,
) {
    val suspendNatives: Boolean
        get() = FingerprintPresentationPolicy.occludeNatives(phase)
}

sealed interface FingerprintSignal {
    data class WorldChanged(val world: LockWorld) : FingerprintSignal
    data object FingerDown : FingerprintSignal
    data object FingerUp : FingerprintSignal
    data object Help : FingerprintSignal
    data object Failure : FingerprintSignal
    data object Success : FingerprintSignal
    data object Lockout : FingerprintSignal
    data object SessionEnded : FingerprintSignal
    data class Reset(val reason: ResetReason) : FingerprintSignal
    data object HoldInterrupted : FingerprintSignal
    data object SuccessSettled : FingerprintSignal
    data object CollapseFinished : FingerprintSignal
    data class Tick(val nowMs: Long) : FingerprintSignal
}

/**
 * Cosmetic fingerprint island. Every transition is driven by an observed
 * signal. Timeouts use the caller's clock. A new finger-down bumps
 * [FingerprintSnapshot.generation] so a late callback cannot revive it.
 */
class FingerprintIslandStateMachine {
    var snapshot: FingerprintSnapshot = FingerprintSnapshot()
        private set

    private var world: LockWorld = LockWorld()
    private var lateSuccessArmed: Boolean = false
    private var forceHidden: Boolean = false

    fun dispatch(signal: FingerprintSignal, nowMs: Long): FingerprintSnapshot {
        this.nowMs = nowMs
        when (signal) {
            is FingerprintSignal.WorldChanged -> applyWorld(signal.world)
            FingerprintSignal.FingerDown -> fingerDown()
            FingerprintSignal.FingerUp -> fingerUp()
            FingerprintSignal.Help -> help()
            FingerprintSignal.Failure -> failure()
            FingerprintSignal.Success -> success()
            FingerprintSignal.Lockout -> lockout()
            FingerprintSignal.SessionEnded -> sessionEnded()
            is FingerprintSignal.Reset -> reset(signal.reason)
            FingerprintSignal.HoldInterrupted -> holdInterrupted()
            FingerprintSignal.SuccessSettled -> successSettled()
            FingerprintSignal.CollapseFinished -> collapseFinished()
            is FingerprintSignal.Tick -> tick(signal.nowMs)
        }
        return snapshot
    }

    private fun applyWorld(next: LockWorld) {
        world = next
        if (!next.lockedOut) forceHidden = false
        when (snapshot.phase) {
            FingerprintPhase.Hidden -> {
                if (pillAllowed()) become(FingerprintPhase.LockPill)
            }
            FingerprintPhase.LockPill -> {
                if (!pillAllowed()) become(FingerprintPhase.Hidden)
            }
            else -> Unit
        }
    }

    private fun fingerDown() {
        if (!FingerprintPresentationPolicy.scanAllowed(world)) return
        if (snapshot.phase == FingerprintPhase.Scanning && snapshot.fingerDown) return
        if (snapshot.phase == FingerprintPhase.Success ||
            snapshot.phase == FingerprintPhase.SuccessHold ||
            snapshot.phase == FingerprintPhase.Collapsing
        ) {
            return
        }
        lateSuccessArmed = false
        snapshot = snapshot.copy(
            phase = FingerprintPhase.Scanning,
            generation = snapshot.generation + 1L,
            fingerDown = true,
            showBlue = true,
            shaking = false,
            playingSuccess = false,
            deadlineMs = null,
        )
    }

    private fun fingerUp() {
        when (snapshot.phase) {
            FingerprintPhase.Scanning -> snapshot = snapshot.copy(
                phase = FingerprintPhase.AwaitingResult,
                fingerDown = false,
                deadlineMs = null,
            ).withDeadline(FingerprintTiming.AWAIT_RESULT_MS)
            FingerprintPhase.Failed -> if (snapshot.fingerDown) {
                snapshot = snapshot.copy(fingerDown = false).withDeadline(FingerprintTiming.FAIL_RETURN_MS)
            }
            else -> Unit
        }
    }

    private fun help() {
        if (snapshot.phase != FingerprintPhase.Scanning &&
            snapshot.phase != FingerprintPhase.AwaitingResult
        ) {
            return
        }
    }

    private fun failure() {
        if (snapshot.phase != FingerprintPhase.Scanning &&
            snapshot.phase != FingerprintPhase.AwaitingResult &&
            snapshot.phase != FingerprintPhase.Failed
        ) {
            return
        }
        val down = snapshot.fingerDown
        snapshot = snapshot.copy(
            phase = FingerprintPhase.Failed,
            showBlue = false,
            shaking = true,
            shakeSerial = snapshot.shakeSerial + 1,
            playingSuccess = false,
            deadlineMs = null,
        )
        if (!down) snapshot = snapshot.withDeadline(FingerprintTiming.FAIL_RETURN_MS)
    }

    private fun success() {
        val allowed = when (snapshot.phase) {
            FingerprintPhase.Scanning,
            FingerprintPhase.AwaitingResult,
            FingerprintPhase.Failed,
            FingerprintPhase.LockPill,
            FingerprintPhase.Collapsing,
            -> true
            FingerprintPhase.Hidden -> lateSuccessArmed
            else -> false
        }
        if (!allowed) return
        lateSuccessArmed = false
        snapshot = snapshot.copy(
            phase = FingerprintPhase.Success,
            fingerDown = false,
            showBlue = false,
            shaking = false,
            playingSuccess = true,
            deadlineMs = null,
        )
    }

    private fun lockout() {
        forceHidden = true
        lateSuccessArmed = false
        collapseUnlessHidden()
    }

    private fun sessionEnded() {
        when (snapshot.phase) {
            FingerprintPhase.Success, FingerprintPhase.SuccessHold, FingerprintPhase.Collapsing -> Unit
            FingerprintPhase.AwaitingResult -> Unit
            FingerprintPhase.Scanning, FingerprintPhase.Failed -> {
                snapshot = snapshot.copy(
                    phase = FingerprintPhase.Collapsing,
                    showBlue = false,
                    shaking = false,
                    deadlineMs = null,
                )
            }
            FingerprintPhase.LockPill -> if (!pillAllowed()) become(FingerprintPhase.Hidden)
            FingerprintPhase.Hidden -> Unit
        }
    }

    private fun reset(reason: ResetReason) {
        if (!FingerprintPresentationPolicy.resetsNow(snapshot.phase, reason)) return
        lateSuccessArmed = false
        snapshot = FingerprintSnapshot(
            phase = FingerprintPhase.Hidden,
            generation = snapshot.generation + 1L,
        )
    }

    private fun holdInterrupted() {
        if (snapshot.phase != FingerprintPhase.Success && snapshot.phase != FingerprintPhase.SuccessHold) return
        snapshot = snapshot.copy(phase = FingerprintPhase.Collapsing, deadlineMs = null)
    }

    private fun successSettled() {
        if (snapshot.phase != FingerprintPhase.Success) return
        snapshot = snapshot.copy(phase = FingerprintPhase.SuccessHold).withDeadline(FingerprintTiming.SUCCESS_HOLD_MS)
    }

    private fun collapseFinished() {
        if (snapshot.phase != FingerprintPhase.Collapsing) return
        snapshot = snapshot.copy(playingSuccess = false, shaking = false, showBlue = false, deadlineMs = null)
        become(if (pillAllowed()) FingerprintPhase.LockPill else FingerprintPhase.Hidden)
    }

    private fun tick(nowMs: Long) {
        val deadline = snapshot.deadlineMs ?: return
        if (nowMs < deadline) return
        when (snapshot.phase) {
            FingerprintPhase.AwaitingResult -> {
                lateSuccessArmed = true
                snapshot = snapshot.copy(showBlue = false, shaking = false, fingerDown = false, deadlineMs = null)
                become(if (pillAllowed()) FingerprintPhase.LockPill else FingerprintPhase.Hidden)
            }
            FingerprintPhase.Failed -> {
                if (snapshot.fingerDown) return
                snapshot = snapshot.copy(shaking = false, showBlue = false, deadlineMs = null)
                become(if (pillAllowed()) FingerprintPhase.LockPill else FingerprintPhase.Hidden)
            }
            FingerprintPhase.SuccessHold -> {
                snapshot = snapshot.copy(phase = FingerprintPhase.Collapsing, deadlineMs = null)
            }
            else -> snapshot = snapshot.copy(deadlineMs = null)
        }
    }

    private fun collapseUnlessHidden() {
        when (snapshot.phase) {
            FingerprintPhase.Hidden -> Unit
            FingerprintPhase.Collapsing -> Unit
            else -> snapshot = snapshot.copy(
                phase = FingerprintPhase.Collapsing,
                showBlue = false,
                shaking = false,
                playingSuccess = false,
                fingerDown = false,
                deadlineMs = null,
            )
        }
    }

    private fun become(phase: FingerprintPhase) {
        snapshot = snapshot.copy(
            phase = phase,
            fingerDown = false,
            showBlue = false,
            shaking = false,
            playingSuccess = false,
            deadlineMs = null,
        )
    }

    private fun pillAllowed(): Boolean =
        !forceHidden && FingerprintPresentationPolicy.pillAllowed(world)

    private var nowMs: Long = 0L

    private fun FingerprintSnapshot.withDeadline(delayMs: Long): FingerprintSnapshot =
        copy(deadlineMs = nowMs + delayMs)
}

object FingerprintTiming {
    const val AWAIT_RESULT_MS = 600L
    const val FAIL_RETURN_MS = 400L
    const val SUCCESS_HOLD_MS = 1_250L
    const val DOWN_DEDUPE_MS = 80L
}
