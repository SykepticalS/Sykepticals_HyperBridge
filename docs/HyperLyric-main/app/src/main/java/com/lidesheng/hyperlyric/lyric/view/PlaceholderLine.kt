/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view

import com.lidesheng.hyperlyric.lyric.model.LyricLine
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine
import com.lidesheng.hyperlyric.lyric.view.line.model.LyricModel

internal const val METADATA_TITLE_LINE = "TitleLine"
internal const val METADATA_COUNTDOWN_LINE = "CountdownLine"

fun IRichLyricLine?.isTitleLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_TITLE_LINE, false) == true

fun IRichLyricLine?.isCountdownLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

internal fun LyricLine?.isCountdownLine(): Boolean =
    this?.metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true

internal fun LyricModel.isCountdownLine(): Boolean =
    metadata?.getBoolean(METADATA_COUNTDOWN_LINE, false) == true
