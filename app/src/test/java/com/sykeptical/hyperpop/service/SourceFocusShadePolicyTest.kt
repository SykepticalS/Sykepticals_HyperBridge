package com.sykeptical.hyperpop.service

import com.sykeptical.hyperpop.models.NotificationType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceFocusShadePolicyTest {
    @Test
    fun callsVoiceAndTransfersReplaceTheShadeRow() {
        assertTrue(SourceFocusShadePolicy.replacesShade(NotificationType.CALL))
        assertTrue(SourceFocusShadePolicy.replacesShade(NotificationType.VOICE_MESSAGE))
        assertTrue(SourceFocusShadePolicy.replacesShade(NotificationType.DOWNLOAD))
        assertTrue(SourceFocusShadePolicy.replacesShade(NotificationType.PROGRESS))
        assertTrue(NotificationLifecyclePolicy.carriesVisibleSourceFocus(NotificationType.DOWNLOAD))
    }

    @Test
    fun otherTypesKeepTheirOwnShadeRow() {
        assertFalse(SourceFocusShadePolicy.replacesShade(NotificationType.MESSAGE))
        assertFalse(SourceFocusShadePolicy.replacesShade(NotificationType.MEDIA))
        assertFalse(SourceFocusShadePolicy.replacesShade(NotificationType.STANDARD))
        assertFalse(SourceFocusShadePolicy.replacesShade(null))
    }

    @Test
    fun playbackStaysOngoingUntilTheSourceItselfEnds() {
        assertTrue(
            SourceFocusShadePolicy.keepSourceOngoing(
                NotificationType.VOICE_MESSAGE,
                finished = true,
                cancelling = false,
            )
        )
        assertFalse(
            SourceFocusShadePolicy.keepSourceOngoing(
                NotificationType.VOICE_MESSAGE,
                finished = false,
                cancelling = true,
            )
        )
    }

    @Test
    fun finishedTransfersCanBeDismissedAndLiveTransfersStayPosted() {
        assertTrue(
            SourceFocusShadePolicy.keepSourceOngoing(
                NotificationType.DOWNLOAD,
                finished = false,
                cancelling = false,
            )
        )
        assertFalse(
            SourceFocusShadePolicy.keepSourceOngoing(
                NotificationType.PROGRESS,
                finished = true,
                cancelling = false,
            )
        )
        assertFalse(
            SourceFocusShadePolicy.keepSourceOngoing(
                NotificationType.CALL,
                finished = false,
                cancelling = false,
            )
        )
    }

    @Test
    fun customShadeLayoutsAreRemovedOnlyForALiveReplacement() {
        assertTrue(SourceFocusShadePolicy.clearCustomShadeViews(replacingShade = true, cancelling = false))
        assertFalse(SourceFocusShadePolicy.clearCustomShadeViews(replacingShade = true, cancelling = true))
        assertFalse(SourceFocusShadePolicy.clearCustomShadeViews(replacingShade = false, cancelling = false))
    }
}
