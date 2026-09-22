/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.TextPaint
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel
import kotlin.math.abs

internal class CountdownDotsRenderer : LineRenderer {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var backgroundColors = intArrayOf(Color.argb(128, 255, 255, 255))
    private var highlightColors = intArrayOf(Color.WHITE)
    private val progressAnimator = ProgressAnimator()
    private var textSize = 0f
    private var lastPosition = Long.MIN_VALUE
    private var lastPlaybackSpeed = 1f

    private var backgroundShader: Shader? = null
    private var highlightShader: Shader? = null
    private var shaderStart = Float.NaN
    private var shaderEnd = Float.NaN
    private var shaderBackgroundHash = 0
    private var shaderHighlightHash = 0

    var isGradientEnabled = true
        set(value) {
            if (field == value) return
            field = value
            clearShaders()
        }

    override var centerIfPossible = false
    override var rightIfPossible = false

    override val isPlaying: Boolean get() = progressAnimator.isAnimating
    override val isFinished: Boolean get() = progressAnimator.currentWidth >= 1f
    override val isStarted: Boolean get() = progressAnimator.currentWidth > 0f

    fun setTextSize(size: Float) {
        if (textSize == size) return
        textSize = size
        clearShaders()
    }

    fun setColors(background: IntArray, highlight: IntArray) {
        if (background.isNotEmpty()) backgroundColors = background.copyOf()
        if (highlight.isNotEmpty()) highlightColors = highlight.copyOf()
        clearShaders()
    }

    fun contentWidth(): Float {
        val radius = baseRadius()
        val maxRadius = radius * MAX_SCALE
        val gap = dotGap()
        return maxRadius * 2f * DOT_COUNT + gap * (DOT_COUNT - 1)
    }

    fun syncFrom(other: CountdownDotsRenderer) {
        progressAnimator.syncFrom(other.progressAnimator)
        lastPosition = other.lastPosition
        lastPlaybackSpeed = other.lastPlaybackSpeed
    }

    fun freeze() {
        progressAnimator.stopAtCurrent()
    }

    override fun seek(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        progressAnimator.jumpTo(progressAt(posMs, model))
        lastPosition = posMs
    }

    override fun update(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int,
        playbackSpeed: Float
    ) {
        if (lastPosition != Long.MIN_VALUE && posMs < lastPosition) {
            seek(model, state, posMs, viewWidth, viewHeight)
            return
        }

        val exactProgress = progressAt(posMs, model)
        val speed = normalizePlaybackSpeed(playbackSpeed)
        if (exactProgress >= 1f) {
            progressAnimator.jumpTo(1f)
            lastPosition = posMs
            lastPlaybackSpeed = speed
            return
        }
        if ((progressAnimator.currentWidth == 0f && exactProgress > 0f) ||
            abs(progressAnimator.currentWidth - exactProgress) >= REANCHOR_THRESHOLD
        ) {
            progressAnimator.jumpTo(exactProgress)
        }

        val fadeStart = FADE_START_PROGRESS
        val target = if (fadeStart > 0f && exactProgress < fadeStart) {
            val dotProgress = exactProgress / fadeStart
            val segment = (dotProgress * DOT_COUNT).toInt().coerceIn(0, DOT_COUNT - 1)
            fadeStart * (segment + 1).toFloat() / DOT_COUNT
        } else {
            1f
        }
        if (target != progressAnimator.targetWidth || !progressAnimator.isAnimating ||
            speed != lastPlaybackSpeed
        ) {
            progressAnimator.animateTo(
                target,
                remainingPhaseDuration(posMs, model, target),
                speed
            )
        }
        lastPosition = posMs
        lastPlaybackSpeed = speed
    }

    override fun step(
        deltaNanos: Long,
        model: LyricModel,
        state: LineState,
        viewWidth: Int
    ): Boolean = progressAnimator.step(deltaNanos)

