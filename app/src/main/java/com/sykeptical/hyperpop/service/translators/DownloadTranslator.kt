package com.sykeptical.hyperpop.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.service.download.DownloadPausePolicy
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

class DownloadTranslator(context: Context) : BaseTranslator(context) {

    private val finishKeywords by lazy {
        context.resources.getStringArray(R.array.progress_finish_keywords).toList()
    }

    fun translate(
        sbn: StatusBarNotification,
        title: String,
        picKey: String,
        config: IslandConfig,
        isUpdate: Boolean,
        retainedPercent: Int? = null,
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
        val reportedPercent = when {
            max > 0 -> ((current.toFloat() / max.toFloat()) * 100).toInt().coerceIn(0, 100)
            textPercent != null -> textPercent
            else -> null
        }
        val actionTitles = sbn.notification.actions?.map { it.title?.toString().orEmpty() }.orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val paused = DownloadPausePolicy.isPaused(
            isDownload = true,
            title = title,
            text = textContent,
            actionTitles = actionTitles,
            extraText = subText,
            finished = finishKeywords.any { textContent.contains(it, ignoreCase = true) },
            groupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
        val percent = (reportedPercent ?: retainedPercent ?: 0).coerceIn(0, 100)
        val isIndeterminate = !paused && indeterminate && textPercent == null
        val isTextFinished = !paused && finishKeywords.any { textContent.contains(it, ignoreCase = true) }
        val isFinished = !paused && (percent >= 100 || isTextFinished)
        val showMeter = !isFinished && !isIndeterminate &&
            (reportedPercent != null || (paused && retainedPercent != null))

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
        val pausedDetail = textContent.ifBlank { context.getString(R.string.download_paused) }
        val expanded = ExpandedFocusContent.transfer(
            title = title,
            detail = when {
                isFinished -> context.getString(R.string.download_complete)
                paused -> pausedDetail
                else -> textContent
            },
            pictureKey = picKey,
            percent = percent,
            showProgress = showMeter,
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
            if (paused && !showMeter) {
                builder.setBigIslandInfo(
                    left = IslandCompactLayout.left(picKey, title, config.marqueeEnabled == true),
                    right = IslandCompactLayout.right(pausedDetail),
                )
                builder.setSmallIsland(picKey)
            } else if (isIndeterminate) {
                val presentation = resolveIslandText(
                    sbn = sbn,
                    title = title,
                    content = textContent,
                    config = config,
                    state = context.getString(R.string.downloading),
                    progress = "",
                )
                // The small island's right slot is the progress indicator only. Status text
                // stays on the expanded island via chat info.
                builder.setBigIslandInfo(
                    left = IslandCompactLayout.left(
                        picKey,
                        presentation.left.ifBlank { title },
                        config.marqueeEnabled == true,
                    ),
                )
                builder.setSmallIsland(picKey)
            } else {
                builder.setBigIslandInfo(
                    left = IslandCompactLayout.left(picKey, ""),
                    progressText = ProgressTextInfo(
                        progressInfo = CircularProgressInfo(progress = percent),
                    ),
                )
                builder.setSmallIslandCircularProgress(picKey, percent, progressColor, isCCW = true)
            }
        }

        val highlight = progressColor
        val islandTimeout = if (paused) Int.MAX_VALUE else config.timeout
        if (paused) builder.setTimeout(Int.MAX_VALUE.toLong())
        builder.setIslandConfig(
            timeout = islandTimeout,
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
        val lifetime = if (paused) {
            IslandVisualMetadata.pinIslandLifetime(json, Int.MAX_VALUE)
        } else {
            json
        }
        return HyperIslandData(builder.buildResourceBundle(), lifetime, highlight)
    }
}
