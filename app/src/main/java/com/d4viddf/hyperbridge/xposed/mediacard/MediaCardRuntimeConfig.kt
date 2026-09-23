package com.d4viddf.hyperbridge.xposed.mediacard

import android.content.SharedPreferences
import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardConstants
import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardLog

internal object MediaCardRuntimeConfig {
    @Volatile
    var current: Snapshot = Snapshot.defaults()
        private set

    fun load(prefs: SharedPreferences) {
        val snapshot = Snapshot.from(prefs)
        current = snapshot
        val summary = "enabled=${snapshot.enabled}, " +
                "notification(layout=${snapshot.notification.layoutStyle}, " +
                "theme=${snapshot.notification.cardTheme}, " +
                "cover=${snapshot.notification.coverStyle}, " +
                "ambient=${snapshot.notification.ambientFlowMode}, " +
                "progress=${snapshot.notification.progressStyle}), " +
                "expanded(layout=${snapshot.islandExpanded.layoutStyle}, " +
                "theme=${snapshot.islandExpanded.cardTheme}, " +
                "cover=${snapshot.islandExpanded.coverStyle}, " +
                "ambient=${snapshot.islandExpanded.ambientFlowMode}, " +
                "progress=${snapshot.islandExpanded.progressStyle}), " +
                "aodCollapseDisabled=${snapshot.alwaysOnDisplay.disableMediaCardCollapsing}"
        MediaCardLog.dState(
            stateId = "MediaCardRuntimeConfig",
            tag = "MediaCardRuntimeConfig",
            state = summary
        ) {
            "媒体卡片实际配置: $summary"
        }
    }

    data class Snapshot(
        val enabled: Boolean,
        val notification: Notification,
        val islandExpanded: IslandExpanded,
        val alwaysOnDisplay: AlwaysOnDisplay
    ) {
        companion object {
            fun defaults() = Snapshot(
                enabled = true,
                notification = Notification.defaults(),
                islandExpanded = IslandExpanded.defaults(),
                alwaysOnDisplay = AlwaysOnDisplay.defaults()
            )

            fun from(prefs: SharedPreferences) = Snapshot(
                enabled = true,
                notification = Notification.from(prefs),
                islandExpanded = IslandExpanded.from(prefs),
                alwaysOnDisplay = AlwaysOnDisplay.from(prefs)
            )
        }
    }

