package com.sykeptical.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.sykeptical.hyperbridge.R
import com.sykeptical.hyperbridge.data.theme.ThemeRepository
import com.sykeptical.hyperbridge.models.HyperIslandData
import com.sykeptical.hyperbridge.models.IslandConfig
import com.sykeptical.hyperbridge.models.theme.HyperTheme
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.d4viddf.hyperisland_kit.models.TimerInfo

class TimerTranslator(context: Context, repo: ThemeRepository) : BaseTranslator(context, repo) {

    fun translate(
        sbn: StatusBarNotification,
        picKey: String,
        config: IslandConfig,
        theme: HyperTheme?,
        isUpdate: Boolean
    ): HyperIslandData {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: context.getString(R.string.fallback_timer)

        // [FIX] Pass sbn.packageName to resolveColor
        val themeHighlight = resolveColor(theme, sbn.packageName, "#FF9500")

        val baseTime = sbn.notification.`when`
        val now = System.currentTimeMillis()
        val isCountdown = baseTime > now
        val timerType = if (isCountdown) -1 else 1

        val builder = HyperIslandNotification.Builder(context, stableBusinessId(picKey), title)
        builder.applyFloatingPresentation(config.isFloat ?: false, isUpdate)
        builder.setIslandConfig(timeout = config.timeout)
        builder.setShowNotification(config.isShowShade ?: true)

        val hiddenKey = "hidden_pixel"
        builder.addPicture(resolveIcon(sbn, picKey))
        builder.addPicture(getTransparentPicture(hiddenKey))

        val actions = extractBridgeActions(sbn, config, theme)

        builder.setChatInfo(
            title = title,
            timer = TimerInfo(timerType, baseTime, if(isCountdown) baseTime - now else now - baseTime, now),
            pictureKey = picKey,
            actionKeys = actions.map { it.action.key }
        )

        if (isCountdown) {
            builder.setBigIslandCountdown(baseTime, picKey)
        } else {
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(1, PicInfo(1, picKey), TextInfo("", "")),
                right = ImageTextInfoRight(1, PicInfo(1, hiddenKey), TextInfo(title, context.getString(R.string.status_active)))
            )
        }

        builder.setSmallIsland(picKey)

        actions.forEach {
            builder.addAction(it.action)
            it.actionImage?.let { pic -> builder.addPicture(pic) }
        }
        builder.setIslandConfig(highlightColor = theme?.global?.highlightColor, expandedTimeMs = config.floatTimeout)

        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }
}
