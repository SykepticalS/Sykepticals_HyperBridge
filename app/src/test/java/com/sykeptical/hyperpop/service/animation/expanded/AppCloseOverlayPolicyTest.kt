package com.sykeptical.hyperpop.service.animation.expanded

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCloseOverlayPolicyTest {
    @Test
    fun visibleFakeIsHiddenWithXiaomisHandoffVisibility() {
        assertEquals(View.INVISIBLE, AppCloseOverlayPolicy.hideVisibility(View.VISIBLE))
    }

    @Test
    fun visibleFakeIsNeverGone() {
        assertNotEquals(View.GONE, AppCloseOverlayPolicy.hideVisibility(View.VISIBLE))
    }

    @Test
    fun ownedPillIslandTracksWithThePillRadius() {
        assertEquals(168f, AppCloseOverlayPolicy.trackingRadius(nativeRadius = 90f, pillRadius = 168f), 0f)
    }

    @Test
    fun nativeIslandTracksWithXiaomisRadius() {
        assertEquals(90f, AppCloseOverlayPolicy.trackingRadius(nativeRadius = 90f, pillRadius = null), 0f)
        assertEquals(90f, AppCloseOverlayPolicy.trackingRadius(nativeRadius = 90f, pillRadius = 0f), 0f)
    }

    @Test
    fun hiddenFakeIsLeftAlone() {
        assertNull(AppCloseOverlayPolicy.hideVisibility(View.INVISIBLE))
        assertNull(AppCloseOverlayPolicy.hideVisibility(View.GONE))
    }
}
