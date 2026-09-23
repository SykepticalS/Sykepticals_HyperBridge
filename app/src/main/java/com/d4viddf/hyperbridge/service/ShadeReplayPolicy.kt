package com.d4viddf.hyperbridge.service

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
        if (previous.visibleHash == incoming.visibleHash) return true
        if (!bulkReplayActive) return false
        // A shade rebuild can rewrite extras while keeping the original post time.
        // An app update during that window advances post time and must still present.
        return incoming.postTime <= previous.postTime
    }
}
