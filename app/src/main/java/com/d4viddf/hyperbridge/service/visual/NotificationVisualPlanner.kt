package com.d4viddf.hyperbridge.service.visual

enum class LargeIconRole { AVATAR, ATTACHMENT, SKIP }

/** Decides whether a notification bitmap is a person avatar or a shared photo/video frame. */
object NotificationVisualPlanner {
    fun isLikelyAvatar(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        val ratio = width.toFloat() / height.toFloat()
        return ratio in 0.62f..1.45f
    }

    fun largeIconRole(
        hasPicture: Boolean,
        hasPersonIcon: Boolean,
        width: Int,
        height: Int,
        sameAsPicture: Boolean = false,
        mediaShareWithoutPersonIcon: Boolean = false,
    ): LargeIconRole {
        if (sameAsPicture) return LargeIconRole.ATTACHMENT
        val avatarShaped = isLikelyAvatar(width, height)
        return when {
            hasPicture && !avatarShaped -> LargeIconRole.SKIP
            hasPersonIcon && !avatarShaped -> LargeIconRole.ATTACHMENT
            mediaShareWithoutPersonIcon && !hasPicture -> LargeIconRole.ATTACHMENT
            !avatarShaped -> LargeIconRole.ATTACHMENT
            else -> LargeIconRole.AVATAR
        }
    }

    /** Square profile photos must not occupy the right-side media slot. */
    fun isDistinctMedia(
        width: Int,
        height: Int,
        avatarWidth: Int,
        avatarHeight: Int,
        sameAsAvatar: Boolean,
    ): Boolean {
        if (sameAsAvatar) return false
        if (!isLikelyAvatar(width, height)) return true
        return avatarWidth > 0 &&
            avatarHeight > 0 &&
            !isLikelyAvatar(avatarWidth, avatarHeight)
    }
}
