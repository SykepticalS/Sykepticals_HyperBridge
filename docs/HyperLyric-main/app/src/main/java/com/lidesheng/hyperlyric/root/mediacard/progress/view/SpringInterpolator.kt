/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Adapted from XiaomiHelper.
 * Copyright (C) 2026 HowieHChen
 */

package com.lidesheng.hyperlyric.root.mediacard.progress.view

import android.view.animation.Interpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class SpringInterpolator @JvmOverloads constructor(
    private val damping: Float = 0.85f,
    private val response: Float = 0.3f,
    private val mass: Float = 1.0f,
    private val acceleration: Float = 0.0f
) : Interpolator {
    var duration: Long = 1000L
        private set
    private var g = 0.0
    private var inputScale = 1.0f
    private var omega = 0.0
    private var p = 0.0
    private var q = 0.0
    private lateinit var solution: SpringSolution
    private var velocity = 0.0f
    private var xStar = 0.0
    private var zeta = 0.0

    init {
        updateParameters()
    }

    override fun getInterpolation(fraction: Float): Float {
        if (fraction == 1.0f) return 1.0f
        val scaledFraction = fraction * inputScale
        val value = solution.x(scaledFraction).toFloat()
        velocity = solution.dX(scaledFraction).toFloat()
        return value
    }

    private fun updateParameters() {
        val dampingRatio = damping.toDouble()
        zeta = dampingRatio
        val angularFrequency = 6.283185307179586 / response
        omega = angularFrequency
        val friction = (((dampingRatio * 2.0) * angularFrequency) * mass) / mass
        p = friction
        val tension = ((angularFrequency * angularFrequency) * mass) / mass
        q = tension
        val accelerationValue = acceleration.toDouble()
        g = accelerationValue
        val equilibrium = ((-accelerationValue) / tension) + 1.0
        xStar = equilibrium
        val discriminant = (friction * friction) - (tension * 4.0)
        val initialOffset = -equilibrium
        solution = when {
            discriminant > 0.0 -> OverDampingSolution(
                discriminant,
                initialOffset,
                friction,
                velocity.toDouble(),
                equilibrium
            )

            discriminant == 0.0 -> CriticalDampingSolution(
                initialOffset,
                friction,
                velocity.toDouble(),
                equilibrium
            )

            else -> UnderDampingSolution(
                discriminant,
                initialOffset,
                friction,
                velocity.toDouble(),
                equilibrium
            )
        }
        val solvedDuration = (solveDuration(discriminant) * 1000.0).toLong()
        duration = solvedDuration
        inputScale = solvedDuration.toFloat() / 1000.0f
    }

    private fun solveDuration(discriminant: Double): Double {
        var targetEnergy: Double
        var position = 0.0
        val threshold = if (discriminant >= 0.0) 0.001 else 1.0E-4
        if (g == 0.0) {
            var time = 0.0f
            while (abs(position - 1.0) > threshold) {
                time += 0.001f
                position = solution.x(time)
                val speed = solution.dX(time)
                if (abs(position - 1.0) <= threshold && speed <= 5.0E-4) break
            }
            return time.toDouble()
        }

        val initialEnergy = solution.solve(0.0, q, g, xStar)
        val equilibriumEnergy = q * xStar * xStar
        val tolerance = (initialEnergy - equilibriumEnergy) * threshold
        var step = 1.0
        var upper = 1.0
        var energy = solution.solve(upper, q, g, xStar)
        var lower = 0.0
        while (true) {
            targetEnergy = equilibriumEnergy + tolerance
            if (energy <= targetEnergy) break
            val next = upper + step
            lower = upper
            step = 1.0
            upper = next
            energy = solution.solve(next, q, g, xStar)
        }
        do {
            val midpoint = (lower + upper) / 2.0
            if (solution.solve(midpoint, q, g, xStar) > targetEnergy) {
                lower = midpoint
            } else {
                upper = midpoint
            }
        } while (upper - lower >= threshold)
        return upper
    }

    internal abstract class SpringSolution {
        abstract fun dX(fraction: Float): Double
        abstract fun x(fraction: Float): Double

        fun solve(time: Double, tension: Double, acceleration: Double, equilibrium: Double): Double {
            val fraction = time.toFloat()
            val x = x(fraction)
            val dx = dX(fraction)
            return (tension * x * x) + (dx * dx) -
                (acceleration * 2.0 * (x - equilibrium))
        }
    }

    internal inner class CriticalDampingSolution(
        initialOffset: Double,
        friction: Double,
        initialVelocity: Double,
        private val equilibrium: Double
    ) : SpringSolution() {
        private val r = -friction / 2.0
        private val c1 = initialOffset
        private val c2 = initialVelocity - (initialOffset * r)

        override fun x(fraction: Float): Double {
            val time = fraction.toDouble()
            return ((c1 + (c2 * time)) * exp(r * time)) + equilibrium
        }

        override fun dX(fraction: Float): Double {
            val time = fraction.toDouble()
            return ((c1 * r) + (c2 * ((r * time) + 1.0))) * exp(r * time)
        }
    }

    internal inner class OverDampingSolution(
        discriminant: Double,
        initialOffset: Double,
        friction: Double,
        initialVelocity: Double,
        private val equilibrium: Double
    ) : SpringSolution() {
        private val root = sqrt(discriminant)
        private val r1 = (root - friction) / 2.0
        private val r2 = ((-root) - friction) / 2.0
        private val c1 = (initialVelocity - (initialOffset * r2)) / root
        private val c2 = (-(initialVelocity - (r1 * initialOffset))) / root

        override fun x(fraction: Float): Double {
            val time = fraction.toDouble()
            return (c1 * exp(r1 * time)) + (c2 * exp(r2 * time)) + equilibrium
        }

        override fun dX(fraction: Float): Double {
            val time = fraction.toDouble()
            return (c1 * r1 * exp(r1 * time)) + (c2 * r2 * exp(r2 * time))
        }
    }

    internal inner class UnderDampingSolution(
        discriminant: Double,
        initialOffset: Double,
        friction: Double,
        initialVelocity: Double,
        private val equilibrium: Double
    ) : SpringSolution() {
        private val alpha = -friction / 2.0
        private val beta = sqrt(-discriminant) / 2.0
        private val c1 = initialOffset
        private val c2 = (initialVelocity - (initialOffset * alpha)) / beta

        override fun x(fraction: Float): Double {
            val time = fraction.toDouble()
            return exp(alpha * time) *
                ((c1 * cos(beta * time)) + (c2 * sin(beta * time))) + equilibrium
        }

        override fun dX(fraction: Float): Double {
            val time = fraction.toDouble()
            val first = (c1 * alpha + c2 * beta) * cos(beta * time)
            val second = (c2 * alpha - c1 * beta) * sin(beta * time)
            return exp(alpha * time) * (first + second)
        }
    }
}
