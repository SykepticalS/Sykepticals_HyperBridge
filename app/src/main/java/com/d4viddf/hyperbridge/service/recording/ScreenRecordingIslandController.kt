package com.d4viddf.hyperbridge.service.recording

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.debug.AgentDebugLog
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.island.backend.HookConfigSync
import com.d4viddf.hyperbridge.island.backend.IslandMetadata
import com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend
import com.d4viddf.hyperbridge.models.IslandVisualMetadata
import com.d4viddf.hyperbridge.models.ScreenRecordingDesignConfig
import com.d4viddf.hyperbridge.screenrecorder.RecorderSnapshot
import com.d4viddf.hyperbridge.screenrecorder.ScreenRecorderContract
import com.d4viddf.hyperbridge.service.BridgeNotificationChannels
import com.d4viddf.hyperbridge.service.translators.IslandFloatingPresentation
import com.d4viddf.hyperbridge.service.translators.IslandFloatingPresentationPolicy
import com.d4viddf.hyperbridge.service.translators.ScreenRecordingSavedPayloadFactory
import com.d4viddf.hyperbridge.service.translators.ScreenRecordingTranslator
import java.util.concurrent.Executors

class ScreenRecordingIslandController(private val context: Context) {
    private val backend = SystemUiIslandBackend.get(context)
    private val translator = ScreenRecordingTranslator(context)
    private val preferences = AppPreferences(context)
    private val postExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "hyperbridge-recorder-island").apply { isDaemon = true }
    }
    @Volatile private var lastFingerprint: Int? = null
    @Volatile private var posted = false
    @Volatile private var hadRecording = false

    fun onSnapshot(snapshot: RecorderSnapshot) {
        val replace = HookConfigSync.replaceScreenRecorder(context)
        // #region agent log
        AgentDebugLog.log(
            "C",
            "ScreenRecordingIslandController.onSnapshot",
            "snapshot received",
            "{\"replace\":$replace,\"state\":${snapshot.state},\"countdown\":${snapshot.countdownRemaining},\"posted\":$posted}",
        )
        // #endregion
        if (!replace) {
            cancel()
            return
        }
        if (snapshot.state == ScreenRecorderContract.STATE_IDLE) {
            val completed = hadRecording
            hadRecording = false
            cancel()
            if (completed) postCompleted()
            return
        }
        hadRecording = snapshot.state == ScreenRecorderContract.STATE_RECORDING ||
            snapshot.state == ScreenRecorderContract.STATE_PAUSED
        val session = sessionFrom(snapshot)
        val design = runCatching { preferences.getScreenRecordingDesignSync() }
            .getOrDefault(ScreenRecordingDesignConfig())
        val fingerprint = ScreenRecordingSemanticFingerprint.compute(session, design)
        if (posted && fingerprint == lastFingerprint) {
            // #region agent log
            AgentDebugLog.log(
                "C",
                "ScreenRecordingIslandController.onSnapshot",
                "skipped duplicate fingerprint",
                "{\"fingerprint\":$fingerprint,\"state\":${snapshot.state}}",
            )
            // #endregion
            return
        }
        val isUpdate = posted
        postExecutor.execute {
            runCatching { postIsland(session, design, fingerprint, isUpdate) }
                .onFailure {
                    Log.e(TAG, "screen recording island post failed", it)
                    // #region agent log
                    AgentDebugLog.log(
                        "B",
                        "ScreenRecordingIslandController.onSnapshot",
                        "post threw",
                        "{\"error\":\"${it.javaClass.simpleName}:${it.message?.replace("\"", "'")}\"}",
                    )
                    // #endregion
                }
        }
    }

    fun cancel() {
        // #region agent log
        AgentDebugLog.log(
            "F",
            "ScreenRecordingIslandController.cancel",
            "live island cancel",
            "{\"posted\":$posted}",
        )
        // #endregion
        if (!posted && lastFingerprint == null) return
        posted = false
        lastFingerprint = null
        postExecutor.execute {
            backend.cancel(NOTIFICATION_ID, LOGICAL_TOKEN)
        }
    }

    private fun postCompleted() {
        postExecutor.execute {
            val title = context.getString(R.string.screen_recording_done)
            val timeoutSeconds = preferences.getScreenRecordingTimeoutSync().takeIf { it > 0 } ?: 4
            val checkKey = "miui.focus.pic_saved_check"
            val sourceKey = "miui.focus.pic_saved_source"
            val json = ScreenRecordingSavedPayloadFactory.build(
                business = "screen_recording_saved",
                compactTitle = title,
                checkKey = checkKey,
                sourceIconKey = sourceKey,
                showNotification = false,
                timeout = timeoutSeconds,
                highlightColor = "#34C759",
                enableFloat = true,
                expandedTime = timeoutSeconds,
            )
            val notification = NotificationCompat.Builder(context, BridgeNotificationChannels.ACTIVE)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(title)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(timeoutSeconds * 1000L)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(0)
                .setSound(null)
                .setVibrate(null)
                .build()
            notification.extras.putBundle(
                "miui.focus.pics",
                Bundle().apply {
                    putParcelable(checkKey, Icon.createWithResource(context, R.drawable.ic_screen_recording_saved))
                    putParcelable(sourceKey, Icon.createWithResource(context, R.drawable.ic_screen_recording_ticker))
                },
            )
            notification.extras.putString(
                "miui.focus.param",
                IslandVisualMetadata.injectFloatingFlags(
                    json,
                    enableFloat = true,
                    islandFirstFloat = true,
                    reopen = true,
                ),
            )
            val success = backend.post(
                SAVED_NOTIFICATION_ID,
                notification,
                IslandMetadata(
                    logicalToken = SAVED_TOKEN,
                    sourcePackage = ScreenRecordingClassifier.PACKAGE_NAME,
                    semanticType = "SCREEN_RECORDING",
                    generation = System.currentTimeMillis(),
                ),
            ).isSuccess
            // #region agent log
            AgentDebugLog.log(
                "E",
                "ScreenRecordingIslandController.postCompleted",
                "completed island posted",
                "{\"success\":$success,\"timeout\":$timeoutSeconds,\"runId\":\"post-fix\"}",
            )
            val savedExpanded = Regex("\"expandedTime\":(-?\\d+)").find(json)?.groupValues?.get(1) ?: "absent"
            val savedIslandTimeout = Regex("\"islandTimeout\":(-?\\d+)").find(json)?.groupValues?.get(1) ?: "absent"
            AgentDebugLog.log(
                "G",
                "ScreenRecordingIslandController.postCompleted",
                "completed timing fields",
                "{\"expandedTime\":\"$savedExpanded\",\"islandTimeout\":\"$savedIslandTimeout\",\"timeoutSeconds\":$timeoutSeconds}",
            )
            // #endregion
        }
    }

    private fun postIsland(
        session: ScreenRecordingSession,
        design: ScreenRecordingDesignConfig,
        fingerprint: Int,
        isUpdate: Boolean,
    ) {
        val title = when {
            session.countdownRemaining > 0 -> context.getString(R.string.screen_recording_starting)
            session.paused -> context.getString(R.string.screen_recording_paused)
            else -> context.getString(R.string.screen_recording_compact)
        }
        val floatPresentation = if (session.countdownRemaining > 0) {
            IslandFloatingPresentation(
                enableFloat = !isUpdate,
                islandFirstFloat = false,
                reopen = false,
            )
        } else {
            IslandFloatingPresentationPolicy.resolve(
                firstFloat = true,
                floatOnUpdate = false,
                isUpdate = isUpdate,
            )
        }
        val data = translator.translate(
            session = session,
            design = design,
            compactText = title,
            expandedText = title,
            isUpdate = isUpdate,
            enableFloat = floatPresentation.enableFloat,
        )
        val notification = NotificationCompat.Builder(context, BridgeNotificationChannels.ACTIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .addExtras(data.resources)
            .build()
        notification.extras.putString(
            "miui.focus.param",
            IslandVisualMetadata.injectFloatingFlags(
                data.jsonParam,
                floatPresentation.enableFloat,
                floatPresentation.islandFirstFloat,
                floatPresentation.reopen,
            ),
        )
        val success = backend.post(
            NOTIFICATION_ID,
            notification,
            IslandMetadata(
                logicalToken = LOGICAL_TOKEN,
                sourcePackage = ScreenRecordingClassifier.PACKAGE_NAME,
                semanticType = "SCREEN_RECORDING",
                generation = session.startedAt + session.countdownRemaining,
            ),
        ).isSuccess
        val focus = notification.extras.getString("miui.focus.param").orEmpty()
        val enableFloatInJson = "\"enableFloat\":true" in focus
        val firstFloatInJson = "\"islandFirstFloat\":true" in focus
        if (success) {
            posted = true
            lastFingerprint = fingerprint
            Log.i(TAG, "posted screen recording island countdown=${session.countdownRemaining} paused=${session.paused}")
        } else {
            Log.e(TAG, "SystemUI rejected screen recording island")
        }
        // #region agent log
        AgentDebugLog.log(
            "A",
            "ScreenRecordingIslandController.postIsland",
            "post finished",
            "{\"success\":$success,\"countdown\":${session.countdownRemaining},\"isUpdate\":$isUpdate,\"enableFloat\":${floatPresentation.enableFloat},\"enableFloatInJson\":$enableFloatInJson,\"firstFloatInJson\":$firstFloatInJson,\"focusPresent\":${focus.isNotEmpty()}}",
        )
        val expandedTimeInJson = Regex("\"expandedTime\":(-?\\d+)").find(focus)?.groupValues?.get(1) ?: "absent"
        AgentDebugLog.log(
            "G",
            "ScreenRecordingIslandController.postIsland",
            "expanded time field",
            "{\"countdown\":${session.countdownRemaining},\"expandedTime\":\"$expandedTimeInJson\"}",
        )
        val chatContent = Regex("\"chatInfo\":\\{\"title\":\"[^\"]*\",\"content\":\"([^\"]*)\"")
            .find(focus)?.groupValues?.get(1) ?: "missing"
        AgentDebugLog.log(
            "H",
            "ScreenRecordingIslandController.postIsland",
            "expanded body",
            "{\"countdown\":${session.countdownRemaining},\"chatContent\":\"$chatContent\"}",
        )
        // #endregion
    }

    private fun sessionFrom(snapshot: RecorderSnapshot): ScreenRecordingSession {
        val startedAt = snapshot.startedAtWallClock.takeIf { it > 0L } ?: System.currentTimeMillis()
        val timerStartedAt = if (snapshot.state == ScreenRecorderContract.STATE_STARTING) {
            startedAt
        } else {
            System.currentTimeMillis() - snapshot.durationAt(SystemClock.elapsedRealtime())
        }
        val recording = snapshot.state == ScreenRecorderContract.STATE_RECORDING
        val paused = snapshot.state == ScreenRecorderContract.STATE_PAUSED
        return ScreenRecordingSession(
            logicalId = LOGICAL_TOKEN,
            sourceKey = LOGICAL_TOKEN,
            packageName = ScreenRecordingClassifier.PACKAGE_NAME,
            startedAt = startedAt,
            capabilities = ScreenRecordingCapabilities(
                canStop = true,
                canPause = recording || paused,
                canResume = paused,
            ),
            paused = paused,
            countdownRemaining = snapshot.countdownRemaining.takeIf {
                snapshot.state == ScreenRecorderContract.STATE_STARTING
            } ?: 0,
            timerStartedAt = timerStartedAt,
        )
    }

    companion object {
        private const val TAG = "ScreenRecordingIsland"
        const val LOGICAL_TOKEN = "screen-recording"
        const val NOTIFICATION_ID = 0x4850
        private const val SAVED_TOKEN = "screen-recording-saved"
        private const val SAVED_NOTIFICATION_ID = 0x4853
    }
}
