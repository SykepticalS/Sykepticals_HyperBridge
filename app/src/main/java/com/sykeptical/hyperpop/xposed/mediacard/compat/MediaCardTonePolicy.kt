package com.sykeptical.hyperpop.xposed.mediacard.compat

import com.sykeptical.hyperpop.xposed.mediacard.MediaCardConstants

object MediaCardTonePolicy {
    fun resolveSoftCoverTone(configuredTone: Int, isSystemDark: Boolean): Int =
        when (configuredTone) {
            MediaCardConstants.MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM -> {
                if (isSystemDark) {
                    MediaCardConstants.MEDIA_SOFT_COVER_TONE_DARK
                } else {
                    MediaCardConstants.MEDIA_SOFT_COVER_TONE_LIGHT
                }
            }

            MediaCardConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
            MediaCardConstants.MEDIA_SOFT_COVER_TONE_DARK -> configuredTone

            else -> MediaCardConstants.MEDIA_SOFT_COVER_TONE_DARK
        }

    fun isSoftCoverDark(configuredTone: Int, isSystemDark: Boolean): Boolean =
        resolveSoftCoverTone(configuredTone, isSystemDark) ==
            MediaCardConstants.MEDIA_SOFT_COVER_TONE_DARK
}
