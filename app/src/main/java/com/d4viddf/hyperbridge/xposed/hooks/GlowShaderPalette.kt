package com.d4viddf.hyperbridge.xposed.hooks

import android.graphics.Color

/**
 * Xiaomi glow-shader palette rebuild adapted from HyperIsland's IslandOuterGlowHook (MIT).
 */
internal object GlowShaderPalette {
    const val LIGHT_COLOR_ARRAY_SIZE = 33

    fun rebuild(base: FloatArray, argb: Int, singleColor: Boolean): FloatArray {
        return if (singleColor) rebuildSingleColorArray(base, argb) else rebuildLightShaderArray(base, argb)
    }

    fun rgb(color: Int): FloatArray = floatArrayOf(
        Color.red(color) / 255f,
        Color.green(color) / 255f,
        Color.blue(color) / 255f,
    )

    private fun rebuildLightShaderArray(base: FloatArray, argb: Int): FloatArray {
        val template = normalizeTemplatePalette(base)
        val output = FloatArray(template.size)
        val seedHsv = FloatArray(3)
        Color.colorToHSV(argb, seedHsv)
        val anchorHsv = extractStopHsv(template, 2)
        val ranges = calcTemplateSvMinMax(template)
        val stopCount = template.size / 3
        for (i in 0 until stopCount) {
            val tplHsv = extractStopHsv(template, i)
            val hue = wrapHue(seedHsv[0] + shortestHueDelta(anchorHsv[0], tplHsv[0]) * 0.45f)
            val sat = clamp01(
                seedHsv[1] *
                    (0.72f + 0.38f * normalize01(tplHsv[1], ranges[0], ranges[1])) *
                    (tplHsv[1] / maxOf(anchorHsv[1], 0.01f)),
            )
            val value = clamp01(
                seedHsv[2] *
                    (0.62f + 0.48f * normalize01(tplHsv[2], ranges[2], ranges[3])) *
                    (tplHsv[2] / maxOf(anchorHsv[2], 0.01f)),
            )
            val color = Color.HSVToColor(Color.alpha(argb), floatArrayOf(hue, sat, value))
            val baseIndex = i * 3
            output[baseIndex] = Color.red(color) / 255f
            output[baseIndex + 1] = Color.green(color) / 255f
            output[baseIndex + 2] = Color.blue(color) / 255f
        }
        return output
    }

    private fun rebuildSingleColorArray(base: FloatArray, argb: Int): FloatArray {
        val template = normalizeTemplatePalette(base)
        val output = FloatArray(template.size)
        val r = Color.red(argb) / 255f
        val g = Color.green(argb) / 255f
        val b = Color.blue(argb) / 255f
        val stopCount = template.size / 3
        for (i in 0 until stopCount) {
            val idx = i * 3
            output[idx] = r
            output[idx + 1] = g
            output[idx + 2] = b
        }
        return output
    }

    private fun normalizeTemplatePalette(base: FloatArray): FloatArray {
        if (base.size >= LIGHT_COLOR_ARRAY_SIZE) return base.copyOf(LIGHT_COLOR_ARRAY_SIZE)
        return floatArrayOf(
            0.502f, 0.525f, 1.0f,
            1.0f, 0.827f, 0.702f,
            1.0f, 0.525f, 0.208f,
            0.518f, 0.494f, 1.0f,
            0.071f, 0.412f, 0.949f,
            0.502f, 0.525f, 1.0f,
            1.0f, 0.827f, 0.702f,
            1.0f, 0.525f, 0.208f,
            0.518f, 0.494f, 1.0f,
            0.071f, 0.412f, 0.949f,
            1.0f, 0.525f, 0.208f,
        )
    }

    private fun extractStopHsv(rgb33: FloatArray, stopIndex: Int): FloatArray {
        val idx = stopIndex * 3
        val hsv = FloatArray(3)
        Color.RGBToHSV(
            (clamp01(rgb33[idx]) * 255f).toInt(),
            (clamp01(rgb33[idx + 1]) * 255f).toInt(),
            (clamp01(rgb33[idx + 2]) * 255f).toInt(),
            hsv,
        )
        return hsv
    }

    private fun calcTemplateSvMinMax(rgb33: FloatArray): FloatArray {
        var minS = 1f
        var maxS = 0f
        var minV = 1f
        var maxV = 0f
        val stopCount = rgb33.size / 3
        for (i in 0 until stopCount) {
            val hsv = extractStopHsv(rgb33, i)
            minS = minOf(minS, hsv[1])
            maxS = maxOf(maxS, hsv[1])
            minV = minOf(minV, hsv[2])
            maxV = maxOf(maxV, hsv[2])
        }
        return floatArrayOf(minS, maxS, minV, maxV)
    }

    private fun normalize01(x: Float, min: Float, max: Float): Float {
        val diff = max - min
        if (diff <= 1e-6f) return 0.5f
        return clamp01((x - min) / diff)
    }

    private fun shortestHueDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private fun wrapHue(value: Float): Float {
        var hue = value % 360f
        if (hue < 0f) hue += 360f
        return hue
    }

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)
}