    override fun draw(
        canvas: Canvas,
        model: LyricModel,
        paint: TextPaint,
        state: LineState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        if (textSize <= 0f || viewWidth <= 0 || viewHeight <= 0) return

        val radius = baseRadius()
        val gap = dotGap()
        val maxWidth = contentWidth()
        val shaderStartX = alignedStart(maxWidth, model, viewWidth)
        val centerY = viewHeight / 2f

        updateShaders(shaderStartX, shaderStartX + maxWidth)

        val progress = progressAnimator.currentWidth
        val fadeStart = FADE_START_PROGRESS
        val lightingProgress = if (fadeStart > 0f) {
            (progress / fadeStart).coerceIn(0f, 1f)
        } else {
            1f
        }
        val fadeProgress = if (fadeStart < 1f && progress > fadeStart) {
            ((progress - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)
        } else {
            0f
        }
        val exitAlpha = 1f - smoothStep(fadeProgress)
        val backgroundBaseAlpha = backgroundPaint.alpha
        val highlightBaseAlpha = highlightPaint.alpha
        backgroundPaint.alpha = (backgroundBaseAlpha * exitAlpha).toInt()
        var cursorX = alignedStart(
            dotsWidth(radius, gap, lightingProgress),
            model,
            viewWidth
        )

        repeat(DOT_COUNT) { index ->
            val localProgress = dotProgress(lightingProgress, index)
            val easedProgress = smoothStep(localProgress)
            val currentRadius = radius * (1f + SCALE_AMOUNT * easedProgress)
            val highlightAlpha = (highlightBaseAlpha * easedProgress * exitAlpha).toInt()
            val centerX = cursorX + currentRadius

            canvas.drawCircle(centerX, centerY, currentRadius, backgroundPaint)
            if (highlightAlpha > 0) {
                highlightPaint.alpha = highlightAlpha
                canvas.drawCircle(centerX, centerY, currentRadius, highlightPaint)
            }
            cursorX += currentRadius * 2f + gap
        }
        backgroundPaint.alpha = backgroundBaseAlpha
        highlightPaint.alpha = highlightBaseAlpha
    }

    override fun reset(state: LineState) {
        progressAnimator.reset()
        lastPosition = Long.MIN_VALUE
        lastPlaybackSpeed = 1f
        state.reset()
    }

    private fun progressAt(posMs: Long, model: LyricModel): Float {
        val span = durationOf(model)
        if (span <= 0L) return 0f
        return ((posMs - model.begin).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    private fun remainingPhaseDuration(
        posMs: Long,
        model: LyricModel,
        targetProgress: Float
    ): Long {
        val span = durationOf(model)
        if (span <= 0L) return 0L
        val phaseEnd = model.begin + (span * targetProgress).toLong()
        return (phaseEnd - posMs).coerceAtLeast(0L)
    }

    private fun durationOf(model: LyricModel): Long =
        (model.end - model.begin).takeIf { it > 0L } ?: model.duration

    private fun alignedStart(width: Float, model: LyricModel, viewWidth: Int): Float = when {
        model.isAlignedRight || rightIfPossible -> viewWidth - width
        centerIfPossible -> (viewWidth - width) / 2f
        else -> 0f
    }.coerceAtLeast(0f)

    private fun dotsWidth(radius: Float, gap: Float, lightingProgress: Float): Float {
        var width = gap * (DOT_COUNT - 1)
        repeat(DOT_COUNT) { index ->
            val easedProgress = smoothStep(dotProgress(lightingProgress, index))
            width += radius * (1f + SCALE_AMOUNT * easedProgress) * 2f
        }
        return width
    }

    private fun dotProgress(lightingProgress: Float, index: Int): Float =
        (lightingProgress * DOT_COUNT - index).coerceIn(0f, 1f)

    private fun baseRadius(): Float = textSize * RADIUS_TEXT_SIZE_FACTOR

    private fun dotGap(): Float = textSize * DOT_GAP_TEXT_SIZE_FACTOR

    private fun updateShaders(start: Float, end: Float) {
        val backgroundHash = backgroundColors.contentHashCode()
        val highlightHash = highlightColors.contentHashCode()
        val geometryChanged = shaderStart != start || shaderEnd != end
        if (
            geometryChanged ||
            shaderBackgroundHash != backgroundHash ||
            shaderHighlightHash != highlightHash
        ) {
            shaderStart = start
            shaderEnd = end
            shaderBackgroundHash = backgroundHash
            shaderHighlightHash = highlightHash
            backgroundShader = createShader(backgroundColors, start, end)
            highlightShader = createShader(highlightColors, start, end)
        }

        backgroundPaint.color = backgroundColors.firstOrNull() ?: Color.GRAY
        highlightPaint.color = highlightColors.firstOrNull() ?: Color.WHITE
        backgroundPaint.shader = backgroundShader
        highlightPaint.shader = highlightShader
    }

    private fun createShader(colors: IntArray, start: Float, end: Float): Shader? {
        if (!isGradientEnabled || colors.size < 2 || end <= start) return null
        val positions = FloatArray(colors.size) { index ->
            index.toFloat() / (colors.size - 1)
        }
        return LinearGradient(
            start,
            0f,
            end,
            0f,
            colors,
            positions,
            Shader.TileMode.CLAMP
        )
    }

    private fun clearShaders() {
        backgroundShader = null
        highlightShader = null
        shaderStart = Float.NaN
        shaderEnd = Float.NaN
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

    private companion object {
        const val DOT_COUNT = 3
        const val RADIUS_TEXT_SIZE_FACTOR = 0.25f
        const val DOT_GAP_TEXT_SIZE_FACTOR = 0.34f
        const val SCALE_AMOUNT = 0.4f
        const val MAX_SCALE = 1f + SCALE_AMOUNT
        const val FADE_START_PROGRESS = 3f / 5f
        const val REANCHOR_THRESHOLD = 0.035f
    }
}
