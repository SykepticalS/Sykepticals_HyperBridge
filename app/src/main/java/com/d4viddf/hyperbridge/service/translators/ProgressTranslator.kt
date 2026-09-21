package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.theme.ThemeRepository
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.models.IslandConfig
import com.d4viddf.hyperbridge.models.IslandVisualMetadata
import com.d4viddf.hyperbridge.models.theme.HyperTheme
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo

class ProgressTranslator(context: Context, repo: ThemeRepository) : BaseTranslator(context, repo) {

    private val finishKeywords by lazy {
        context.resources.getStringArray(R.array.progress_finish_keywords).toList()
    }

    fun translate(
        sbn: StatusBarNotification,
        title: String,
        picKey: String,
        config: IslandConfig,
        theme: HyperTheme?,
        isUpdate: Boolean
    ): HyperIslandData {

        // [FIX] Prioritize Progress Colors -> Global Highlight -> Default
        val themeProgressColor = theme?.defaultProgress?.activeColor
            ?: resolveColor(theme, sbn.packageName, "#007AFF")

        val themeFinishColor = theme?.defaultProgress?.finishedColor
            ?: resolveColor(theme, sbn.packageName, "#34C759")

        val customTick = getThemeBitmap(theme, "tick_icon")

        val builder = HyperIslandNotification.Builder(context, stableBusinessId(picKey), title)

        builder.setShowNotification(config.isShowShade ?: true)
        
        // Always enable float if the user wants it, but only "First Float" (expand) on the initial appearance
        val floatPresentation = IslandFloatingPresentationPolicy.resolve(config.firstFloat ?: false, config.floatOnUpdate ?: false, isUpdate)
        val isFloatEnabled = floatPresentation.enableFloat
        builder.setEnableFloat(floatPresentation.enableFloat)
        builder.setIslandFirstFloat(floatPresentation.islandFirstFloat)

        val extras = sbn.notification.extras
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        val textContent = (extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "")

        val textPercent = extractTextPercentage(title, textContent)
        val percent = (if (max > 0) {
            ((current.toFloat() / max.toFloat()) * 100).toInt()
        } else {
            textPercent ?: 0
        }).coerceIn(0, 100)
        val isIndeterminate = indeterminate && textPercent == null
        val isTextFinished = finishKeywords.any { textContent.contains(it, ignoreCase = true) }
        val isFinished = percent >= 100 || isTextFinished

        val tickKey = "${picKey}_tick"
        val hiddenKey = "hidden_pixel"

        builder.addPicture(resolveIcon(sbn, picKey))
        builder.addPicture(getTransparentPicture(hiddenKey))

        if (isFinished) {
            if (customTick != null) {
                builder.addPicture(HyperPicture(tickKey, customTick))
            } else {
                // Tint default tick with specific finish color
                builder.addPicture(getColoredPicture(tickKey, R.drawable.rounded_check_circle_24, themeFinishColor))
            }
        }

        val actions = extractBridgeActions(
            sbn = sbn,
            config = config,
            theme = theme,
            actionKeyPrefix = "act_${picKey.removePrefix("pic_")}",
            fallbackActionGlyphs = true,
        )

        builder.setChatInfo(
            title = title,
            content = if (isFinished) "Complete" else textContent,
            pictureKey = picKey,
            appPkg = sbn.packageName
        )

        if (!isFinished && !isIndeterminate) {
            builder.setProgressBar(percent, themeProgressColor)
        }

        if (isFinished) {
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(1, PicInfo(1, hiddenKey)),
                right = ImageTextInfoRight(2, PicInfo(1, tickKey))
            )
            builder.setSmallIsland(tickKey)
        } else {
            if (isIndeterminate) {
                val presentation = resolveIslandText(
                    sbn = sbn,
                    title = title,
                    content = textContent,
                    config = config,
                    state = "Processing...",
                    progress = "",
                )
                builder.setBigIslandInfo(
                    left = IslandCompactLayout.left(picKey, presentation.left.ifBlank { title }),
                    right = IslandCompactLayout.right(presentation.right.ifBlank { "Processing..." }),
                )
                builder.setSmallIsland(picKey)
            } else {
                builder.setBigIslandInfo(
                    left = IslandCompactLayout.left(picKey, ""),
                    progressText = ProgressTextInfo(
                        progressInfo = CircularProgressInfo(progress = percent),
                        textInfo = IslandCompactLayout.text(
                            textContent.ifBlank { "$percent%" },
                        ),
                    ),
                )
                builder.setSmallIslandCircularProgress(picKey, percent, themeProgressColor, isCCW = true)
            }
        }

        val highlight = resolveColor(theme, sbn.packageName, themeProgressColor)
        builder.setIslandConfig(
            timeout = config.timeout,
            highlightColor = highlight,
            dismissible = isFinished,
            expandedTimeMs = if (isFloatEnabled) config.floatTimeout else null,
        )
        actions.forEach { it.actionImage?.let { pic -> builder.addPicture(pic) } }
        val hyperActions = actions.map { it.action }.toTypedArray()
        hyperActions.forEach {
            builder.addAction(it)
        }
        hyperActions.forEach { builder.addHiddenAction(it) }

        val json = IslandVisualMetadata.injectProgressColor(
            IslandVisualMetadata.injectUpdatable(builder.buildJsonParam(), updatable = !isFinished),
            themeProgressColor,
        )
        return HyperIslandData(builder.buildResourceBundle(), json, highlight)
    }
}
