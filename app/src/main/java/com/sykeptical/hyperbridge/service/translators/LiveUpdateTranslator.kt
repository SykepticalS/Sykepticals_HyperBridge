package com.sykeptical.hyperbridge.service.translators

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.toColorInt
import com.sykeptical.hyperbridge.R
import com.sykeptical.hyperbridge.data.theme.ThemeRepository
import com.sykeptical.hyperbridge.models.NavContent
import com.sykeptical.hyperbridge.models.NotificationType
import com.sykeptical.hyperbridge.service.call.CallActionIconSizingPolicy
import com.sykeptical.hyperbridge.service.call.CallSession
import com.sykeptical.hyperbridge.service.call.CallState
import com.sykeptical.hyperbridge.service.call.CallTimerPolicy

class LiveUpdateTranslator(
    context: Context,
    repo: ThemeRepository
) : BaseTranslator(context, repo) {

    fun translateToLiveUpdate(
        sbn: StatusBarNotification?,
        channelId: String,
        type: NotificationType,
        navRight: NavContent? = null,
        config: com.sykeptical.hyperbridge.models.IslandConfig? = null,
        callSession: CallSession? = null,
        resolvedTitle: String? = null,
        resolvedText: String? = null
    ): NotificationCompat.Builder {
        val original = sbn?.notification
        val extras = original?.extras

        val title = resolvedTitle
            ?: extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val sourceText = resolvedText
            ?: extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val text = if (type == NotificationType.CALL && callSession != null) {
            when (callSession.state) {
                CallState.INCOMING_RINGING -> context.getString(R.string.call_incoming)
                CallState.OUTGOING_CALLING -> context.getString(R.string.call_calling)
                CallState.OUTGOING_RINGING -> context.getString(R.string.call_ringing)
                CallState.CONNECTING -> context.getString(R.string.call_connecting)
                CallState.ACTIVE -> context.getString(R.string.call_ongoing)
                CallState.ENDED -> context.getString(R.string.call_ended)
            }
        } else {
            sourceText
        }

        val progressMax = extras?.getInt(Notification.EXTRA_PROGRESS_MAX, 0) ?: 0
        val progress = extras?.getInt(Notification.EXTRA_PROGRESS, 0) ?: 0
        val indeterminate = extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ?: false

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(original?.category)

        original?.contentIntent?.let { builder.setContentIntent(it) }

        if (type == NotificationType.CALL && callSession != null) {
            val connectedAt = CallTimerPolicy.connectedAtForTimer(callSession)
            val isActive = connectedAt != null
            builder.setShowWhen(isActive)
            builder.setUsesChronometer(isActive)
            if (isActive) builder.setWhen(connectedAt)
        }

        // --- THEME COLOR & ICON INJECTION ---
        val theme = repository?.activeTheme?.value

        val originalBitmap = sbn?.let { getNotificationBitmap(it) }
        if (originalBitmap != null) {
            builder.setLargeIcon(originalBitmap)
        }

        if (type == NotificationType.NAVIGATION) {
            // 1. Inject Theme Nav Color
            val themeColorStr = theme?.defaultNavigation?.progressBarColor
                ?: resolveColor(theme, sbn?.packageName, "#34C759") // Green fallback

            val themeColorInt = try {
                themeColorStr.toColorInt()
            } catch (_: Exception) {
                original?.color ?: 0
            }
            builder.setColor(themeColorInt)

            // 2. Inject Theme Nav Icon
            val navStartBitmap = getThemeBitmap(theme, "nav_start")
            if (navStartBitmap != null) {
                builder.setSmallIcon(IconCompat.createWithBitmap(navStartBitmap))
            } else {
                builder.setSmallIcon(original?.smallIcon?.let { IconCompat.createFromIcon(context, it) } ?: IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground))
            }
        } else {
            // Standard fallback for non-navigation
            builder.setColor(original?.color ?: 0)
            builder.setSmallIcon(original?.smallIcon?.let { IconCompat.createFromIcon(context, it) } ?: IconCompat.createWithResource(context, R.drawable.ic_launcher_foreground))
        }

        // --- ACTIONS ---
        val rawActions = original?.actions ?: emptyArray()
        rawActions.forEachIndexed { index, action ->
            val sourceIcon = action.getIcon()
            val iconCompat = if (type == NotificationType.CALL && sourceIcon != null && sbn != null) {
                val normalizedBitmap = loadIconBitmap(sourceIcon, sbn.packageName)?.let {
                    createNormalizedActionGlyph(
                        it,
                        CallActionIconSizingPolicy.NATIVE_PADDING_PERCENT
                    )
                }
                normalizedBitmap?.let(IconCompat::createWithBitmap)
                    ?: IconCompat.createFromIcon(context, sourceIcon)
            } else if (sourceIcon != null) {
                IconCompat.createFromIcon(context, sourceIcon)
            } else {
                IconCompat.createWithResource(context, action.icon)
            }
            
            val hasRemoteInput = action.remoteInputs != null && action.remoteInputs!!.isNotEmpty()
            val finalIntent = if (hasRemoteInput) {
                if (config?.enableInlineReply != false) {
                    val uniqueKey = "act_${sbn?.key.hashCode()}_$index"
                    val replyIntent = android.content.Intent(context, com.sykeptical.hyperbridge.receiver.InlineReplyReceiver::class.java).apply {
                        putExtra("pending_intent", action.actionIntent)
                        putExtra("result_key", action.remoteInputs!![0].resultKey)
                        putExtra("package_name", sbn?.packageName)
                    }
                    android.app.PendingIntent.getBroadcast(
                        context,
                        uniqueKey.hashCode(),
                        replyIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                    )
                } else {
                    original?.contentIntent ?: action.actionIntent
                }
            } else {
                action.actionIntent
            }

            builder.addAction(NotificationCompat.Action.Builder(iconCompat, action.title, finalIntent).build())
        }

        // --- APPLY STYLES ---
        // BigTextStyle ensures text isn't completely hidden by the progress bar
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(text).setBigContentTitle(title))

        // Add back the progress bar to show the user where they are
        if (progressMax > 0 || indeterminate) {
            builder.setProgress(progressMax, progress, indeterminate)
        }

        // --- ANDROID 16 LIVE UPDATE INJECTION ---
        val shortAlertText = generateCriticalShortText(title, text, progress, progressMax, type, navRight, sbn)

        builder.setRequestPromotedOngoing(true)
        builder.setShortCriticalText(shortAlertText)

        return builder
    }

    private fun generateCriticalShortText(
        title: String,
        text: String,
        progress: Int,
        max: Int,
        type: NotificationType,
        navRight: NavContent?,
        sbn: StatusBarNotification?
    ): String {

        if (type == NotificationType.MEDIA) return title.ifBlank { "Media" }

        // Advanced Extraction for Navigation Layouts
        if (type == NotificationType.NAVIGATION && sbn != null) {
            val extras = sbn.notification.extras
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.replace("\n", " ")?.trim() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.replace("\n", " ")?.trim() ?: ""

            val timeRegex = Regex("(\\d{1,2}:\\d{2})|(\\d+h\\s*\\d+m)", RegexOption.IGNORE_CASE)
            val distanceRegex = Regex("^\\d+([,.]\\d+)?\\s*(m|km|ft|mi|yd|yards|miles|meters)", RegexOption.IGNORE_CASE)

            var distance = ""
            var eta = ""

            // Extract ETA
            if (timeRegex.containsMatchIn(subText)) eta = subText
            else if (timeRegex.containsMatchIn(text) && !distanceRegex.containsMatchIn(text)) eta = text

            // Extract Distance
            val candidates = listOf(bigText, title, text).filter { it.isNotEmpty() }
            val contentSource = candidates.firstOrNull { str -> distanceRegex.containsMatchIn(str) } ?: title.ifEmpty { text }

            if (distanceRegex.containsMatchIn(contentSource)) {
                distanceRegex.find(contentSource)?.let { distance = it.value }
            }

            // Return the value based on the user's customized Right Side layout!
            return when (navRight) {
                NavContent.ETA -> eta.ifEmpty { distance }
                NavContent.DISTANCE -> distance.ifEmpty { eta }
                NavContent.DISTANCE_ETA -> listOf(distance, eta).filter { it.isNotEmpty() }.joinToString(" • ")
                NavContent.INSTRUCTION -> title
                else -> eta.ifEmpty { distance }.ifEmpty { title } // Fallback
            }
        }

        // Standard Progress Fallback (Only applied if NOT Navigation)
        val textPercent = extractTextPercentage(title, text)
        if (max > 0) return "${(progress * 100) / max}%"
        if (textPercent != null) return "$textPercent%"

        // Timer Fallback
        val timeRegex = Regex("(\\d+\\s*(min|m))", RegexOption.IGNORE_CASE)
        timeRegex.find(text)?.let { return it.groupValues[1] }

        return title
    }
}
