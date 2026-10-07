package com.sykeptical.hyperpop.xposed.hooks.fingerprint

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import com.sykeptical.hyperpop.service.animation.fingerprint.FingerprintIslandGeometry
import com.sykeptical.hyperpop.service.animation.fingerprint.LottieFingerprintParser
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Black plate, white ridge strokes, and the enrollment blue trim. The trim
 * follows Xiaomi's Lottie frame clock. Blur steps are cached.
 */
internal class FingerprintIslandRenderer {
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = FingerprintIslandGeometry.PLATE_COLOR
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = FingerprintIslandGeometry.PLATE_STROKE_COLOR
    }
    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = FingerprintIslandGeometry.RIDGE_WHITE
    }
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = FingerprintIslandGeometry.RIDGE_WHITE
    }
    private val lockFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = FingerprintIslandGeometry.RIDGE_WHITE
    }
    private val lockStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = FingerprintIslandGeometry.RIDGE_WHITE
    }
    private val clipPath = Path()
    private val ridgePath = Path()
    private val checkPath = Path()
    private val trimmed = Path()
    private val measure = PathMeasure()
    private val bluePaths = ArrayList<Path>()
    private var built: LottieFingerprintParser.Artwork? = null
    private val blurs = arrayOfNulls<RenderEffect>(BLUR_DP.size)
    private var blurDensity = -1f

    fun draw(canvas: Canvas, frame: Frame) {
        val plate = frame.plate
        if (plate.width <= 1f || plate.height <= 1f || frame.plateAlpha <= 0.01f) return
        platePaint.alpha = (frame.plateAlpha * 255f).roundToInt().coerceIn(0, 255)
        val strokeAlpha = (Color.alpha(frame.strokeColor) * frame.plateAlpha).roundToInt().coerceIn(0, 255)
        strokePaint.color = Color.argb(
            strokeAlpha,
            Color.red(frame.strokeColor),
            Color.green(frame.strokeColor),
            Color.blue(frame.strokeColor),
        )
        strokePaint.strokeWidth = frame.strokePx
        canvas.drawRoundRect(plate.left, plate.top, plate.right, plate.bottom, frame.radius, frame.radius, platePaint)
        if (frame.strokePx > 0.5f && strokeAlpha > 0) {
            val outset = frame.strokePx / 2f
            canvas.drawRoundRect(
                plate.left - outset,
                plate.top - outset,
                plate.right + outset,
                plate.bottom + outset,
                frame.radius + outset,
                frame.radius + outset,
                strokePaint,
            )
        }
        val artwork = frame.artwork
        if (artwork != null && frame.glyphAlpha > 0.01f) {
            ensurePaths(artwork)
            drawRidges(canvas, frame, artwork)
        }
        if (frame.lockAlpha > 0.01f) {
            val clip = canvas.save()
            clipPath.rewind()
            clipPath.addRoundRect(
                plate.left,
                plate.top,
                plate.right,
                plate.bottom,
                frame.radius,
                frame.radius,
                Path.Direction.CW,
            )
            canvas.clipPath(clipPath)
            drawPadlock(canvas, frame)
            canvas.restoreToCount(clip)
        }
        if (frame.checkAlpha > 0.01f && artwork != null) drawCheck(canvas, frame, artwork)
    }

    private fun drawRidges(canvas: Canvas, frame: Frame, artwork: LottieFingerprintParser.Artwork) {
        val alpha = (frame.glyphAlpha * (1f - frame.success) * 255f).roundToInt().coerceIn(0, 255)
        if (alpha == 0) return
        ridgePaint.alpha = alpha
        ridgePaint.strokeWidth = artwork.ridgeStrokeWidth
        bluePaint.color = argb(
            artwork.blueRed,
            artwork.blueGreen,
            artwork.blueBlue,
            frame.blueAlpha * frame.glyphAlpha * (1f - frame.success),
        )
        val showFill = Color.alpha(bluePaint.color) > 0
        val blur = frame.success * frame.maxBlurPx
        val scale = 1f - 0.08f * frame.success
        drawScaled(canvas, frame, blur, scale, frame.glyph.centerX + frame.glyphExtraPx, frame.glyph.centerY) {
            drawGlyph(it, frame, artwork, showFill)
        }
    }

    private fun drawGlyph(
        canvas: Canvas,
        frame: Frame,
        artwork: LottieFingerprintParser.Artwork,
        showFill: Boolean,
    ) {
        val glyph = frame.glyph
        canvas.save()
        canvas.translate(glyph.left + frame.glyphExtraPx, glyph.top)
        canvas.scale(glyph.width / artwork.canvasWidth, glyph.height / artwork.canvasHeight)
        canvas.drawPath(ridgePath, ridgePaint)
        if (showFill) drawFill(canvas, frame.fillFrame, artwork)
        canvas.restore()
    }

    private fun drawFill(canvas: Canvas, frame: Float, artwork: LottieFingerprintParser.Artwork) {
        val progress = frame.coerceIn(0f, LottieFingerprintParser.FILL_END_FRAME)
        val count = minOf(artwork.fill.size, bluePaths.size)
        for (index in 0 until count) {
            val ridge = artwork.fill[index]
            val start = wrapPercent(ridge.start.at(progress) + ridge.offset.at(progress))
            val end = wrapPercent(ridge.end.at(progress) + ridge.offset.at(progress))
            if (abs(start - end) < 0.05f) continue
            measure.setPath(bluePaths[index], false)
            val length = measure.length
            if (length <= 0.5f) continue
            trimmed.rewind()
            if (end > start) {
                measure.getSegment(length * start / 100f, length * end / 100f, trimmed, true)
            } else {
                measure.getSegment(length * start / 100f, length, trimmed, true)
                measure.getSegment(0f, length * end / 100f, trimmed, true)
            }
            bluePaint.strokeWidth = ridge.strokeWidth
            canvas.drawPath(trimmed, bluePaint)
        }
    }

    private fun wrapPercent(value: Float): Float {
        var wrapped = value % 100f
        if (wrapped < 0f) wrapped += 100f
        return wrapped
    }

    private fun drawCheck(canvas: Canvas, frame: Frame, artwork: LottieFingerprintParser.Artwork) {
        val glyph = frame.glyph
        checkPaint.alpha = (frame.checkAlpha * frame.plateAlpha * 255f).roundToInt().coerceIn(0, 255)
        val scaleX = glyph.width / artwork.canvasWidth
        val scaleY = glyph.height / artwork.canvasHeight
        val blur = (1f - frame.success) * frame.maxBlurPx
        val scale = 0.8f + 0.2f * frame.success
        drawScaled(canvas, frame, blur, scale, glyph.centerX, glyph.centerY) { target ->
            target.save()
            target.translate(glyph.left + frame.glyphExtraPx, glyph.top)
            target.scale(scaleX, scaleY)
            target.drawPath(checkPath, checkPaint)
            target.restore()
        }
    }

    private fun drawPadlock(canvas: Canvas, frame: Frame) {
        val rect = frame.padlock
        val alpha = (frame.lockAlpha * frame.plateAlpha * 255f).roundToInt().coerceIn(0, 255)
        lockFill.alpha = alpha
        lockStroke.alpha = alpha
        val width = rect.width
        val height = rect.height
        val bodyTop = rect.top + height * 0.42f
        val radius = width * 0.22f
        canvas.drawRoundRect(rect.left, bodyTop, rect.right, rect.bottom, radius, radius, lockFill)
        lockStroke.strokeWidth = width * 0.16f
        canvas.drawArc(
            rect.left + width * 0.18f,
            rect.top,
            rect.right - width * 0.18f,
            bodyTop + height * 0.22f,
            180f,
            180f,
            false,
            lockStroke,
        )
    }

    private fun drawScaled(
        canvas: Canvas,
        frame: Frame,
        blurPx: Float,
        scale: Float,
        pivotX: Float,
        pivotY: Float,
        draw: (Canvas) -> Unit,
    ) {
        canvas.save()
        canvas.scale(scale, scale, pivotX, pivotY)
        if (blurPx > 0.5f && canvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= 31) {
            val effect = blur(frame.density, blurPx)
            if (effect != null) {
                val node = frame.blurNode
                node.setRenderEffect(effect)
                node.setPosition(0, 0, frame.viewWidth.coerceAtLeast(1), frame.viewHeight.coerceAtLeast(1))
                val recording = node.beginRecording()
                draw(recording)
                node.endRecording()
                canvas.drawRenderNode(node)
            } else {
                draw(canvas)
            }
        } else {
            draw(canvas)
        }
        canvas.restore()
    }

    private fun blur(density: Float, blurPx: Float): RenderEffect? {
        if (blurDensity != density) {
            blurs.fill(null)
            blurDensity = density
        }
        val requestedDp = blurPx / density.coerceAtLeast(0.5f)
        var best = 0
        var bestDelta = Float.MAX_VALUE
        for (index in BLUR_DP.indices) {
            val delta = kotlin.math.abs(BLUR_DP[index] - requestedDp)
            if (delta < bestDelta) {
                bestDelta = delta
                best = index
            }
        }
        if (BLUR_DP[best] <= 0f) return null
        blurs[best]?.let { return it }
        val radius = BLUR_DP[best] * density
        val effect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        blurs[best] = effect
        return effect
    }

    private fun ensurePaths(artwork: LottieFingerprintParser.Artwork) {
        if (built === artwork) return
        built = artwork
        ridgePath.rewind()
        artwork.ridges.forEach { addContour(ridgePath, it) }
        checkPath.rewind()
        addContour(checkPath, artwork.checkmark)
        bluePaths.clear()
        artwork.fill.forEach { ridge ->
            val path = Path()
            addContour(path, ridge.contour)
            bluePaths += path
        }
    }

    private fun addContour(path: Path, contour: LottieFingerprintParser.Contour) {
        val count = contour.x.size
        if (count < 2) return
        path.moveTo(contour.x[0], contour.y[0])
        val segments = if (contour.closed) count else count - 1
        for (index in 0 until segments) {
            val next = (index + 1) % count
            path.cubicTo(
                contour.x[index] + contour.outX[index],
                contour.y[index] + contour.outY[index],
                contour.x[next] + contour.inX[next],
                contour.y[next] + contour.inY[next],
                contour.x[next],
                contour.y[next],
            )
        }
        if (contour.closed) path.close()
    }

    private fun argb(red: Float, green: Float, blue: Float, alpha: Float): Int {
        fun channel(value: Float) = (value * 255f).roundToInt().coerceIn(0, 255)
        return Color.argb(channel(alpha), channel(red), channel(green), channel(blue))
    }

    data class Frame(
        val plate: FingerprintIslandGeometry.Rect,
        val glyph: FingerprintIslandGeometry.Rect,
        val padlock: FingerprintIslandGeometry.Rect,
        val radius: Float,
        val strokePx: Float,
        val strokeColor: Int,
        val plateAlpha: Float,
        val glyphAlpha: Float,
        val lockAlpha: Float,
        val blueAlpha: Float,
        val checkAlpha: Float,
        val success: Float,
        val glyphExtraPx: Float,
        val maxBlurPx: Float,
        val density: Float,
        val viewWidth: Int,
        val viewHeight: Int,
        val artwork: LottieFingerprintParser.Artwork?,
        val fillFrame: Float,
        val blurNode: android.graphics.RenderNode,
    )

    private companion object {
        val BLUR_DP = floatArrayOf(0f, 4f, 8f, 12f)
    }
}
