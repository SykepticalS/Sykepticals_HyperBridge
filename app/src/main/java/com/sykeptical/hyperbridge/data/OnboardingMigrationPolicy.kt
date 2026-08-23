package com.sykeptical.hyperbridge.data

object OnboardingMigrationPolicy {
    fun shouldRestoreCompletedSetup(
        resetVersion: Int,
        repairComplete: Boolean,
        setupComplete: Boolean,
        hasExistingProfile: Boolean
    ): Boolean = resetVersion == 19 &&
            !repairComplete &&
            !setupComplete &&
            hasExistingProfile
}
