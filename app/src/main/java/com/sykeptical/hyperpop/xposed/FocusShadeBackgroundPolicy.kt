package com.sykeptical.hyperpop.xposed

/** Isolates the decision to enter Xiaomi's ordinary row-background branch for Focus rows. */
internal object FocusShadeBackgroundPolicy {
    fun shouldUseRegularRowSelector(isFocusNotification: Boolean): Boolean = isFocusNotification
}
