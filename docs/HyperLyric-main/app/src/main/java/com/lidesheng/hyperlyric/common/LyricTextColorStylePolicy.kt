package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

object LyricTextColorStylePolicy {
    fun read(prefs: SharedPreferences): Int {
        return prefs.getInt(
            RootConstants.KEY_HOOK_TEXT_COLOR_STYLE,
            RootConstants.DEFAULT_HOOK_TEXT_COLOR_STYLE
        ).coerceIn(
            RootConstants.TEXT_COLOR_STYLE_DEFAULT,
            RootConstants.TEXT_COLOR_STYLE_FOLLOW_STATUS_BAR
        )
    }

    fun usesCoverColor(style: Int): Boolean =
        style == RootConstants.TEXT_COLOR_STYLE_COVER_COLOR ||
                style == RootConstants.TEXT_COLOR_STYLE_COVER_GRADIENT

    fun usesCoverGradient(style: Int): Boolean =
        style == RootConstants.TEXT_COLOR_STYLE_COVER_GRADIENT

    fun followsStatusBar(style: Int): Boolean =
        style == RootConstants.TEXT_COLOR_STYLE_FOLLOW_STATUS_BAR
}
