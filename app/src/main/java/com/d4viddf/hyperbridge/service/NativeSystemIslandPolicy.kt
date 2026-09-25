package com.d4viddf.hyperbridge.service

/**
 * HyperOS ShowOnce islands (charging, bluetooth, …) dismiss the current island, play, then
 * restore it. That stays the behavior whenever a real bridged island is on screen.
 *
 * If only the permanent stub is showing, the native island must update 9999 in place — the same
 * occupancy path incoming notification islands use — instead of overlay-dismissing the stub.
 *
 * HyperIsland's keep-island charging profile is the real-world analog: charging updates the
 * keep-island rather than auto-hiding it.
 */
object NativeSystemIslandPolicy {
    const val LOGICAL_PREFIX = "native-system:"
    const val SEMANTIC_STUB = "PERMANENT"
    const val SEMANTIC_NATIVE = "NATIVE_SYSTEM"
    const val DEFAULT_DURATION_MS = 3_500L

    fun logicalId(notifyId: String): String = "$LOGICAL_PREFIX${notifyId.ifBlank { "system" }}"

    fun isSoftOccupant(logicalId: String?): Boolean =
        logicalId?.startsWith(LOGICAL_PREFIX) == true

    fun effectiveOccupant(occupyingLogicalId: String?): String? =
        occupyingLogicalId?.takeUnless { isSoftOccupant(it) }

    fun isStubLikeSemantic(semanticType: String?): Boolean {
        val type = semanticType?.trim().orEmpty()
        return type.isEmpty() || type == SEMANTIC_STUB || type == SEMANTIC_NATIVE
    }

    /**
     * Engine-side: a native ShowOnce may occupy 9999 only while no real bridged island is live.
     * Soft native occupancy is not a real island.
     */
    fun shouldAbsorb(
        permanentPosted: Boolean,
        occupyingLogicalId: String?,
        realIslandCount: Int,
    ): Boolean {
        if (!permanentPosted) return false
        if (realIslandCount > 0) return false
        return occupyingLogicalId == null || isSoftOccupant(occupyingLogicalId)
    }

    /**
     * SystemUI-side: inspect posted HyperBridge notifications. Extra owned ids mean a real
     * island is stacked beside 9999; a non-stub semantic on 9999 means a bridged occupant.
     */
    fun shouldAbsorbFromPosted(
        permanentPosted: Boolean,
        permanentSemanticType: String?,
        extraOwnedIslandCount: Int,
    ): Boolean {
        if (!permanentPosted) return false
        if (extraOwnedIslandCount > 0) return false
        return isStubLikeSemantic(permanentSemanticType)
    }

    fun durationMs(bundleDurationMs: Long?): Long {
        val duration = bundleDurationMs ?: return DEFAULT_DURATION_MS
        if (duration < 0L) return DEFAULT_DURATION_MS
        return duration
    }

    fun isHide(durationMs: Long, explicitHide: Boolean): Boolean =
        explicitHide || durationMs == 0L
}
