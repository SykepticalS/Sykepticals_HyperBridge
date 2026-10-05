package com.sykeptical.hyperpop.xposed

/**
 * One shade-background tweak covers Focus rows and the shade media card.
 * Focus rows enter Xiaomi's ordinary selector. The media card has no such flag,
 * so the hook swaps in the same ordinary drawable and two-stop blend names.
 */
internal object FocusShadeBackgroundPolicy {
    fun shouldUseRegularRowSelector(enabled: Boolean, isFocusNotification: Boolean): Boolean =
        enabled && isFocusNotification

    fun shouldRestyleMediaCard(enabled: Boolean): Boolean = enabled

    /** Drawable Xiaomi uses for an ordinary, non-heads-up shade row. */
    fun ordinaryDrawableName(blurOpened: Boolean, fullAod: Boolean): String = when {
        blurOpened -> "notification_heads_up_transparent_bg"
        fullAod -> "notification_fullaod_item_bg"
        else -> "notification_item_bg"
    }

    /**
     * Ordinary two-stop blend. Null when blur is closed and the solid drawable
     * is the whole background. Names match the non-focus branch of updateBlurBg.
     */
    fun ordinaryBlendResources(blurOpened: Boolean, keyguard: Boolean): OrdinaryBlendResources? {
        if (!blurOpened) return null
        val prefix = if (keyguard) {
            "notification_element_blend_keyguard"
        } else {
            "notification_element_blend_shade"
        }
        return OrdinaryBlendResources(
            color1 = "${prefix}_color_1",
            mode1 = "${prefix}_mode_1",
            color2 = "${prefix}_color_2",
            mode2 = "${prefix}_mode_2",
        )
    }
}

internal data class OrdinaryBlendResources(
    val color1: String,
    val mode1: String,
    val color2: String,
    val mode2: String,
)
