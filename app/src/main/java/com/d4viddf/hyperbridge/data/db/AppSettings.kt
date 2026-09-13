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
    const val GLOBAL_FLOAT = "global_float"
    const val GLOBAL_SHADE = "global_shade"
    const val GLOBAL_TIMEOUT = "global_timeout"
    const val GLOBAL_FLOAT_TIMEOUT = "global_float_timeout"
    const val GLOBAL_REMOVE_NOTIF = "global_remove_original_notif"
    const val GLOBAL_DISMISS_WITH_ORIGINAL = "global_dismiss_with_original"
    const val GLOBAL_ENABLE_INLINE_REPLY = "global_enable_inline_reply"
    const val GLOBAL_BLOCKED_TERMS = "global_blocked_terms"

    // Nav
    const val NAV_LEFT = "nav_left_content"
    const val NAV_RIGHT = "nav_right_content"

    // System Island
    const val SCREEN_RECORDING_TIMEOUT = "screen_recording_timeout"
    const val SCREEN_RECORDING_LEFT_DESIGN = "screen_recording_left_design"
    const val SCREEN_RECORDING_RIGHT_DESIGN = "screen_recording_right_design"
}
