package com.sykeptical.hyperpop.service.animation.expanded

/**
 * How the compact-island touch region should change while a sibling is hidden.
 *
 * Xiaomi records big/small press flags from live island rects, then expands
 * whichever handler owns that press. The window region only decides whether
 * the island window receives the event. This policy says which compact
 * contributions belong to the hidden sibling.
 */
enum class YieldedCompactTouch {
    /** Leave Xiaomi's small/big region unchanged. */
    KEEP,

    /** The region exists only for the hidden sibling. */
    DROP_ALL,

    /** A visible big island shares the region with the hidden circle. */
    DROP_SMALL,

    /** A visible circle shares the region with the hidden big island. */
    DROP_BIG,
}

object YieldedCompactTouchPolicy {
    fun decide(
        blockTouch: Boolean,
        hasBig: Boolean,
        bigYielded: Boolean,
        hasSmall: Boolean,
        smallYielded: Boolean,
    ): YieldedCompactTouch {
        if (!blockTouch || (!bigYielded && !smallYielded)) return YieldedCompactTouch.KEEP
        val visibleBig = hasBig && !bigYielded
        val visibleSmall = hasSmall && !smallYielded
        return when {
            !visibleBig && !visibleSmall -> YieldedCompactTouch.DROP_ALL
            bigYielded && visibleSmall -> YieldedCompactTouch.DROP_BIG
            smallYielded && visibleBig -> YieldedCompactTouch.DROP_SMALL
            else -> YieldedCompactTouch.DROP_ALL
        }
    }
}
