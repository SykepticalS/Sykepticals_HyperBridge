package com.d4viddf.hyperbridge.xposed.mediacard.notification.background

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.d4viddf.hyperbridge.xposed.mediacard.MediaArtworkSampler
import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardConstants
import com.d4viddf.hyperbridge.xposed.mediacard.compat.ColorExtractor
import com.d4viddf.hyperbridge.xposed.mediacard.background.MediaFlowTone
import com.d4viddf.hyperbridge.xposed.mediacard.background.MediaSoftArtworkFactory
import com.d4viddf.hyperbridge.xposed.mediacard.background.MediaSoftPaletteExtractor
import com.d4viddf.hyperbridge.xposed.mediacard.style.MediaCardForegroundColorSource
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal data class NotificationMediaColorConfig(
    /** Tone measured from the final rendered background bitmap. */
    val backgroundIsDark: Boolean,
    val foregroundSource: MediaCardForegroundColorSource? = null
)

private data class BaseBackgroundColors(
    val backgroundStart: Int,
    val backgroundEnd: Int
)

internal data class RenderedNotificationMediaBackground(
    val bitmap: Bitmap,
    val colors: NotificationMediaColorConfig,
    val artworkFingerprint: Long
) {
    /** Upload pixels before the UI thread swaps the background, so that frame does not hitch. */
    fun preparedForDisplay(): RenderedNotificationMediaBackground {
        if (bitmap.isRecycled || bitmap.config == Bitmap.Config.HARDWARE) return this
        val gpu = runCatching { bitmap.copy(Bitmap.Config.HARDWARE, false) }.getOrNull() ?: return this
        if (gpu !== bitmap) bitmap.recycle()
        return copy(bitmap = gpu)
    }
}

