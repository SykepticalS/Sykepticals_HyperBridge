package com.sykeptical.hyperpop.service.animation.fingerprint

import kotlin.math.PI
import kotlin.math.abs

/**
 * One step of the Folme spring Xiaomi builds as `spring(damping, response)`.
 * `response` is the natural period in seconds. [stepInto] writes into the
 * caller-supplied array so a frame loop can reuse it.
 */
object IslandSpring {
    const val SHOW_DAMPING = 0.95f
    const val SHOW_RESPONSE = 0.35f
    const val CHANGE_DAMPING = 0.82f
    const val CHANGE_RESPONSE = 0.4f
    const val APPEAR_DAMPING = 0.7f
    const val APPEAR_RESPONSE = 0.5f
    const val HIDDEN_DAMPING = 1.0f
    const val HIDDEN_RESPONSE = 0.2f

    fun step(
        damping: Float,
        response: Float,
        value: Float,
        velocity: Float,
        target: Float,
        dtSec: Float,
    ): FloatArray {
        val out = stepInto(damping, response, value, velocity, target, dtSec, FloatArray(2))
        return out
    }

    fun stepInto(
        damping: Float,
        response: Float,
        value: Float,
        velocity: Float,
        target: Float,
        dtSec: Float,
        out: FloatArray,
    ): FloatArray {
        if (dtSec <= 0f || response <= 0f) {
            out[0] = value
            out[1] = velocity
            return out
        }
        val omega = (2.0 * PI / response).toFloat()
        val delta = value - target
        val accel = -omega * omega * delta - 2f * damping * omega * velocity
        val nextVelocity = velocity + accel * dtSec
        out[0] = value + nextVelocity * dtSec
        out[1] = nextVelocity
        return out
    }

    fun settled(value: Float, velocity: Float, target: Float, epsilon: Float = 0.35f): Boolean =
        abs(value - target) <= epsilon && abs(velocity) <= epsilon
}
