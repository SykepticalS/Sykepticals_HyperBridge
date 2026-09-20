package com.d4viddf.hyperbridge.xposed.runtime

data class SuppressionSignals(
    val engineHealthy: Boolean,
    val fullScreenIntent: Boolean,
    val packageEnabled: Boolean,
    val semanticType: String?,
    val enabledTypes: Set<String>,
)

object SuppressionEligibility {
    fun shouldSuppress(signals: SuppressionSignals): Boolean =
        signals.engineHealthy && !signals.fullScreenIntent && signals.packageEnabled &&
            signals.semanticType != null && signals.semanticType in signals.enabledTypes
}
