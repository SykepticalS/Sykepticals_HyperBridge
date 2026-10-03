package com.sykeptical.hyperpop.service.animation.expanded

/**
 * One expanded island owns the status-bar fade. Other islands stay active.
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
    var statusBarAlpha: Float = 1f
        private set
    var secondaryAlpha: Float = 1f
        private set
    var secondaryActive: Boolean = false
        private set
    var secondarySuppressed: Boolean = false
        private set
    var allowsNewExpansion: Boolean = true
        private set

    private var lastFrameMs: Long = 0L
    private var disableRequested: Boolean = false

    fun arm(ownerId: Int, nowMs: Long): Long {
        if (!allowsNewExpansion) return -1L
        generation += 1L
        this.ownerId = ownerId
        phase = TakeoverPhase.EXPANDING
        lastFrameMs = nowMs
        publish(1f)
        return generation
    }

    fun accepts(ownerId: Int, generation: Long): Boolean =
        this.ownerId == ownerId && this.generation == generation && phase != TakeoverPhase.NATIVE

    fun onSecondaryPresence(active: Boolean) {
        secondaryActive = active
        refreshSuppression()
    }

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

    fun expireIfStale(nowMs: Long): Boolean {
        val owner = ownerId ?: return false
        if (phase == TakeoverPhase.NATIVE) return false
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
        val resolved = if (phase == TakeoverPhase.NATIVE) 1f else alpha
        statusBarAlpha = resolved
        secondaryAlpha = if (secondaryActive && phase != TakeoverPhase.NATIVE) resolved else 1f
        refreshSuppression()
    }

    private fun refreshSuppression() {
        secondarySuppressed = secondaryActive && phase != TakeoverPhase.NATIVE && secondaryAlpha < 0.99f
    }
}
