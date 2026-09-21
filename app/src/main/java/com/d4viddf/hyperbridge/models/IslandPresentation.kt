package com.d4viddf.hyperbridge.models

data class IslandTextSource(
    val title: String = "", val content: String = "", val subtitle: String = "",
    val app: String = "", val sender: String = "", val state: String = "", val progress: String = "",
)

data class IslandTextPresentation(val left: String, val right: String)

object IslandTextPresentationResolver {
    fun resolve(
        source: IslandTextSource,
        left: IslandTextContent?,
        right: IslandTextContent?,
        leftExpression: String? = null,
        rightExpression: String? = null,
    ): IslandTextPresentation = IslandTextPresentation(
        resolveOne(left ?: IslandTextContent.AUTOMATIC, source, source.sender.ifBlank { source.title.ifBlank { source.app } }, leftExpression),
        resolveOne(right ?: IslandTextContent.AUTOMATIC, source, source.content.ifBlank { source.state.ifBlank { source.progress } }, rightExpression),
    )

    private fun resolveOne(mode: IslandTextContent, source: IslandTextSource, automatic: String, expression: String?): String = when (mode) {
        IslandTextContent.AUTOMATIC -> automatic
        IslandTextContent.TITLE -> source.title
        IslandTextContent.CONTENT -> source.content
        IslandTextContent.SUBTITLE -> source.subtitle
        IslandTextContent.APP -> source.app
        IslandTextContent.SENDER -> source.sender.ifBlank { source.title }
        IslandTextContent.STATE -> source.state
        IslandTextContent.PROGRESS -> source.progress
        IslandTextContent.NONE -> ""
        IslandTextContent.CUSTOM -> expand(expression.orEmpty(), source)
    }.trim()

    private fun expand(expression: String, source: IslandTextSource): String = mapOf(
        "title" to source.title, "content" to source.content, "subtitle" to source.subtitle,
        "app" to source.app, "sender" to source.sender, "state" to source.state, "progress" to source.progress,
    ).entries.fold(expression) { value, (key, replacement) -> value.replace("{$key}", replacement, ignoreCase = true) }
}

data class IslandGlowPresentation(
    val islandMode: GlowMode, val focusMode: GlowMode, val islandColor: String?, val focusColor: String?,
    val dynamicColor: String?, val forceIsland: Boolean, val forceFocus: Boolean,
) {
    val islandEnabled: Boolean get() = islandMode != GlowMode.OFF
    val focusEnabled: Boolean get() = focusMode != GlowMode.OFF
    fun resolvedIslandColor(): String? = resolvedColor(islandMode, islandColor)
        ?: resolvedColor(focusMode, focusColor)
    fun resolvedFocusColor(): String? = resolvedColor(focusMode, focusColor)
        ?: resolvedColor(islandMode, islandColor)

    private fun resolvedColor(mode: GlowMode, manual: String?): String? = when (mode) {
        GlowMode.OFF -> null
        GlowMode.FOLLOW_DYNAMIC -> dynamicColor ?: manual
        GlowMode.ON -> manual ?: dynamicColor
    }
}

object IslandGlowResolver {
    const val CONTACT_PINK = "#FF69B4"
    private val CONTACT_TOKENS = arrayOf("levixcs", "nisamm")

    fun runtimeEnabled(modeRaw: String?, effectPresent: Boolean, force: Boolean): Boolean {
        val mode = GlowMode.parse(modeRaw)
        if (mode == GlowMode.OFF) return false
        return mode != null || effectPresent || force
    }

    fun contactPink(vararg texts: CharSequence?): String? {
        val haystack = texts.joinToString(" ") { it?.toString().orEmpty() }
        if (haystack.isBlank()) return null
        return CONTACT_PINK.takeIf { CONTACT_TOKENS.any { token -> haystack.contains(token, ignoreCase = true) } }
    }

    fun resolve(
        global: IslandConfig,
        app: IslandConfig,
        dynamicColor: String?,
        vararg contactTexts: CharSequence?,
    ): IslandGlowPresentation {
        val pinkEnabled = app.contactPinkGlow ?: global.contactPinkGlow ?: false
        val pink = contactPink(*contactTexts).takeIf { pinkEnabled }
        val islandMode = if (pink != null) GlowMode.ON else app.islandGlowMode ?: global.islandGlowMode ?: GlowMode.OFF
        val focusMode = if (pink != null) GlowMode.ON else app.focusGlowMode ?: global.focusGlowMode ?: GlowMode.OFF
        return IslandGlowPresentation(
            islandMode, focusMode,
            pink ?: normalizeColor(app.islandGlowColor ?: global.islandGlowColor),
            pink ?: normalizeColor(app.focusGlowColor ?: global.focusGlowColor),
            pink ?: normalizeColor(dynamicColor),
            islandMode != GlowMode.OFF && (pink != null || (app.forceIslandGlow ?: global.forceIslandGlow ?: false)),
            focusMode != GlowMode.OFF && (pink != null || (app.forceFocusGlow ?: global.forceFocusGlow ?: false)),
        )
    }

    fun normalizeColor(raw: String?): String? {
        val value = raw?.trim()?.removePrefix("#") ?: return null
        if (value.length !in setOf(6, 8) || value.any { it.digitToIntOrNull(16) == null }) return null
        return "#${value.uppercase()}"
    }

    fun clampRange(value: Int): Int = value.coerceIn(0, 100)
}

object MarqueeTimeoutPolicy {
    fun effectiveTimeout(originalSeconds: Int, mode: MarqueeDismissMode, hasOverflow: Boolean): Int? =
        if (mode.overridesTimeout && hasOverflow) null else originalSeconds.coerceAtLeast(1)

    fun shouldDismiss(mode: MarqueeDismissMode, completedLoops: Int, ongoing: Boolean): Boolean =
        !ongoing && mode.loops > 0 && completedLoops >= mode.loops
}

object IslandGenerationGuard {
    fun isCurrent(expected: Long, current: Long, owned: Boolean): Boolean =
        owned && expected != Long.MIN_VALUE && expected == current
}
