package com.sykeptical.hyperpop.xposed

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusShadeBackgroundPolicyTest {
    @Test
    fun focusRowUsesTheRegularBackgroundOnlyWhenTheTweakIsOn() {
        assertEquals(true, FocusShadeBackgroundPolicy.shouldUseRegularRowSelector(true, true))
        assertEquals(false, FocusShadeBackgroundPolicy.shouldUseRegularRowSelector(false, true))
    }

    @Test
    fun ordinaryNotificationIsNotModified() {
        assertEquals(false, FocusShadeBackgroundPolicy.shouldUseRegularRowSelector(true, false))
    }

    @Test
    fun mediaCardUsesTheSameToggle() {
        assertEquals(true, FocusShadeBackgroundPolicy.shouldRestyleMediaCard(true))
        assertEquals(false, FocusShadeBackgroundPolicy.shouldRestyleMediaCard(false))
    }

    @Test
    fun mediaCardUsesTheOrdinaryNotificationDrawable() {
        assertEquals(
            "notification_item_bg",
            FocusShadeBackgroundPolicy.ordinaryDrawableName(blurOpened = false, fullAod = false),
        )
        assertEquals(
            "notification_fullaod_item_bg",
            FocusShadeBackgroundPolicy.ordinaryDrawableName(blurOpened = false, fullAod = true),
        )
        assertEquals(
            "notification_heads_up_transparent_bg",
            FocusShadeBackgroundPolicy.ordinaryDrawableName(blurOpened = true, fullAod = false),
        )
    }

    @Test
    fun mediaBlurUsesTheOrdinaryTwoStopBlend() {
        assertEquals(null, FocusShadeBackgroundPolicy.ordinaryBlendResources(false, false))
        val shade = FocusShadeBackgroundPolicy.ordinaryBlendResources(blurOpened = true, keyguard = false)
        assertEquals("notification_element_blend_shade_color_1", shade?.color1)
        assertEquals("notification_element_blend_shade_mode_2", shade?.mode2)
        val keyguard = FocusShadeBackgroundPolicy.ordinaryBlendResources(blurOpened = true, keyguard = true)
        assertEquals("notification_element_blend_keyguard_color_1", keyguard?.color1)
        assertEquals("notification_element_blend_keyguard_mode_2", keyguard?.mode2)
    }
}
