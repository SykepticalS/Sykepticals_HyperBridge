package com.d4viddf.hyperbridge.models

/**
 * Xiaomi OS4 glow views and window-level glow containers are shared across islands.
 * Glow may only start from the currently displayed island's extras — never from a
 * leftover target belonging to a previously posted glowing island.
 */
object IslandGlowIsolation {
    fun shouldStartGlow(currentOwned: Boolean, currentRequestsGlow: Boolean): Boolean =
        currentOwned && currentRequestsGlow

    fun shouldStopGlow(
        currentKnown: Boolean,
        currentOwned: Boolean,
        currentRequestsGlow: Boolean,
    ): Boolean = currentKnown && (!currentOwned || !currentRequestsGlow)

    fun canReuseRecentTarget(
        currentKey: String?,
        recentKey: String?,
        currentRequestsGlow: Boolean,
    ): Boolean {
        if (currentRequestsGlow) return true
        if (currentKey.isNullOrBlank() || recentKey.isNullOrBlank()) return false
        return currentKey == recentKey
    }
}
