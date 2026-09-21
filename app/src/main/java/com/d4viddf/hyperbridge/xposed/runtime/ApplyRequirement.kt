package com.d4viddf.hyperbridge.xposed.runtime

enum class RestartTarget { SYSTEM_UI, XMSF, LAUNCHER, SCREEN_RECORDER }

sealed interface ApplyRequirement {
    data object None : ApplyRequirement
    data object HotReload : ApplyRequirement
    data class Restart(val targets: Set<RestartTarget>) : ApplyRequirement

    fun merge(other: ApplyRequirement): ApplyRequirement = when {
        this is Restart && other is Restart -> Restart(targets + other.targets)
        this is Restart -> this
        other is Restart -> other
        this == HotReload || other == HotReload -> HotReload
        else -> None
    }

    companion object {
        fun forSetting(key: String): ApplyRequirement = when (key) {
            "focus_whitelist_hook" -> Restart(setOf(RestartTarget.SYSTEM_UI))
            "focus_auth_hook" -> Restart(setOf(RestartTarget.XMSF))
            "outer_glow_hook", "heads_up_suppression_hook" -> Restart(setOf(RestartTarget.SYSTEM_UI))
            "screen_recording_replace_floating",
            "screen_recorder_replace" -> Restart(setOf(RestartTarget.SCREEN_RECORDER))
            else -> HotReload
        }
    }
}
