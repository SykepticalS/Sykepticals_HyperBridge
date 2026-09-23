package com.d4viddf.hyperbridge.island.backend

object IslandProtocol {
    const val VERSION = 1
    const val APP_PACKAGE = "com.sykeptical.hyperbridge"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    const val SCREEN_RECORDER_PACKAGE = "com.miui.screenrecorder"
    const val OWNER = APP_PACKAGE
    const val PERMISSION = "$APP_PACKAGE.permission.SEND_ISLAND"

    const val ACTION_POST = "$APP_PACKAGE.action.POST_ISLAND"
    const val ACTION_CANCEL = "$APP_PACKAGE.action.CANCEL_ISLAND"
    const val ACTION_CANCEL_ALL = "$APP_PACKAGE.action.CANCEL_ALL_ISLANDS"
    const val ACTION_PING = "$APP_PACKAGE.action.PING_BACKEND"
    const val ACTION_PONG = "$APP_PACKAGE.action.BACKEND_PONG"
    const val ACTION_PING_XMSF = "$APP_PACKAGE.action.PING_XMSF"
    const val ACTION_RELOAD_ENGINE = "$APP_PACKAGE.action.RELOAD_ENGINE"
    const val ACTION_CANCEL_SOURCE = "$APP_PACKAGE.action.CANCEL_SOURCE"
    const val ACTION_REPLY_COMPOSER = "$APP_PACKAGE.action.REPLY_COMPOSER"

    const val EXTRA_PROTOCOL = "hyperbridge.protocol"
    const val EXTRA_NOTIFICATION = "hyperbridge.notification"
    const val EXTRA_TAG = "hyperbridge.tag"
    const val EXTRA_ID = "hyperbridge.id"
    const val EXTRA_GENERATION = "hyperbridge.generation"
    const val EXTRA_OWNER = "hyperbridge.owner"
    const val EXTRA_SOURCE_KEY = "hyperbridge.source_key"
    const val EXTRA_SOURCE_PACKAGE = "hyperbridge.source_pkg"
    const val EXTRA_SOURCE_CHANNEL = "hyperbridge.source_channel"
    const val EXTRA_SEMANTIC_TYPE = "hyperbridge.semantic_type"
    const val EXTRA_NONCE = "hyperbridge.nonce"
    const val EXTRA_CAPABILITIES = "hyperbridge.capabilities"
    const val EXTRA_HOOK_PACKAGE = "hyperbridge.hook_package"
    // Keep this identical to HyperIsland's IslandDispatchContract so the source marker and the
    // copied SystemUI suppression hook share the same proven contract.
    const val EXTRA_SUPPRESS_SOURCE_HEADS_UP = "hyperisland.suppress_source_heads_up"
    const val EXTRA_RESULT_RECEIVER = "hyperbridge.result_receiver"
    const val EXTRA_MARQUEE_ENABLED = "hyperbridge.marquee.enabled"
    const val EXTRA_MARQUEE_MODE = "hyperbridge.marquee.mode"
    const val EXTRA_ORIGINAL_TIMEOUT = "hyperbridge.marquee.original_timeout"
    const val EXTRA_SOURCE_ONGOING = "hyperbridge.source_ongoing"
    const val EXTRA_GLOW_ISLAND_MODE = "hyperbridge.glow.island_mode"
    const val EXTRA_GLOW_FOCUS_MODE = "hyperbridge.glow.focus_mode"
    const val EXTRA_GLOW_ISLAND_COLOR = "hyperbridge.glow.island_color"
    const val EXTRA_GLOW_FOCUS_COLOR = "hyperbridge.glow.focus_color"
    const val EXTRA_GLOW_DYNAMIC_COLOR = "hyperbridge.glow.dynamic_color"
    const val EXTRA_FORCE_ISLAND_GLOW = "hyperbridge.glow.force_island"
    const val EXTRA_FORCE_FOCUS_GLOW = "hyperbridge.glow.force_focus"
    const val EXTRA_UPDATABLE = "hyperbridge.updatable"
    const val EXTRA_TEXT_UPDATE_ANIMATION = "hyperbridge.text_update_animation"
    const val EXTRA_REPLY_COMPOSER_OPEN = "hyperbridge.reply_composer_open"
    const val MIUI_SBN = "miui.sbn"
    const val MIUI_BIG_ISLAND_EFFECT = "miui.bigIsland.effect.src"
    const val MIUI_EFFECT = "miui.effect.src"
    const val EFFECT_OUTER_GLOW = "outer_glow"

    const val RESULT_POSTED = 1
    const val RESULT_REJECTED = 2

    const val CAP_POST = 1
    const val CAP_TAGGED_CANCEL = 1 shl 1
    const val CAP_FOCUS_BYPASS = 1 shl 2
    const val CAP_HEADS_UP = 1 shl 3
    const val CAP_NOTIFICATION_INGRESS = 1 shl 4
    const val CAP_MARQUEE = 1 shl 5
    const val CAP_ISLAND_DISMISS = 1 shl 6
    const val CAP_FULL_GLOW = 1 shl 7
    const val CAP_TEXT_UPDATE_ANIMATION = 1 shl 8
    const val REQUIRED_CAPABILITIES = CAP_POST or CAP_TAGGED_CANCEL or CAP_FOCUS_BYPASS or
        CAP_HEADS_UP or CAP_NOTIFICATION_INGRESS

    const val MAX_PARCEL_BYTES = 700 * 1024
    const val HEARTBEAT_LEASE_MS = 45_000L
    const val REMOTE_PREFS = "HyperBridgeHookConfig"

    fun compatible(version: Int): Boolean = version == VERSION
}
