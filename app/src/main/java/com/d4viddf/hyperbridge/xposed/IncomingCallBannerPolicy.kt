package com.d4viddf.hyperbridge.xposed

/**
 * Decides when an incoming-call notification may lose its on-screen heads-up banner.
 *
 * The notification, its vibration, and its full-screen intent stay in place. This only
 * authorizes hiding the banner that drops from the top while the person is on the home
 * screen or inside another app.
 */
internal object IncomingCallBannerPolicy {
    fun shouldSuppress(
        suppressSourceEnabled: Boolean,
        engineReady: Boolean,
        packageAllowed: Boolean,
        callIslandsEnabled: Boolean,
        incomingStageEnabled: Boolean,
        hasFullScreenIntent: Boolean,
        isIncomingCall: Boolean,
    ): Boolean {
        return suppressSourceEnabled &&
            engineReady &&
            packageAllowed &&
            callIslandsEnabled &&
            incomingStageEnabled &&
            hasFullScreenIntent &&
            isIncomingCall
    }

    /**
     * Home screen and in-app use are interactive and unlocked.
     * A locked or sleeping phone keeps the system's full-screen incoming call UI.
     */
    fun inUnlockedApp(interactive: Boolean?, keyguardLocked: Boolean?): Boolean {
        return interactive == true && keyguardLocked == false
    }
}
