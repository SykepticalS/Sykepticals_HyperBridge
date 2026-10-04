package com.sykeptical.hyperpop.service.animation.expanded

import kotlin.math.abs

/**
 * When an armed takeover re-reads Xiaomi's expanded content, and whether the
 * new reading replaces the one the session was armed with.
 *
 * A takeover can be armed before the Focus template is complete: the expanded
 * view is not installed yet, or it has no measurable layout. That decision has
 * no content profile, so it is provisional. The first reading that does have a
 * profile is Xiaomi's complete native state and always replaces it. After
 * that, only a real change of offset or height replaces the decision, which
 * keeps an unchanged island from being re-laid on every update.
 */
object ExpandedDecisionRefreshPolicy {
    const val OFFSET_SLACK_PX = 2
    const val HEIGHT_SLACK_PX = 8

    /** A profile-backed session re-reads on every native size update; a provisional one until it has a profile. */
    fun shouldRefresh(tight: Boolean, provisional: Boolean): Boolean = tight || provisional

    /**
     * Whether the next expanded target must be built from a fresh reading:
     * the session never had a profile, or Xiaomi bound or installed content
     * after the session last read it.
     */
    fun needsSettle(provisional: Boolean, nativeVersion: Int, settledVersion: Int): Boolean =
        provisional || nativeVersion != settledVersion

    fun adopt(
        current: ExpandedLayoutDecision.Takeover,
        refreshed: ExpandedLayoutDecision.Takeover,
        provisional: Boolean,
    ): Boolean {
        if (provisional) return refreshed.tightLayout
        return refreshed.bodyOffsetPx > current.bodyOffsetPx + OFFSET_SLACK_PX ||
            abs(refreshed.card.height - current.card.height) > HEIGHT_SLACK_PX
    }
}
