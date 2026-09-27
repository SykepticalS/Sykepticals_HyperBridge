package com.d4viddf.hyperbridge.xposed.mediacard.island.compact

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.text.TextPaint
import android.view.View
import java.lang.ref.WeakReference
import kotlin.math.ceil

/**
 * Single-line title that draws itself so it can be shaded exactly where the island shows it:
 * against the album cover on the left and against the camera cutout on the right.
 *
 * Xiaomi caps `area_left` after measuring, so part of this view can sit under the cutout.
 * [visibleWidth] is measured against the area's real bounds; scrolling and the right shade use it.
 */
internal class CompactTitleView(context: Context) : View(context) {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    var text: String = ""
        set(value) {
            if (field == value) return
            field = value
            textWidthCache = -1f
            requestLayout()
            invalidate()
        }

    /** How far the text has scrolled towards the album cover, in pixels. */
    var offset: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Space between the cover and the resting text. The start shade covers it. */
    var insetPx = 0
    var endPaddingPx = 0
    var startFadePx = 0
    var endFadePx = 0

    private var areaRef: WeakReference<View>? = null
    private var textWidthCache = -1f
    private val location = IntArray(2)
    private val areaLocation = IntArray(2)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private var startShader: LinearGradient? = null
    private var startShaderWidth = -1
    private var endShader: LinearGradient? = null
    private var endShaderEdge = Float.NaN
    private val relayoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }

    val textWidth: Float
        get() {
            if (textWidthCache < 0f) textWidthCache = paint.measureText(text)
            return textWidthCache
        }

    val baselinePx: Float
        get() {
            val metrics = paint.fontMetrics
            return (height - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
        }

    /** The island animates `area_left`; redraw so the cutout shade follows it. */
    fun clipTo(area: View) {
        if (areaRef?.get() === area) return
        areaRef?.get()?.removeOnLayoutChangeListener(relayoutListener)
        areaRef = WeakReference(area)
        area.addOnLayoutChangeListener(relayoutListener)
    }

    /** Part of this view that is not hidden past the end of `area_left`. */
    fun visibleWidth(): Int {
        val area = areaRef?.get() ?: return width
        if (width <= 0 || area.width <= 0) return width
        getLocationInWindow(location)
        area.getLocationInWindow(areaLocation)
        val right = areaLocation[0] + area.width - area.paddingRight
        return (right - location[0]).coerceIn(0, width)
    }

    /** Distance to scroll so the last glyph lands just before the cutout shade. */
    fun overflowPx(): Float {
        if (text.isEmpty() || width <= 0) return 0f
        val room = visibleWidth() - insetPx - endPaddingPx
        val overflow = textWidth - room
        return if (overflow <= resources.displayMetrics.density) 0f else overflow
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = paint.fontMetrics
        val wantedHeight = ceil(metrics.descent - metrics.ascent).toInt()
        val wantedWidth = ceil(insetPx + textWidth + endPaddingPx).toInt()
        setMeasuredDimension(
            resolveSize(wantedWidth, widthMeasureSpec),
            resolveSize(wantedHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (text.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val edge = visibleWidth().toFloat()
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawText(text, insetPx - offset, baselinePx, paint)
        if (startFadePx > 0) {
            if (startShaderWidth != startFadePx) {
                startShaderWidth = startFadePx
                startShader = LinearGradient(
                    0f, 0f, startFadePx.toFloat(), 0f,
                    Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP,
                )
            }
            maskPaint.shader = startShader
            canvas.drawRect(0f, 0f, startFadePx.toFloat(), h, maskPaint)
        }
        if (endFadePx > 0 && edge > 0f) {
            if (endShaderEdge != edge) {
                endShaderEdge = edge
                endShader = LinearGradient(
                    edge - endFadePx, 0f, edge, 0f,
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP,
                )
            }
            maskPaint.shader = endShader
            canvas.drawRect(edge - endFadePx, 0f, w, h, maskPaint)
        }
        canvas.restoreToCount(layer)
    }
}