internal class NotificationMediaBackgroundRenderer(
    private val classLoader: ClassLoader
) {
    private val monet = MonetApi.create(classLoader)
    private val cacheLock = Any()

    private companion object {
        private const val MAX_ARTWORK_PIXELS = 96 * 96
        private const val MAX_RENDER_SIDE = 480
    }

    @Volatile
    private var closed = false
    private val profileCache = LruCache<Long, ArtworkProfile>(12)
    private val renderCache = object : LruCache<RenderCacheKey, CachedBackground>(8) {
        override fun entryRemoved(
            evicted: Boolean,
            key: RenderCacheKey,
            oldValue: CachedBackground,
            newValue: CachedBackground?
        ) {
            if (oldValue.bitmap !== newValue?.bitmap && !oldValue.bitmap.isRecycled) {
                oldValue.bitmap.recycle()
            }
        }
    }

    fun close() {
        synchronized(cacheLock) {
            closed = true
            renderCache.evictAll()
            profileCache.evictAll()
        }
    }

    fun render(
        context: Context,
        artworkIcon: Icon?,
        packageName: String,
        style: Int,
        blurAmount: Int,
        autoInvert: Boolean,
        softCoverTone: Int,
        width: Int,
        height: Int
    ): RenderedNotificationMediaBackground? {
        val artwork = runCatching {
            artworkIcon?.loadDrawable(context)
                ?: context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        return renderArtwork(
            context,
            artwork,
            style,
            blurAmount,
            autoInvert,
            softCoverTone,
            width,
            height
        )
    }

    fun renderDrawable(
        context: Context,
        artworkDrawable: Drawable?,
        packageName: String,
        style: Int,
        blurAmount: Int,
        autoInvert: Boolean,
        softCoverTone: Int,
        width: Int,
        height: Int
    ): RenderedNotificationMediaBackground? {
        val artwork = runCatching {
            artworkDrawable?.constantState
                ?.newDrawable(context.resources)
                ?.mutate()
                ?: artworkDrawable
                ?: context.packageManager.getApplicationIcon(packageName)
        }.getOrNull() ?: return null
        return renderArtwork(
            context,
            artwork,
            style,
            blurAmount,
            autoInvert,
            softCoverTone,
            width,
            height
        )
    }

    private fun renderArtwork(
        context: Context,
        artwork: Drawable,
        style: Int,
        blurAmount: Int,
        autoInvert: Boolean,
        softCoverTone: Int,
        width: Int,
        height: Int
    ): RenderedNotificationMediaBackground? {
        if (closed || width <= 0 || height <= 0) return null
        val longest = max(width, height)
        val renderScale = if (longest > MAX_RENDER_SIDE) MAX_RENDER_SIDE.toFloat() / longest else 1f
        val renderWidth = (width * renderScale).toInt().coerceAtLeast(1)
        val renderHeight = (height * renderScale).toInt().coerceAtLeast(1)
        val source = artwork.toBitmapSafe() ?: return null
        val fingerprint = source.fingerprint()
        val darkMode = context.resources.configuration.uiMode and 0x30 == 0x20
        val cacheKey = RenderCacheKey(
            fingerprint,
            style,
            blurAmount,
            autoInvert,
            softCoverTone,
            darkMode,
            renderWidth,
            renderHeight
        )
        cachedBackground(cacheKey)?.let { cached ->
            source.recycle()
            return cached
        }
        val profile = artworkProfile(source, fingerprint) ?: run {
            source.recycle()
            return null
        }
        val baseColors = colorConfig(style, profile, autoInvert, softCoverTone)
        val result = when (style) {
            1 -> renderCoverArt(source, baseColors, darkMode, fingerprint, renderWidth, renderHeight)
            2 -> renderBlurredCover(source, baseColors, blurAmount, renderWidth, renderHeight)
            3 -> renderRadialGradient(source, baseColors, renderWidth, renderHeight)
            4 -> renderLinearGradient(source, baseColors, renderWidth, renderHeight)
            5 -> renderSoftCover(profile.rawColors, softCoverTone, renderWidth, renderHeight)
            else -> null
        }
        source.recycle()
        result ?: return null
        val colors = NotificationMediaColorConfig(
            backgroundIsDark = result.brightness() < 192f,
            foregroundSource = when (style) {
                1 -> MediaCardForegroundColorSource.Monet(
                    primary = profile.palette.accent1[2]
                )

                2 -> MediaCardForegroundColorSource.Monet(
                    primary = profile.palette.neutral1[1],
                    secondary = profile.palette.neutral2[3]
                )

                3 -> MediaCardForegroundColorSource.Monet(
                    primary = profile.palette.neutral1[1],
                    secondary = profile.palette.neutral2[3]
                )

                4 -> {
                    val reverse = autoInvert && profile.brightness >= 192f
                    MediaCardForegroundColorSource.Monet(
                        primary = profile.palette.accent1[if (reverse) 8 else 2]
                    )
                }

                5 -> MediaCardForegroundColorSource.Soft(
                    colors = MediaSoftPaletteExtractor.fromColors(profile.rawColors).asList()
                )

                else -> null
            }
        )
        cacheBackground(cacheKey, result, colors)
        return RenderedNotificationMediaBackground(result, colors, fingerprint)
    }

    private fun colorConfig(
        style: Int,
        profile: ArtworkProfile,
        autoInvert: Boolean,
        softCoverTone: Int
    ): BaseBackgroundColors {
        val palette = profile.palette
        return when (style) {
            1 -> BaseBackgroundColors(
                backgroundStart = palette.accent1[8],
                backgroundEnd = palette.accent1[8]
            )

            2, 3 -> BaseBackgroundColors(
                backgroundStart = palette.accent2[9],
                backgroundEnd = palette.accent1[9]
            )

            4 -> {
                val reverse = autoInvert && profile.brightness >= 192f
                val background = palette.accent1[if (reverse) 3 else 8]
                BaseBackgroundColors(
                    backgroundStart = background,
                    backgroundEnd = background
                )
            }

            5 -> {
                val tone = softCoverTone.toFlowTone()
                val surface = MediaSoftArtworkFactory.appearance(tone).surface
                BaseBackgroundColors(
                    backgroundStart = surface,
                    backgroundEnd = surface
                )
            }

            else -> BaseBackgroundColors(
                backgroundStart = Color.BLACK,
                backgroundEnd = Color.BLACK
            )
        }
    }

    private fun renderCoverArt(
        artwork: Bitmap,
        colors: BaseBackgroundColors,
        darkMode: Boolean,
        fingerprint: Long,
        width: Int,
        height: Int
    ): Bitmap {
        val tile = artwork.scaleOwned(132, 132)
        val smallTile = tile.scaleOwned(66, 66)
        val mosaic = createBitmap(264, 264)
        val canvas = Canvas(mosaic)
        val random = Random((fingerprint xor (fingerprint ushr 32)).toInt())
        val positions = arrayOf(0f to 0f, 132f to 0f, 0f to 132f, 132f to 132f, 99f to 99f)
        positions.forEachIndexed { index, position ->
            val source = if (index < 4) tile else smallTile
            val matrix = Matrix().apply {
                postRotate(random.nextInt(4) * 90f, source.width / 2f, source.height / 2f)
                postScale(
                    if (random.nextBoolean()) -1f else 1f,
                    if (random.nextBoolean()) -1f else 1f,
                    source.width / 2f,
                    source.height / 2f
                )
            }
            val transformed = Bitmap.createBitmap(
                source, 0, 0, source.width, source.height, matrix, true
            )
            canvas.drawBitmap(transformed, position.first, position.second, null)
            if (transformed !== source) transformed.recycle()
        }
        tile.recycle()
        smallTile.recycle()

        val correction = when (mosaic.brightness()) {
            in 0f..<50f -> 40f
            in 50f..<100f -> 20f
            in 100f..<200f -> -20f
            else -> -40f
        }
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, correction,
                0f, 1f, 0f, 0f, correction,
                0f, 0f, 1f, 0f, correction,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val corrected = createBitmap(mosaic.width, mosaic.height)
        val correctedCanvas = Canvas(corrected)
        correctedCanvas.drawBitmap(mosaic, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        })
        mosaic.recycle()
        correctedCanvas.drawColor(colors.backgroundStart.withAlpha(111))
        val overlay = if (darkMode) 0 else 248
        correctedCanvas.drawColor(Color.argb(20, overlay, overlay, overlay))

        val blurred = corrected.fastBlur(40f)
        corrected.recycle()
        val result = blurred.centerCrop(width, height)
        blurred.recycle()
        return result
    }

    private fun renderBlurredCover(
        artwork: Bitmap,
        colors: BaseBackgroundColors,
        blurAmount: Int,
        width: Int,
        height: Int
    ): Bitmap {
        val base = createBitmap(artwork.width, artwork.height)
        val canvas = Canvas(base)
        canvas.drawColor(colors.backgroundStart)
        canvas.drawBitmap(artwork, 0f, 0f, null)
        canvas.drawCircleGradient(
            colors.backgroundStart,
            colors.backgroundEnd,
            startAlpha = 64,
            endAlpha = 255,
            radiusScale = 1.0f
        )
        val blurred = base.fastBlur(height * blurAmount.coerceIn(1, 20) / 100f)
        base.recycle()

        val squareSize = max(blurred.width, blurred.height)
        val square = createBitmap(squareSize, squareSize)
        Canvas(square).apply {
            drawColor(colors.backgroundStart)
            drawBitmap(
                blurred,
                (squareSize - blurred.width) / 2f,
                (squareSize - blurred.height) / 2f,
                null
            )
        }
        blurred.recycle()
        val result = square.centerCrop(width, height)
        square.recycle()
        return result
    }

    private fun renderRadialGradient(
        artwork: Bitmap,
        colors: BaseBackgroundColors,
        width: Int,
        height: Int
    ): Bitmap {
        val squareSize = max(width, height)
        val result = createBitmap(squareSize, squareSize)
        val canvas = Canvas(result)
        canvas.drawSquareArtwork(
            artwork = artwork,
            squareSize = squareSize,
            fill = false,
            backgroundColor = colors.backgroundEnd
        )
        canvas.drawCircleGradient(
            colors.backgroundStart,
            colors.backgroundEnd,
            startAlpha = 64,
            endAlpha = 255,
            radiusScale = 1.0f
        )
        val cropped = result.centerCrop(width, height)
        result.recycle()
        return cropped
    }

    private fun renderLinearGradient(
        artwork: Bitmap,
        colors: BaseBackgroundColors,
        width: Int,
        height: Int
    ): Bitmap {
        val result = createBitmap(width, height)
        val canvas = Canvas(result)
        canvas.drawColor(colors.backgroundStart)
        val coverSize = min(width, height)
        val coverLeft = width - coverSize
        val cover = artwork.centerCrop(coverSize, coverSize)
        canvas.drawBitmap(cover, coverLeft.toFloat(), 0f, null)
        cover.recycle()
        val shader = LinearGradient(
            coverLeft.toFloat(), 0f, width.toFloat(), 0f,
            intArrayOf(
                colors.backgroundStart,
                colors.backgroundStart.withAlpha(51)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(coverLeft.toFloat(), 0f, width.toFloat(), height.toFloat(), Paint().apply {
            this.shader = shader
        })
        return result
    }

    private fun Canvas.drawSquareArtwork(
        artwork: Bitmap,
        squareSize: Int,
        fill: Boolean,
        backgroundColor: Int
    ) {
        drawColor(backgroundColor)
        val scale = if (fill) {
            max(squareSize / artwork.width.toFloat(), squareSize / artwork.height.toFloat())
        } else {
            min(squareSize / artwork.width.toFloat(), squareSize / artwork.height.toFloat())
        }
        val scaledWidth = artwork.width * scale
        val scaledHeight = artwork.height * scale
        val left = (squareSize - scaledWidth) / 2f
        val top = (squareSize - scaledHeight) / 2f
        drawBitmap(
            artwork,
            null,
            RectF(left, top, left + scaledWidth, top + scaledHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
    }

    private fun renderSoftCover(
        rawColors: List<Int>,
        softCoverTone: Int,
        width: Int,
        height: Int
    ): Bitmap = MediaSoftArtworkFactory.renderStatic(
        palette = MediaSoftPaletteExtractor.fromColors(rawColors),
        tone = softCoverTone.toFlowTone(),
        width = width,
        height = height
    )

    private fun Canvas.drawCircleGradient(
        start: Int,
        end: Int,
        centerXFraction: Float = 0.5f,
        centerYFraction: Float = 0.5f,
        startAlpha: Int = 48,
        endAlpha: Int = 235,
        radiusScale: Float = 0.85f
    ) {
        val shader = RadialGradient(
            width * centerXFraction,
            height * centerYFraction,
            max(width, height) * radiusScale,
            intArrayOf(
                start.withAlpha(startAlpha),
                end.withAlpha(endAlpha)
            ), null, Shader.TileMode.CLAMP
        )
        drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
    }

    private fun artworkProfile(bitmap: Bitmap, fingerprint: Long): ArtworkProfile? {
        synchronized(cacheLock) {
            profileCache.get(fingerprint)?.let { return it }
        }
        val extracted = ColorExtractor.extractThemePalette(bitmap, 3).rawColors
        if (extracted.isEmpty()) return null
        // WallpaperColors.fromBitmap quantizes every pixel again. The extracted
        // palette is already enough, and the second pass is what stalls song changes.
        val wallpaperColors = WallpaperColors(
            Color.valueOf(extracted[0]),
            extracted.getOrNull(1)?.let { Color.valueOf(it) },
            extracted.getOrNull(2)?.let { Color.valueOf(it) }
        )
        val palette = monet.palette(wallpaperColors) ?: return null
        val profile = ArtworkProfile(palette, bitmap.brightness(), extracted.toList())
        synchronized(cacheLock) {
            if (!closed) profileCache.put(fingerprint, profile)
        }
        return profile
    }

    private fun cachedBackground(key: RenderCacheKey): RenderedNotificationMediaBackground? {
        return synchronized(cacheLock) {
            if (closed) return@synchronized null
            renderCache.get(key)?.let { cached ->
                RenderedNotificationMediaBackground(
                    cached.bitmap.copy(Bitmap.Config.ARGB_8888, true),
                    cached.colors,
                    key.fingerprint
                )
            }
        }
    }

    private fun cacheBackground(
        key: RenderCacheKey,
        bitmap: Bitmap,
        colors: NotificationMediaColorConfig
    ) {
        synchronized(cacheLock) {
            if (closed) return
            val cacheBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            renderCache.put(key, CachedBackground(cacheBitmap, colors))
        }
    }

    private fun Drawable.toBitmapSafe(): Bitmap? =
        MediaArtworkSampler.sample(this, MAX_ARTWORK_PIXELS)

    private fun Bitmap.centerCrop(targetWidth: Int, targetHeight: Int): Bitmap {
        val scale = max(targetWidth / width.toFloat(), targetHeight / height.toFloat())
        val scaledWidth = (width * scale).toInt().coerceAtLeast(targetWidth)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(targetHeight)
        val scaled = scale(scaledWidth, scaledHeight)
        val result = Bitmap.createBitmap(
            scaled,
            (scaled.width - targetWidth) / 2,
            (scaled.height - targetHeight) / 2,
            targetWidth,
            targetHeight
        ).copy(Bitmap.Config.ARGB_8888, true)
        if (scaled !== this) scaled.recycle()
        return result
    }

    private fun Bitmap.scaleOwned(targetWidth: Int, targetHeight: Int): Bitmap {
        val scaled = scale(targetWidth, targetHeight)
        return if (scaled === this) copy(Bitmap.Config.ARGB_8888, true) else scaled
    }

    private fun Bitmap.brightness(): Float {
        val step = 5
        var total = 0f
        var count = 0
        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixel = getPixel(x, y)
                total += Color.red(pixel) * 0.299f +
                        Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f
                count++
            }
        }
        return if (count == 0) 0f else total / count
    }

    private fun Bitmap.fingerprint(): Long {
        var hash = 1125899906842597L
        hash = hash * 31 + width
        hash = hash * 31 + height
        val stepX = max(1, width / 16)
        val stepY = max(1, height / 16)
        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                hash = hash * 31 + getPixel(x, y).toLong()
            }
        }
        return hash
    }

    /**
     * CPU blur. A nested [android.graphics.HardwareRenderer] inside SystemUI waits on
     * the same GPU queue as the status bar and freezes the device on every song change.
     */
    private fun Bitmap.fastBlur(radius: Float): Bitmap {
        val safeRadius = radius.coerceAtLeast(0f)
        val software = if (config == Bitmap.Config.ARGB_8888) {
            this
        } else {
            copy(Bitmap.Config.ARGB_8888, false)
        }
        if (safeRadius < 0.5f || software.width <= 0 || software.height <= 0) {
            val copy = software.copy(Bitmap.Config.ARGB_8888, false)
            if (software !== this) software.recycle()
            return copy
        }
        val downscale = (56f / safeRadius).coerceIn(0.25f, 1f)
        val targetW = (software.width * downscale).toInt().coerceAtLeast(1)
        val targetH = (software.height * downscale).toInt().coerceAtLeast(1)
        val small = if (targetW == software.width && targetH == software.height) {
            software
        } else {
            software.scale(targetW, targetH)
        }
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        val blurredPixels = boxBlur(
            pixels,
            small.width,
            small.height,
            (safeRadius * downscale).toInt().coerceIn(1, 32),
        )
        val blurredSmall = createBitmap(small.width, small.height)
        blurredSmall.setPixels(blurredPixels, 0, small.width, 0, 0, small.width, small.height)
        val result = if (blurredSmall.width == width && blurredSmall.height == height) {
            blurredSmall
        } else {
            val scaled = blurredSmall.scale(width, height)
            if (scaled !== blurredSmall) blurredSmall.recycle()
            scaled
        }
        if (small !== software && small !== result) small.recycle()
        if (software !== this && software !== result) software.recycle()
        return result
    }

    private fun boxBlur(source: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0) return source
        val horizontal = IntArray(source.size)
        val result = IntArray(source.size)
        val window = radius * 2 + 1
        for (y in 0 until height) {
            var alpha = 0L
            var red = 0L
            var green = 0L
            var blue = 0L
            for (offset in -radius..radius) {
                val color = source[y * width + offset.coerceIn(0, width - 1)]
                alpha += Color.alpha(color)
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
            }
            for (x in 0 until width) {
                horizontal[y * width + x] = Color.argb(
                    (alpha / window).toInt(),
                    (red / window).toInt(),
                    (green / window).toInt(),
                    (blue / window).toInt(),
                )
                val removed = source[y * width + (x - radius).coerceIn(0, width - 1)]
                val added = source[y * width + (x + radius + 1).coerceIn(0, width - 1)]
                alpha += Color.alpha(added) - Color.alpha(removed)
                red += Color.red(added) - Color.red(removed)
                green += Color.green(added) - Color.green(removed)
                blue += Color.blue(added) - Color.blue(removed)
            }
        }
        for (x in 0 until width) {
            var alpha = 0L
            var red = 0L
            var green = 0L
            var blue = 0L
            for (offset in -radius..radius) {
                val color = horizontal[offset.coerceIn(0, height - 1) * width + x]
                alpha += Color.alpha(color)
                red += Color.red(color)
                green += Color.green(color)
                blue += Color.blue(color)
            }
            for (y in 0 until height) {
                result[y * width + x] = Color.argb(
                    (alpha / window).toInt(),
                    (red / window).toInt(),
                    (green / window).toInt(),
                    (blue / window).toInt(),
                )
                val removed = horizontal[(y - radius).coerceIn(0, height - 1) * width + x]
                val added = horizontal[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                alpha += Color.alpha(added) - Color.alpha(removed)
                red += Color.red(added) - Color.red(removed)
                green += Color.green(added) - Color.green(removed)
                blue += Color.blue(added) - Color.blue(removed)
            }
        }
        return result
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return this and 0x00ffffff or (alpha.coerceIn(0, 255) shl 24)
    }

    private fun Int.toFlowTone(): MediaFlowTone =
        if (this == MediaCardConstants.MEDIA_SOFT_COVER_TONE_LIGHT) {
            MediaFlowTone.LIGHT
        } else {
            MediaFlowTone.DARK
        }

    private data class ArtworkProfile(
        val palette: MonetPalette,
        val brightness: Float,
        val rawColors: List<Int>
    )

    private data class CachedBackground(
        val bitmap: Bitmap,
        val colors: NotificationMediaColorConfig
    )

    private data class RenderCacheKey(
        val fingerprint: Long,
        val style: Int,
        val blurAmount: Int,
        val autoInvert: Boolean,
        val softCoverTone: Int,
        val darkMode: Boolean,
        val width: Int,
        val height: Int
    )

    private data class MonetPalette(
        val neutral1: List<Int>,
        val neutral2: List<Int>,
        val accent1: List<Int>,
        val accent2: List<Int>
    )

    private class MonetApi private constructor(
        private val constructor: java.lang.reflect.Constructor<*>,
        private val styleContent: Any,
        private val allShadesField: java.lang.reflect.Field,
        private val neutral1Field: java.lang.reflect.Field?,
        private val neutral2Field: java.lang.reflect.Field?,
        private val accent1Field: java.lang.reflect.Field,
        private val accent2Field: java.lang.reflect.Field
    ) {
        @Suppress("UNCHECKED_CAST")
        fun palette(colors: WallpaperColors): MonetPalette? = runCatching {
            val scheme = if (constructor.parameterCount == 3) {
                constructor.newInstance(colors, true, styleContent)
            } else {
                constructor.newInstance(colors, styleContent)
            }
            val neutral1 = shades(neutral1Field ?: accent1Field, scheme)
            val neutral2 = shades(neutral2Field ?: accent2Field, scheme)
            val accent1 = shades(accent1Field, scheme)
            val accent2 = shades(accent2Field, scheme)
            MonetPalette(
                neutral1 = neutral1,
                neutral2 = neutral2,
                accent1 = accent1,
                accent2 = accent2
            )
        }.getOrNull()

        @Suppress("UNCHECKED_CAST")
        private fun shades(field: java.lang.reflect.Field, scheme: Any): List<Int> =
            allShadesField.get(field.get(scheme)) as List<Int>

        companion object {
            fun create(classLoader: ClassLoader): MonetApi {
                val schemeClass = classLoader.loadClass("com.android.systemui.monet.ColorScheme")
                val paletteClass = classLoader.loadClass("com.android.systemui.monet.TonalPalette")
                val styleClass = classLoader.loadClass("com.android.systemui.monet.Style")
                val constructor =
                    schemeClass.declaredConstructors.singleOrNull { it.parameterCount == 3 }
                        ?: schemeClass.declaredConstructors.single { it.parameterCount == 2 }
                constructor.isAccessible = true
                val valueOf = styleClass.getDeclaredMethod("valueOf", String::class.java).apply {
                    isAccessible = true
                }
                return MonetApi(
                    constructor,
                    valueOf.invoke(null, "CONTENT") ?: error("Monet CONTENT style is unavailable"),
                    paletteClass.requiredField("allShades"),
                    schemeClass.optionalField("mNeutral1", "neutral1"),
                    schemeClass.optionalField("mNeutral2", "neutral2"),
                    schemeClass.requiredField("mAccent1", "accent1"),
                    schemeClass.requiredField("mAccent2", "accent2")
                )
            }

            private fun Class<*>.requiredField(vararg names: String): java.lang.reflect.Field {
                names.forEach { name ->
                    runCatching { getDeclaredField(name) }.getOrNull()?.let {
                        it.isAccessible = true
                        return it
                    }
                }
                error("Missing field ${names.joinToString()} in $name")
            }

            private fun Class<*>.optionalField(
                vararg names: String
            ): java.lang.reflect.Field? {
                names.forEach { name ->
                    runCatching { getDeclaredField(name) }.getOrNull()?.let {
                        it.isAccessible = true
                        return it
                    }
                }
                return null
            }
        }
    }
}
