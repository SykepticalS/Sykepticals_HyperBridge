package com.d4viddf.hyperbridge.service.call

/**
 * Apps whose in-call screen follows a Mute or Unmute action on their own notification.
 * Other calling apps are left alone until a live notification shows the same kind of action.
 */
internal object CallMicrophoneApps {
    private val packages = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
    )

    fun supportsNotificationMute(packageName: String): Boolean = packageName in packages
}
