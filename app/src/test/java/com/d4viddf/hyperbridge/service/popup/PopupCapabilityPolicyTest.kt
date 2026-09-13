package com.d4viddf.hyperbridge.service.popup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupCapabilityPolicyTest {
    private fun signals(
        supported: Boolean = true,
        access: Boolean = true,
        association: Boolean = true,
        connected: Boolean = true,
        verification: Boolean? = true,
        failed: Boolean = false
    ) = PopupCapabilitySignals(supported, access, association, connected, verification, failed)

    @Test fun unsupportedWins() = assertEquals(PopupSuppressionCapabilityState.UNSUPPORTED, PopupCapabilityPolicy.resolve(signals(supported = false)))
    @Test fun notificationAccessComesBeforeAssociation() = assertEquals(PopupSuppressionCapabilityState.NEEDS_NOTIFICATION_ACCESS, PopupCapabilityPolicy.resolve(signals(access = false, association = false)))
    @Test fun associationIsRequired() = assertEquals(PopupSuppressionCapabilityState.NEEDS_ASSOCIATION, PopupCapabilityPolicy.resolve(signals(association = false)))
    @Test fun listenerMustActuallyConnect() = assertEquals(PopupSuppressionCapabilityState.WAITING_FOR_LISTENER, PopupCapabilityPolicy.resolve(signals(connected = false)))
    @Test fun nullVerificationMeansVerifying() = assertEquals(PopupSuppressionCapabilityState.VERIFYING, PopupCapabilityPolicy.resolve(signals(verification = null)))
    @Test fun failedVerificationNeedsRepair() = assertEquals(PopupSuppressionCapabilityState.ERROR, PopupCapabilityPolicy.resolve(signals(verification = false, failed = true)))
    @Test fun readyRequiresEveryRealSignal() = assertEquals(PopupSuppressionCapabilityState.READY, PopupCapabilityPolicy.resolve(signals()))

    @Test
    fun freshSetupRequiresVerifiedReadyStateOnSupportedDevices() {
        assertFalse(PopupOnboardingPolicy.canFinishFreshSetup(PopupSuppressionCapabilityState.NEEDS_ASSOCIATION, false))
        assertFalse(PopupOnboardingPolicy.canFinishFreshSetup(PopupSuppressionCapabilityState.VERIFYING, false))
        assertFalse(PopupOnboardingPolicy.canFinishFreshSetup(PopupSuppressionCapabilityState.READY, false))
        assertTrue(PopupOnboardingPolicy.canFinishFreshSetup(PopupSuppressionCapabilityState.READY, true))
        assertTrue(PopupOnboardingPolicy.canFinishFreshSetup(PopupSuppressionCapabilityState.UNSUPPORTED, false))
    }

    @Test
    fun upgradeGateCoversCancellationRepairUnsupportedAndIntentionalDisable() {
        assertTrue(PopupOnboardingPolicy.shouldBlockUpgrade(true, true, false, false, PopupSuppressionCapabilityState.NEEDS_ASSOCIATION))
        assertTrue(PopupOnboardingPolicy.shouldBlockUpgrade(true, false, true, false, PopupSuppressionCapabilityState.ERROR))
        assertFalse(PopupOnboardingPolicy.shouldBlockUpgrade(true, false, true, false, PopupSuppressionCapabilityState.READY))
        assertFalse(PopupOnboardingPolicy.shouldBlockUpgrade(true, true, false, true, PopupSuppressionCapabilityState.NEEDS_ASSOCIATION))
        assertFalse(PopupOnboardingPolicy.shouldBlockUpgrade(false, true, false, false, PopupSuppressionCapabilityState.UNSUPPORTED))
    }
}
