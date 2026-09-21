package com.d4viddf.hyperbridge.xposed

/**
 * Predicts whether a source notification will be represented by an island.
 *
 * WhatsApp posts an InboxStyle group summary alongside its MessagingStyle child. Both are
 * CATEGORY_MESSAGE notifications, but only the child advertises MessagingStyle. When messages
 * are presented through the standard island translator, the summary still needs the suppression
 * marker or Xiaomi can float it after the child has already been suppressed.
 */
internal object SourceHeadsUpReplacementPolicy {
    private val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")

    fun expectsReplacement(
        packageName: String,
        semanticType: String,
        enabledTypes: Set<String>,
        directMessagingStyle: Boolean,
    ): Boolean {
        if (semanticType in enabledTypes) return true
        if (semanticType != "MESSAGE" || "STANDARD" !in enabledTypes) return false
        return directMessagingStyle || packageName in whatsappPackages
    }
}
