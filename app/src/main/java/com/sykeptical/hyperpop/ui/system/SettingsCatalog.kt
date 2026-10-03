package com.sykeptical.hyperpop.ui.system

import androidx.annotation.StringRes
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.db.SettingsKeys
import com.sykeptical.hyperpop.island.backend.HookConfigSync
import com.sykeptical.hyperpop.xposed.mediacard.MediaCardConstants

enum class SettingsPlace {
    ISLANDS,
    NOTIFICATIONS,
    SYSTEM,
    ADVANCED,
    ABOUT,
    PRIORITY,
    TIMING,
    TEXT,
    GLOW,
    SCENES,
    MOTION,
    EXPERIMENTS,
    LOGIN_CODES,
    DND,
    NAVIGATION,
    MEDIA,
    BLOCKLIST,
    DIAGNOSTICS,
    BUG_REPORT,
    BACKUP,
    SCREEN_RECORDING,
    APPS,
}

data class SettingsEntry(
    val id: String,
    @StringRes val titleRes: Int,
    val place: SettingsPlace,
    val keys: List<String>,
    val keywords: List<String> = emptyList(),
)

object OnboardingCompletion {
    /** Finishing setup records only this flag. Other preference values stay as they are. */
    fun complete(existing: Map<String, String>): Map<String, String> =
        existing + (SettingsKeys.SETUP_COMPLETE to "true")
}

object SettingsCatalog {
    val perAppSuffixes: List<String> = listOf(
        "first_float", "shade", "timeout", "float_timeout", "remove_notif",
        "dismiss_with_original", "enable_inline_reply", "float_on_update", "marquee",
        "marquee_dismiss", "left_content", "right_content", "left_expression", "right_expression",
        "island_glow", "focus_glow", "island_glow_color", "focus_glow_color",
        "force_island_glow", "force_focus_glow", "contact_pink_glow", "restore_lockscreen",
        "dnd_behavior", "fullscreen_behavior", "landscape_behavior",
        "blocked", "nav_left", "nav_right", "call_stages", "voice_compact_duration",
    )

    val mediaKeys: Set<String> = MediaCardConstants::class.java.declaredFields
        .filter { it.name.startsWith("KEY_") && it.type == String::class.java }
        .map { it.get(null) as String }
        .toSet()

