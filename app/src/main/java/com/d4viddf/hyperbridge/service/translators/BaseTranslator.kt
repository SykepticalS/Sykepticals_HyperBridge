package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.graphics.shapes.toPath
import androidx.palette.graphics.Palette
import com.d4viddf.hyperbridge.data.theme.ThemeRepository
import com.d4viddf.hyperbridge.models.BridgeAction
import com.d4viddf.hyperbridge.models.IslandConfig
import com.d4viddf.hyperbridge.models.IslandTextPresentation
import com.d4viddf.hyperbridge.models.IslandTextPresentationResolver
import com.d4viddf.hyperbridge.models.IslandTextSource
import com.d4viddf.hyperbridge.models.theme.ActionButtonMode
import com.d4viddf.hyperbridge.models.theme.ActionConfig
import com.d4viddf.hyperbridge.models.theme.HyperTheme
import com.d4viddf.hyperbridge.models.theme.ResourceType
import com.d4viddf.hyperbridge.models.theme.ThemeResource
import com.d4viddf.hyperbridge.service.visual.IconGeometry
import com.d4viddf.hyperbridge.service.visual.LargeIconRole
import com.d4viddf.hyperbridge.service.visual.NotificationVisualPlanner
import com.d4viddf.hyperbridge.service.visual.NotificationVisualSource
import com.d4viddf.hyperbridge.service.visual.PixelBounds
import com.d4viddf.hyperbridge.service.visual.ResolvedNotificationVisual
import com.d4viddf.hyperbridge.ui.screens.theme.getShapeFromId
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperPicture
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Typeface
import androidx.core.graphics.withClip
import androidx.core.graphics.get

