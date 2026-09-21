package com.d4viddf.hyperbridge.models

/**
 * Xiaomi's island window is typically `FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM`.
 * `FLAG_ALT_FOCUSABLE_IM` inverts IME targeting, so a not-focusable island still
 * becomes the input-method target and steals the keyboard from the app underneath
 * until the island hides.
 *
 * Keep that inversion off except while HyperBridge's in-island reply composer needs
 * the IME. `FLAG_NOT_FOCUSABLE` still allows taps on the island itself.
 */
object IslandWindowImePolicy {
    const val FLAG_NOT_FOCUSABLE = 0x00000008
    const val FLAG_NOT_TOUCH_MODAL = 0x00000020
    const val FLAG_ALT_FOCUSABLE_IM = 0x00020000

    fun apply(flags: Int, composerOpen: Boolean): Int =
        if (composerOpen) composerFlags(flags) else idleFlags(flags)

    fun idleFlags(flags: Int): Int =
        flags and FLAG_ALT_FOCUSABLE_IM.inv() or FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL

    fun composerFlags(flags: Int): Int =
        flags and FLAG_NOT_FOCUSABLE.inv() and FLAG_ALT_FOCUSABLE_IM.inv() or FLAG_NOT_TOUCH_MODAL

    const val SOFT_INPUT_STATE_VISIBLE = 0x00000004
    const val SOFT_INPUT_ADJUST_NOTHING = 0x00000030
    const val SOFT_INPUT_MASK_ADJUST = 0x000000f0
    const val SOFT_INPUT_MASK_STATE = 0x0000000f

    fun composerSoftInputMode(current: Int): Int =
        (current and SOFT_INPUT_MASK_ADJUST.inv() and SOFT_INPUT_MASK_STATE.inv()) or
            SOFT_INPUT_ADJUST_NOTHING or SOFT_INPUT_STATE_VISIBLE

    fun idleSoftInputMode(current: Int): Int =
        current and SOFT_INPUT_MASK_ADJUST.inv() and SOFT_INPUT_MASK_STATE.inv()
}
