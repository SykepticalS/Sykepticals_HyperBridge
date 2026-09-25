package com.d4viddf.hyperbridge.service.translators

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.theme.ThemeRepository
import com.d4viddf.hyperbridge.models.BridgeAction
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.models.IslandConfig
import com.d4viddf.hyperbridge.models.IslandVisualMetadata
import com.d4viddf.hyperbridge.models.theme.HyperTheme
import com.d4viddf.hyperbridge.service.NotificationRemoteViewsParser
import com.d4viddf.hyperbridge.service.voice.VoiceIslandPlanner
import com.d4viddf.hyperbridge.service.voice.VoicePlaybackDetector
import com.d4viddf.hyperbridge.service.voice.VoicePlaybackRole
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo

class VoiceMessageTranslator(
    context: Context,
    repo: ThemeRepository,
) : BaseTranslator(context, repo) {

    fun translate(
        sbn: StatusBarNotification,
        title: String,
        text: String,
        picKey: String,
        config: IslandConfig,
        theme: HyperTheme?,
        isUpdate: Boolean,
        compactDuration: Boolean = false,
    ): HyperIslandData {
        val extras = sbn.notification.extras
        val remote = NotificationRemoteViewsParser.collect(
            sbn.notification,
            context = context,
            packageName = sbn.packageName,
        )
        val extrasMax = extras.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0)
        val extrasProgress = extras.getInt(android.app.Notification.EXTRA_PROGRESS, 0)
        val progress = if (extrasMax > 0) extrasProgress else remote.progress
        val progressMax = if (extrasMax > 0) extrasMax else remote.progressMax
        val percent = VoicePlaybackDetector.percent(progress, progressMax)
        val caption = VoicePlaybackDetector.playbackCaption(
            remote.texts + listOf(title, text, sbn.notification.tickerText?.toString().orEmpty())
        )
        val sender = VoicePlaybackDetector.senderName(caption)
            ?: title.takeIf { it.isNotBlank() }
            ?: caption
            ?: context.getString(R.string.voice_message_title)
        val expandedTitle = caption ?: sender
        val clock = VoicePlaybackDetector.playbackClock(progress, progressMax)
        val notificationControl = sbn.notification.actions?.firstOrNull { action ->
            action.actionIntent != null &&
                VoicePlaybackDetector.roleFor(action.title?.toString().orEmpty()) != null
        }
        val control = remote.clicks.firstOrNull { VoicePlaybackDetector.roleFor(it.label) != null }
        val role = control?.let { VoicePlaybackDetector.roleFor(it.label) }
            ?: notificationControl?.let { VoicePlaybackDetector.roleFor(it.title?.toString().orEmpty()) }
        val pendingIntent = control?.pendingIntent ?: notificationControl?.actionIntent
        val plan = VoiceIslandPlanner.plan(sender, percent, role)

        val themeProgressColor = theme?.defaultProgress?.activeColor
            ?: resolveColor(theme, sbn.packageName, "#007AFF")
        val highlightColor = resolveColor(theme, sbn.packageName, themeProgressColor)

        val builder = HyperIslandNotification.Builder(context, stableBusinessId(picKey), sender)
        builder.applyFloatingPresentation(config.firstFloat ?: false, config.floatOnUpdate ?: false, isUpdate)
        val floatPresentation = IslandFloatingPresentationPolicy.resolve(
            config.firstFloat ?: false,
            config.floatOnUpdate ?: false,
            isUpdate,
        )
        builder.setShowNotification(config.isShowShade ?: false)
        builder.setIslandConfig(
            timeout = config.timeout,
            highlightColor = highlightColor,
            dismissible = false,
            expandedTimeMs = floatPresentation.expandedTimeMs(config.floatTimeout),
        )

        builder.addPicture(resolveVoicePicture(sbn, picKey, R.drawable.ic_call_microphone_live))
        builder.setIconTextInfo(
            picKey = picKey,
            title = expandedTitle,
            content = clock,
        )
        if (compactDuration) {
            builder.setBigIslandInfo(
                left = IslandCompactLayout.left(picKey, plan.compactLeft),
                right = IslandCompactLayout.right(clock),
            )
        } else {
            builder.setBigIslandInfo(
                left = IslandCompactLayout.left(picKey, plan.compactLeft),
                progressText = ProgressTextInfo(
                    progressInfo = CircularProgressInfo(progress = plan.percent),
                    textInfo = IslandCompactLayout.text(""),
                ),
            )
        }
        builder.setSmallIsland(picKey)

        pendingIntent?.let { intent ->
            playbackAction(sbn, picKey, intent, role, themeProgressColor)?.let { bridge ->
                bridge.actionImage?.let(builder::addPicture)
                builder.addAction(bridge.action)
                builder.addHiddenAction(bridge.action)
            }
        }

        val json = IslandVisualMetadata.injectProgressColor(
            IslandVisualMetadata.injectUpdatable(builder.buildJsonParam(), updatable = true),
            themeProgressColor,
        )
        return HyperIslandData(builder.buildResourceBundle(), json, highlightColor)
    }

    private fun playbackAction(
        sbn: StatusBarNotification,
        picKey: String,
        pendingIntent: PendingIntent,
        role: VoicePlaybackRole?,
        colorHex: String,
    ): BridgeAction? {
        if (role == null) return null
        val isPause = role == VoicePlaybackRole.PAUSE
        val label = context.getString(if (isPause) R.string.voice_action_pause else R.string.voice_action_play)
        val iconRes = if (isPause) R.drawable.ic_voice_pause else R.drawable.ic_voice_play
        val key = "voice_${picKey.removePrefix("pic_")}"
        val iconKey = "${key}_icon"
        val picture = getColoredPicture(iconKey, iconRes, "#FFFFFF")
        val action = HyperAction(
            key = key,
            title = label,
            icon = Icon.createWithBitmap(playbackGlyph(iconRes)),
            pendingIntent = pendingIntent,
            actionIntentType = 1,
            actionBgColor = colorHex,
            titleColor = "#FFFFFF",
        )
        return BridgeAction(action, picture)
    }

    private fun playbackGlyph(resId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate()
        drawable?.setTint("#FFFFFF".toColorInt())
        return drawable?.toBitmap() ?: createFallbackBitmap()
    }
}
