package com.lidesheng.hyperlyric.common

import android.content.SharedPreferences

data class SyllablePreferenceState(
    val relativeProgress: Boolean,
    val relativeHighlight: Boolean,
    val lineDisplay: Boolean
)

object SyllablePreferencePolicy {
    fun read(prefs: SharedPreferences): SyllablePreferenceState = normalize(
        relativeProgress = prefs.getBoolean(
            RootConstants.KEY_HOOK_SYLLABLE_RELATIVE,
            RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE
        ),
        relativeHighlight = prefs.getBoolean(
            RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT,
            RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT
        ),
        lineDisplay = prefs.getBoolean(
            RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY,
            RootConstants.DEFAULT_HOOK_SYLLABLE_LINE_DISPLAY
        )
    )

    fun normalize(
        relativeProgress: Boolean,
        relativeHighlight: Boolean,
        lineDisplay: Boolean
    ): SyllablePreferenceState = if (lineDisplay) {
        SyllablePreferenceState(
            relativeProgress = false,
            relativeHighlight = relativeHighlight,
            lineDisplay = true
        )
    } else {
        SyllablePreferenceState(
            relativeProgress = relativeProgress,
            relativeHighlight = relativeHighlight,
            lineDisplay = false
        )
    }

    fun write(editor: SharedPreferences.Editor, state: SyllablePreferenceState) {
        editor.putBoolean(RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY, state.lineDisplay)
        editor.putBoolean(RootConstants.KEY_HOOK_SYLLABLE_RELATIVE, state.relativeProgress)
        editor.putBoolean(RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT, state.relativeHighlight)
    }

    fun normalizeInPlace(prefs: SharedPreferences) {
        val current = SyllablePreferenceState(
            relativeProgress = prefs.getBoolean(
                RootConstants.KEY_HOOK_SYLLABLE_RELATIVE,
                RootConstants.DEFAULT_HOOK_SYLLABLE_RELATIVE
            ),
            relativeHighlight = prefs.getBoolean(
                RootConstants.KEY_HOOK_SYLLABLE_HIGHLIGHT,
                RootConstants.DEFAULT_HOOK_SYLLABLE_HIGHLIGHT
            ),
            lineDisplay = prefs.getBoolean(
                RootConstants.KEY_HOOK_SYLLABLE_LINE_DISPLAY,
                RootConstants.DEFAULT_HOOK_SYLLABLE_LINE_DISPLAY
            )
        )
        val normalized = normalize(
            relativeProgress = current.relativeProgress,
            relativeHighlight = current.relativeHighlight,
            lineDisplay = current.lineDisplay
        )
        if (current == normalized) return

        val editor = prefs.edit()
        write(editor, normalized)
        editor.apply()
    }
}