abstract class BaseTranslator(
    protected val context: Context,
    protected val repository: ThemeRepository? = null
) {

    protected fun resolveIslandText(
        sbn: StatusBarNotification,
        title: String,
        content: String,
        config: IslandConfig,
        sender: String = "",
        state: String = "",
        progress: String = "",
    ): IslandTextPresentation {
        val extras = sbn.notification.extras
        val appLabel = runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)
        val subtitle = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        return IslandTextPresentationResolver.resolve(
            source = IslandTextSource(title, content, subtitle, appLabel, sender, state, progress),
            left = config.leftContent,
            right = config.rightContent,
            leftExpression = config.leftCustomExpression,
            rightExpression = config.rightCustomExpression,
        )
    }

    enum class ActionDisplayMode { TEXT, ICON, BOTH }

    /** Stable across source-notification replacements because picKey is derived from bridgeId. */
    protected fun stableBusinessId(picKey: String): String = "bridge_${picKey.removePrefix("pic_")}"

    private val appColorCache = ConcurrentHashMap<String, String>()
    private val appIconBitmapCache = ConcurrentHashMap<String, Bitmap>()

    protected inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? {
        return getParcelable(key, T::class.java)
    }

    protected inline fun <reified T : Parcelable> Bundle.getParcelableArrayListCompat(key: String): ArrayList<T>? {
        return getParcelableArrayList(key, T::class.java)
    }

    // --- THEME HELPERS ---

    protected fun getThemeBitmap(theme: HyperTheme?, resourceKey: String): Bitmap? {
        if (theme == null || repository == null) return null
        val resource = ThemeResource(ResourceType.LOCAL_FILE, "icons/$resourceKey.png")
        return repository.getResourceBitmap(resource)
    }

    protected fun resolveColor(theme: HyperTheme?, pkg: String?, defaultHex: String): String {
        if (theme == null) return defaultHex

        // 1. App Specific Override (Highest Priority)
        if (pkg != null) {
            val override = theme.apps[pkg]
            // A. Specific Color Override
            val overrideColor = override?.highlightColor
            if (!overrideColor.isNullOrEmpty()) {
                return overrideColor.toSafeColorHex(defaultHex)
            }

            // B. App-Specific "Use App Colors"
            // If explicit true -> extract. If explicit false -> skip extraction (fall to global).
            if (override?.useAppColors == true) {
                return (getAppBrandColor(pkg) ?: theme.global.highlightColor ?: defaultHex).toSafeColorHex(defaultHex)
            }
        }

        // 2. Global "Use App Colors" -> Extract from Icon
        // Only run if app override didn't explicitly disable it (useAppColors != false)
        val appOverrideDisabled = theme.apps[pkg]?.useAppColors == false
        if (theme.global.useAppColors && !appOverrideDisabled && pkg != null) {
            return (getAppBrandColor(pkg) ?: theme.global.highlightColor ?: defaultHex).toSafeColorHex(defaultHex)
        }

        // 3. Global Theme Highlight -> Default Fallback
        return (theme.global.highlightColor ?: defaultHex).toSafeColorHex(defaultHex)
    }

    /**
     * Validates that a color string can be parsed by [android.graphics.Color.parseColor].
     * Returns the original string if valid, or [fallback] if parsing fails.
     * This prevents SystemUI crashes caused by invalid color values in theme configs.
     */
    private fun String.toSafeColorHex(fallback: String): String {
        return try {
            Color.parseColor(this)
            this
        } catch (_: IllegalArgumentException) {
            Log.w("BaseTranslator", "Invalid highlight color \"$this\", falling back to \"$fallback\"")
            fallback
        }
    }

    private fun getAppBrandColor(pkg: String): String? {
        // Check cache first
        val cached = appColorCache[pkg]
        if (cached != null) return cached

        // Extract and Cache
        val extracted = extractColorFromAppIcon(pkg)
        if (extracted != null) {
            appColorCache[pkg] = extracted
            return extracted
        }
        return null
    }

    private fun extractColorFromAppIcon(pkg: String): String? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(pkg)
            val bitmap = drawable.toBitmap(width = 128, height = 128)
            val palette = Palette.from(bitmap).clearFilters().generate()

            val swatches = listOf(
                palette.vibrantSwatch,
                palette.darkVibrantSwatch,
                palette.lightVibrantSwatch,
                palette.dominantSwatch,
                palette.mutedSwatch
            )

            val bestSwatch = swatches.firstOrNull { it != null && !isGrayscale(it.rgb) }
                ?: palette.dominantSwatch

            if (bestSwatch != null) {
                String.format("#%06X", (0xFFFFFF and bestSwatch.rgb))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isGrayscale(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val diff = abs(r - g) + abs(g - b) + abs(b - r)
        return diff < 30
    }

    // --- NEW: Resolve Shape Logic ---
    protected fun resolveShape(theme: HyperTheme?, pkg: String): String {
        return theme?.apps?.get(pkg)?.iconShapeId ?: theme?.global?.iconShapeId ?: "circle"
    }

    protected fun resolvePadding(theme: HyperTheme?, pkg: String): Int {
        return theme?.apps?.get(pkg)?.iconPaddingPercent ?: theme?.global?.iconPaddingPercent ?: 15
    }

    protected fun resolveActionConfig(theme: HyperTheme?, pkg: String, actionTitle: String): ActionConfig? {
        if (theme == null) return null

        val appOverride = theme.apps[pkg]?.actions?.entries?.find { (keyword, _) ->
            actionTitle.contains(keyword, ignoreCase = true)
        }?.value

        if (appOverride != null) return appOverride

        return theme.defaultActions.entries.find { (keyword, _) ->
            actionTitle.contains(keyword, ignoreCase = true)
        }?.value
    }

    protected fun resolveIcon(
        sbn: StatusBarNotification,
        picKey: String,
        preferNativeAppBadge: Boolean = false
    ): HyperPicture = pictureFromVisual(
        picKey,
        resolveAvatarVisual(sbn),
        sbn.packageName,
        preferNativeAppBadge = preferNativeAppBadge,
    )

    protected data class CompactIslandAssets(
        val avatar: HyperPicture,
        val attachment: HyperPicture?,
        val left: io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft,
        val right: io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight,
        val smallKey: String,
    )

    protected fun compactIslandAssets(
        sbn: StatusBarNotification,
        picKey: String,
        presentation: IslandTextPresentation,
    ): CompactIslandAssets {
        val started = android.os.SystemClock.elapsedRealtime()
        val avatar = resolveAvatarVisual(sbn)
        val avatarReady = android.os.SystemClock.elapsedRealtime()
        val attachmentKey = "${picKey}_media"
        val attachment = resolveAttachmentVisual(sbn, avatar.bitmap)
        Log.d("HyperBridgeTiming", "visual package=${sbn.packageName} avatarMs=${avatarReady - started} attachmentMs=${android.os.SystemClock.elapsedRealtime() - avatarReady}")
        val (left, right) = IslandCompactLayout.sides(
            picKey = picKey,
            presentation = presentation,
            rightPicKey = attachmentKey.takeIf { attachment != null },
        )
        return CompactIslandAssets(
            avatar = pictureFromVisual(picKey, avatar, sbn.packageName),
            attachment = attachment?.let { pictureFromVisual(attachmentKey, it, sbn.packageName, skipAppBadge = true) },
            left = left,
            right = right,
            smallKey = picKey,
        )
    }

    private fun pictureFromVisual(
        picKey: String,
        visual: ResolvedNotificationVisual,
        packageName: String,
        preferNativeAppBadge: Boolean = false,
        skipAppBadge: Boolean = false,
    ): HyperPicture {
        var originalBitmap = visual.bitmap
        if (visual.shouldShowAppBadge && !preferNativeAppBadge && !skipAppBadge) {
            originalBitmap = compositeAppBadge(originalBitmap, packageName)
        }
        if ((visual.source == NotificationVisualSource.SMALL_ICON ||
                    visual.source == NotificationVisualSource.FALLBACK) &&
            isBitmapDarkAndMonochrome(originalBitmap)
        ) {
            originalBitmap = tintBitmap(originalBitmap, Color.WHITE)
        }
        return HyperPicture(picKey, originalBitmap)
    }

    protected fun isBitmapDarkAndMonochrome(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return false

        var darkPixels = 0
        var totalPixels = 0
        var isMonochrome = true

        val stepX = maxOf(1, width / 20)
        val stepY = maxOf(1, height / 20)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap[x, y]
                val alpha = Color.alpha(pixel)
                if (alpha > 50) {
                    totalPixels++
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    val diff = abs(r - g) + abs(g - b) + abs(b - r)
                    if (diff > 45) {
                        isMonochrome = false
                    }

                    val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
                    if (luminance < 80) {
                        darkPixels++
                    }
                }
            }
        }

        if (totalPixels == 0 || !isMonochrome) return false
        return (darkPixels.toFloat() / totalPixels) > 0.7f
    }

    // --- THEME APPLICATION LOGIC ---

    protected fun applyThemeToActionIcon(source: Bitmap, shapeId: String, paddingPercent: Int, bgColor: Int): Bitmap {
        val size = 96
        val output = createBitmap(size, size)
        val canvas = Canvas(output)

        val polygon = getShapeFromId(shapeId)
        val path = polygon.toPath()
        val bounds = RectF()
        path.computeBounds(bounds, true)

        val matrix = Matrix()
        val destRect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        matrix.setRectToRect(bounds, destRect, Matrix.ScaleToFit.FILL)
        path.transform(matrix)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, bgPaint)

        val paddingPx = (size * (paddingPercent / 100f))
        val iconDestRect = RectF(paddingPx, paddingPx, size - paddingPx, size - paddingPx)

        if (iconDestRect.width() > 0 && iconDestRect.height() > 0) {
            val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            drawNormalizedBitmap(canvas, source, iconDestRect, iconPaint)
        }

        return output
    }

    // Overloaded to handle overrides automatically
    protected fun applyThemeToActionIcon(source: Bitmap, theme: HyperTheme, pkg: String, bgColor: Int): Bitmap {
        val shapeId = resolveShape(theme, pkg)
        val padding = resolvePadding(theme, pkg)
        return applyThemeToActionIcon(source, shapeId, padding, bgColor)
    }

    // --- CORE LOGIC ---

    protected fun extractBridgeActions(
        sbn: StatusBarNotification,
        config: com.d4viddf.hyperbridge.models.IslandConfig,
        theme: HyperTheme? = null,
        mode: ActionDisplayMode = ActionDisplayMode.BOTH,
        actionKeyPrefix: String? = null,
        fallbackActionGlyphs: Boolean = false,
    ): List<BridgeAction> {
        val bridgeActions = mutableListOf<BridgeAction>()
        val actions = sbn.notification.actions ?: return emptyList()

        val defaultActionBg = if (theme != null) {
            try {
                // Use updated resolveColor logic
                val hex = resolveColor(theme, sbn.packageName, "#007AFF")
                hex.toColorInt()
            } catch (e: Exception) { "#007AFF".toColorInt() }
        } else {
            "#007AFF".toColorInt()
        }

        actions.forEachIndexed { index, androidAction ->
            val hasRemoteInput = androidAction.remoteInputs != null && androidAction.remoteInputs!!.isNotEmpty()
            val rawTitle = androidAction.title?.toString() ?: ""
            val isMarkAsRead = androidAction.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ || rawTitle.equals("mark as read", ignoreCase = true)

            if (config.removeOriginalNotification == true && hasRemoteInput) {
                return@forEachIndexed
            }

            val uniqueKey = "${actionKeyPrefix ?: "act_${sbn.key.hashCode()}"}_$index"

            val actionConfig = resolveActionConfig(theme, sbn.packageName, rawTitle)

            val finalBgColorInt = if (actionConfig?.backgroundColor != null) {
                try {
                    actionConfig.backgroundColor.toColorInt()
                } catch(e: Exception) { defaultActionBg }
            } else {
                defaultActionBg
            }

            val finalBgColorHex = String.format("#%08X", (0xFFFFFFFF and finalBgColorInt.toLong()))
            val finalTintColorHex = actionConfig?.tintColor ?: "#FFFFFF"

            val effectiveMode = when (actionConfig?.mode) {
                ActionButtonMode.ICON -> ActionDisplayMode.ICON
                ActionButtonMode.TEXT -> ActionDisplayMode.TEXT
                ActionButtonMode.BOTH -> ActionDisplayMode.BOTH
                null -> mode
            }

            var actionIcon: Icon? = null
            var hyperPic: HyperPicture? = null

            val finalTitle = if (effectiveMode == ActionDisplayMode.ICON) "" else rawTitle
            val shouldLoadIcon = (effectiveMode != ActionDisplayMode.TEXT)

            var bitmapToUse: Bitmap? = null
            val configIconRes = actionConfig?.icon
            if (configIconRes != null && configIconRes.type == ResourceType.LOCAL_FILE && repository != null) {
                bitmapToUse = repository.getResourceBitmap(configIconRes)
            }

            if (bitmapToUse == null && shouldLoadIcon) {
                val originalIcon = androidAction.getIcon()
                if (originalIcon != null) {
                    bitmapToUse = loadIconBitmap(originalIcon, sbn.packageName)
                }
            }
            if ((bitmapToUse == null || !isUsableBitmap(bitmapToUse)) && fallbackActionGlyphs && shouldLoadIcon) {
                fallbackActionGlyphRes(rawTitle)?.let { resId ->
                    bitmapToUse = ContextCompat.getDrawable(context, resId)?.mutate()?.toBitmap(width = 96, height = 96)
                }
            }

            if (bitmapToUse != null) {
                val processedBitmap = if (theme != null) {
                    // [FIX] Pass package name to respect app-specific shape overrides
                    applyThemeToActionIcon(bitmapToUse, theme, sbn.packageName, finalBgColorInt)
                } else {
                    createRoundedIconWithBackground(bitmapToUse, finalBgColorInt, 12)
                }

                actionIcon = Icon.createWithBitmap(processedBitmap)
                hyperPic = HyperPicture("${uniqueKey}_icon", processedBitmap)
            }

            val inlineReply = hasRemoteInput && config.enableInlineReply != false
            val finalIntent = if (inlineReply) {
                com.d4viddf.hyperbridge.ui.InlineReplyIntents.pendingIntent(
                    context,
                    uniqueKey.hashCode(),
                    androidAction.actionIntent,
                    androidAction.remoteInputs!![0].resultKey,
                    sbn.packageName,
                )
            } else if (hasRemoteInput) {
                sbn.notification.contentIntent ?: androidAction.actionIntent
            } else {
                androidAction.actionIntent
            }

            val appliedBgColor = if (effectiveMode == ActionDisplayMode.TEXT) null else finalBgColorHex

            val hyperAction = HyperAction(
                key = uniqueKey,
                title = finalTitle,
                icon = actionIcon,
                pendingIntent = finalIntent,
                actionIntentType = 1,
                actionBgColor = appliedBgColor,
                titleColor = finalTintColorHex
            )

            bridgeActions.add(BridgeAction(hyperAction, hyperPic))
        }
        return bridgeActions
    }

    // --- UTILS ---

    protected fun getTransparentPicture(key: String): HyperPicture {
        val conf = Bitmap.Config.ARGB_8888
        val transparentBitmap = createBitmap(96, 96, conf)
        return HyperPicture(key, transparentBitmap)
    }

    protected fun getDrawablePicture(key: String, resId: Int): HyperPicture {
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate()
        val bitmap = drawable?.toBitmap() ?: createFallbackBitmap()
        return HyperPicture(key, bitmap)
    }

    protected fun getColoredPicture(key: String, resId: Int, colorHex: String): HyperPicture {
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate()
        val color = try { colorHex.toColorInt() } catch (e: Exception) { Color.WHITE }
        drawable?.setTint(color)
        val bitmap = drawable?.toBitmap() ?: createFallbackBitmap()
        return HyperPicture(key, bitmap)
    }

    /**
     * Places a country flag behind Xiaomi's native app-icon badge position. The provider icon
     * remains a separate native ChatInfo layer, so both identities stay visible as a stack.
     */
    protected fun getCountryFlagBadgedPicture(
        key: String,
        resId: Int,
        colorHex: String,
        countryFlagBitmap: Bitmap?,
        flagEmoji: String?
    ): HyperPicture {
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate()
        val color = try { colorHex.toColorInt() } catch (_: Exception) { Color.WHITE }
        drawable?.setTint(color)
        val glyph = drawable?.toBitmap()?.let { source ->
            createBitmap(96, 96).also { target ->
                drawNormalizedBitmap(
                    Canvas(target),
                    source,
                    RectF(0f, 0f, 96f, 96f),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
            }
        } ?: createFallbackBitmap()
        if (!isUsableBitmap(countryFlagBitmap) && flagEmoji.isNullOrBlank()) return HyperPicture(key, glyph)

        val output = runCatching {
            val width = glyph.width.coerceAtLeast(1)
            val height = glyph.height.coerceAtLeast(1)
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            canvas.drawBitmap(glyph, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

            val minSide = minOf(width, height).toFloat()
            val radius = minSide * 0.235f
            // Offset up and left so Xiaomi's native provider badge overlaps instead of hiding it.
            val centerX = width - minSide * 0.31f
            val centerY = height - minSide * 0.31f
            canvas.drawCircle(centerX, centerY, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
            })
            canvas.withClip(centerX - radius, centerY - radius, centerX + radius, centerY + radius) {
                if (isUsableBitmap(countryFlagBitmap)) {
                    drawNormalizedBitmap(
                        canvas,
                        checkNotNull(countryFlagBitmap),
                        RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                } else {
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        textAlign = Paint.Align.CENTER
                        textSize = radius * 1.65f
                        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    }
                    val metrics = textPaint.fontMetrics
                    val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
                    canvas.drawText(checkNotNull(flagEmoji), centerX, baseline, textPaint)
                }
            }
            bitmap
        }.getOrDefault(glyph)
        return HyperPicture(key, output)
    }

    protected fun getThemedActionPicture(
        key: String,
        resId: Int,
        theme: HyperTheme?,
        packageName: String?,
        backgroundColor: Int
    ): HyperPicture {
        val source = ContextCompat.getDrawable(context, resId)?.mutate()?.toBitmap(width = 96, height = 96)
            ?: createFallbackBitmap()
        val bitmap = if (theme != null && packageName != null) {
            applyThemeToActionIcon(source, theme, packageName, backgroundColor)
        } else {
            applyThemeToActionIcon(source, "circle", 24, backgroundColor)
        }
        return HyperPicture(key, bitmap)
    }


    protected fun getNotificationBitmap(sbn: StatusBarNotification): Bitmap? {
        return resolveAvatarVisual(sbn).bitmap
    }

    protected fun resolveNotificationVisual(sbn: StatusBarNotification): ResolvedNotificationVisual =
        resolveAvatarVisual(sbn)

    private fun resolveAvatarVisual(sbn: StatusBarNotification): ResolvedNotificationVisual {
        val pkg = sbn.packageName
        try {
            if (isGroupConversation(sbn)) {
                loadShortcutBitmap(sbn)?.let { return ResolvedNotificationVisual(it, NotificationVisualSource.PERSON) }
                val groupLargeBig = loadLargeIconBigBitmap(sbn)
                if (groupLargeBig != null &&
                    NotificationVisualPlanner.isLikelyAvatar(groupLargeBig.width, groupLargeBig.height)
                ) {
                    return ResolvedNotificationVisual(groupLargeBig, NotificationVisualSource.LARGE_ICON)
                }
                val groupLarge = loadLargeIconBitmap(sbn)
                if (groupLarge != null &&
                    NotificationVisualPlanner.isLikelyAvatar(groupLarge.width, groupLarge.height)
                ) {
                    return ResolvedNotificationVisual(groupLarge, NotificationVisualSource.LARGE_ICON)
                }
            }
            loadPersonBitmap(sbn)?.let { return ResolvedNotificationVisual(it, NotificationVisualSource.PERSON) }
            loadShortcutBitmap(sbn)?.let { return ResolvedNotificationVisual(it, NotificationVisualSource.PERSON) }

            val picture = loadPictureBitmap(sbn)
            val largeBig = loadLargeIconBigBitmap(sbn)
            if (largeBig != null &&
                NotificationVisualPlanner.isLikelyAvatar(largeBig.width, largeBig.height) &&
                !sameBitmap(largeBig, picture)
            ) {
                return ResolvedNotificationVisual(largeBig, NotificationVisualSource.LARGE_ICON)
            }

            val large = loadLargeIconBitmap(sbn)
            if (large != null) {
                val role = NotificationVisualPlanner.largeIconRole(
                    hasPicture = picture != null,
                    hasPersonIcon = false,
                    width = large.width,
                    height = large.height,
                    sameAsPicture = sameBitmap(large, picture),
                    mediaShareWithoutPersonIcon = isMediaShareNotification(sbn),
                )
                if (role == LargeIconRole.AVATAR) {
                    return ResolvedNotificationVisual(large, NotificationVisualSource.LARGE_ICON)
                }
            }

            remoteAvatarBitmap(sbn, picture)?.let {
                return ResolvedNotificationVisual(it, NotificationVisualSource.PERSON)
            }

            if (sbn.notification.smallIcon != null) {
                val bitmap = loadIconBitmap(sbn.notification.smallIcon, pkg)
                if (bitmap != null && isUsableBitmap(bitmap)) {
                    return ResolvedNotificationVisual(bitmap, NotificationVisualSource.SMALL_ICON)
                }
            }

            val appIcon = getAppIconBitmap(pkg)
            if (appIcon != null && isUsableBitmap(appIcon)) {
                return ResolvedNotificationVisual(appIcon, NotificationVisualSource.APP_ICON)
            }
        } catch (e: Exception) {
            Log.e("BaseTranslator", "Error extracting avatar", e)
            val appIcon = getAppIconBitmap(pkg)
            if (appIcon != null && isUsableBitmap(appIcon)) {
                return ResolvedNotificationVisual(appIcon, NotificationVisualSource.APP_ICON)
            }
        }
        return ResolvedNotificationVisual(createFallbackBitmap(), NotificationVisualSource.FALLBACK)
    }

    private fun resolveAttachmentVisual(sbn: StatusBarNotification, avatar: Bitmap): ResolvedNotificationVisual? {
        val picture = loadPictureBitmap(sbn)
        if (picture != null && isUsableBitmap(picture) && !sameBitmap(picture, avatar)) {
            return ResolvedNotificationVisual(picture, NotificationVisualSource.PICTURE)
        }
        val large = loadLargeIconBitmap(sbn)
        if (large != null && isUsableBitmap(large)) {
            val personIcon = loadPersonBitmap(sbn) != null || loadShortcutBitmap(sbn) != null
            val role = NotificationVisualPlanner.largeIconRole(
                hasPicture = picture != null,
                hasPersonIcon = personIcon,
                width = large.width,
                height = large.height,
                sameAsPicture = sameBitmap(large, picture),
                mediaShareWithoutPersonIcon = isMediaShareNotification(sbn) && !personIcon,
            )
            if (role == LargeIconRole.ATTACHMENT &&
                NotificationVisualPlanner.isDistinctMedia(
                    large.width,
                    large.height,
                    avatar.width,
                    avatar.height,
                    sameBitmap(large, avatar),
                )
            ) {
                return ResolvedNotificationVisual(large, NotificationVisualSource.LARGE_ICON)
            }
        }
        return remoteAttachmentBitmap(sbn, avatar)?.let {
            ResolvedNotificationVisual(it, NotificationVisualSource.PICTURE)
        }
    }

    private fun isGroupConversation(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        if (extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return true
        return try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
                ?.isGroupConversation == true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadPictureBitmap(sbn: StatusBarNotification): Bitmap? {
        val extras = sbn.notification.extras
        extras.getParcelableCompat<Bitmap>(Notification.EXTRA_PICTURE)
            ?.takeIf(::isUsableBitmap)
            ?.let { return it }
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            extras.getParcelableCompat<Icon>(Notification.EXTRA_PICTURE_ICON)
                ?.let { loadIconBitmap(it, sbn.packageName) }
                ?.takeIf(::isUsableBitmap)
                ?.let { return it }
        }
        return null
    }

    private fun loadLargeIconBitmap(sbn: StatusBarNotification): Bitmap? {
        sbn.notification.getLargeIcon()?.let { loadIconBitmap(it, sbn.packageName) }
            ?.takeIf(::isUsableBitmap)
            ?.let { return it }
        @Suppress("DEPRECATION")
        return sbn.notification.extras.getParcelableCompat<Bitmap>(Notification.EXTRA_LARGE_ICON)
            ?.takeIf(::isUsableBitmap)
    }

    private fun loadLargeIconBigBitmap(sbn: StatusBarNotification): Bitmap? {
        val extras = sbn.notification.extras
        extras.getParcelableCompat<Icon>(Notification.EXTRA_LARGE_ICON_BIG)
            ?.let { loadIconBitmap(it, sbn.packageName) }
            ?.takeIf(::isUsableBitmap)
            ?.let { return it }
        return extras.getParcelableCompat<Bitmap>(Notification.EXTRA_LARGE_ICON_BIG)
            ?.takeIf(::isUsableBitmap)
    }

    private fun loadShortcutBitmap(sbn: StatusBarNotification): Bitmap? =
        com.d4viddf.hyperbridge.service.NotificationConversationShortcut.iconBitmap(context, sbn)
            ?.takeIf(::isUsableBitmap)

    private fun isMediaShareNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        return listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
            sbn.notification.tickerText,
        ).any { com.d4viddf.hyperbridge.service.NotificationIdentityResolver.isMediaShareCaption(it) }
    }

    private fun sameBitmap(left: Bitmap?, right: Bitmap?): Boolean {
        if (left == null || right == null) return false
        return left === right || runCatching { left.sameAs(right) }.getOrDefault(false)
    }

    private fun remoteViewsExtract(sbn: StatusBarNotification) =
        com.d4viddf.hyperbridge.service.NotificationRemoteViewsParser.collect(
            sbn.notification,
            context,
            // A reconstructed MessagingStyle/InboxStyle layout only repeats extras already
            // inspected above. Building and reflecting three layouts costs ~2s per WhatsApp
            // post on HyperOS. Only inspect custom RemoteViews actually supplied by the app.
            recoverIfMissing = false,
        )

    private fun remoteAvatarBitmap(sbn: StatusBarNotification, picture: Bitmap?): Bitmap? =
        remoteViewsExtract(sbn).bitmaps
            .filter { isUsableBitmap(it) && NotificationVisualPlanner.isLikelyAvatar(it.width, it.height) }
            .filter { !sameBitmap(it, picture) }
            .minByOrNull { it.width.toLong() * it.height.toLong() }

    private fun remoteAttachmentBitmap(sbn: StatusBarNotification, avatar: Bitmap): Bitmap? =
        remoteViewsExtract(sbn).bitmaps
            .filter {
                isUsableBitmap(it) &&
                    NotificationVisualPlanner.isDistinctMedia(
                        it.width,
                        it.height,
                        avatar.width,
                        avatar.height,
                        sameBitmap(it, avatar),
                    )
            }
            .maxByOrNull { it.width.toLong() * it.height.toLong() }

    private fun loadPersonBitmap(sbn: StatusBarNotification): Bitmap? {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val self = selfPersonNames(sbn)
        val fromMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.toList()
            .orEmpty()
            .asReversed()
            .firstNotNullOfOrNull { parcelable ->
                val bundle = parcelable as? Bundle ?: return@firstNotNullOfOrNull null
                val person = bundle.getParcelableCompat<Person>("sender_person") ?: return@firstNotNullOfOrNull null
                if (self.any { it.equals(person.name?.toString()?.trim().orEmpty(), ignoreCase = true) }) {
                    return@firstNotNullOfOrNull null
                }
                person.icon?.let { loadIconBitmap(it, pkg) }?.takeIf(::isUsableBitmap)
            }
        if (fromMessages != null) return fromMessages
        val people = buildList {
            extras.getParcelableCompat<Person>(Notification.EXTRA_CALL_PERSON)?.let(::add)
            extras.getParcelableArrayListCompat<Person>(Notification.EXTRA_PEOPLE_LIST)
                ?.filterNot { person ->
                    self.any { it.equals(person.name?.toString()?.trim().orEmpty(), ignoreCase = true) }
                }
                ?.let(::addAll)
        }
        return people.firstNotNullOfOrNull { person ->
            person.icon?.let { loadIconBitmap(it, pkg) }?.takeIf(::isUsableBitmap)
        }
    }

    private fun selfPersonNames(sbn: StatusBarNotification): Set<String> {
        val extras = sbn.notification.extras
        val names = linkedSetOf<String>()
        extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(names::add)
        extras.getParcelableCompat<Person>(Notification.EXTRA_MESSAGING_PERSON)
            ?.name?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(names::add)
        return names
    }

    protected fun createRoundedIconWithBackground(source: Bitmap, backgroundColor: Int, paddingDp: Int = 8): Bitmap {
        val size = 96
        val output = createBitmap(size, size)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            color = backgroundColor
        }

        val center = size / 2f
        canvas.drawCircle(center, center, center, paint)

        val density = context.resources.displayMetrics.density
        val paddingPx = (paddingDp * density).toInt()

        val targetSize = size - (paddingPx * 2)
        if (targetSize > 0) {
            val whiteSource = tintBitmap(source, Color.WHITE)
            val destRect = Rect(paddingPx, paddingPx, size - paddingPx, size - paddingPx)
            drawNormalizedBitmap(canvas, whiteSource, RectF(destRect), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        return output
    }

    private fun drawNormalizedBitmap(canvas: Canvas, source: Bitmap, destRect: RectF, paint: Paint?) {
        if (!isUsableBitmap(source) || destRect.width() <= 0f || destRect.height() <= 0f) return

        val visible = getVisibleBitmapBounds(source)
        val srcRect = if (visible != null) {
            Rect(visible.left, visible.top, visible.right + 1, visible.bottom + 1)
        } else {
            Rect(0, 0, source.width, source.height)
        }

        if (srcRect.width() <= 0 || srcRect.height() <= 0) return

        val fitted = IconGeometry.fitCenterInside(
            srcRect.width(),
            srcRect.height(),
            destRect.left,
            destRect.top,
            destRect.right,
            destRect.bottom
        )
        val finalRect = RectF(fitted.left, fitted.top, fitted.right, fitted.bottom)
        if (finalRect.width() <= 0f || finalRect.height() <= 0f) return
        canvas.drawBitmap(source, srcRect, finalRect, paint)
    }

    private fun getVisibleBitmapBounds(bitmap: Bitmap): PixelBounds? {
        if (!isUsableBitmap(bitmap)) return null
        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            IconGeometry.findVisibleBounds(pixels, bitmap.width, bitmap.height)
        } catch (_: Exception) {
            null
        }
    }

    protected fun isUsableBitmap(bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
        return bitmap.width <= 2_048 &&
                bitmap.height <= 2_048 &&
                bitmap.width.toLong() * bitmap.height.toLong() <= 4_194_304L
    }

    private fun tintBitmap(source: Bitmap, color: Int): Bitmap {
        val result = createBitmap(source.width, source.height)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            isFilterBitmap = true
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    protected fun loadIconBitmap(
        icon: Icon,
        packageName: String,
        width: Int? = null,
        height: Int? = null
    ): Bitmap? {
        return try {
            val resPackage = runCatching { icon.resPackage }.getOrNull()?.takeIf { it.isNotBlank() }
            val drawable = when {
                icon.type != Icon.TYPE_RESOURCE -> icon.loadDrawable(context)
                else -> {
                    val contexts = listOfNotNull(
                        context,
                        runCatching { context.createPackageContext(packageName, 0) }.getOrNull(),
                        resPackage?.takeIf { it != packageName }?.let {
                            runCatching { context.createPackageContext(it, 0) }.getOrNull()
                        },
                        runCatching { context.createPackageContext("android", 0) }.getOrNull(),
                    ).distinct()
                    contexts.firstNotNullOfOrNull { candidate ->
                        runCatching { icon.loadDrawable(candidate) }.getOrNull()
                    }
                }
            }
            drawable?.toBitmap(width = width, height = height)
        } catch (e: Exception) {
            null
        }
    }

    private fun fallbackActionGlyphRes(title: String): Int? {
        val value = title.lowercase()
        return when {
            value.isBlank() -> android.R.drawable.ic_menu_more
            listOf("pause", "pausar", "pausa").any { it in value } -> android.R.drawable.ic_media_pause
            listOf("resume", "play", "contin", "reanud", "reprendre").any { it in value } -> android.R.drawable.ic_media_play
            listOf("cancel", "stop", "remove", "delete", "cancelar", "detener", "eliminar").any { it in value } ->
                android.R.drawable.ic_menu_close_clear_cancel
            listOf("open", "view", "abrir", "ver").any { it in value } -> android.R.drawable.ic_menu_view
            else -> android.R.drawable.ic_menu_more
        }
    }

    private fun getAppIconBitmap(packageName: String): Bitmap? {
        appIconBitmapCache[packageName]?.let { return it }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = drawable.toBitmap()
            if (isUsableBitmap(bitmap)) {
                appIconBitmapCache[packageName] = bitmap
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun compositeAppBadge(source: Bitmap, packageName: String): Bitmap {
        val appIcon = getAppIconBitmap(packageName) ?: return source
        if (!isUsableBitmap(source) || !isUsableBitmap(appIcon)) return source

        return try {
            val width = source.width.coerceAtLeast(1)
            val height = source.height.coerceAtLeast(1)
            val output = createBitmap(width, height)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(source, null, Rect(0, 0, width, height), paint)

            val minSide = minOf(width, height)
            val badgeSize = (minSide * 0.36f).roundToInt().coerceIn(18, minSide)
            val iconDest = RectF(
                (width - badgeSize).toFloat(),
                (height - badgeSize).toFloat(),
                width.toFloat(),
                height.toFloat()
            )
            // Preserve the application's full-colour adaptive-icon artwork without adding
            // another background plate or tint around it.
            drawNormalizedBitmap(canvas, appIcon, iconDest, paint)
            output
        } catch (_: Exception) {
            source
        }
    }

    protected fun extractTextPercentage(title: String?, text: String?): Int? {
        val pattern = Regex("""\b(\d{1,3})\s*%""")
        val textMatch = text?.let { pattern.find(it) }
        val titleMatch = title?.let { pattern.find(it) }
        val match = textMatch ?: titleMatch
        if (match != null) {
            val value = match.groupValues[1].toIntOrNull()
            if (value != null && value in 0..100) {
                return value
            }
        }
        return null
    }

    protected fun createFallbackBitmap(): Bitmap = createBitmap(1, 1)

    protected fun Drawable.toBitmap(width: Int? = null, height: Int? = null): Bitmap {
        if (this is BitmapDrawable && this.bitmap != null) {
            if (width != null && height != null) {
                return this.bitmap.scale(width, height)
            }
            return this.bitmap
        }

        val w = width ?: if (intrinsicWidth > 0) intrinsicWidth else 96
        val h = height ?: if (intrinsicHeight > 0) intrinsicHeight else 96

        val bitmap = createBitmap(w, h)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}
