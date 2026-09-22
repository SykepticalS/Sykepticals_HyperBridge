/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

/** Maps media-time progress to the visible highlight progress of one timed word. */
internal object WordProgressMapper {
    fun map(progress: Float, sustainStrength: Float): Float {
        val linear = progress.coerceIn(0f, 1f)
        val strength = sustainStrength.coerceIn(0f, 1f)
        if (strength <= 0f || linear <= 0f || linear >= 1f) return linear

        // Smoothly moves through 20% of the width in about 9% of the media time and through
        // 50% in about 26%, while retaining a non-zero 0.5x slope at the end of the sustain.
        val sustainCurve = linear * (2.5f - 2.5f * linear + linear * linear)
        return linear + (sustainCurve - linear) * strength
    }
}
