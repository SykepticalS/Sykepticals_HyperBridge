package com.sykeptical.hyperbridge.service.floating

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingNotificationSetupTest {
    @Test
    fun promptAppearsOnlyForFirstAppActivation() {
        assertEquals(
            true,
            FloatingNotificationSetup.shouldShowFirstActivationPrompt(
                selectedPackagesBeforeToggle = emptySet(),
                packageName = "first.app",
                isEnabled = true,
                promptAlreadyShown = false
            )
        )
        assertEquals(
            false,
            FloatingNotificationSetup.shouldShowFirstActivationPrompt(
                selectedPackagesBeforeToggle = setOf("first.app"),
                packageName = "second.app",
                isEnabled = true,
                promptAlreadyShown = false
            )
        )
        assertEquals(
            false,
            FloatingNotificationSetup.shouldShowFirstActivationPrompt(
                selectedPackagesBeforeToggle = emptySet(),
                packageName = "another.app",
                isEnabled = true,
                promptAlreadyShown = true
            )
        )
    }

    @Test
    fun disablingOrRetogglingDoesNotTriggerFirstActivationPrompt() {
        assertEquals(
            false,
            FloatingNotificationSetup.shouldShowFirstActivationPrompt(
                selectedPackagesBeforeToggle = emptySet(),
                packageName = "first.app",
                isEnabled = false,
                promptAlreadyShown = false
            )
        )
        assertEquals(
            false,
            FloatingNotificationSetup.shouldShowFirstActivationPrompt(
                selectedPackagesBeforeToggle = setOf("first.app"),
                packageName = "first.app",
                isEnabled = true,
                promptAlreadyShown = false
            )
        )
    }

    @Test
    fun selectedAppNeedsReviewUntilUserConfirms() {
        assertEquals(
            FloatingSetupStatus.NEEDS_REVIEW,
            FloatingNotificationSetup.status(true, false, true)
        )
        assertEquals(
            FloatingSetupStatus.USER_CONFIRMED,
            FloatingNotificationSetup.status(true, true, true)
        )
    }

    @Test
    fun unsupportedOrUnselectedAppDoesNotRequireSetup() {
        assertEquals(FloatingSetupStatus.NOT_REQUIRED, FloatingNotificationSetup.status(true, false, false))
        assertEquals(FloatingSetupStatus.NOT_REQUIRED, FloatingNotificationSetup.status(false, false, true))
    }
}
