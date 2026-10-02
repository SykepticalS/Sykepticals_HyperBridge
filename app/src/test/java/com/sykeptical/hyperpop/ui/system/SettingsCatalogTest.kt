package com.sykeptical.hyperpop.ui.system

import com.sykeptical.hyperpop.data.db.SettingsKeys
import com.sykeptical.hyperpop.island.backend.HookConfigSync
import com.sykeptical.hyperpop.xposed.mediacard.MediaCardConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {
    @Test
    fun catalogCoversExposedPreferenceKeys() {
        val required = setOf(
            SettingsKeys.ALLOWED_PACKAGES,
            "vpn_island_enabled",
            "limit_mode",
            SettingsKeys.PRIORITY_ORDER,
            SettingsKeys.GLOBAL_TIMEOUT,
            SettingsKeys.GLOBAL_FIRST_FLOAT,
            SettingsKeys.GLOBAL_FLOAT_TIMEOUT,
            SettingsKeys.GLOBAL_FLOAT_ON_UPDATE,
            SettingsKeys.GLOBAL_MARQUEE,
            SettingsKeys.GLOBAL_MARQUEE_DISMISS,
            SettingsKeys.GLOBAL_LEFT_CONTENT,
            SettingsKeys.GLOBAL_RIGHT_CONTENT,
            SettingsKeys.GLOBAL_LEFT_EXPRESSION,
            SettingsKeys.GLOBAL_RIGHT_EXPRESSION,
            SettingsKeys.GLOBAL_ISLAND_GLOW,
            SettingsKeys.GLOBAL_FOCUS_GLOW,
            SettingsKeys.GLOBAL_ISLAND_GLOW_COLOR,
            SettingsKeys.GLOBAL_FOCUS_GLOW_COLOR,
            SettingsKeys.GLOBAL_FORCE_ISLAND_GLOW,
            SettingsKeys.GLOBAL_FORCE_FOCUS_GLOW,
            SettingsKeys.GLOBAL_FULLSCREEN_BEHAVIOR,
            SettingsKeys.GLOBAL_LANDSCAPE_BEHAVIOR,
            SettingsKeys.GLOBAL_DND_BEHAVIOR,
            "auto_detect_dnd",
            "dnd_mode_enabled",
            SettingsKeys.LOGIN_CODE_ENABLED,
            SettingsKeys.LOGIN_CODE_COPY_ACTION,
            SettingsKeys.LOGIN_CODE_DISMISS_AFTER_COPY,
            SettingsKeys.LOGIN_CODE_GLOW,
            SettingsKeys.LOGIN_CODE_COMPACT,
            SettingsKeys.LOGIN_CODE_PACKAGES,
            SettingsKeys.NAV_LEFT,
            SettingsKeys.NAV_RIGHT,
            SettingsKeys.GLOBAL_BLOCKED_TERMS,
            SettingsKeys.SCREEN_RECORDING_TIMEOUT,
            SettingsKeys.SCREEN_RECORDING_LEFT_DESIGN,
            SettingsKeys.SCREEN_RECORDING_RIGHT_DESIGN,
            SettingsKeys.SCREEN_RECORDING_REPLACE_FLOATING,
            SettingsKeys.SCREEN_RECORDING_IMMEDIATE_START,
            SettingsKeys.SCREEN_RECORDING_COUNTDOWN_ENABLED,
            SettingsKeys.SCREEN_RECORDING_ICON_STYLE,
            HookConfigSync.KEY_BETTER_ANIMATIONS_ENABLED,
            HookConfigSync.KEY_MARQUEE_SPEED,
            HookConfigSync.KEY_GLOW_RANGE,
            HookConfigSync.KEY_SINGLE_COLOR_GLOW,
            HookConfigSync.KEY_GLOW_BASE_COLOR,
            MediaCardConstants.KEY_HOOK_MEDIA_CARD_EDITING_ENABLED,
        )
        val missing = required - SettingsCatalog.preferenceKeys
        assertTrue(missing.joinToString(), missing.isEmpty())
        assertTrue(SettingsCatalog.preferenceKeys.containsAll(SettingsCatalog.mediaKeys))
        assertEquals(
            listOf(
                "first_float", "shade", "timeout", "float_timeout", "remove_notif",
                "dismiss_with_original", "enable_inline_reply", "float_on_update", "marquee",
                "marquee_dismiss", "left_content", "right_content", "left_expression", "right_expression",
                "island_glow", "focus_glow", "island_glow_color", "focus_glow_color",
                "force_island_glow", "force_focus_glow", "contact_pink_glow", "restore_lockscreen",
                "dnd_behavior", "fullscreen_behavior", "landscape_behavior",
                "blocked", "nav_left", "nav_right", "call_stages", "voice_compact_duration",
            ),
            SettingsCatalog.perAppSuffixes,
        )
    }

    @Test
    fun finishingOnboardingKeepsExistingPreferences() {
        val existing = mapOf(
            "limit_mode" to "MOST_RECENT",
            SettingsKeys.LOGIN_CODE_ENABLED to "true",
        )
        val next = OnboardingCompletion.complete(existing)
        assertEquals("true", next[SettingsKeys.SETUP_COMPLETE])
        assertEquals("MOST_RECENT", next["limit_mode"])
        assertEquals("true", next[SettingsKeys.LOGIN_CODE_ENABLED])
        assertEquals(existing.size + 1, next.size)
    }
}
