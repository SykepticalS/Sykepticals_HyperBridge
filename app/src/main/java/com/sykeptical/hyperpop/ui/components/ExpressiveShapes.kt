package com.sykeptical.hyperpop.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed class ShapeStyle(val topRadius: Dp, val bottomRadius: Dp) {
    data object None : ShapeStyle(0.dp, 0.dp)
    data object ExtraSmall : ShapeStyle(2.dp, 1.dp)
    data object Small : ShapeStyle(4.dp, 2.dp)
    data object Medium : ShapeStyle(15.dp, 5.dp)
    data object Large : ShapeStyle(16.dp, 0.dp)
    data object ExtraLarge : ShapeStyle(48.dp, 16.dp)
}

fun getExpressiveShape(groupSize: Int, index: Int, style: ShapeStyle = ShapeStyle.Large): Shape {
    if (groupSize <= 1) return RoundedCornerShape(style.topRadius)
    return when (index) {
        0 -> RoundedCornerShape(
            topStart = style.topRadius,
            topEnd = style.topRadius,
            bottomEnd = style.bottomRadius,
            bottomStart = style.bottomRadius,
        )
        groupSize - 1 -> RoundedCornerShape(
            topStart = style.bottomRadius,
            topEnd = style.bottomRadius,
            bottomEnd = style.topRadius,
            bottomStart = style.topRadius,
        )
        else -> RoundedCornerShape(style.bottomRadius)
    }
}
