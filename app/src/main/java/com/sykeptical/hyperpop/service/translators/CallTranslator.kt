package com.sykeptical.hyperpop.service.translators

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.models.BridgeAction
import com.sykeptical.hyperpop.models.HyperIslandData
import com.sykeptical.hyperpop.models.IslandConfig
import com.sykeptical.hyperpop.models.IslandVisualMetadata
import com.sykeptical.hyperpop.service.call.CallActionIconSizingPolicy
import com.sykeptical.hyperpop.service.call.CallIslandTimeoutPolicy
import com.sykeptical.hyperpop.service.call.CallActionRole
import com.sykeptical.hyperpop.service.call.CallActionSelectionPolicy
import com.sykeptical.hyperpop.service.call.CallActionSignal
import com.sykeptical.hyperpop.service.call.CallMicrophoneState
import com.sykeptical.hyperpop.service.call.CallNotificationClassifier
import com.sykeptical.hyperpop.service.call.CallSession
import com.sykeptical.hyperpop.service.call.CallState
import com.sykeptical.hyperpop.service.call.CallTimerPolicy
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.TimerInfo

class CallTranslator(context: Context) : BaseTranslator(context) {

    private val hangUpKeywords by lazy { context.resources.getStringArray(R.array.call_keywords_hangup).toList() }
    private val answerKeywords by lazy { context.resources.getStringArray(R.array.call_keywords_answer).toList() }
    private val speakerKeywords by lazy { context.resources.getStringArray(R.array.call_keywords_speaker).toList() }
    private val classifier by lazy {
        CallNotificationClassifier(
            answerKeywords = answerKeywords,
            declineKeywords = hangUpKeywords,
            hangUpKeywords = hangUpKeywords,
            muteKeywords = context.resources.getStringArray(R.array.call_keywords_mute).toList(),
            unmuteKeywords = context.resources.getStringArray(R.array.call_keywords_unmute).toList(),
            speakerKeywords = speakerKeywords
        )
    }

    fun translate(
        sbn: StatusBarNotification,
        picKey: String,
        config: IslandConfig,
        session: CallSession,
        isUpdate: Boolean,
        resolvedTitle: String? = null
    ): HyperIslandData {
        val extras = sbn.notification.extras
        val title = resolvedTitle?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: "Call"
        val now = System.currentTimeMillis()

        val isIncoming = session.state == CallState.INCOMING_RINGING

        val builder = HyperIslandNotification.Builder(context, stableBusinessId(picKey), title)
        builder.applyFloatingPresentation(config.firstFloat ?: false, config.floatOnUpdate ?: false, isUpdate)

        val hiddenKey = "hidden_pixel"
        builder.addPicture(resolveIcon(sbn, picKey, preferNativeAppBadge = false))
        builder.addPicture(getTransparentPicture(hiddenKey))

        val bridgeActions = getFilteredCallActions(sbn, picKey, isIncoming)
        val actionKeys = bridgeActions.map { it.action.key }

        val callStateText = when (session.state) {
            CallState.INCOMING_RINGING -> context.getString(R.string.call_incoming)
            CallState.OUTGOING_CALLING -> context.getString(R.string.call_calling)
            CallState.OUTGOING_RINGING -> context.getString(R.string.call_ringing)
            CallState.CONNECTING -> context.getString(R.string.call_connecting)
            CallState.ACTIVE -> context.getString(R.string.call_ongoing)
            CallState.ENDED -> context.getString(R.string.call_ended)
        }
        val presentation = resolveIslandText(
            sbn = sbn,
            title = title,
            content = callStateText,
            config = config,
            sender = title,
            state = callStateText,
        )
        val rightText = presentation.right.ifBlank { callStateText }
        val leftText = presentation.left.ifBlank { title }
        var timerInfo: TimerInfo? = null
        val connectedAtForTimer = CallTimerPolicy.connectedAtForTimer(session)
        if (connectedAtForTimer != null) {
            val duration = (now - connectedAtForTimer).coerceAtLeast(0L)
            timerInfo = TimerInfo(1, connectedAtForTimer, duration, now)
        }

        bridgeActions.forEach {
            builder.addAction(it.action)
            it.actionImage?.let { pic -> builder.addPicture(pic) }
        }

        val expanded = ExpandedFocusContent.call(
            title = title,
            detail = rightText,
            pictureKey = picKey,
            actionKeys = actionKeys,
        )
        builder.setShowNotification(expanded.showInShade)
        builder.setChatInfo(
            title = expanded.title,
            content = expanded.detail,
            pictureKey = expanded.pictureKey,
            actionKeys = expanded.actionKeys,
            // The avatar already carries the composited app badge. A real package here makes
            // Xiaomi draw a second native badge beside it.
            appPkg = hiddenKey,
            timer = timerInfo
        )

        builder.setSmallIsland(picKey)

        val highlight = "#FFFFFF"
        val persistentTimeout = CallIslandTimeoutPolicy.PERSISTENT_TIMEOUT_MILLIS
        builder.setTimeout(persistentTimeout.toLong())
        builder.setIslandConfig(
            timeout = persistentTimeout,
            dismissible = false,
            highlightColor = highlight,
            expandedTimeMs = config.floatTimeout
        )

        if (isIncoming) {
            builder.setBigIslandInfo(
                left = IslandCompactLayout.left(picKey, title, config.marqueeEnabled == true),
                right = IslandCompactLayout.right(context.getString(R.string.call_incoming)),
            )
        } else if (connectedAtForTimer != null) {
            builder.setBigIslandCountUp(connectedAtForTimer, picKey)
        } else {
            builder.setBigIslandInfo(
                left = IslandCompactLayout.left(picKey, leftText, config.marqueeEnabled == true),
                right = IslandCompactLayout.right(rightText),
            )
        }

        val jsonParam = IslandVisualMetadata.pinIslandLifetime(
            builder.buildJsonParam().let { raw ->
                if (!isIncoming && connectedAtForTimer != null) {
                    IslandVisualMetadata.injectCompactLeftTitle(
                        raw,
                        IslandCompactLayout.compactLeftText(title, config.marqueeEnabled == true),
                    )
                } else {
                    raw
                }
            },
            CallIslandTimeoutPolicy.PERSISTENT_TIMEOUT_MILLIS,
        )
        return HyperIslandData(builder.buildResourceBundle(), jsonParam)
    }

