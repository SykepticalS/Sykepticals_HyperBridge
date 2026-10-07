package com.sykeptical.hyperpop.service.animation.fingerprint

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Horizontal failure shake. Three half-swings in [DURATION_MS], decaying so
 * the plate is back at 0 at the end. The glyph total travel is 1.5× the plate.
 * The plate amplitude is clamped by [FingerprintIslandGeometry] before this runs.
 */
object IslandShake {
    const val DURATION_MS = 450L
    const val GLYPH_FACTOR = 1.5f
    private const val HALF_SWINGS = 3.0
    private const val DECAY = 3.0

    data class Offsets(val platePx: Float, val glyphExtraPx: Float) {
        val glyphPx: Float get() = platePx + glyphExtraPx
    }

    fun offsets(elapsedMs: Long, amplitudePx: Float, animationsEnabled: Boolean): Offsets {
        if (!animationsEnabled || amplitudePx <= 0f || elapsedMs < 0L || elapsedMs >= DURATION_MS) {
            return Offsets(0f, 0f)
        }
        val t = elapsedMs / DURATION_MS.toFloat()
        val decay = exp(-DECAY * t).toFloat()
        val plate = amplitudePx * decay * sin(HALF_SWINGS * PI * t).toFloat()
        val extra = plate * (GLYPH_FACTOR - 1f)
        return Offsets(plate, extra)
    }
}
