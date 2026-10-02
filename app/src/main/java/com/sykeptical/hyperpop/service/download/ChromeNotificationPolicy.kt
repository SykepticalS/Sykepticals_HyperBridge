package com.sykeptical.hyperpop.service.download

/**
 * Chrome posts several ongoing notifications that are not transfers.
 * The paused download on this device is channel `downloads`, title `5GB.zip`,
 * text `Download paused`, actions Resume and Cancel, and progress 0/0.
 * Incognito is channel `incognito` and must stay a normal notification.
 */
object ChromeNotificationPolicy {
    fun isChrome(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.android.chrome" ||
            pkg == "com.chrome.beta" ||
            pkg == "com.chrome.dev" ||
            pkg == "com.chrome.canary" ||
            pkg == "com.google.android.apps.chrome" ||
            pkg.startsWith("com.chrome.")
    }

    fun isIncognito(packageName: String, channelId: String?): Boolean =
        isChrome(packageName) && channelId.equals(INCOGNITO_CHANNEL, ignoreCase = true)

    fun isDownloadChannel(channelId: String?): Boolean {
        val channel = channelId?.lowercase().orEmpty()
        return channel == DOWNLOADS_CHANNEL || channel == COMPLETED_DOWNLOADS_CHANNEL
    }

    private const val INCOGNITO_CHANNEL = "incognito"
    private const val DOWNLOADS_CHANNEL = "downloads"
    private const val COMPLETED_DOWNLOADS_CHANNEL = "completed_downloads"
}
