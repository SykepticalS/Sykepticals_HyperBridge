package com.d4viddf.hyperbridge.service.recording

data class ScreenRecordingSignals(
    val packageName: String,
    val notificationId: Int,
    val channelId: String?,
    val isOngoing: Boolean,
    val isForegroundService: Boolean,
    val isGroupSummary: Boolean
)

object ScreenRecordingClassifier {
    const val PACKAGE_NAME = "com.miui.screenrecorder"
    const val ACTIVE_NOTIFICATION_ID = 110
    const val ACTIVE_CHANNEL_ID = "com.miui.screenrecorder.start"
    const val SAVED_NOTIFICATION_ID = 111
    const val SAVED_CHANNEL_ID = "com.miui.screenrecorder.stop"

    fun isScreenRecording(signals: ScreenRecordingSignals): Boolean =
        signals.packageName == PACKAGE_NAME &&
                signals.notificationId == ACTIVE_NOTIFICATION_ID &&
                channelMatches(signals.channelId, ACTIVE_CHANNEL_ID) &&
                signals.isOngoing &&
                !signals.isGroupSummary

    fun isSavedScreenRecording(signals: ScreenRecordingSignals): Boolean =
        signals.packageName == PACKAGE_NAME &&
                signals.notificationId == SAVED_NOTIFICATION_ID &&
                channelMatches(signals.channelId, SAVED_CHANNEL_ID) &&
                !signals.isOngoing &&
                !signals.isForegroundService &&
                !signals.isGroupSummary

    private fun channelMatches(actual: String?, expected: String): Boolean {
        val value = actual.orEmpty()
        return value.isBlank() || value == expected || value.contains("screenrecorder", ignoreCase = true)
    }
}

object ScreenRecordingTimeoutPolicy {
    fun resolve(
        configuredTimeout: Int?,
        systemScreenRecordingTimeout: Int,
        isActiveRecording: Boolean,
        isSavedRecording: Boolean
    ): Int? = when {
        isActiveRecording -> null
        isSavedRecording -> systemScreenRecordingTimeout
        else -> configuredTimeout
    }
}
