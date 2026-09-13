package com.d4viddf.hyperbridge.service.popup

import android.app.NotificationManager
import com.d4viddf.hyperbridge.service.SemanticSignature

enum class ChannelSemanticState {
    UNKNOWN,
    TARGET_ONLY,
    NON_TARGET_ONLY,
    MIXED
}

object PopupSuppressionPolicy {
    fun semanticState(
        observed: Set<SemanticSignature>,
        effectiveTypes: Set<String>
    ): ChannelSemanticState {
        if (observed.isEmpty()) return ChannelSemanticState.UNKNOWN
        val targetCount = observed.count { it.isReplacedBy(effectiveTypes) }
        return when (targetCount) {
            0 -> ChannelSemanticState.NON_TARGET_ONLY
            observed.size -> ChannelSemanticState.TARGET_ONLY
            else -> ChannelSemanticState.MIXED
        }
    }

    fun appliedImportance(originalImportance: Int): Int? =
        NotificationManager.IMPORTANCE_DEFAULT.takeIf {
            originalImportance > NotificationManager.IMPORTANCE_DEFAULT
        }

    fun canSafelyRestore(currentImportance: Int, appliedImportance: Int): Boolean =
        currentImportance == appliedImportance
}
