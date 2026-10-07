package com.sykeptical.hyperpop.service.animation.fingerprint

/**
 * Lock-pill and scan eligibility, plus which lifecycle edges snap the island
 * away with no animation. The state machine applies this; hooks only fill
 * [LockWorld] from signals they actually observed.
 */
data class LockWorld(
    val featureEnabled: Boolean = false,
    val lockPillSetting: Boolean = true,
    val keyguardShowing: Boolean = false,
    val keyguardOccluded: Boolean = false,
    val bouncerShowing: Boolean = false,
    val shadeExpanded: Boolean = false,
    val lockedOut: Boolean = false,
    val fingerprintRunning: Boolean = false,
    val displayOn: Boolean = false,
    val dozing: Boolean = false,
    val portrait: Boolean = true,
)

enum class ResetReason {
    ScreenOff,
    Doze,
    Rotation,
    KeyguardGone,
    Detach,
}

object FingerprintPresentationPolicy {
    fun pillAllowed(world: LockWorld): Boolean =
        world.featureEnabled &&
            world.lockPillSetting &&
            listening(world)

    fun scanAllowed(world: LockWorld): Boolean =
        world.featureEnabled && listening(world)

    fun resetsNow(phase: FingerprintPhase, reason: ResetReason): Boolean {
        if (phase == FingerprintPhase.Hidden) return false
        if (reason == ResetReason.KeyguardGone) {
            return phase != FingerprintPhase.Success &&
                phase != FingerprintPhase.SuccessHold &&
                phase != FingerprintPhase.Collapsing
        }
        return true
    }

    /**
     * Native islands stay Xiaomi-owned except while HyperPop's scan overlay is
     * the surface the user should see. The idle lock pill must not hide them:
     * Xiaomi writes window height on every expand, collapse, and app-close, and
     * zeroing their alpha there makes those islands flicker or look empty.
     */
    fun occludeNatives(phase: FingerprintPhase): Boolean =
        phase != FingerprintPhase.Hidden && phase != FingerprintPhase.LockPill

    private fun listening(world: LockWorld): Boolean =
        world.keyguardShowing &&
            !world.keyguardOccluded &&
            !world.bouncerShowing &&
            !world.shadeExpanded &&
            !world.lockedOut &&
            world.fingerprintRunning &&
            world.displayOn &&
            !world.dozing &&
            world.portrait
}