    data class AlwaysOnDisplay(
        val disableMediaCardCollapsing: Boolean
    ) {
        companion object {
            fun defaults() = AlwaysOnDisplay(
                disableMediaCardCollapsing =
                    MediaCardConstants.DEFAULT_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING
            )

            fun from(prefs: SharedPreferences) = AlwaysOnDisplay(
                disableMediaCardCollapsing = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING,
                    MediaCardConstants.DEFAULT_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING
                )
            )
        }
    }

    data class Notification(
        val cardSwitcherEnabled: Boolean,
        val cardSwitcherMode: Int,
        val cardSwitcherMaxCount: Int,
        val layoutStyle: Int,
        val ambientFlowMode: Int,
        val cardTheme: Int,
        val coverStyle: Int,
        val progressStyle: Int,
        val progressHeadGlow: Boolean,
        val thumbStyle: Int,
        val hideCoverSource: Boolean,
        val hideCoverShadow: Boolean,
        val disableCoverFlip: Boolean,
        val hideDeviceSwitch: Boolean,
        val hideCustomActions: Boolean,
        val hideTime: Boolean,
        val actionAlignLeft: Boolean,
        val actionOrder: Int,
        val backgroundStyle: Int,
        val backgroundBlur: Int,
        val backgroundColorAnimation: Boolean,
        val backgroundAutoInvert: Boolean,
        val softCoverTone: Int
    ) {
        companion object {
            fun defaults() = Notification(
                cardSwitcherEnabled =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED,
                cardSwitcherMode =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE,
                cardSwitcherMaxCount =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
                layoutStyle = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE,
                ambientFlowMode = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE,
                cardTheme = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME,
                coverStyle = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_COVER_STYLE,
                progressStyle = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE,
                progressHeadGlow =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW,
                thumbStyle = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE,
                hideCoverSource = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
                hideCoverShadow =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW,
                disableCoverFlip =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP,
                hideDeviceSwitch = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
                hideCustomActions =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS,
                hideTime = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_TIME,
                actionAlignLeft =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT,
                actionOrder = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER,
                backgroundStyle = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE,
                backgroundBlur = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR,
                backgroundColorAnimation =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION,
                backgroundAutoInvert =
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT,
                softCoverTone = MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE
            )

            fun from(prefs: SharedPreferences) = Notification(
                cardSwitcherEnabled = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED
                ),
                cardSwitcherMode = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE
                ).coerceIn(
                    MediaCardConstants.NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_SINGLE,
                    MediaCardConstants.NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_MULTI
                ),
                cardSwitcherMaxCount = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT
                ).coerceIn(
                    MediaCardConstants.MIN_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
                    MediaCardConstants.MAX_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT
                ),
                layoutStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE
                ).let { style ->
                    when (style) {
                        MediaCardConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_IOS,
                        MediaCardConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS,
                        MediaCardConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI,
                        MediaCardConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI,
                        MediaCardConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL -> style

                        else -> MediaCardConstants.NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM
                    }
                },
                ambientFlowMode = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE
                ).coerceIn(
                    MediaCardConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED,
                    MediaCardConstants.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL
                ),
                cardTheme = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME
                ).coerceIn(
                    MediaCardConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM,
                    MediaCardConstants.MEDIA_CARD_THEME_ALWAYS_DARK
                ),
                coverStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_COVER_STYLE
                ).coerceIn(
                    MediaCardConstants.NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT,
                    MediaCardConstants.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN
                ),
                progressStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE
                ).let { style ->
                    if (style == MediaCardConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE) {
                        MediaCardConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE
                    } else {
                        MediaCardConstants.NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT
                    }
                },
                progressHeadGlow = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW
                ) || prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE
                ) == LEGACY_NOTIFICATION_MEDIA_PROGRESS_STYLE_GLOW,
                thumbStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE
                ).coerceIn(
                    MediaCardConstants.NOTIFICATION_MEDIA_THUMB_STYLE_DEFAULT,
                    MediaCardConstants.NOTIFICATION_MEDIA_THUMB_STYLE_HIDDEN
                ),
                hideCoverSource = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE
                ),
                hideCoverShadow = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW
                ),
                disableCoverFlip = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP
                ),
                hideDeviceSwitch = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH
                ),
                hideCustomActions = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS
                ),
                hideTime = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_TIME,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_TIME
                ),
                actionAlignLeft = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT
                ),
                actionOrder = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER
                ).coerceIn(
                    MediaCardConstants.NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT,
                    MediaCardConstants.NOTIFICATION_MEDIA_ACTION_ORDER_PLAY_LEFT
                ),
                backgroundStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE
                ).coerceIn(
                    MediaCardConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT,
                    MediaCardConstants.NOTIFICATION_MEDIA_BACKGROUND_STYLE_SOFT_COVER
                ),
                backgroundBlur = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR
                ).coerceIn(1, 20),
                backgroundColorAnimation = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION
                ),
                backgroundAutoInvert = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT
                ),
                softCoverTone = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE,
                    MediaCardConstants.DEFAULT_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE
                ).coerceIn(
                    MediaCardConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
                    MediaCardConstants.MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM
                )
            )
        }
    }

    data class IslandExpanded(
        val layoutStyle: Int,
        val ambientFlowMode: Int,
        val cardTheme: Int,
        val coverStyle: Int,
        val progressStyle: Int,
        val progressHeadGlow: Boolean,
        val thumbStyle: Int,
        val hideCoverSource: Boolean,
        val disableCoverFlip: Boolean,
        val hideDeviceSwitch: Boolean,
        val hideCustomActions: Boolean,
        val hideTime: Boolean,
        val actionAlignLeft: Boolean,
        val actionOrder: Int,
        val backgroundStyle: Int,
        val backgroundBlur: Int,
        val backgroundColorAnimation: Boolean,
        val backgroundAutoInvert: Boolean,
        val softCoverTone: Int
    ) {
        companion object {
            fun defaults() = IslandExpanded(
                layoutStyle = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE,
                ambientFlowMode =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE,
                cardTheme = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME,
                coverStyle = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE,
                progressStyle = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE,
                progressHeadGlow =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW,
                thumbStyle = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE,
                hideCoverSource =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
                disableCoverFlip =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP,
                hideDeviceSwitch =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH,
                hideCustomActions =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS,
                hideTime = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME,
                actionAlignLeft =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT,
                actionOrder = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER,
                backgroundStyle =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE,
                backgroundBlur = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR,
                backgroundColorAnimation =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION,
                backgroundAutoInvert =
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT,
                softCoverTone = MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE
            )

            fun from(prefs: SharedPreferences) = IslandExpanded(
                layoutStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE
                ).let { style ->
                    when (style) {
                        MediaCardConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_IOS,
                        MediaCardConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_COLOROS,
                        MediaCardConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_ONEUI,
                        MediaCardConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_MIUI,
                        MediaCardConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_PIXEL -> style

                        else -> MediaCardConstants.ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_SYSTEM
                    }
                },
                ambientFlowMode = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE
                ).coerceIn(
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT,
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL
                ),
                cardTheme = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME
                ).coerceIn(
                    MediaCardConstants.MEDIA_CARD_THEME_FOLLOW_SYSTEM,
                    MediaCardConstants.MEDIA_CARD_THEME_ALWAYS_DARK
                ),
                coverStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE
                ).coerceIn(
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT,
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN
                ),
                progressStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE
                ).coerceIn(
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_DEFAULT,
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE
                ),
                progressHeadGlow = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW
                ),
                thumbStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE
                ).coerceIn(
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_THUMB_STYLE_DEFAULT,
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_THUMB_STYLE_HIDDEN
                ),
                hideCoverSource = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE
                ),
                disableCoverFlip = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP
                ),
                hideDeviceSwitch = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH
                ),
                hideCustomActions = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS
                ),
                hideTime = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME
                ),
                actionAlignLeft = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT
                ),
                actionOrder = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER
                ).coerceIn(
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_ACTION_ORDER_DEFAULT,
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_ACTION_ORDER_PLAY_LEFT
                ),
                backgroundStyle = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE
                ).coerceIn(
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_DEFAULT,
                    MediaCardConstants.ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_SOFT_COVER
                ),
                backgroundBlur = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR
                ).coerceIn(1, 20),
                backgroundColorAnimation = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION
                ),
                backgroundAutoInvert = prefs.getBoolean(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT
                ),
                softCoverTone = prefs.getInt(
                    MediaCardConstants.KEY_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE,
                    MediaCardConstants.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE
                ).coerceIn(
                    MediaCardConstants.MEDIA_SOFT_COVER_TONE_LIGHT,
                    MediaCardConstants.MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM
                )
            )
        }
    }

    private const val LEGACY_NOTIFICATION_MEDIA_PROGRESS_STYLE_GLOW = 2

    private fun SharedPreferences.float(
        key: String,
        default: Float,
        min: Float,
        max: Float
    ): Float = runCatching { getFloat(key, default) }
        .recoverCatching { getInt(key, default.toInt()).toFloat() }
        .getOrDefault(default)
        .coerceIn(min, max)
}
