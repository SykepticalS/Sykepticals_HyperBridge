package com.d4viddf.hyperbridge.service.translators

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.integration.xiaomi.HyperIslandProtocolOptions
import com.d4viddf.hyperbridge.integration.xiaomi.buildJsonParam
import com.d4viddf.hyperbridge.island.backend.HookConfigSync
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.models.ScreenRecordingDesignConfig
import com.d4viddf.hyperbridge.models.ScreenRecordingLeftDesign
import com.d4viddf.hyperbridge.models.ScreenRecordingRightDesign
import com.d4viddf.hyperbridge.receiver.ScreenRecordingActionReceiver
import com.d4viddf.hyperbridge.service.recording.ScreenRecordingSession
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.d4viddf.hyperisland_kit.models.TimerInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ScreenRecordingTranslator(context: Context) : BaseTranslator(context) {
    fun translate(
        session: ScreenRecordingSession,
        now: Long = System.currentTimeMillis(),
        design: ScreenRecordingDesignConfig = ScreenRecordingDesignConfig(),
        compactText: String? = null,
        expandedText: String? = null,
        isUpdate: Boolean = false,
        enableFloat: Boolean? = null,
        pauseIntent: PendingIntent? = null,
        stopIntent: PendingIntent? = null,
    ): HyperIslandData {
        val compact = compactText ?: if (session.countdownRemaining > 0) {
            context.getString(R.string.screen_recording_starting)
        } else {
            context.getString(R.string.screen_recording_compact)
        }
        val expanded = expandedText ?: if (session.countdownRemaining > 0) {
            context.getString(R.string.screen_recording_starting)
        } else {
            context.getString(R.string.screen_recording_active)
        }
        val builder = HyperIslandNotification.Builder(context, COUNTDOWN_BUSINESS, compact)
        val floatPresentation = IslandFloatingPresentationPolicy.resolve(
            firstFloat = true,
            floatOnUpdate = false,
            isUpdate = isUpdate,
        )
        val shouldFloat = enableFloat ?: floatPresentation.enableFloat
        builder.setEnableFloat(shouldFloat)
        builder.setIslandFirstFloat(shouldFloat)
        builder.setReopen(shouldFloat)
        builder.setShowNotification(false)
        builder.setIslandConfig(
            priority = 1,
            timeout = PERSISTENT_ISLAND_TIMEOUT_MILLIS,
            dismissible = false,
            highlightColor = HIGHLIGHT_COLOR,
        )

        val ticker = getColoredPicture(PIC_TICKER, tickerIcon(), HIGHLIGHT_COLOR)
        builder.addPicture(ticker)
        builder.addPicture(getTransparentPicture(PIC_HIDDEN))
        builder.addPicture(getColoredPicture(PIC_APP_BADGE, R.drawable.ic_screen_recording_app_badge_blank, "#FFFFFF"))

        val pausePending = pauseIntent ?: actionIntent(
            session,
            1,
            if (session.paused) ScreenRecordingActionReceiver.ACTION_RESUME else ScreenRecordingActionReceiver.ACTION_PAUSE,
        )
        val stopPending = stopIntent ?: actionIntent(session, 2, ScreenRecordingActionReceiver.ACTION_STOP)
        val actionKeys = mutableListOf<String>()
        if (session.capabilities.canPause) {
            val pausePicture = getDrawablePicture(
                if (session.paused) PIC_RESUME else PIC_PAUSE,
                if (session.paused) R.drawable.ic_focus_resume else R.drawable.ic_focus_pause,
            )
            builder.addPicture(pausePicture)
            builder.addAction(
                HyperAction(
                    key = ACTION_PAUSE,
                    title = "",
                    icon = pausePicture.icon,
                    pendingIntent = pausePending,
                    actionIntentType = 1,
                    actionBgColor = null,
                    actionBgColorDark = null,
                    titleColor = "#FFFFFF",
                    titleColorDark = "#FFFFFF",
                )
            )
            actionKeys += ACTION_PAUSE
        }
        if (session.capabilities.canStop) {
            val stopPicture = getDrawablePicture(PIC_STOP, R.drawable.ic_screen_recording_stop_dark)
            builder.addPicture(stopPicture)
            builder.addAction(
                HyperAction(
                    key = if (session.capabilities.canPause) ACTION_STOP else ACTION_PAUSE,
                    title = "",
                    icon = stopPicture.icon,
                    pendingIntent = stopPending,
                    actionIntentType = 1,
                    actionBgColor = null,
                    actionBgColorDark = null,
                    titleColor = "#FFFFFF",
                    titleColorDark = "#FFFFFF",
                )
            )
            actionKeys += if (session.capabilities.canPause) ACTION_STOP else ACTION_PAUSE
        }

        val timerType = if (session.paused) TIMER_TYPE_PAUSED else TIMER_TYPE_COUNT_UP
        val timer = TimerInfo(
            timerType = timerType,
            timerWhen = session.timerStartedAt,
            timerTotal = session.timerStartedAt,
            timerSystemCurrent = now,
        )
        builder.setChatInfo(
            title = expanded,
            content = "",
            pictureKey = PIC_TICKER,
            appPkg = PIC_APP_BADGE,
            actionKeys = actionKeys,
            timer = timer.takeIf { session.countdownRemaining <= 0 },
        )

        when {
            session.countdownRemaining > 0 -> builder.setBigIslandInfo(
                left = countdownLeft(design, compact),
                right = ImageTextInfoRight(
                    type = 2,
                    picInfo = PicInfo(type = 1, pic = PIC_HIDDEN),
                    textInfo = TextInfo(title = session.countdownRemaining.toString(), content = ""),
                ),
            )
            design.right == ScreenRecordingRightDesign.TIMER -> builder.setBigIslandCountUp(session.timerStartedAt, PIC_TICKER)
            else -> builder.setBigIslandInfo(left = countdownLeft(design, compact))
        }
        builder.setSmallIsland(PIC_TICKER)

        return HyperIslandData(
            builder.buildResourceBundle(),
            builder.buildJsonParam(
                HyperIslandProtocolOptions(
                    islandProperty = 2,
                    timerSystemCurrentMillis = now.takeIf { session.countdownRemaining <= 0 },
                )
            ),
            HIGHLIGHT_COLOR,
        )
    }

    fun buildFocusExtras(
        session: ScreenRecordingSession,
        now: Long = System.currentTimeMillis(),
        design: ScreenRecordingDesignConfig = ScreenRecordingDesignConfig(),
        compactText: String? = null,
        expandedText: String? = null,
        picturePackage: String = context.packageName,
        notifyId: String = "${context.packageName}:${session.logicalId.hashCode()}",
        business: String = BUSINESS,
        enableFloat: Boolean = session.countdownRemaining > 0,
        tickerIcon: Int = tickerIcon(),
        pauseIntent: PendingIntent? = null,
        stopIntent: PendingIntent? = null,
    ): Bundle {
        val compact = compactText ?: if (session.countdownRemaining > 0) {
            context.getString(R.string.screen_recording_starting)
        } else {
            context.getString(R.string.screen_recording_compact)
        }
        val expanded = expandedText ?: if (session.countdownRemaining > 0) {
            context.getString(R.string.screen_recording_starting)
        } else {
            context.getString(R.string.screen_recording_active)
        }
        val json = ScreenRecordingPayloadFactory.build(
            session = session,
            now = now,
            compactText = compact,
            expandedText = expanded,
            notifyId = notifyId,
            design = design,
            business = business,
            enableFloat = enableFloat,
        )
        val canStop = session.capabilities.canStop
        val canPause = session.capabilities.canPause
        val pictures = Bundle().apply {
            putParcelable(PIC_TICKER, getColoredPicture(PIC_TICKER, tickerIcon, HIGHLIGHT_COLOR).icon)
            putParcelable(
                PIC_APP_BADGE,
                Icon.createWithResource(picturePackage, R.drawable.ic_screen_recording_app_badge_blank),
            )
            if (canPause) {
                putParcelable(PIC_PAUSE, getDrawablePicture(PIC_PAUSE, R.drawable.ic_focus_pause).icon)
                putParcelable(PIC_PAUSE_DARK, getDrawablePicture(PIC_PAUSE_DARK, R.drawable.ic_focus_pause).icon)
                putParcelable(PIC_RESUME, getDrawablePicture(PIC_RESUME, R.drawable.ic_focus_resume).icon)
                putParcelable(PIC_RESUME_DARK, getDrawablePicture(PIC_RESUME_DARK, R.drawable.ic_focus_resume).icon)
            }
            if (canStop) {
                putParcelable(PIC_STOP, getDrawablePicture(PIC_STOP, R.drawable.ic_screen_recording_stop_dark).icon)
                putParcelable(PIC_STOP_DARK, getDrawablePicture(PIC_STOP_DARK, R.drawable.ic_screen_recording_stop_dark).icon)
            }
        }
        return Bundle().apply {
            putString("miui.focus.param", json)
            putBundle("miui.focus.pics", pictures)
            val actions = Bundle()
            var actionIndex = 1
            if (canPause) {
                val pendingIntent = pauseIntent ?: actionIntent(
                    session,
                    actionIndex,
                    if (session.paused) {
                        ScreenRecordingActionReceiver.ACTION_RESUME
                    } else {
                        ScreenRecordingActionReceiver.ACTION_PAUSE
                    },
                )
                actions.putParcelable(
                    ACTION_PAUSE,
                    Notification.Action.Builder(
                        null,
                        context.getString(
                            if (session.paused) R.string.screen_recording_resume else R.string.screen_recording_pause
                        ),
                        pendingIntent,
                    ).build(),
                )
                actionIndex++
            }
            if (canStop) {
                val pendingIntent = stopIntent ?: actionIntent(
                    session,
                    actionIndex,
                    ScreenRecordingActionReceiver.ACTION_STOP,
                )
                actions.putParcelable(
                    if (canPause) ACTION_STOP else ACTION_PAUSE,
                    Notification.Action.Builder(
                        null,
                        context.getString(R.string.screen_recording_stop),
                        pendingIntent,
                    ).build(),
                )
            }
            if (!actions.isEmpty) putBundle("miui.focus.actions", actions)
        }
    }

    private fun countdownLeft(design: ScreenRecordingDesignConfig, compact: String): ImageTextInfoLeft =
        when (design.left) {
            ScreenRecordingLeftDesign.ICON_ONLY -> ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_TICKER),
                textInfo = TextInfo(title = "", content = ""),
            )
            ScreenRecordingLeftDesign.TEXT_ONLY -> ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_APP_BADGE),
                textInfo = TextInfo(title = compact, content = ""),
            )
            ScreenRecordingLeftDesign.ICON_AND_TEXT -> ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_TICKER),
                textInfo = TextInfo(title = compact, content = ""),
            )
        }

    private fun actionIntent(session: ScreenRecordingSession, index: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            session.logicalId.hashCode() + index,
            Intent(context, ScreenRecordingActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_SESSION_ID, session.logicalId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun tickerIcon(): Int =
        if (HookConfigSync.screenRecorderIconStyle(context) == "voice_recorder") {
            R.drawable.ic_focus_ticker_recorder
        } else {
            R.drawable.ic_screen_recording_ticker
        }

    companion object {
        const val BUSINESS = "screen_recording"
        const val COUNTDOWN_BUSINESS = "hyperbridge_screen_recording"
        const val SCENE = "recorder"
        const val HIGHLIGHT_COLOR = "#FB382F"
        const val PERSISTENT_ISLAND_TIMEOUT_MILLIS = 86_400_000
        const val ISLAND_TIMEOUT_SECONDS = Int.MAX_VALUE
        const val TIMER_TYPE_COUNT_UP = 1
        const val TIMER_TYPE_PAUSED = 2
        const val ACTION_PAUSE = "miui.focus.action_1"
        const val ACTION_STOP = "miui.focus.action_2"
        const val PIC_TICKER = "miui.focus.pic_ticker"
        const val PIC_HIDDEN = "miui.focus.pic_hidden"
        const val PIC_APP_BADGE = "miui.focus.pic_recorder_app_badge"
        const val PIC_STOP = "miui.focus.pic_stop"
        const val PIC_STOP_DARK = "miui.focus.pic_stop_dark"
        const val PIC_PAUSE = "miui.focus.pic_pause"
        const val PIC_PAUSE_DARK = "miui.focus.pic_pause_dark"
        const val PIC_RESUME = "miui.focus.pic_resume"
        const val PIC_RESUME_DARK = "miui.focus.pic_resume_dark"
        const val EXTRA_SESSION_ID = "screen_recording_session_id"
    }
}
internal object ScreenRecordingPayloadFactory {
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    fun build(
        session: ScreenRecordingSession,
        now: Long,
        compactText: String,
        expandedText: String,
        notifyId: String,
        design: ScreenRecordingDesignConfig = ScreenRecordingDesignConfig(),
        business: String = ScreenRecordingTranslator.BUSINESS,
        enableFloat: Boolean = false,
    ): String {
        val timerType = if (session.paused) {
            ScreenRecordingTranslator.TIMER_TYPE_PAUSED
        } else {
            ScreenRecordingTranslator.TIMER_TYPE_COUNT_UP
        }
        val timerInfo = RecorderTimerInfo(
            timerWhen = session.timerStartedAt,
            timerType = timerType,
            timerSystemCurrent = now
        )
        val chatTimerInfo = RecorderChatTimerInfo(
            timerWhen = session.timerStartedAt,
            timerType = timerType,
            timerTotal = session.timerStartedAt,
            timerSystemCurrent = now
        )
        val imageTextInfoLeft = when (design.left) {
            ScreenRecordingLeftDesign.ICON_ONLY -> RecorderImageTextInfo(
                type = 1,
                picInfo = RecorderPicInfo(
                    type = 1,
                    pic = ScreenRecordingTranslator.PIC_TICKER
                ),
                textInfo = null
            )
            ScreenRecordingLeftDesign.ICON_AND_TEXT -> RecorderImageTextInfo(
                type = 1,
                picInfo = RecorderPicInfo(
                    type = 1,
                    pic = ScreenRecordingTranslator.PIC_TICKER
                ),
                textInfo = RecorderTextInfo(title = compactText, content = "")
            )
            ScreenRecordingLeftDesign.TEXT_ONLY -> RecorderImageTextInfo(
                type = 1,
                picInfo = RecorderPicInfo(
                    type = 1,
                    pic = ScreenRecordingTranslator.PIC_APP_BADGE
                ),
                textInfo = RecorderTextInfo(title = compactText, content = "")
            )
        }
        val imageTextInfoRight = if (session.countdownRemaining > 0) {
            RecorderImageTextInfo(
                type = 2,
                picInfo = null,
                textInfo = RecorderTextInfo(title = session.countdownRemaining.toString(), content = ""),
            )
        } else {
            null
        }
        val sameWidthDigitInfo = when {
            session.countdownRemaining > 0 -> null
            design.right == ScreenRecordingRightDesign.TIMER -> RecorderSameWidthDigitInfo(timerInfo = timerInfo)
            else -> null
        }
        val payload = RecorderFocusRoot(
            paramV2 = RecorderParamV2(
                protocol = 1,
                updatable = true,
                enableFloat = enableFloat,
                business = business,
                scene = ScreenRecordingTranslator.SCENE,
                content = compactText,
                notifyId = notifyId,
                islandFirstFloat = enableFloat,
                reopen = enableFloat,
                ticker = compactText,
                tickerPic = ScreenRecordingTranslator.PIC_TICKER,
                tickerPicDark = ScreenRecordingTranslator.PIC_TICKER,
                paramIsland = RecorderParamIsland(
                    islandPriority = 1,
                    islandTimeout = ScreenRecordingTranslator.ISLAND_TIMEOUT_SECONDS,
                    islandProperty = 2,
                    highlightColor = ScreenRecordingTranslator.HIGHLIGHT_COLOR,
                    bigIslandArea = RecorderBigIslandArea(
                        imageTextInfoLeft = imageTextInfoLeft,
                        imageTextInfoRight = imageTextInfoRight,
                        sameWidthDigitInfo = sameWidthDigitInfo
                    ),
                    smallIslandArea = RecorderSmallIslandArea(
                        RecorderPicInfo(type = 1, pic = ScreenRecordingTranslator.PIC_TICKER)
                    )
                ),
                chatInfo = RecorderChatInfo(
                    type = 1,
                    title = expandedText,
                    timerInfo = chatTimerInfo,
                    picProfile = ScreenRecordingTranslator.PIC_TICKER,
                    picProfileDark = ScreenRecordingTranslator.PIC_TICKER,
                    appIconPkg = ScreenRecordingTranslator.PIC_APP_BADGE
                ),
                animTextInfo = if (session.countdownRemaining > 0) {
                    null
                } else {
                    RecorderAnimTextInfo(
                        timerInfo = timerInfo,
                        animIconInfo = RecorderAnimIconInfo(
                            type = 1,
                            src = "voiceWaveBig",
                            number = 0,
                            loop = true,
                            autoplay = true,
                        ),
                        picInfo = RecorderPicInfo(type = 1, pic = ScreenRecordingTranslator.PIC_TICKER),
                    )
                },
                actions = buildList {
                    if (session.capabilities.canPause) {
                        add(
                            RecorderActionRef(
                                actionIntentType = 0,
                                action = ScreenRecordingTranslator.ACTION_PAUSE,
                                type = 0,
                                actionIcon = if (session.paused) {
                                    ScreenRecordingTranslator.PIC_RESUME
                                } else {
                                    ScreenRecordingTranslator.PIC_PAUSE
                                },
                                actionIconDark = if (session.paused) {
                                    ScreenRecordingTranslator.PIC_RESUME_DARK
                                } else {
                                    ScreenRecordingTranslator.PIC_PAUSE_DARK
                                }
                            )
                        )
                    }
                    if (session.capabilities.canStop) {
                        add(
                            RecorderActionRef(
                                actionIntentType = 0,
                                action = if (session.capabilities.canPause) {
                                    ScreenRecordingTranslator.ACTION_STOP
                                } else {
                                    ScreenRecordingTranslator.ACTION_PAUSE
                                },
                                type = 0,
                                actionIcon = ScreenRecordingTranslator.PIC_STOP,
                                actionIconDark = ScreenRecordingTranslator.PIC_STOP_DARK
                            )
                        )
                    }
                }.ifEmpty { null }
            )
        )

        return json.encodeToString(payload)
    }
}

@Serializable
private data class RecorderFocusRoot(
    @SerialName("param_v2") val paramV2: RecorderParamV2
)

@Serializable
private data class RecorderParamV2(
    val protocol: Int,
    val updatable: Boolean,
    val enableFloat: Boolean,
    val business: String,
    val scene: String,
    val content: String,
    val notifyId: String,
    val islandFirstFloat: Boolean,
    val reopen: Boolean = false,
    val ticker: String,
    val tickerPic: String,
    val tickerPicDark: String,
    @SerialName("param_island") val paramIsland: RecorderParamIsland,
    val chatInfo: RecorderChatInfo,
    val animTextInfo: RecorderAnimTextInfo? = null,
    val actions: List<RecorderActionRef>? = null
)

@Serializable
private data class RecorderAnimTextInfo(
    val timerInfo: RecorderTimerInfo,
    val animIconInfo: RecorderAnimIconInfo,
    val picInfo: RecorderPicInfo,
)

@Serializable
private data class RecorderAnimIconInfo(
    val type: Int,
    val src: String,
    val number: Int,
    val loop: Boolean,
    val autoplay: Boolean,
)

@Serializable
private data class RecorderParamIsland(
    val islandPriority: Int,
    val islandTimeout: Int,
    val islandProperty: Int,
    val highlightColor: String,
    val bigIslandArea: RecorderBigIslandArea,
    val smallIslandArea: RecorderSmallIslandArea
)

@Serializable
private data class RecorderBigIslandArea(
    val imageTextInfoLeft: RecorderImageTextInfo,
    val imageTextInfoRight: RecorderImageTextInfo? = null,
    val sameWidthDigitInfo: RecorderSameWidthDigitInfo? = null
)

@Serializable
private data class RecorderSmallIslandArea(val picInfo: RecorderPicInfo)

@Serializable
private data class RecorderImageTextInfo(
    val type: Int,
    val picInfo: RecorderPicInfo? = null,
    val textInfo: RecorderTextInfo? = null
)

@Serializable
private data class RecorderTextInfo(val title: String, val content: String)

@Serializable
private data class RecorderSameWidthDigitInfo(
    val timerInfo: RecorderTimerInfo,
    val content: String? = null
)

@Serializable
private data class RecorderTimerInfo(
    val timerWhen: Long,
    val timerType: Int,
    val timerSystemCurrent: Long
)

@Serializable
private data class RecorderChatInfo(
    val type: Int,
    val title: String,
    val timerInfo: RecorderChatTimerInfo,
    @SerialName("picProfile") val picProfile: String,
    @SerialName("picProfileDark") val picProfileDark: String,
    @SerialName("appIconPkg") val appIconPkg: String
)

@Serializable
private data class RecorderChatTimerInfo(
    val timerWhen: Long,
    val timerType: Int,
    val timerTotal: Long,
    val timerSystemCurrent: Long
)

@Serializable
private data class RecorderPicInfo(
    val type: Int,
    val pic: String,
    val loop: Boolean = false,
    val autoplay: Boolean = false,
    val number: Int = 0
)

@Serializable
private data class RecorderActionRef(
    val actionIntentType: Int,
    val action: String,
    val type: Int,
    val actionIcon: String,
    val actionIconDark: String
)
