package com.d4viddf.hyperbridge.service.animation

/** Pure policy for selecting the app-exit target without disturbing Xiaomi's island lifecycle. */
object BetterAnimationsPolicy {
    fun shouldUseCenteredExit(
        enabled: Boolean,
        freeform: Boolean,
        interrupted: Boolean,
        hasClosingIsland: Boolean,
        activeIslandCount: Int,
    ): Boolean = enabled &&
        !freeform &&
        !interrupted &&
        hasClosingIsland &&
        activeIslandCount == 0
}
