package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Whether an island whose takeover was released while Xiaomi still shows it
 * expanded gets its takeover back.
 *
 * Xiaomi recomputes the expanded position through a private reset that is not
 * a collapse (cutout, insets, config, or content updates reach it), so the
 * state stays Expanded after HyperPop has restored its margins and surface.
 * Nothing else re-arms an idle island: Folme emits no frames and no setState
 * follows. The release is therefore remembered and ends only when Xiaomi
 * leaves Expanded or the takeover is armed again.
 */
object ExpandedReacquirePolicy {
    fun shouldReacquire(
        released: Boolean,
        stillExpanded: Boolean,
        hasSession: Boolean,
        blocked: Boolean,
        tempHidden: Boolean,
        enabled: Boolean,
    ): Boolean = released && stillExpanded && !hasSession && !blocked && !tempHidden && enabled

    /** The release note is dropped once the island is no longer the expanded one. */
    fun keepsRelease(stillExpanded: Boolean): Boolean = stillExpanded
}
