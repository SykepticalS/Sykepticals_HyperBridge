package com.sykeptical.hyperpop.service

/**
 * Identity of a notification already sitting in the shade.
 *
 * [postTime] is recorded so callers can tell a later app update apart from the
 * original, but it is not part of "nothing visible changed". HyperOS rewrites
 * post time while rebuilding the shade.
 */
data class ShadeEntryIdentity(
    val visibleHash: Int,
    val postTime: Long,
    /** CallStyle state that can change while all rendered notification text stays identical. */
    val callLifecycleHash: Int? = null,
)

/**
 * Clear-recents rebuilds every shade entry through the same path as a new post.
 * Entries we have already seen must not become islands again. A notification that
 * was not in the shade still presents, including one that arrives during the rebuild.
 */
object ShadeReplayPolicy {
    fun shouldIgnore(
        previous: ShadeEntryIdentity?,
        incoming: ShadeEntryIdentity,
        bulkReplayActive: Boolean,
    ): Boolean {
        if (previous == null) return false
        if (previous.callLifecycleHash != incoming.callLifecycleHash) {
            // Answer/connect updates are frequently delivered while the screen is off without a
            // visible text change. They must reach the call tracker. During a bulk shade rebuild,
            // retain the existing post-time guard so a lock-screen privacy rewrite is not treated
            // as a fresh lifecycle event.
            return bulkReplayActive && incoming.postTime <= previous.postTime
        }
        if (previous.visibleHash == incoming.visibleHash) return true
        if (!bulkReplayActive) return false
        // A shade rebuild can rewrite extras while keeping the original post time.
        // An app update during that window advances post time and must still present.
        return incoming.postTime <= previous.postTime
    }
}
