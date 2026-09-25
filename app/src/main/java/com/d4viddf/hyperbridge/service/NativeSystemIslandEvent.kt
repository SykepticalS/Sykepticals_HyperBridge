package com.d4viddf.hyperbridge.service

import android.content.Intent
import android.os.Bundle
import com.d4viddf.hyperbridge.island.backend.IslandProtocol

data class NativeSystemIslandEvent(
    val notifyId: String,
    val left: String,
    val right: String,
    val durationMs: Long,
    val hide: Boolean,
) {
    val logicalId: String get() = NativeSystemIslandPolicy.logicalId(notifyId)

    fun putExtras(target: Bundle) {
        target.putString(IslandProtocol.EXTRA_NATIVE_NOTIFY_ID, notifyId)
        target.putString(IslandProtocol.EXTRA_NATIVE_LEFT, left)
        target.putString(IslandProtocol.EXTRA_NATIVE_RIGHT, right)
        target.putLong(IslandProtocol.EXTRA_NATIVE_DURATION, durationMs)
        target.putBoolean(IslandProtocol.EXTRA_NATIVE_HIDE, hide)
    }

    companion object {
        fun fromIntent(intent: Intent): NativeSystemIslandEvent? {
            if (intent.action != IslandProtocol.ACTION_NATIVE_SYSTEM_ISLAND) return null
            val notifyId = intent.getStringExtra(IslandProtocol.EXTRA_NATIVE_NOTIFY_ID)?.trim().orEmpty()
            if (notifyId.isEmpty() && !intent.getBooleanExtra(IslandProtocol.EXTRA_NATIVE_HIDE, false)) {
                return null
            }
            val duration = NativeSystemIslandPolicy.durationMs(
                if (intent.hasExtra(IslandProtocol.EXTRA_NATIVE_DURATION)) {
                    intent.getLongExtra(IslandProtocol.EXTRA_NATIVE_DURATION, -1L)
                } else {
                    null
                }
            )
            val hide = NativeSystemIslandPolicy.isHide(
                duration,
                intent.getBooleanExtra(IslandProtocol.EXTRA_NATIVE_HIDE, false),
            )
            return NativeSystemIslandEvent(
                notifyId = notifyId.ifBlank { "system" },
                left = intent.getStringExtra(IslandProtocol.EXTRA_NATIVE_LEFT).orEmpty(),
                right = intent.getStringExtra(IslandProtocol.EXTRA_NATIVE_RIGHT).orEmpty(),
                durationMs = duration,
                hide = hide,
            )
        }
    }
}
