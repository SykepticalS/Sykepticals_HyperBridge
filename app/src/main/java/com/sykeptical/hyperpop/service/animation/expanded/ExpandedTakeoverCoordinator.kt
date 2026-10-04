package com.sykeptical.hyperpop.service.animation.expanded

/** One island waiting to expand after the current owner finishes collapsing. */
data class ExpansionCandidate(
    val id: Int,
    val key: String,
    val token: Long,
)

sealed class EnqueueExpansion {
    data object NotActive : EnqueueExpansion()
    data class Queued(
        val pending: ExpansionCandidate,
        val displaced: ExpansionCandidate?,
    ) : EnqueueExpansion()
}

/**
 * One expanded island owns the status-bar fade, whether it started as the
 * big island or the circle. The other island stays semantically alive.
 * A disable request waits for the next native boundary so an in-flight morph
 * is not cut in half. Rotation, keyguard, detach, and the watchdog abandon
 * immediately and restore every alpha.
 */
class ExpandedTakeoverCoordinator(
    private val frameTimeoutMs: Long = 3_000L,
) {
    var phase: TakeoverPhase = TakeoverPhase.NATIVE
        private set
    var generation: Long = 0L
        private set
    var ownerId: Int? = null
        private set
    var expandedFromSmallIsland: Boolean = false
        private set
    var statusBarAlpha: Float = 1f
        private set
    var secondaryAlpha: Float = 1f
        private set
    var secondaryActive: Boolean = false
        private set
    var secondarySuppressed: Boolean = false
        private set
    var yieldedIslandId: Int? = null
        private set
    var allowsNewExpansion: Boolean = true
        private set

    var pendingExpansion: ExpansionCandidate? = null
        private set

    private var lastFrameMs: Long = 0L
    private var disableRequested: Boolean = false
    private var nextToken: Long = 0L

    fun arm(ownerId: Int, nowMs: Long, fromSmallIsland: Boolean = false): Long {
        if (!allowsNewExpansion) return -1L
        generation += 1L
        this.ownerId = ownerId
        expandedFromSmallIsland = fromSmallIsland
        yieldedIslandId = null
        phase = TakeoverPhase.EXPANDING
        lastFrameMs = nowMs
        publish(1f)
        return generation
    }

    fun accepts(ownerId: Int, generation: Long): Boolean =
        this.ownerId == ownerId && this.generation == generation && phase != TakeoverPhase.NATIVE

    fun onSecondaryPresence(active: Boolean, islandId: Int? = null) {
        secondaryActive = active
        yieldedIslandId = if (active) islandId ?: yieldedIslandId else null
        refreshSuppression()
    }

    /** Settled expanded geometry stays put when Folme stops emitting frames. */
    fun retainsExpandedGeometry(): Boolean = phase == TakeoverPhase.EXPANDED

    /**
     * The circle is hidden with Xiaomi's native hidden Folme state while the
     * big island is the expanded owner. A circle that itself expands fades
     * the remaining compact island with the status-bar alpha instead.
     */
    fun suppressesSecondaryVisual(): Boolean =
        !expandedFromSmallIsland &&
            secondaryActive &&
            (phase == TakeoverPhase.EXPANDING || phase == TakeoverPhase.EXPANDED)

    /**
     * The compact island that is not expanding fades with the status bar.
     * Its session stays active; only the drawn alpha follows [secondaryAlpha].
     */
    fun fadesUnexpandedCompact(): Boolean =
        expandedFromSmallIsland && secondaryActive && phase != TakeoverPhase.NATIVE

    /**
     * Expanding the circle does not run the big-island fade, and Xiaomi only
     * hides that layer once, at animation start. Keep it hidden for the whole
     * takeover so its unrounded plate cannot sit around the pill. Collapse
     * has to show the layer again.
     */
    fun hidesOwnedBigIslandLayer(): Boolean =
        expandedFromSmallIsland &&
            (phase == TakeoverPhase.EXPANDING || phase == TakeoverPhase.EXPANDED)

    /** The circle's own compact view should be gone once the pill has settled. */
    fun hidesSettledSmallIslandLayer(): Boolean =
        expandedFromSmallIsland && phase == TakeoverPhase.EXPANDED

    /**
     * Hidden-sibling hits stay disabled through the show animation. The
     * status-bar fade path arms them again once that sibling is visible.
     * The native-hide path waits until the takeover is fully native.
     */
    fun blocksYieldedIslandTouch(): Boolean {
        if (!secondaryActive || yieldedIslandId == null) return false
        return when (phase) {
            TakeoverPhase.EXPANDING, TakeoverPhase.EXPANDED -> true
            TakeoverPhase.COLLAPSING ->
                !expandedFromSmallIsland || secondaryAlpha < REVEAL_TOUCH_ALPHA
            TakeoverPhase.NATIVE -> false
        }
    }

    fun blocksTouchFor(islandId: Int): Boolean =
        blocksYieldedIslandTouch() && yieldedIslandId == islandId && ownerId != islandId

    /** A hidden sibling is restored only when its source is still present. */
    fun shouldRestoreYieldedIsland(): Boolean = secondaryActive && yieldedIslandId != null

    /** Keep the secondary's pre-expansion X until the takeover is fully native again. */
    fun holdsSecondaryPosition(): Boolean = secondaryActive && phase != TakeoverPhase.NATIVE

    /**
     * @return false when the frame belongs to a stale generation or another island.
     */
    fun onFrame(
        ownerId: Int,
        generation: Long,
        live: IslandRect,
        compact: IslandRect,
        target: IslandRect,
        statusGroup: IslandRect?,
        nowMs: Long,
    ): Boolean {
        if (!accepts(ownerId, generation)) return false
        lastFrameMs = nowMs
        val alpha = TakeoverFadePolicy.alpha(compact, live, target, statusGroup)
        if (phase == TakeoverPhase.EXPANDING && alpha <= 0.02f) {
            phase = TakeoverPhase.EXPANDED
        }
        if (phase == TakeoverPhase.COLLAPSING && live.height <= compact.height + 8) {
            settleNative()
            return true
        }
        publish(alpha)
        return true
    }

    fun beginCollapse(ownerId: Int, generation: Long): Boolean {
        if (!accepts(ownerId, generation)) return false
        phase = TakeoverPhase.COLLAPSING
        return true
    }

    fun abandon(ownerId: Int, generation: Long): Boolean {
        if (this.ownerId != ownerId || this.generation != generation) return false
        if (phase == TakeoverPhase.NATIVE) return false
        settleNative()
        return true
    }

    fun requestDisable() {
        disableRequested = true
        allowsNewExpansion = false
    }

    fun requestEnable() {
        disableRequested = false
        allowsNewExpansion = true
    }

    /**
     * One incoming expand waits while the current owner collapses.
     * A newer candidate displaces the previous one; the caller shows the
     * displaced island compact. Returns null when nothing is expanded, so
     * the caller lets Xiaomi handle the add directly.
     */
    fun enqueueExpansion(id: Int, key: String): EnqueueExpansion {
        if (phase == TakeoverPhase.NATIVE || ownerId == null || ownerId == id) return EnqueueExpansion.NotActive
        nextToken += 1L
        val displaced = pendingExpansion
        val pending = ExpansionCandidate(id, key, nextToken)
        pendingExpansion = pending
        return EnqueueExpansion.Queued(pending, displaced)
    }

    /** Collapse finished, or the takeover was abandoned. The candidate may already be gone. */
    fun takePendingExpansion(): ExpansionCandidate? {
        if (phase != TakeoverPhase.NATIVE) return null
        val ready = pendingExpansion
        pendingExpansion = null
        return ready
    }

    fun clearPendingExpansion(): ExpansionCandidate? {
        val ready = pendingExpansion
        pendingExpansion = null
        return ready
    }

    fun expireIfStale(nowMs: Long): Boolean {
        val owner = ownerId ?: return false
        if (phase == TakeoverPhase.NATIVE || phase == TakeoverPhase.EXPANDED) return false
        if (nowMs - lastFrameMs < frameTimeoutMs) return false
        return abandon(owner, generation)
    }

    private fun settleNative() {
        phase = TakeoverPhase.NATIVE
        ownerId = null
        expandedFromSmallIsland = false
        publish(1f)
        if (disableRequested) allowsNewExpansion = false
    }

    private fun publish(alpha: Float) {
        val resolved = if (phase == TakeoverPhase.NATIVE) 1f else alpha
        statusBarAlpha = resolved
        secondaryAlpha = if (secondaryActive && phase != TakeoverPhase.NATIVE) resolved else 1f
        refreshSuppression()
    }

    private fun refreshSuppression() {
        secondarySuppressed = suppressesSecondaryVisual()
    }

    private companion object {
        const val REVEAL_TOUCH_ALPHA = 0.98f
    }
}
