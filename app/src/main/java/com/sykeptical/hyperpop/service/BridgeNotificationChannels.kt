package com.sykeptical.hyperpop.service

data class BridgeChannelContract(
    val id: String,
    val sourceOwnsAudibleAlert: Boolean,
    val showsBadge: Boolean
)

/**
 * These IDs intentionally remain compatible with upstream and Pass 1/2. Their shipped defaults
 * were already silent, so a versioned replacement would only discard user channel choices.
 */
object BridgeNotificationChannels {
    const val SCHEMA_VERSION = 1
    const val ACTIVE = "hyper_pop_notification_channel"
    const val LIVE_UPDATE = "hyper_pop_live_update_channel"
    const val WATCH_RELAY = "hyper_pop_watch_relay_channel"

    val contracts = listOf(
        BridgeChannelContract(ACTIVE, sourceOwnsAudibleAlert = true, showsBadge = false),
        BridgeChannelContract(LIVE_UPDATE, sourceOwnsAudibleAlert = true, showsBadge = false),
        BridgeChannelContract(WATCH_RELAY, sourceOwnsAudibleAlert = true, showsBadge = false)
    )

    fun isWatchRelay(channelId: String?): Boolean = channelId == WATCH_RELAY
}
