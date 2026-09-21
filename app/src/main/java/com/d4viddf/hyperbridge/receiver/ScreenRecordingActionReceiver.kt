package com.d4viddf.hyperbridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.d4viddf.hyperbridge.service.diagnostics.DiagnosticsStore
import com.d4viddf.hyperbridge.service.recording.ScreenRecordingClassifier
import com.d4viddf.hyperbridge.service.recording.XiaomiScreenRecordingControlBackend
import com.d4viddf.hyperbridge.screenrecorder.ScreenRecorderCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenRecordingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = when (intent.action) {
                    ACTION_STOP -> XiaomiScreenRecordingControlBackend(context).stop()
                    ACTION_PAUSE -> ScreenRecorderCommands.pause(context)
                    ACTION_RESUME -> ScreenRecorderCommands.resume(context)
                    else -> return@launch
                }
                DiagnosticsStore.record(
                    classification = "SCREEN_RECORDING",
                    action = when (intent.action) {
                        ACTION_STOP -> if (result.isSuccess) "stop-sent" else "stop-failed"
                        ACTION_PAUSE -> if (result.isSuccess) "pause-sent" else "pause-failed"
                        else -> if (result.isSuccess) "resume-sent" else "resume-failed"
                    },
                    packageName = ScreenRecordingClassifier.PACKAGE_NAME,
                    reason = result.exceptionOrNull()?.javaClass?.simpleName
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.d4viddf.hyperbridge.action.STOP_SCREEN_RECORDING"
        const val ACTION_PAUSE = "com.d4viddf.hyperbridge.action.PAUSE_SCREEN_RECORDING"
        const val ACTION_RESUME = "com.d4viddf.hyperbridge.action.RESUME_SCREEN_RECORDING"
    }
}
