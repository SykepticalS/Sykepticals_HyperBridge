package com.d4viddf.hyperbridge.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)

object SettingsKeys {
    // Migration Flag
    const val MIGRATION_COMPLETE = "migration_to_room_complete"

    // Core Keys
    const val SETUP_COMPLETE = "setup_complete"
    const val LAST_VERSION = "last_version_code"
    const val PRIORITY_EDU = "priority_edu_shown"
    const val ALLOWED_PACKAGES = "allowed_packages"
    const val PRIORITY_ORDER = "priority_app_order"
    const val FEATURED_PERMISSION_WARNING = "featured_permission_warning"
    @Deprecated("Migrated to automatic popup control")
    const val FLOATING_SETUP_NOTICE_PENDING = "floating_setup_notice_pending"
    @Deprecated("Migrated to automatic popup control")
    const val FLOATING_SETUP_CONFIRMED_PACKAGES = "floating_setup_confirmed_packages"
    const val POPUP_CONTROL_ENABLED = "popup_control_enabled"
    const val POPUP_CONTROL_UPGRADE_REQUIRED = "popup_control_upgrade_required"
    const val POPUP_CONTROL_INTENTIONALLY_DISABLED = "popup_control_intentionally_disabled"
    const val POPUP_CONTROL_MIGRATION_COMPLETE = "popup_control_migration_complete"
    const val POPUP_CONTROL_OPT_IN_REQUESTED = "popup_control_opt_in_requested"
    const val POPUP_SEMANTIC_RULES_FINGERPRINT = "popup_semantic_rules_fingerprint"

    // Global Configs
    const val GLOBAL_FLOAT = "global_float" // legacy migration source
    const val GLOBAL_FIRST_FLOAT = "global_first_float"
    const val GLOBAL_FLOAT_ON_UPDATE = "global_float_on_update"
    const val GLOBAL_SHADE = "global_shade"
    const val GLOBAL_TIMEOUT = "global_timeout"
    const val GLOBAL_FLOAT_TIMEOUT = "global_float_timeout"
    const val GLOBAL_REMOVE_NOTIF = "global_remove_original_notif"
    const val GLOBAL_DISMISS_WITH_ORIGINAL = "global_dismiss_with_original"
    const val GLOBAL_ENABLE_INLINE_REPLY = "global_enable_inline_reply"
    const val GLOBAL_MARQUEE = "global_marquee"
    const val GLOBAL_MARQUEE_DISMISS = "global_marquee_dismiss"
    const val GLOBAL_LEFT_CONTENT = "global_left_content"
    const val GLOBAL_RIGHT_CONTENT = "global_right_content"
    const val GLOBAL_LEFT_EXPRESSION = "global_left_expression"
    const val GLOBAL_RIGHT_EXPRESSION = "global_right_expression"
    const val GLOBAL_ISLAND_GLOW = "global_island_glow"
    const val GLOBAL_FOCUS_GLOW = "global_focus_glow"
    const val GLOBAL_ISLAND_GLOW_COLOR = "global_island_glow_color"
    const val GLOBAL_FOCUS_GLOW_COLOR = "global_focus_glow_color"
    const val GLOBAL_FORCE_ISLAND_GLOW = "global_force_island_glow"
    const val GLOBAL_FORCE_FOCUS_GLOW = "global_force_focus_glow"
    const val GLOBAL_CONTACT_PINK_GLOW = "global_contact_pink_glow"
    const val GLOBAL_RESTORE_LOCKSCREEN = "global_restore_lockscreen"
    const val GLOBAL_DND_BEHAVIOR = "global_dnd_behavior"
    const val GLOBAL_FULLSCREEN_BEHAVIOR = "global_fullscreen_behavior"
    const val GLOBAL_LANDSCAPE_BEHAVIOR = "global_landscape_behavior"
    const val ISLAND_CONFIG_V2_MIGRATED = "island_config_v2_migrated"
    const val GLOBAL_BLOCKED_TERMS = "global_blocked_terms"

    // Nav
    const val NAV_LEFT = "nav_left_content"
    const val NAV_RIGHT = "nav_right_content"

    // System Island
    const val SCREEN_RECORDING_TIMEOUT = "screen_recording_timeout"
    const val SCREEN_RECORDING_LEFT_DESIGN = "screen_recording_left_design"
    const val SCREEN_RECORDING_RIGHT_DESIGN = "screen_recording_right_design"
    const val SCREEN_RECORDING_REPLACE_FLOATING = "screen_recording_replace_floating"
    const val SCREEN_RECORDING_IMMEDIATE_START = "screen_recording_immediate_start"
    const val SCREEN_RECORDING_ICON_STYLE = "screen_recording_icon_style"
}
