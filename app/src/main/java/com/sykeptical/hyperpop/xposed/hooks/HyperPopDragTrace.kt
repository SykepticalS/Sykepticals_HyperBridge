package com.sykeptical.hyperpop.xposed.hooks

import android.util.Log

/**
 * Temporary before/after dump for the bottom-handle drag. One line per phase.
 * Removed once the fake copy is shown to keep the real copy's geometry.
 */
internal object HyperPopDragTrace {
    private const val TAG = "HyperPopDrag"
    private var updateLogged = false

    fun down(message: String) {
        Log.i(TAG, message)
    }

    fun start(message: String) {
        updateLogged = false
        Log.i(TAG, message)
    }

    fun updateOpen(): Boolean = !updateLogged

    fun update(message: String) {
        if (updateLogged) return
        updateLogged = true
        Log.i(TAG, message)
    }

    fun end(message: String) {
        updateLogged = false
        Log.i(TAG, message)
    }
}
