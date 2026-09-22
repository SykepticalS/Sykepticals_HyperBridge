package com.d4viddf.hyperbridge.debug

import android.util.Log

internal object AgentDebugLog {
    private const val TAG = "HBDebug3597f2"

    fun log(hypothesisId: String, location: String, message: String, data: String) {
        // #region agent log
        Log.i(
            TAG,
            "{\"sessionId\":\"3597f2\",\"timestamp\":${System.currentTimeMillis()},\"hypothesisId\":\"$hypothesisId\",\"location\":\"$location\",\"message\":\"$message\",\"data\":$data}",
        )
        // #endregion
    }
}