    val entries: List<SettingsEntry> = listOf(
        entry("apps", R.string.tab_apps, SettingsPlace.APPS, listOf(SettingsKeys.ALLOWED_PACKAGES, "vpn_island_enabled"), listOf("bridge", "library", "vpn")),
        entry("priority", R.string.islands_priority, SettingsPlace.PRIORITY, listOf("limit_mode", SettingsKeys.PRIORITY_ORDER), listOf("limit", "queue", "order")),
        entry("timing", R.string.islands_timing, SettingsPlace.TIMING, listOf(SettingsKeys.GLOBAL_TIMEOUT, SettingsKeys.GLOBAL_FIRST_FLOAT, SettingsKeys.GLOBAL_FLOAT_TIMEOUT, SettingsKeys.GLOBAL_FLOAT_ON_UPDATE), listOf("hide", "timeout", "expand", "float")),
        entry("text", R.string.islands_text, SettingsPlace.TEXT, listOf(SettingsKeys.GLOBAL_MARQUEE, SettingsKeys.GLOBAL_MARQUEE_DISMISS, SettingsKeys.GLOBAL_LEFT_CONTENT, SettingsKeys.GLOBAL_RIGHT_CONTENT, SettingsKeys.GLOBAL_LEFT_EXPRESSION, SettingsKeys.GLOBAL_RIGHT_EXPRESSION), listOf("marquee", "scroll", "content")),
        entry("glow", R.string.islands_glow, SettingsPlace.GLOW, listOf(SettingsKeys.GLOBAL_ISLAND_GLOW, SettingsKeys.GLOBAL_FOCUS_GLOW, SettingsKeys.GLOBAL_ISLAND_GLOW_COLOR, SettingsKeys.GLOBAL_FOCUS_GLOW_COLOR), listOf("glow", "color")),
        entry("scenes", R.string.islands_scenes, SettingsPlace.SCENES, listOf(SettingsKeys.GLOBAL_FULLSCREEN_BEHAVIOR, SettingsKeys.GLOBAL_LANDSCAPE_BEHAVIOR), listOf("fullscreen", "landscape")),
        entry("motion", R.string.islands_motion, SettingsPlace.MOTION, listOf(HookConfigSync.KEY_BETTER_ANIMATIONS_ENABLED, HookConfigSync.KEY_EXPAND_OVER_STATUS_BAR_ENABLED, HookConfigSync.KEY_EXPANDED_BLACK_BACKGROUND, HookConfigSync.KEY_EXPANDED_ROUNDED_PILL, HookConfigSync.KEY_MARQUEE_SPEED), listOf("animation", "speed", "expand", "status bar", "ios", "black", "pill", "rounded")),
        entry("login", R.string.login_code_title, SettingsPlace.LOGIN_CODES, listOf(SettingsKeys.LOGIN_CODE_ENABLED, SettingsKeys.LOGIN_CODE_COPY_ACTION, SettingsKeys.LOGIN_CODE_DISMISS_AFTER_COPY, SettingsKeys.LOGIN_CODE_GLOW, SettingsKeys.LOGIN_CODE_COMPACT, SettingsKeys.LOGIN_CODE_PACKAGES), listOf("otp", "code", "verification")),
        entry("dnd", R.string.dnd_mode_title, SettingsPlace.DND, listOf("auto_detect_dnd", "dnd_mode_enabled", SettingsKeys.GLOBAL_DND_BEHAVIOR), listOf("do not disturb", "silent")),
        entry("nav", R.string.nav_layout_title, SettingsPlace.NAVIGATION, listOf(SettingsKeys.NAV_LEFT, SettingsKeys.NAV_RIGHT), listOf("navigation", "maps", "eta")),
        entry("media", R.string.notifications_media, SettingsPlace.MEDIA, mediaKeys.toList(), listOf("music", "player", "aod")),
        entry("block", R.string.blocked_terms, SettingsPlace.BLOCKLIST, listOf(SettingsKeys.GLOBAL_BLOCKED_TERMS), listOf("spoiler", "filter", "keywords")),
        entry("system", R.string.settings_system, SettingsPlace.SYSTEM, emptyList(), listOf("root", "lsposed", "systemui", "xmsf", "setup")),
        entry("experiments", R.string.advanced_experiments, SettingsPlace.EXPERIMENTS, listOf(HookConfigSync.KEY_GLOW_RANGE, HookConfigSync.KEY_SINGLE_COLOR_GLOW, HookConfigSync.KEY_GLOW_BASE_COLOR, SettingsKeys.GLOBAL_FORCE_ISLAND_GLOW, SettingsKeys.GLOBAL_FORCE_FOCUS_GLOW), listOf("experimental", "force glow")),
        entry("diagnostics", R.string.diagnostics_title, SettingsPlace.DIAGNOSTICS, emptyList(), listOf("logs", "health")),
        entry("bug", R.string.bug_report_entry_title, SettingsPlace.BUG_REPORT, emptyList(), listOf("report", "feedback")),
        entry("backup", R.string.backup_restore_title, SettingsPlace.BACKUP, emptyList(), listOf("export", "import", "restore")),
        entry("recorder", R.string.screen_recording_title, SettingsPlace.SCREEN_RECORDING, listOf(SettingsKeys.SCREEN_RECORDING_TIMEOUT, SettingsKeys.SCREEN_RECORDING_LEFT_DESIGN, SettingsKeys.SCREEN_RECORDING_RIGHT_DESIGN, SettingsKeys.SCREEN_RECORDING_REPLACE_FLOATING, SettingsKeys.SCREEN_RECORDING_IMMEDIATE_START, SettingsKeys.SCREEN_RECORDING_COUNTDOWN_ENABLED, SettingsKeys.SCREEN_RECORDING_ICON_STYLE), listOf("recorder")),
        entry("about", R.string.settings_about, SettingsPlace.ABOUT, emptyList(), listOf("version", "license", "language", "privacy")),
    )

    val preferenceKeys: Set<String> = entries.flatMap { it.keys }.toSet() + mediaKeys

    fun match(query: String): List<SettingsEntry> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return entries
        return entries.filter { entry ->
            entry.id.contains(needle) ||
                entry.keywords.any { it.contains(needle) } ||
                entry.keys.any { it.contains(needle) }
        }
    }

    private fun entry(
        id: String,
        @StringRes titleRes: Int,
        place: SettingsPlace,
        keys: List<String>,
        keywords: List<String>,
    ) = SettingsEntry(id, titleRes, place, keys, keywords)
}
