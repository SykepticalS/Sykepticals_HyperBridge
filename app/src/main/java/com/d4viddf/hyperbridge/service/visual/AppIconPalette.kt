package com.d4viddf.hyperbridge.service.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import com.d4viddf.hyperbridge.models.IslandGlowResolver
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Source-app icon palette used for island glow when Follow dynamic / On has no custom color. */
object AppIconPalette {
    private val cache = ConcurrentHashMap<String, String>()

    fun color(context: Context, packageName: String?): String? {
        val pkg = packageName?.takeIf { it.isNotBlank() } ?: return null
        cache[pkg]?.let { return it }
        val extracted = extract(context, pkg) ?: return null
        cache[pkg] = extracted
        return extracted
    }

    fun prefer(artworkOrAccent: String?, appIcon: String?): String? =
        IslandGlowResolver.normalizeColor(artworkOrAccent)
            ?.takeUnless { it.equals("#FFFFFF", ignoreCase = true) || it.equals("#FFFFFFFF", ignoreCase = true) }
            ?: IslandGlowResolver.normalizeColor(appIcon)
            ?: IslandGlowResolver.normalizeColor(artworkOrAccent)

    private fun extract(context: Context, pkg: String): String? = runCatching {
        val drawable = context.packageManager.getApplicationIcon(pkg)
        val bitmap = drawable.toBitmap(128, 128)
        val palette = Palette.from(bitmap).clearFilters().generate()
        val swatches = listOf(
            palette.vibrantSwatch,
            palette.darkVibrantSwatch,
            palette.lightVibrantSwatch,
            palette.dominantSwatch,
            palette.mutedSwatch,
        )
        val best = swatches.firstOrNull { it != null && !isGrayscale(it.rgb) } ?: palette.dominantSwatch
        best?.let { String.format("#%06X", 0xFFFFFF and it.rgb) }
    }.getOrNull()

    private fun isGrayscale(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return abs(r - g) + abs(g - b) + abs(b - r) < 30
    }

    private fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return bitmap.scale(width, height)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}
