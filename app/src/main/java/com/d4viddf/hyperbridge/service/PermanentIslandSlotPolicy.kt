package com.d4viddf.hyperbridge.service

/**
 * Permanent island slot: incoming islands update the posted 9999 pill first. A second island
 * may use its own id only while that occupant is still active. When extras leave, the last
 * remaining island is folded back onto 9999, collapsed, and expansion stays locked until a
 * new island occupies the slot.
 */
data class PermanentSlotPostPlan(
    val usePermanentSlot: Boolean,
    val notifyInPlace: Boolean,
    val lockExpansion: Boolean,
    val migrateFromPermanent: Boolean = false,
    val deferAutoExpand: Boolean = false,
)

enum class PermanentSlotAfterRemoval {
    NONE,
    RESTORE_STUB,
    COLLAPSE_LAST_ON_PERMANENT,
    ADOPT_LAST_ONTO_PERMANENT,
}

data class PermanentSlotRemovalPlan(
    val action: PermanentSlotAfterRemoval,
    val lastLogicalId: String? = null,
    val lastBridgeId: Int? = null,
)

data class PermanentSlotIslandRef(
    val logicalId: String,
    val bridgeId: Int,
    val postTime: Long,
)

object PermanentIslandSlotPolicy {
    const val AUTO_EXPAND_DELAY_MS = 300L

    fun postPlan(
        slotAvailable: Boolean,
        occupyingLogicalId: String?,
        occupantStillActive: Boolean,
        expansionLocked: Boolean,
        permanentPosted: Boolean,
        logicalId: String,
        alreadyPosted: Boolean,
        previousBridgeId: Int?,
        eligible: Boolean,
        forcePermanent: Boolean = false,
        forceLockExpansion: Boolean = false,
        forceUnlockExpansion: Boolean = false,
    ): PermanentSlotPostPlan {
        if (!eligible || !slotAvailable) {
            return PermanentSlotPostPlan(
                usePermanentSlot = false,
                notifyInPlace = alreadyPosted && previousBridgeId != PermanentIslandManager.PERMANENT_BRIDGE_ID,
                lockExpansion = false,
                migrateFromPermanent = previousBridgeId == PermanentIslandManager.PERMANENT_BRIDGE_ID,
            )
        }

        val occupantBlocksSlot = occupyingLogicalId != null &&
            occupyingLogicalId != logicalId &&
            occupantStillActive
        val usePermanentSlot = forcePermanent ||
            previousBridgeId == PermanentIslandManager.PERMANENT_BRIDGE_ID ||
            (!alreadyPosted && !occupantBlocksSlot) ||
            (alreadyPosted && previousBridgeId == PermanentIslandManager.PERMANENT_BRIDGE_ID)

        val lockExpansion = usePermanentSlot && !forceUnlockExpansion && (
            forceLockExpansion || (expansionLocked && alreadyPosted)
            )
        val notifyInPlace = if (usePermanentSlot) {
            permanentPosted || previousBridgeId == PermanentIslandManager.PERMANENT_BRIDGE_ID
        } else {
            alreadyPosted
        }
        val deferAutoExpand = usePermanentSlot &&
            !alreadyPosted &&
            !lockExpansion &&
            !forceUnlockExpansion
        return PermanentSlotPostPlan(
            usePermanentSlot = usePermanentSlot,
            notifyInPlace = notifyInPlace,
            lockExpansion = lockExpansion,
            deferAutoExpand = deferAutoExpand,
        )
    }

    fun afterRemoval(
        slotAvailable: Boolean,
        occupyingLogicalId: String?,
        remaining: List<PermanentSlotIslandRef>,
    ): PermanentSlotRemovalPlan {
        if (!slotAvailable) return PermanentSlotRemovalPlan(PermanentSlotAfterRemoval.NONE)
        if (remaining.isEmpty()) {
            return PermanentSlotRemovalPlan(PermanentSlotAfterRemoval.RESTORE_STUB)
        }
        val occupant = occupyingLogicalId?.let { occupied ->
            remaining.firstOrNull { it.logicalId == occupied }
        }
        if (occupant != null) {
            return PermanentSlotRemovalPlan(
                PermanentSlotAfterRemoval.COLLAPSE_LAST_ON_PERMANENT,
                occupant.logicalId,
                occupant.bridgeId,
            )
        }
        val last = remaining.maxBy { it.postTime }
        return PermanentSlotRemovalPlan(
            PermanentSlotAfterRemoval.ADOPT_LAST_ONTO_PERMANENT,
            last.logicalId,
            last.bridgeId,
        )
    }

    fun logicalToken(bridgeId: Int): String =
        if (bridgeId == PermanentIslandManager.PERMANENT_BRIDGE_ID) {
            PermanentIslandManager.PERMANENT_LOGICAL_TOKEN
        } else {
            bridgeId.toString()
        }
}
