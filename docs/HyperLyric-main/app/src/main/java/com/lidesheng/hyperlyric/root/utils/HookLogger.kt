package com.lidesheng.hyperlyric.root.utils

import android.content.SharedPreferences
import android.util.Log
import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.common.UIConstants
import io.github.libxposed.api.XposedModule

private const val TAG = "HyperLyric"

object HookLogger : HyperLogger {
    @Volatile
    private var logPrefs: SharedPreferences? = null

    @Volatile
    private var logPrefsResolved = false

    @Volatile
    private var logLevel = UIConstants.DEFAULT_LOG_LEVEL

    private val debugStates = HashMap<String, String>()

    var module: XposedModule? = null
        set(value) {
            field = value
            logPrefs = null
            logPrefsResolved = false
            synchronized(debugStates) {
                debugStates.clear()
            }
            refreshLogLevel()
        }

    override fun d(tag: String, msg: String) {
        if (logLevel < 1) return
        val finalMsg = format(tag, msg)
        Log.d(TAG, finalMsg)
        module?.log(Log.DEBUG, TAG, finalMsg)
    }

    /**
     * Emits a diagnostic only when a low-frequency state changes. Callers keep the message
     * lazy so adding diagnostics does not add formatting work while Debug logging is disabled.
     */
    fun dState(
        stateId: String,
        tag: String,
        state: String,
        message: () -> String
    ) {
        if (!isDebugEnabled) return
        val changed = synchronized(debugStates) {
            if (debugStates[stateId] == state) {
                false
            } else {
                debugStates[stateId] = state
                true
            }
        }
        if (changed) d(tag, message())
    }

    val isDebugEnabled: Boolean
        get() = logLevel >= 1

    override fun i(tag: String, msg: String) {
        val finalMsg = format(tag, msg)
        Log.i(TAG, finalMsg)
        module?.log(Log.INFO, TAG, finalMsg)
    }

    override fun w(tag: String, msg: String, e: Throwable?) {
        val finalMsg = format(tag, msg)
        Log.w(TAG, finalMsg, e)
        module?.log(Log.WARN, TAG, finalMsg, e)
    }

    override fun e(tag: String, msg: String, e: Throwable?) {
        val finalMsg = format(tag, msg)
        Log.e(TAG, finalMsg, e)
        module?.log(Log.ERROR, TAG, finalMsg, e)
    }

    fun refreshLogLevel() {
        logLevel = runCatching {
            resolveLogPrefs()?.getInt(UIConstants.KEY_LOG_LEVEL, UIConstants.DEFAULT_LOG_LEVEL)
                ?: UIConstants.DEFAULT_LOG_LEVEL
        }.getOrDefault(UIConstants.DEFAULT_LOG_LEVEL)
    }

    private fun resolveLogPrefs(): SharedPreferences? {
        if (logPrefsResolved) return logPrefs
        synchronized(this) {
            if (!logPrefsResolved) {
                logPrefs = runCatching {
                    module?.getRemotePreferences(UIConstants.PREF_NAME)
                }.getOrNull()
                logPrefsResolved = true
            }
        }
        return logPrefs
    }

    private fun format(tag: String, msg: String): String {
        return "[${tag.trim()}] ${msg.trim()}"
    }
}
