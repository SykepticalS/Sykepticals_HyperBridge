package com.sykeptical.hyperpop.service.animation.fingerprint

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IslandSpringTest {
    @Test
    fun showAndAppearSpringsSettleOnTheTarget() {
        settle(IslandSpring.SHOW_DAMPING, IslandSpring.SHOW_RESPONSE)
        settle(IslandSpring.APPEAR_DAMPING, IslandSpring.APPEAR_RESPONSE)
        settle(IslandSpring.HIDDEN_DAMPING, IslandSpring.HIDDEN_RESPONSE)
        settle(IslandSpring.CHANGE_DAMPING, IslandSpring.CHANGE_RESPONSE)
    }

    private fun settle(damping: Float, response: Float) {
        var value = 0f
        var velocity = 0f
        val out = FloatArray(2)
        val target = 100f
        repeat(400) {
            IslandSpring.stepInto(damping, response, value, velocity, target, 0.008f, out)
            value = out[0]
            velocity = out[1]
        }
        assertTrue(IslandSpring.settled(value, velocity, target, epsilon = 0.5f))
        assertTrue(abs(value - target) < 0.5f)
    }
}
