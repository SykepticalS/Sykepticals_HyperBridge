package com.lidesheng.hyperlyric.common.media

import com.lidesheng.hyperlyric.common.RootConstants

object MediaCardTonePolicy {
    fun resolveSoftCoverTone(configuredTone: Int, isSystemDark: Boolean): Int =
        when (configuredTone) {
            RootConstants.MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM -> {
                if (isSystemDark) {
                    RootConstants.MEDIA_SOFT_COVER_TONE_DARK
                } else {
                    RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT
                }
            }

            RootConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
            RootConstants.MEDIA_SOFT_COVER_TONE_DARK -> configuredTone

            else -> RootConstants.MEDIA_SOFT_COVER_TONE_DARK
        }

    fun isSoftCoverDark(configuredTone: Int, isSystemDark: Boolean): Boolean =
        resolveSoftCoverTone(configuredTone, isSystemDark) ==
            RootConstants.MEDIA_SOFT_COVER_TONE_DARK
}
