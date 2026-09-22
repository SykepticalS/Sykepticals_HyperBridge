package com.d4viddf.hyperbridge.service

/**
 * One staged auto-expand. Max cutout width applies until the expand animation has captured
 * the wide pill. After that the cutout island is content-measured, including on dismiss.
 */
enum class IslandAutoExpandPhase {
    Inactive,
    Entrance,
    Expanding,
    Measured,
}

data class IslandAutoExpandDecision(
    val phase: IslandAutoExpandPhase,
    val requestExpand: Boolean = false,
    val releaseWidth: Boolean = false,
)

object IslandAutoExpandPhaseMachine {
    /** Time from the notification arriving until the cutout island auto-expands. */
    const val EXPAND_DELAY_MS = 750L

    fun start(tagged: Boolean): IslandAutoExpandPhase =
        if (tagged) IslandAutoExpandPhase.Entrance else IslandAutoExpandPhase.Inactive

    fun forcesMaxWidth(phase: IslandAutoExpandPhase): Boolean =
        phase == IslandAutoExpandPhase.Entrance || phase == IslandAutoExpandPhase.Expanding

    /**
     * Xiaomi's `getMaxWidth()` is orientation-dependent on some phone builds and can report the
     * portrait display height while the island is laid out horizontally. Never let that value
     * move a cutout island beyond the actual content-view bounds.
     */
    fun entranceWidth(reportedMaxWidth: Int, laidOutWidth: Int): Int {
        val validMax = reportedMaxWidth.takeIf { it > 0 }
        val validLayout = laidOutWidth.takeIf { it > 0 }
        return when {
            validMax != null && validLayout != null -> minOf(validMax, validLayout)
            validLayout != null -> validLayout
            validMax != null -> validMax
            else -> 0
        }
    }

    /** Live plugin methods that play the cutout appear animation. */
    fun isCutoutAppearAnimation(methodName: String): Boolean =
        methodName == "hiddenToBigIslandAnimation" || methodName == "initToBigIslandAnimation"

    fun isCutoutBigState(stateClassName: String?): Boolean {
        val simple = simpleStateName(stateClassName)
        return simple == "BigIsland" || simple == "ShowOnceBigIsland"
    }

    fun isExpandedState(stateClassName: String?): Boolean =
        simpleStateName(stateClassName).contains("Expanded")

    private fun simpleStateName(stateClassName: String?): String =
        stateClassName
            ?.substringAfterLast('$')
            ?.substringAfterLast('.')
            .orEmpty()

    fun onAppearFinished(
        phase: IslandAutoExpandPhase,
        appearPending: Boolean,
        settledInCutout: Boolean,
    ): IslandAutoExpandDecision {
        if (phase != IslandAutoExpandPhase.Entrance || !appearPending || !settledInCutout) {
            return IslandAutoExpandDecision(phase)
        }
        return IslandAutoExpandDecision(
            phase = IslandAutoExpandPhase.Expanding,
            requestExpand = true,
        )
    }

    /**
     * An update with `miui.island.updateNoFloat` does not replay the appear animation.
     * The island is already in the cutout, so the staged expand can start.
     */
    fun onAlreadyInCutout(
        phase: IslandAutoExpandPhase,
        suppressesAppearAnimation: Boolean,
        settledInCutout: Boolean,
    ): IslandAutoExpandDecision {
        if (phase != IslandAutoExpandPhase.Entrance || !suppressesAppearAnimation || !settledInCutout) {
            return IslandAutoExpandDecision(phase)
        }
        return IslandAutoExpandDecision(
            phase = IslandAutoExpandPhase.Expanding,
            requestExpand = true,
        )
    }

    fun onExpandCaptured(phase: IslandAutoExpandPhase): IslandAutoExpandDecision {
        if (phase != IslandAutoExpandPhase.Entrance && phase != IslandAutoExpandPhase.Expanding) {
            return IslandAutoExpandDecision(phase)
        }
        return IslandAutoExpandDecision(
            phase = IslandAutoExpandPhase.Measured,
            releaseWidth = true,
        )
    }
}
