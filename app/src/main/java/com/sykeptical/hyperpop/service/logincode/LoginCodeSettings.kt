package com.sykeptical.hyperpop.service.logincode

data class LoginCodeSettings(
    val enabled: Boolean = true,
    val copyAction: Boolean = true,
    val dismissAfterCopy: Boolean = false,
    val glow: Boolean = true,
    val compactCode: Boolean = true,
    val packages: Set<String> = DEFAULT_PACKAGES,
) {
    fun appliesTo(packageName: String): Boolean =
        enabled && (copyAction || glow || compactCode) && (packageName in packages || packageName == "com.android.shell") // TEMP-TEST

    companion object {
        /** SMS, e-mail and messenger apps that commonly receive sign-in codes. */
        val DEFAULT_PACKAGES: Set<String> = setOf(
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.samsung.android.messaging",
            "com.google.android.apps.googlevoice",
            "com.microsoft.android.smsorganizer",
            "com.truecaller",
            "com.textra",
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "ch.protonmail.android",
            "com.readdle.spark",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.thoughtcrime.securesms",
        )
    }
}
