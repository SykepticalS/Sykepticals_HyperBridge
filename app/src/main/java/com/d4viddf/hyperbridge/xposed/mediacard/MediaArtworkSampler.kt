package com.d4viddf.hyperbridge.xposed.mediacard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.sqrt

/**
 * Artwork sampling used on the song-change path.
 *
 * Notification artwork is often a large hardware bitmap. Copying or scaling that
 * bitmap reads the full texture back from the GPU and stalls composition for the
 * whole device. Every sample is therefore drawn into a small software bitmap.
 */
internal object MediaArtworkSampler {
    private const val MAX_SAMPLE_PIXELS = 100 * 100

    fun sample(bitmap: Bitmap, maxPixels: Int = MAX_SAMPLE_PIXELS): Bitmap? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return null
        val (width, height) = targetSize(bitmap.width, bitmap.height, maxPixels)
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            return rasterize(bitmap, width, height)
        }
        if (width == bitmap.width && height == bitmap.height) {
            return runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        }
        val scaled = runCatching {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }.getOrNull() ?: return null
        if (scaled.config == Bitmap.Config.ARGB_8888) return scaled
        return runCatching { scaled.copy(Bitmap.Config.ARGB_8888, false) }
            .also { scaled.recycle() }
            .getOrNull()
    }

    fun sample(drawable: Drawable, maxPixels: Int = MAX_SAMPLE_PIXELS): Bitmap? {
        (drawable as? BitmapDrawable)?.bitmap?.let { return sample(it, maxPixels) }
        val sourceWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
        val sourceHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
        val (width, height) = targetSize(sourceWidth, sourceHeight, maxPixels)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val originalBounds = Rect(drawable.bounds)
        return try {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(result))
            result
        } catch (_: Throwable) {
            result.recycle()
            null
        } finally {
            drawable.bounds = originalBounds
        }
    }

    fun fingerprint(bitmap: Bitmap): Long {
        var hash = 1125899906842597L
        hash = hash * 31 + bitmap.width
        hash = hash * 31 + bitmap.height
        val stepX = maxOf(1, bitmap.width / 16)
        val stepY = maxOf(1, bitmap.height / 16)
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                hash = hash * 31 + bitmap.getPixel(x, y).toLong()
            }
        }
        return hash
    }

    private fun targetSize(width: Int, height: Int, maxPixels: Int): Pair<Int, Int> {
        val pixels = width.toLong() * height
        if (pixels <= maxPixels) return width to height
        val scale = sqrt(maxPixels.toDouble() / pixels)
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }

    private fun rasterize(bitmap: Bitmap, width: Int, height: Int): Bitmap? {
        return runCatching {
            val picture = Picture()
            val canvas = picture.beginRecording(width, height)
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            picture.endRecording()
            Bitmap.createBitmap(picture, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}