    private fun getFilteredCallActions(
        sbn: StatusBarNotification,
        picKey: String,
        isIncoming: Boolean
    ): List<BridgeAction> {
        val rawActions = sbn.notification.actions ?: return emptyList()
        val results = mutableListOf<BridgeAction>()

        val hangUpColor = "#FF3B30"
        val answerColor = "#34C759"
        val neutralColor = "#8E8E93"

        val actionSignals = rawActions.map { action ->
            CallActionSignal(
                title = action.title?.toString().orEmpty(),
                semanticAction = action.semanticAction,
                hasPendingIntent = action.actionIntent != null
            )
        }
        val selectedActions = CallActionSelectionPolicy.select(
            actions = actionSignals,
            isIncoming = isIncoming,
            classifier = classifier,
            packageName = sbn.packageName,
        )

        selectedActions.forEach { selected ->
            val index = selected.index
            val action = rawActions[index]
            val role = selected.role
            val microphoneState = selected.microphoneState
            val stateKey = if (role == CallActionRole.MICROPHONE) {
                "_${microphoneState.name.lowercase()}"
            } else {
                ""
            }
            val uniqueKey = "act_${picKey.removePrefix("pic_")}_${index}$stateKey"
            val isHangUp = role == CallActionRole.DECLINE_OR_HANG_UP
            val isAnswer = role == CallActionRole.ANSWER
            val isMicrophone = role == CallActionRole.MICROPHONE

            val bgColorHex = when {
                isHangUp -> hangUpColor
                isAnswer -> answerColor
                isMicrophone && microphoneState == CallMicrophoneState.MUTED -> "#FF9500"
                else -> neutralColor
            }
            val bgColorInt = try { bgColorHex.toColorInt() } catch(e: Exception) { 0xFF8E8E93.toInt() }

            var originalBitmap: Bitmap? = null
            if (isMicrophone && microphoneState != CallMicrophoneState.UNKNOWN) {
                val microphoneIcon = if (microphoneState == CallMicrophoneState.MUTED) {
                    R.drawable.ic_call_microphone_muted
                } else {
                    R.drawable.ic_call_microphone_live
                }
                originalBitmap = ContextCompat.getDrawable(context, microphoneIcon)
                    ?.mutate()
                    ?.toBitmap(width = 96, height = 96)
            } else {
                val originalIcon = action.getIcon()
                if (originalIcon != null) {
                    originalBitmap = loadIconBitmap(
                        originalIcon,
                        sbn.packageName,
                        width = 96,
                        height = 96
                    )
                }
                if (originalBitmap == null && (isHangUp || isAnswer)) {
                    val fallback = if (isHangUp) R.drawable.ic_call_hang_up else R.drawable.ic_call_answer
                    originalBitmap = ContextCompat.getDrawable(context, fallback)
                        ?.mutate()
                        ?.toBitmap(width = 96, height = 96)
                }
            }

            var actionIcon: Icon? = null
            var hyperPic: HyperPicture? = null

            val configuredPadding = 15
            val padding = if (isAnswer || isHangUp) {
                CallActionIconSizingPolicy.answerRejectPaddingPercent(configuredPadding)
            } else {
                CallActionIconSizingPolicy.classicPaddingPercent(configuredPadding)
            }

            if (originalBitmap != null) {
                // This bitmap has a fixed 96 px canvas, so use percentage padding. The old
                // path converted 12 dp using screen density and could shrink the actual
                // phone glyph to roughly 24 px on a xxhdpi device.
                val processedBitmap = styleActionIcon(
                    originalBitmap,
                    padding,
                    bgColorInt
                )

                val actionPictureKey = "${uniqueKey}_icon"
                actionIcon = Icon.createWithBitmap(processedBitmap)
                hyperPic = HyperPicture(actionPictureKey, processedBitmap)
            }

            val hyperAction = io.github.d4viddf.hyperisland_kit.HyperAction(
                key = uniqueKey,
                title = CallActionIconSizingPolicy.islandActionTitle(
                    role,
                    action.title?.toString().orEmpty(),
                    hasIcon = actionIcon != null,
                ),
                icon = actionIcon,
                pendingIntent = action.actionIntent,
                actionIntentType = 1,
                actionBgColor = bgColorHex,
                titleColor = "#FFFFFF"
            )

            results.add(BridgeAction(hyperAction, hyperPic))
        }
        return results
    }
}
