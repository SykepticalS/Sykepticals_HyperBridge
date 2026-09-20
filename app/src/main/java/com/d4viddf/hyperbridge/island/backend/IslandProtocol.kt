package com.d4viddf.hyperbridge.island.backend

object IslandProtocol {
    const val VERSION = 1
    const val APP_PACKAGE = "com.sykeptical.hyperbridge"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    const val OWNER = APP_PACKAGE
    const val PERMISSION = "$APP_PACKAGE.permission.SEND_ISLAND"

    const val ACTION_POST = "$APP_PACKAGE.action.POST_ISLAND"
    const val ACTION_CANCEL = "$APP_PACKAGE.action.CANCEL_ISLAND"
    const val ACTION_CANCEL_ALL = "$APP_PACKAGE.action.CANCEL_ALL_ISLANDS"
    const val ACTION_PING = "$APP_PACKAGE.action.PING_BACKEND"
    const val ACTION_PONG = "$APP_PACKAGE.action.BACKEND_PONG"
    const val ACTION_PING_XMSF = "$APP_PACKAGE.action.PING_XMSF"

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

    const val CAP_POST = 1
    const val CAP_TAGGED_CANCEL = 1 shl 1
    const val CAP_FOCUS_BYPASS = 1 shl 2
    const val CAP_HEADS_UP = 1 shl 3
    const val REQUIRED_CAPABILITIES = CAP_POST or CAP_TAGGED_CANCEL or CAP_FOCUS_BYPASS or CAP_HEADS_UP

    const val MAX_PARCEL_BYTES = 700 * 1024
    const val HEARTBEAT_LEASE_MS = 45_000L
    const val REMOTE_PREFS = "HyperBridgeHookConfig"

    fun compatible(version: Int): Boolean = version == VERSION
}
