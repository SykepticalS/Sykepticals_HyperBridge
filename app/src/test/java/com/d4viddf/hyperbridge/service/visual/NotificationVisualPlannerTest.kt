package com.d4viddf.hyperbridge.service.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationVisualPlannerTest {
    @Test
    fun videoFramesAreAttachmentsAndSquareIconsStayAvatars() {
        assertFalse(NotificationVisualPlanner.isLikelyAvatar(1080, 1920))
        assertFalse(NotificationVisualPlanner.isLikelyAvatar(1920, 1080))
        assertTrue(NotificationVisualPlanner.isLikelyAvatar(256, 256))
        assertTrue(NotificationVisualPlanner.isLikelyAvatar(96, 96))
    }

    @Test
    fun bigPictureThumbnailDoesNotReplaceAvatarSlot() {
        assertEquals(
            LargeIconRole.SKIP,
            NotificationVisualPlanner.largeIconRole(
                hasPicture = true,
                hasPersonIcon = false,
                width = 1080,
                height = 1920,
            ),
        )
        assertEquals(
            LargeIconRole.AVATAR,
            NotificationVisualPlanner.largeIconRole(
                hasPicture = true,
                hasPersonIcon = false,
                width = 128,
                height = 128,
            ),
        )
        assertEquals(
            LargeIconRole.ATTACHMENT,
            NotificationVisualPlanner.largeIconRole(
                hasPicture = false,
                hasPersonIcon = true,
                width = 1920,
                height = 1080,
            ),
        )
    }

    @Test
    fun squareMediaShareThumbnailIsAttachmentWhenAvatarIsMissing() {
        assertEquals(
            LargeIconRole.ATTACHMENT,
            NotificationVisualPlanner.largeIconRole(
                hasPicture = false,
                hasPersonIcon = false,
                width = 144,
                height = 144,
                mediaShareWithoutPersonIcon = true,
            ),
        )
    }

    @Test
    fun squareProfilePhotosAreNotDistinctMedia() {
        assertFalse(
            NotificationVisualPlanner.isDistinctMedia(
                width = 144,
                height = 144,
                avatarWidth = 96,
                avatarHeight = 96,
                sameAsAvatar = false,
            ),
        )
        assertTrue(
            NotificationVisualPlanner.isDistinctMedia(
                width = 1920,
                height = 1080,
                avatarWidth = 96,
                avatarHeight = 96,
                sameAsAvatar = false,
            ),
        )
        assertFalse(
            NotificationVisualPlanner.isDistinctMedia(
                width = 1920,
                height = 1080,
                avatarWidth = 96,
                avatarHeight = 96,
                sameAsAvatar = true,
            ),
        )
    }

    @Test
    fun largeIconThatDuplicatesThePictureIsAnAttachment() {
        assertEquals(
            LargeIconRole.ATTACHMENT,
            NotificationVisualPlanner.largeIconRole(
                hasPicture = true,
                hasPersonIcon = false,
                width = 256,
                height = 256,
                sameAsPicture = true,
            ),
        )
    }
}
