/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.lidesheng.hyperlyric.lyric.view.line

import android.graphics.Typeface
import android.text.TextPaint

internal class TextPaintX : TextPaint(ANTI_ALIAS_FLAG)

internal fun TextPaint.applyFont(
    typeface: Typeface?,
    variationSettings: String?
) {
    // Paint 会基于当前 Typeface 派生 variation Typeface；先清理旧轴，
    // 再替换基础字体并重新应用，避免切换设置后继续持有上一条回退链。
    setFontVariationSettings(null)
    this.typeface = typeface
    variationSettings?.let(::setFontVariationSettings)
}
