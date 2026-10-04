package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Compact islands Xiaomi still has slotted while some other island is expanded.
 * Slot ownership stays Xiaomi's. This only decides which of those slots must
 * not be drawn or touched, and which of them still need a native redraw once
 * the expanded island is gone.
 */
data class QuarantineSnapshot(
    val expandedId: Int? = null,
    val bigId: Int? = null,
    val smallId: Int? = null,
    val showOnceId: Int? = null,
    val bigTempId: Int? = null,
    val takeoverHolding: Boolean = false,
)

sealed class QuarantineFlush {
    /** Expanded owner is present. Hide every other compact current. */
    data class Activate(
        val ownerId: Int,
        val generation: Long,
        val sweepIds: Set<Int>,
    ) : QuarantineFlush()

    /** Expanded island has left its handler, but the takeover card is still up. */
    data class Hold(val sweepIds: Set<Int>) : QuarantineFlush()

    /** Quarantine ended. Compact renders in this flush are native. */
    data class Release(val generation: Long) : QuarantineFlush()

    data object None : QuarantineFlush()
}

enum class QuarantineRegion {
    KEEP,
    DROP_ALL,
    DROP_SMALL,
    DROP_BIG,
}

data class QuarantineTouchMask(
    val blockBig: Boolean = false,
    val blockSmall: Boolean = false,
    val blockShowOnce: Boolean = false,
    val blockBigTemp: Boolean = false,
    val blockDefault: Boolean = false,
)

/**
 * A primary collapse may reveal its sibling as the status bar fades back in.
 * A secondary collapse still covers the main island's ears for the whole
 * shrink, so that sibling stays quarantined until the card is compact.
 */
fun releasesQuarantineForDespan(ownerState: String?): Boolean = ownerState == "BigIsland"

class SecondaryUiQuarantine {
    var generation: Long = 0L
        private set
    var ownerId: Int? = null
        private set
    var active: Boolean = false
        private set

    private var snapshot = QuarantineSnapshot()
    private val hiddenIds = mutableSetOf<Int>()
    private val handoffIds = mutableSetOf<Int>()
    private var refreshableGeneration: Long = -1L

    fun onFlushStart(snapshot: QuarantineSnapshot): QuarantineFlush {
        this.snapshot = snapshot
        val expanded = snapshot.expandedId
        if (expanded != null) {
            val replacing = !active || ownerId != expanded
            if (replacing) {
                val outgoing = ownerId.takeIf { active }
                generation += 1L
                ownerId = expanded
                hiddenIds.clear()
                refreshableGeneration = -1L
                handoffIds -= expanded
                // Xiaomi's handleReplacedState moved the previous expanded owner
                // into a compact slot in this same flush. Its Expanded -> compact
                // morph is the native replacement animation.
                if (outgoing != null && outgoing in compactIds(snapshot)) handoffIds += outgoing
            }
            handoffIds.retainAll(compactIds(snapshot))
            active = true
            return QuarantineFlush.Activate(expanded, generation, sweepIds())
        }
        if (snapshot.takeoverHolding && active) {
            handoffIds.retainAll(compactIds(snapshot))
            return QuarantineFlush.Hold(sweepIds())
        }
        handoffIds.clear()
        if (!active) return QuarantineFlush.None
        val released = generation
        active = false
        ownerId = null
        refreshableGeneration = released
        return QuarantineFlush.Release(released)
    }

    fun blocks(id: Int): Boolean =
        active && id != ownerId && id !in handoffIds && id in compactIds(snapshot)

    /** The previous expanded owner, still running Xiaomi's Expanded -> compact morph. */
    fun inHandoff(id: Int): Boolean = id in handoffIds

    /**
     * The native morph finished, was cancelled, or was replaced by another
     * motion. From here the island follows the settled quarantine.
     */
    fun endHandoff(id: Int): Boolean = handoffIds.remove(id)

    fun wasHidden(id: Int): Boolean = id in hiddenIds

    fun hiddenIds(): Set<Int> = hiddenIds.toSet()

    fun touchMask(): QuarantineTouchMask {
        if (!active) return QuarantineTouchMask()
        return QuarantineTouchMask(
            blockBig = blockedCompact(snapshot.bigId),
            blockSmall = blockedCompact(snapshot.smallId),
            blockShowOnce = blockedCompact(snapshot.showOnceId),
            blockBigTemp = blockedCompact(snapshot.bigTempId),
            blockDefault = true,
        )
    }

    fun region(): QuarantineRegion {
        if (!active) return QuarantineRegion.KEEP
        val bigBlocked = blockedCompact(snapshot.bigId)
        val smallBlocked = blockedCompact(snapshot.smallId)
        if (!bigBlocked && !smallBlocked) return QuarantineRegion.KEEP
        val visibleBig = snapshot.bigId != null && !bigBlocked
        val visibleSmall = snapshot.smallId != null && !smallBlocked
        return when {
            !visibleBig && !visibleSmall -> QuarantineRegion.DROP_ALL
            bigBlocked && visibleSmall -> QuarantineRegion.DROP_BIG
            smallBlocked && visibleBig -> QuarantineRegion.DROP_SMALL
            else -> QuarantineRegion.DROP_ALL
        }
    }

    fun markHidden(id: Int) {
        if (active) hiddenIds += id
    }

    /** A native compact render ran after release, so this island needs no second redraw. */
    fun markRendered(id: Int) {
        hiddenIds -= id
    }

    /**
     * Compact currents that were hidden and did not get a native render in the
     * flush that released the quarantine. The caller asks Xiaomi to redraw them
     * from their current slot. A stale [generation] is refused once.
     */
    fun onFlushEnd(snapshot: QuarantineSnapshot): Set<Int> {
        this.snapshot = snapshot
        if (active) return emptySet()
        val live = compactIds(snapshot)
        val refresh = hiddenIds.filter { it in live }.toSet()
        hiddenIds.clear()
        return refresh
    }

    fun acceptsRefresh(generation: Long): Boolean {
        if (active || generation != this.generation || generation != refreshableGeneration) return false
        refreshableGeneration = -1L
        return true
    }

    /** Compact currents that should be hidden but have not been marked yet. */
    fun violations(snapshot: QuarantineSnapshot): Set<Int> {
        if (!active) return emptySet()
        val owner = ownerId ?: return emptySet()
        return compactIds(snapshot).filter { it != owner && it !in hiddenIds && it !in handoffIds }.toSet()
    }

    private fun blockedCompact(id: Int?): Boolean = id != null && id != ownerId

    private fun sweepIds(): Set<Int> {
        val owner = ownerId ?: return emptySet()
        return compactIds(snapshot).filter { it != owner && it !in handoffIds }.toSet()
    }

    private fun compactIds(snapshot: QuarantineSnapshot): Set<Int> = setOfNotNull(
        snapshot.bigId,
        snapshot.smallId,
        snapshot.showOnceId,
        snapshot.bigTempId,
    )
}
