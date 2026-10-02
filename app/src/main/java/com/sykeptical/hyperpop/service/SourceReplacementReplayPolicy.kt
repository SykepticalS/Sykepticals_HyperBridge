package com.sykeptical.hyperpop.service

/**
 * Keeps source heads-up suppression attached when Xiaomi snapshots the same notification more
 * than once. The ingress hook deliberately clears the marker before every fresh processing pass,
 * but a shade-replay dedupe must restore it when the identical source was already replaced.
 */
object SourceReplacementReplayPolicy {
    fun shouldRestoreSuppression(
        replaced: ShadeEntryIdentity?,
        incoming: ShadeEntryIdentity,
    ): Boolean = replaced != null &&
        replaced.visibleHash == incoming.visibleHash &&
        replaced.callLifecycleHash == incoming.callLifecycleHash
}
