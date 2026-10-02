package com.sykeptical.hyperpop.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusShadeBackgroundPolicyTest {
    @Test
    fun focusNotificationUsesRegularRowSelector() {
        assertEquals(true, FocusShadeBackgroundPolicy.shouldUseRegularRowSelector(true))
    }

    @Test
    fun ordinaryNotificationIsNotModified() {
        assertEquals(false, FocusShadeBackgroundPolicy.shouldUseRegularRowSelector(false))
    }
}
