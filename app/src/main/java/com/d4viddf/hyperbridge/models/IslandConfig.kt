package com.d4viddf.hyperbridge.models

enum class IslandTextContent { AUTOMATIC, TITLE, CONTENT, SUBTITLE, APP, SENDER, STATE, PROGRESS, NONE, CUSTOM }

enum class MarqueeDismissMode(
    val loops: Int,
    val overridesTimeout: Boolean,
    val holdsForRightScroll: Boolean = false,
) {
    OFF(0, false), AFTER_ONE(1, false), AFTER_TWO(2, false),
    AFTER_ONE_OVERRIDE_TIMEOUT(1, true), AFTER_TWO_OVERRIDE_TIMEOUT(2, true),
    /** Keep a timed island up until the right text has fully scrolled, then settle briefly. */
    WAIT_FOR_RIGHT_SCROLL(0, true, holdsForRightScroll = true);

    companion object {
        fun parse(value: String?): MarqueeDismissMode = entries.firstOrNull { it.name == value } ?: OFF
    }
}

enum class GlowMode {
    OFF, ON, FOLLOW_DYNAMIC;
    companion object { fun parse(value: String?): GlowMode? = entries.firstOrNull { it.name == value } }
}

enum class IslandSceneBehavior { DEFAULT, SMALL_ONLY, EXPAND, SUPPRESS }

object FloatConfigMigration {
    fun fromLegacy(isFloat: Boolean?): Pair<Boolean?, Boolean?> = isFloat to isFloat?.let { false }
}

data class IslandConfig(
    val firstFloat: Boolean? = null,
    val isShowShade: Boolean? = null,
    val timeout: Int? = null,
    val floatTimeout: Int? = null,
    val removeOriginalNotification: Boolean? = null,
    val dismissWithOriginal: Boolean? = null,
    val enableInlineReply: Boolean? = null,
    val floatOnUpdate: Boolean? = null,
    val marqueeEnabled: Boolean? = null,
    val marqueeDismissMode: MarqueeDismissMode? = null,
    val leftContent: IslandTextContent? = null,
    val rightContent: IslandTextContent? = null,
    val leftCustomExpression: String? = null,
    val rightCustomExpression: String? = null,
    val islandGlowMode: GlowMode? = null,
    val focusGlowMode: GlowMode? = null,
    val islandGlowColor: String? = null,
    val focusGlowColor: String? = null,
    val forceIslandGlow: Boolean? = null,
    val forceFocusGlow: Boolean? = null,
    val contactPinkGlow: Boolean? = null,
    val restoreLockscreen: Boolean? = null,
    val dndBehavior: IslandSceneBehavior? = null,
    val fullscreenBehavior: IslandSceneBehavior? = null,
    val landscapeBehavior: IslandSceneBehavior? = null,
) {
    fun hasOverrides(): Boolean = listOf(
        firstFloat, floatOnUpdate, isShowShade, timeout, floatTimeout, removeOriginalNotification,
        dismissWithOriginal, enableInlineReply, marqueeEnabled, marqueeDismissMode, leftContent,
        rightContent, leftCustomExpression, rightCustomExpression, islandGlowMode, focusGlowMode,
        islandGlowColor, focusGlowColor, forceIslandGlow, forceFocusGlow, contactPinkGlow, restoreLockscreen,
        dndBehavior, fullscreenBehavior, landscapeBehavior,
    ).any { it != null }

    fun mergeWith(global: IslandConfig): IslandConfig = IslandConfig(
        firstFloat = firstFloat ?: global.firstFloat ?: true,
        floatOnUpdate = floatOnUpdate ?: global.floatOnUpdate ?: false,
        isShowShade = isShowShade ?: global.isShowShade ?: false,
        timeout = timeout ?: global.timeout ?: 10,
        floatTimeout = floatTimeout ?: global.floatTimeout ?: 5,
        removeOriginalNotification = removeOriginalNotification ?: global.removeOriginalNotification ?: false,
        dismissWithOriginal = dismissWithOriginal ?: global.dismissWithOriginal ?: true,
        enableInlineReply = enableInlineReply ?: global.enableInlineReply ?: true,
        marqueeEnabled = marqueeEnabled ?: global.marqueeEnabled ?: false,
        marqueeDismissMode = marqueeDismissMode ?: global.marqueeDismissMode ?: MarqueeDismissMode.OFF,
        leftContent = leftContent ?: global.leftContent ?: IslandTextContent.AUTOMATIC,
        rightContent = rightContent ?: global.rightContent ?: IslandTextContent.AUTOMATIC,
        leftCustomExpression = leftCustomExpression ?: global.leftCustomExpression,
        rightCustomExpression = rightCustomExpression ?: global.rightCustomExpression,
        islandGlowMode = islandGlowMode ?: global.islandGlowMode ?: GlowMode.OFF,
        focusGlowMode = focusGlowMode ?: global.focusGlowMode ?: GlowMode.OFF,
        islandGlowColor = islandGlowColor ?: global.islandGlowColor,
        focusGlowColor = focusGlowColor ?: global.focusGlowColor,
        forceIslandGlow = forceIslandGlow ?: global.forceIslandGlow ?: false,
        forceFocusGlow = forceFocusGlow ?: global.forceFocusGlow ?: false,
        contactPinkGlow = contactPinkGlow ?: global.contactPinkGlow ?: false,
        restoreLockscreen = restoreLockscreen ?: global.restoreLockscreen ?: false,
        dndBehavior = dndBehavior ?: global.dndBehavior ?: IslandSceneBehavior.SUPPRESS,
        fullscreenBehavior = fullscreenBehavior ?: global.fullscreenBehavior ?: IslandSceneBehavior.DEFAULT,
        landscapeBehavior = landscapeBehavior ?: global.landscapeBehavior ?: IslandSceneBehavior.DEFAULT,
    )
}
