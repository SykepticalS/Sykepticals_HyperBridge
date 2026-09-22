/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line.model

import android.graphics.Paint
import android.graphics.Rect
import com.lidesheng.hyperlyric.lyric.model.LyricLine
import com.lidesheng.hyperlyric.lyric.model.LyricMetadata
import com.lidesheng.hyperlyric.lyric.model.LyricWord
import com.lidesheng.hyperlyric.lyric.model.extensions.TimingNavigator
import kotlin.math.min

data class LyricModel(
    val begin: Long = 0,
    val end: Long = 0,
    val duration: Long = 0,
    val text: String,
    val words: List<WordModel>,
    val isAlignedRight: Boolean = false,
    var metadata: LyricMetadata? = null,
) {
    var width: Float = 0f
        private set

    val wordText: String by lazy { words.toText() }
    val wordTimingNavigator: TimingNavigator<WordModel> by lazy { TimingNavigator(words.toTypedArray()) }
    val isPlainText: Boolean = words.isEmpty()

    fun updateSizes(paint: Paint) {
        width = getTextFullWidth(paint, text)
        var previous: WordModel? = null
        words.forEach { word ->
            word.updateSizes(previous, paint)
            previous = word
        }
        updateSustainStrengths(paint.textSize)
    }

    /**
     * 获取文字绘制所需的实际宽度
     */
    private fun getTextFullWidth(paint: Paint, text: String): Float {
        val measureWidth = paint.measureText(text)
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        // 如果 bounds.right 大于 measureWidth，说明文字向右侧溢出了
        return if (bounds.right > measureWidth) {
            bounds.right.toFloat()
        } else {
            measureWidth
        }
    }

    private fun updateSustainStrengths(textSize: Float) {
        if (words.isEmpty() || textSize <= 0f) return

        val durations = LongArray(words.size) { index -> durationOf(words[index]) }
        val paces = FloatArray(words.size) { index ->
            val visualUnits = (words[index].textWidth / textSize).coerceAtLeast(0.5f)
            durations[index].toFloat() / visualUnits
        }

        words.forEachIndexed { index, word ->
            val duration = durations[index]
            if (duration < SUSTAIN_MIN_DURATION_MS || word.textWidth <= 0f) {
                word.sustainStrength = 0f
                return@forEachIndexed
            }

            val peerPaces = paces.filterIndexed { peerIndex, _ ->
                peerIndex != index && durations[peerIndex] > 0L
            }.sorted()
            val relativeStrength = if (peerPaces.isNotEmpty()) {
                val baseline = median(peerPaces).coerceAtLeast(1f)
                smoothStep(
                    (paces[index] / baseline - SUSTAIN_RATIO_START) /
                            (SUSTAIN_RATIO_FULL - SUSTAIN_RATIO_START)
                )
            } else {
                smoothStep(
                    (duration - SINGLE_WORD_SUSTAIN_START_MS).toFloat() /
                            (SINGLE_WORD_SUSTAIN_FULL_MS - SINGLE_WORD_SUSTAIN_START_MS).toFloat()
                )
            }
            val durationStrength = smoothStep(
                (duration - SUSTAIN_MIN_DURATION_MS).toFloat() /
                        (SUSTAIN_FULL_DURATION_MS - SUSTAIN_MIN_DURATION_MS).toFloat()
            )
            word.sustainStrength = min(relativeStrength, durationStrength)
        }
    }

    private fun durationOf(word: WordModel): Long =
        (word.end - word.begin).takeIf { it > 0L } ?: word.duration

    private fun median(sorted: List<Float>): Float {
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2f
        } else {
            sorted[middle]
        }
    }

    private fun smoothStep(value: Float): Float {
        val progress = value.coerceIn(0f, 1f)
        return progress * progress * (3f - 2f * progress)
    }

    private companion object {
        const val SUSTAIN_MIN_DURATION_MS = 700L
        const val SUSTAIN_FULL_DURATION_MS = 1_400L
        const val SUSTAIN_RATIO_START = 1.4f
        const val SUSTAIN_RATIO_FULL = 2.4f
        const val SINGLE_WORD_SUSTAIN_START_MS = 1_000L
        const val SINGLE_WORD_SUSTAIN_FULL_MS = 2_200L
    }
}

internal fun emptyLyricModel(): LyricModel = LyricModel(
    words = emptyList(),
    text = ""
)

/**
 * 将 LyricLine 转换为 LyricModel
 */
internal fun LyricLine.createModel(): LyricModel = LyricModel(
    begin = begin,
    end = end,
    duration = duration,
    text = text.orEmpty(),
    words = words?.toWordModels() ?: emptyList(),
    isAlignedRight = isAlignedRight,
    metadata = metadata
)

/**
 * 将 LyricWord 列表转换为 WordModel 列表，并建立前后引用关系
 */
private fun List<LyricWord>.toWordModels(): List<WordModel> {
    val models = mutableListOf<WordModel>()
    var previousModel: WordModel? = null

    forEach { word ->
        val model = WordModel(
            begin = word.begin,
            end = word.end,
            duration = word.duration,
            text = word.text.orEmpty(),
            metadata = word.metadata
        )

        model.previous = previousModel
        previousModel?.next = model

        models.add(model)
        previousModel = model
    }
    return models
}

