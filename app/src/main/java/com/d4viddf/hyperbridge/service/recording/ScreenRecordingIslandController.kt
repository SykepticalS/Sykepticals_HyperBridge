package com.d4viddf.hyperbridge.service.recording

import android.content.Context
import android.util.Log
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.island.backend.HookConfigSync
import com.d4viddf.hyperbridge.island.backend.IslandMetadata
import com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend
import com.d4viddf.hyperbridge.models.IslandVisualMetadata
import com.d4viddf.hyperbridge.models.ScreenRecordingDesignConfig
import com.d4viddf.hyperbridge.screenrecorder.RecorderSnapshot
import com.d4viddf.hyperbridge.screenrecorder.ScreenRecorderContract
import com.d4viddf.hyperbridge.service.BridgeNotificationChannels
import com.d4viddf.hyperbridge.service.translators.IslandFloatingPresentationPolicy
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

    fun onSnapshot(snapshot: RecorderSnapshot) {
        if (!HookConfigSync.replaceScreenRecorder(context)) {
            cancel()
            return
        }
        if (snapshot.state == ScreenRecorderContract.STATE_IDLE) {
            cancel()
            return
        }
        val session = sessionFrom(snapshot)
        val design = runCatching { preferences.getScreenRecordingDesignSync() }
            .getOrDefault(ScreenRecordingDesignConfig())
        val fingerprint = ScreenRecordingSemanticFingerprint.compute(session, design)
        if (posted && fingerprint == lastFingerprint) return
        val isUpdate = posted
        postExecutor.execute {
            runCatching { postIsland(session, design, fingerprint, isUpdate) }
                .onFailure { Log.e(TAG, "screen recording island post failed", it) }
        }
    }

    fun cancel() {
        if (!posted && lastFingerprint == null) return
        posted = false
        lastFingerprint = null
        postExecutor.execute {
            backend.cancel(NOTIFICATION_ID, LOGICAL_TOKEN)
        }
    }

    private fun postIsland(
        session: ScreenRecordingSession,
        design: ScreenRecordingDesignConfig,
        fingerprint: Int,
        isUpdate: Boolean,
    ) {
        val title = if (session.countdownRemaining > 0) {
            context.getString(R.string.screen_recording_starting)
        } else {
            context.getString(R.string.screen_recording_compact)
        }
        val floatPresentation = IslandFloatingPresentationPolicy.resolve(
            firstFloat = true,
            floatOnUpdate = false,
            isUpdate = isUpdate,
        )
        val data = translator.translate(
            session = session,
            design = design,
            compactText = title,
            expandedText = if (session.countdownRemaining > 0) {
                title
            } else {
                context.getString(R.string.screen_recording_active)
            },
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
        if (success) {
            posted = true
            lastFingerprint = fingerprint
            Log.i(TAG, "posted screen recording island countdown=${session.countdownRemaining} paused=${session.paused}")
        } else {
            Log.e(TAG, "SystemUI rejected screen recording island")
        }
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
    }
}
