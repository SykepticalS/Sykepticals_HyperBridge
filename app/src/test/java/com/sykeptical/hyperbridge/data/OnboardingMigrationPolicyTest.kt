package com.sykeptical.hyperbridge.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingMigrationPolicyTest {
    @Test
    fun repairsOnlyEstablishedProfilesAffectedByPassReset() {
        assertTrue(OnboardingMigrationPolicy.shouldRestoreCompletedSetup(19, false, false, true))
        assertFalse(OnboardingMigrationPolicy.shouldRestoreCompletedSetup(19, false, false, false))
        assertFalse(OnboardingMigrationPolicy.shouldRestoreCompletedSetup(19, true, false, true))
        assertFalse(OnboardingMigrationPolicy.shouldRestoreCompletedSetup(0, false, false, true))
    }
}
