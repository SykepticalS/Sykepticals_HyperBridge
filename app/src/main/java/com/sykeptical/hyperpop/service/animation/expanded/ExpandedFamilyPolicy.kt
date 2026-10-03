package com.sykeptical.hyperpop.service.animation.expanded

/**
 * Which expanded families share the camera band, and which stay below it.
 * Package name is not an input.
 */
object MessagePolicy {
    fun titleRole(): ContentLeafRole = ContentLeafRole.PRIMARY_TITLE
    fun bodyRole(): ContentLeafRole = ContentLeafRole.SECONDARY_TEXT
    fun avatarUsesBand(): Boolean = true
    fun bodyScrolls(): Boolean = false
}

object VoiceMessagePolicy {
    fun titleRole(): ContentLeafRole = ContentLeafRole.PRIMARY_TITLE
    fun playControlRole(): ContentLeafRole = ContentLeafRole.CALL_CONTROL
    fun durationRole(): ContentLeafRole = ContentLeafRole.SECONDARY_TEXT
}

object ProgressPolicy {
    fun role(): ContentLeafRole = ContentLeafRole.PROGRESS
    fun staysBelowCamera(): Boolean = true
    fun labelScrolls(): Boolean = false
}

object NavigationPolicy {
    fun titleRole(): ContentLeafRole = ContentLeafRole.PRIMARY_TITLE
    fun specialTitleUsesBand(): Boolean = false
}
