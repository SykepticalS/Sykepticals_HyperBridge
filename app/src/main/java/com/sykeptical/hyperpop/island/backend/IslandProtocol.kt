package com.sykeptical.hyperpop.island.backend

object IslandProtocol {
    const val VERSION = 1
    const val APP_PACKAGE = "com.sykeptical.hyperpop"
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
    /** HyperOS only lets foreground apps write the clipboard, so SystemUI writes it for us. */
    const val ACTION_COPY_TEXT = "$APP_PACKAGE.action.COPY_TEXT"
    const val EXTRA_COPY_TEXT = "hyperpop.copy.text"
    const val EXTRA_COPY_LABEL = "hyperpop.copy.label"
    const val EXTRA_COPY_CONFIRMATION = "hyperpop.copy.confirmation"

    const val EXTRA_PROTOCOL = "hyperpop.protocol"
    const val EXTRA_NOTIFICATION = "hyperpop.notification"
    const val EXTRA_TAG = "hyperpop.tag"
    const val EXTRA_ID = "hyperpop.id"
    const val EXTRA_GENERATION = "hyperpop.generation"
    const val EXTRA_OWNER = "hyperpop.owner"
    const val EXTRA_SOURCE_KEY = "hyperpop.source_key"
    const val EXTRA_SOURCE_PACKAGE = "hyperpop.source_pkg"
    const val EXTRA_SOURCE_CHANNEL = "hyperpop.source_channel"
    const val EXTRA_SEMANTIC_TYPE = "hyperpop.semantic_type"
    const val EXTRA_NONCE = "hyperpop.nonce"
    const val EXTRA_CAPABILITIES = "hyperpop.capabilities"
    const val EXTRA_HOOK_PACKAGE = "hyperpop.hook_package"
    // Keep this identical to HyperIsland's IslandDispatchContract so the source marker and the
    // copied SystemUI suppression hook share the same proven contract.
    const val EXTRA_SUPPRESS_SOURCE_HEADS_UP = "hyperisland.suppress_source_heads_up"
    /**
     * The source notification carries the island Focus payload instead of a SystemUI proxy.
     * The wire values stay call-named so decorations already in flight still match.
     */
    const val EXTRA_SOURCE_FOCUS = "hyperpop.call_focus"
    const val EXTRA_SOURCE_FOCUS_DECORATION = "hyperpop.call_focus_decoration"
    const val EXTRA_CALL_FOCUS = EXTRA_SOURCE_FOCUS
    const val EXTRA_CALL_FOCUS_DECORATION = EXTRA_SOURCE_FOCUS_DECORATION
    /** Replace the app's shade row with the expanded Focus card. */
    const val EXTRA_SOURCE_FOCUS_REPLACE_SHADE = "hyperpop.source_focus_replace_shade"
    /** Whether this source must stay ongoing so a low-priority Focus card is not dropped. */
    const val EXTRA_SOURCE_FOCUS_ONGOING = "hyperpop.source_focus_ongoing"
    /** True once the remote party has answered; the source CallStyle chronometer may run. */
    const val EXTRA_CALL_CONNECTED = "hyperpop.call_connected"
    /** Call island is showing, so the source shade entry must stay user-dismissable. */
    const val EXTRA_CALL_SHADE_DISMISSIBLE = "hyperpop.call_shade_dismissible"
    const val EXTRA_RESULT_RECEIVER = "hyperpop.result_receiver"
    const val EXTRA_MARQUEE_ENABLED = "hyperpop.marquee.enabled"
    const val EXTRA_MARQUEE_MODE = "hyperpop.marquee.mode"
    const val EXTRA_ORIGINAL_TIMEOUT = "hyperpop.marquee.original_timeout"
    const val EXTRA_SOURCE_ONGOING = "hyperpop.source_ongoing"
    const val EXTRA_GLOW_ISLAND_MODE = "hyperpop.glow.island_mode"
    const val EXTRA_GLOW_FOCUS_MODE = "hyperpop.glow.focus_mode"
    const val EXTRA_GLOW_ISLAND_COLOR = "hyperpop.glow.island_color"
    const val EXTRA_GLOW_FOCUS_COLOR = "hyperpop.glow.focus_color"
    const val EXTRA_GLOW_DYNAMIC_COLOR = "hyperpop.glow.dynamic_color"
    const val EXTRA_FORCE_ISLAND_GLOW = "hyperpop.glow.force_island"
    const val EXTRA_FORCE_FOCUS_GLOW = "hyperpop.glow.force_focus"
    /** Keep SystemUI's stock glow palette and ignore the user's glow color settings. */
    const val EXTRA_GLOW_NATIVE = "hyperpop.glow.native"
    const val EXTRA_UPDATABLE = "hyperpop.updatable"
    const val EXTRA_TEXT_UPDATE_ANIMATION = "hyperpop.text_update_animation"
    const val EXTRA_REPLY_COMPOSER_OPEN = "hyperpop.reply_composer_open"
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
    const val REMOTE_PREFS = "HyperPopHookConfig"

    fun compatible(version: Int): Boolean = version == VERSION
}
