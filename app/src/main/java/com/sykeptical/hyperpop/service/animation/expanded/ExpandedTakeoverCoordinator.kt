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

/** A replaced owner still morphing into its compact slot under its own takeover geometry. */
data class RetiringOwner(val id: Int, val generation: Long)

data class Replacement(val retired: RetiringOwner, val generation: Long)

enum class RetiringFrame {
    IGNORED,
    MOVING,
    SETTLED,
}

/**
 * One expanded island owns the status-bar fade, whether it started as the
 * big island or the circle. A disable request waits for the next native
 * boundary so an in-flight morph is not cut in half. Rotation, keyguard,
 * detach, and the watchdog abandon immediately and restore the status bar.
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
    var statusBarAlpha: Float = 1f
        private set
    var allowsNewExpansion: Boolean = true
        private set

    var pendingExpansion: ExpansionCandidate? = null
        private set

    private var lastFrameMs: Long = 0L
    private var disableRequested: Boolean = false
    private var nextToken: Long = 0L
    private var ownerAlpha: Float = 1f
    private val retiring = LinkedHashMap<Int, Retiring>()

    private class Retiring(val generation: Long, var alpha: Float)

    fun arm(ownerId: Int, nowMs: Long): Long {
        if (!allowsNewExpansion) return -1L
        generation += 1L
        this.ownerId = ownerId
        phase = TakeoverPhase.EXPANDING
        lastFrameMs = nowMs
        publish(1f)
        return generation
    }

    /**
     * Xiaomi's handleReplacedState moves the expanded owner to a compact slot in
     * the same operation that expands the incoming island. That only happens
     * while the owner is still Xiaomi's expanded current. A collapsing owner has
     * already left that slot, so its incoming add waits in the queue instead.
     */
    fun admitsNativeReplacement(
        incomingId: Int,
        xiaomiExpandedIsOwner: Boolean,
        ownerTempShow: Boolean,
    ): Boolean {
        val owner = ownerId ?: return false
        if (owner == incomingId || !xiaomiExpandedIsOwner || ownerTempShow) return false
        if (pendingExpansion != null) return false
        return phase == TakeoverPhase.EXPANDING || phase == TakeoverPhase.EXPANDED
    }

    /**
     * Arms [incomingId] over an owner that is still on screen. The owner keeps
     * its generation as a retiring island until its own compact morph lands.
     * Returns null when there is no owner to hand off, so the caller arms normally.
     */
    fun replace(incomingId: Int, nowMs: Long): Replacement? {
        val outgoing = ownerId ?: return null
        if (outgoing == incomingId || phase == TakeoverPhase.NATIVE || !allowsNewExpansion) return null
        val retired = RetiringOwner(outgoing, generation)
        retiring.remove(outgoing)
        retiring[outgoing] = Retiring(generation, ownerAlpha)
        return Replacement(retired, arm(incomingId, nowMs))
    }

    fun retires(id: Int, generation: Long): Boolean = retiring[id]?.generation == generation

    fun retiringGeneration(id: Int): Long? = retiring[id]?.generation

    fun hasRetiring(): Boolean = retiring.isNotEmpty()

    /** The retiring card covers the status bar too until it reaches compact height. */
    fun onRetiringFrame(
        id: Int,
        generation: Long,
        live: IslandRect,
        compact: IslandRect,
        target: IslandRect,
        statusGroup: IslandRect?,
    ): RetiringFrame {
        val entry = retiring[id]?.takeIf { it.generation == generation } ?: return RetiringFrame.IGNORED
        if (live.height <= compact.height + 8) {
            retiring.remove(id)
            publish(ownerAlpha)
            return RetiringFrame.SETTLED
        }
        entry.alpha = TakeoverFadePolicy.alpha(compact, live, target, statusGroup)
        publish(ownerAlpha)
        return RetiringFrame.MOVING
    }

    fun finishRetiring(id: Int, generation: Long): Boolean {
        if (retiring[id]?.generation != generation) return false
        retiring.remove(id)
        publish(ownerAlpha)
        return true
    }

    fun accepts(ownerId: Int, generation: Long): Boolean =
        this.ownerId == ownerId && this.generation == generation && phase != TakeoverPhase.NATIVE

    /** Settled expanded geometry stays put when Folme stops emitting frames. */
    fun retainsExpandedGeometry(): Boolean = phase == TakeoverPhase.EXPANDED

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
     * One incoming expand waits while the current owner collapses, or while an
     * earlier candidate is already waiting. See [admitsNativeReplacement].
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
        publish(1f)
        if (disableRequested) allowsNewExpansion = false
    }

    private fun publish(alpha: Float) {
        ownerAlpha = if (phase == TakeoverPhase.NATIVE) 1f else alpha
        statusBarAlpha = retiring.values.fold(ownerAlpha) { lowest, entry -> minOf(lowest, entry.alpha) }
    }
}
