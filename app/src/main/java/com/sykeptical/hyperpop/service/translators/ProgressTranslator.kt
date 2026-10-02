package com.sykeptical.hyperpop.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.models.HyperIslandData
import com.sykeptical.hyperpop.models.IslandConfig
import com.sykeptical.hyperpop.models.IslandVisualMetadata
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo

class ProgressTranslator(context: Context) : BaseTranslator(context) {

    private val finishKeywords by lazy {
        context.resources.getStringArray(R.array.progress_finish_keywords).toList()
    }

    fun translate(
        sbn: StatusBarNotification,
        title: String,
        picKey: String,
        config: IslandConfig,
        isUpdate: Boolean
    ): HyperIslandData {

        val progressColor = "#007AFF"
        val finishColor = "#34C759"

        val builder = HyperIslandNotification.Builder(context, stableBusinessId(picKey), title)

        val floatPresentation = IslandFloatingPresentationPolicy.resolve(config.firstFloat ?: false, config.floatOnUpdate ?: false, isUpdate)
        val isFloatEnabled = floatPresentation.enableFloat
        builder.setEnableFloat(floatPresentation.enableFloat)
        builder.setIslandFirstFloat(floatPresentation.islandFirstFloat)
        builder.setReopen(floatPresentation.reopen)

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
            builder.addPicture(getColoredPicture(tickKey, R.drawable.rounded_check_circle_24, finishColor))
        }

        val actions = extractBridgeActions(
            sbn = sbn,
            config = config,
            actionKeyPrefix = "act_${picKey.removePrefix("pic_")}",
            fallbackActionGlyphs = true,
            transportControls = true,
        )
        val expanded = ExpandedFocusContent.transfer(
            title = title,
            detail = if (isFinished) "Complete" else textContent,
            pictureKey = picKey,
            percent = percent,
            showProgress = !isFinished && !isIndeterminate,
            progressColor = progressColor,
            actionKeys = actions.map { it.action.key },
        )
        builder.setShowNotification(expanded.showInShade)

        builder.setChatInfo(
            title = expanded.title,
            content = expanded.detail,
            pictureKey = expanded.pictureKey,
            actionKeys = expanded.actionKeys.takeIf { it.isNotEmpty() },
            appPkg = sbn.packageName
        )

        expanded.progressPercent?.let { progress ->
            builder.setProgressBar(progress, expanded.progressColor ?: progressColor)
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
                    left = IslandCompactLayout.left(
                        picKey,
                        presentation.left.ifBlank { title },
                        config.marqueeEnabled == true,
                    ),
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
                builder.setSmallIslandCircularProgress(picKey, percent, progressColor, isCCW = true)
            }
        }

        val highlight = progressColor
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
            progressColor,
        )
        return HyperIslandData(builder.buildResourceBundle(), json, highlight)
    }
}
