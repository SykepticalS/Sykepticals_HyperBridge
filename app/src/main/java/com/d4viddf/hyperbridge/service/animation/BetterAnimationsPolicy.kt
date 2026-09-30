package com.d4viddf.hyperbridge.service.animation

data class AppExitTargetBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val isValid: Boolean
        get() = right > left && bottom > top
}

enum class AppExitTargetSlot {
    PRIMARY,
    SECONDARY,
    CUTOUT,
    UNKNOWN,
}

/** Pure policy for selecting the app-exit target without disturbing Xiaomi's island lifecycle. */
object BetterAnimationsPolicy {
    fun classifyNativeTarget(
        native: AppExitTargetBounds?,
        cutout: AppExitTargetBounds,
    ): AppExitTargetSlot {
        if (native == null || !native.isValid || !cutout.isValid) return AppExitTargetSlot.UNKNOWN
        if (native == cutout) return AppExitTargetSlot.CUTOUT

        val overlapsCutoutHorizontally =
            native.left < cutout.right && native.right > cutout.left
        return if (overlapsCutoutHorizontally) {
            AppExitTargetSlot.PRIMARY
        } else {
            AppExitTargetSlot.SECONDARY
        }
    }

    fun shouldUseCenteredExit(
        enabled: Boolean,
        freeform: Boolean,
        interrupted: Boolean,
        hasClosingIsland: Boolean,
        activeIslandCount: Int,
        hasCurrentBigIsland: Boolean,
        nativeTargetSlot: AppExitTargetSlot,
    ): Boolean {
        if (!enabled || freeform || interrupted || !hasClosingIsland) return false
        if (activeIslandCount == 0) return true

        return activeIslandCount >= 1 &&
            hasCurrentBigIsland &&
            nativeTargetSlot == AppExitTargetSlot.PRIMARY
    }
}
