/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import android.graphics.Canvas
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel

/**
 * Draws a plain lyric line while using the line's own begin/end as its timeline.
 *
 * This is deliberately separate from [WordSyncRenderer]: a line-only lyric must not create
 * WordModel instances or enter the word highlight/character-motion path just to follow its
 * horizontal progress.
 */
internal class LineTimelineRenderer : LineRenderer {
    private val progressAnimator = ProgressAnimator()
    private val scrollStepper = ScrollStepper()

    override var centerIfPossible = false
    override var rightIfPossible = false

    private var lastPosition = Long.MIN_VALUE

    override val isPlaying: Boolean
        get() = progressAnimator.isAnimating
    override val isFinished: Boolean
        get() = progressAnimator.hasFinished
    override val isStarted: Boolean
        get() = progressAnimator.hasStarted

    override fun step(
        deltaNanos: Long,
        model: LyricModel,
        state: LineState,
        viewWidth: Int
    ): Boolean {
        val changed = progressAnimator.step(deltaNanos)
        if (changed) {
            reconcileLifecycleFlags(model)
            updateScrollState(model, state, viewWidth)
        }
        return changed
    }

    override fun draw(
        canvas: Canvas,
        model: LyricModel,
        paint: TextPaint,
        state: LineState,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val width = viewWidth.toFloat()
        val offset = if (model.width <= width) {
            when {
                model.isAlignedRight || rightIfPossible -> width - model.width
                centerIfPossible -> (width - model.width) / 2f
                else -> 0f
            }
        } else {
            state.scrollOffset
        }

        val fontMetrics = paint.fontMetrics
        val baseline = (viewHeight - (fontMetrics.descent - fontMetrics.ascent)) / 2f -
                fontMetrics.ascent

        if (offset < width && offset + model.width > 0f) {
            canvas.withTranslation(x = offset) {
                drawText(model.text, 0f, baseline, paint)
            }
        }
    }

    override fun seek(
        model: LyricModel,
        state: LineState,
        posMs: Long,
        viewWidth: Int,
        viewHeight: Int
    ) {
        progressAnimator.jumpTo(targetWidth(model, posMs))
        lastPosition = posMs
        reconcileLifecycleFlags(model)
        updateScrollState(model, state, viewWidth)
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

        val duration = lineDuration(model)
        when {
            posMs <= model.begin -> progressAnimator.jumpTo(0f)
            duration <= 0L || model.width <= 0f -> progressAnimator.jumpTo(0f)
            posMs >= lineEnd(model, duration) -> progressAnimator.jumpTo(model.width)
            else -> progressAnimator.animateRange(
                rangeStart = 0f,
                rangeEnd = model.width,
                durationMs = duration,
                positionMs = (posMs - model.begin).coerceIn(0L, duration),
                playbackSpeed = playbackSpeed,
                forceAnchor = lastPosition == Long.MIN_VALUE
            )
        }

        lastPosition = posMs
        reconcileLifecycleFlags(model)
        updateScrollState(model, state, viewWidth)
    }

    override fun reset(state: LineState) {
        progressAnimator.reset()
        lastPosition = Long.MIN_VALUE
        state.reset()
    }

    fun freeze(model: LyricModel, state: LineState, viewWidth: Int) {
        progressAnimator.stopAtCurrent()
        reconcileLifecycleFlags(model)
        updateScrollState(model, state, viewWidth)
    }

    fun updateLayout(model: LyricModel, state: LineState, viewWidth: Int, viewHeight: Int) {
        if (progressAnimator.hasFinished) {
            progressAnimator.jumpTo(model.width)
        }
        reconcileLifecycleFlags(model)
        updateScrollState(model, state, viewWidth)
    }

    fun syncFrom(other: LineTimelineRenderer) {
        progressAnimator.syncFrom(other.progressAnimator)
        lastPosition = other.lastPosition
    }

    private fun targetWidth(model: LyricModel, posMs: Long): Float {
        if (model.width <= 0f) return 0f
        val duration = lineDuration(model)
        if (duration <= 0L) return 0f
        val progress = ((posMs - model.begin).toFloat() / duration.toFloat())
            .coerceIn(0f, 1f)
        return model.width * progress
    }

    private fun lineDuration(model: LyricModel): Long =
        (model.end - model.begin).takeIf { it > 0L } ?: model.duration

    private fun lineEnd(model: LyricModel, duration: Long): Long =
        model.end.takeIf { it > model.begin } ?: (model.begin + duration)

    private fun reconcileLifecycleFlags(model: LyricModel) {
        if (progressAnimator.currentWidth <= 0f) {
            progressAnimator.hasStarted = false
        } else {
            progressAnimator.hasStarted = true
        }
        if (model.width > 0f && progressAnimator.currentWidth >= model.width) {
            progressAnimator.hasFinished = true
        } else {
            progressAnimator.hasFinished = false
        }
    }

    private fun updateScrollState(model: LyricModel, state: LineState, viewWidth: Int) {
        state.scrollOffset = scrollStepper.compute(
            progressAnimator.currentWidth,
            model.width,
            viewWidth.toFloat(),
            progressAnimator.hasFinished,
            state.isScrollFinished
        )
        state.isScrollFinished = progressAnimator.hasFinished || model.width <= viewWidth
    }
}
