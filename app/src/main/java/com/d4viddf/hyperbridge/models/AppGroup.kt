package com.d4viddf.hyperbridge.models

import android.graphics.drawable.Drawable

data class AppGroup(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val widgetIds: List<Int>
)