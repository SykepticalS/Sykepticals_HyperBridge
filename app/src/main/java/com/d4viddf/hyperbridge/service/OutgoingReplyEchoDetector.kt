package com.d4viddf.hyperbridge.service

/**
 * WhatsApp and similar apps rewrite the conversation notification after an inline reply so the
 * latest line is the user's own message ("You: …"). That is not a new inbound event.
 */
object OutgoingReplyEchoDetector {
    private val selfLabels = setOf(
        "you", "me", "yo", "tú", "tu", "vous", "du", "eu", "io", "ich", "أنا",
    )
    private val selfPrefix = Regex(
        """^(?:you|me|yo|tú|tu|vous|du|eu|io|ich)\s*[:：]\s+.+""",
        RegexOption.IGNORE_CASE,
    )

    fun isOutgoingEcho(
        messages: List<MessageContentCandidate>,
        selfName: String?,
        title: String,
        text: String,
        remoteInputHistory: Array<out CharSequence>? = null,
        extras: List<String?> = emptyList(),
    ): Boolean {
        val latest = messages.lastOrNull { !it.sender.isNullOrBlank() || !it.text.isNullOrBlank() }
        if (latest != null) {
            if (latest.isSelf) return true
            if (isSelfLabel(latest.sender, selfName)) return true
            if (hasSelfPrefix(latest.text) || hasSelfPrefix(latest.sender)) return true
            if (matchesHistory(latest.text, remoteInputHistory)) return true
        }
        if (isSelfLabel(title, selfName) && hasSelfPrefix(text)) return true
        if (hasSelfPrefix(title) || hasSelfPrefix(text)) return true
        if (extras.any { hasSelfPrefix(it) }) return true
        if (matchesHistory(text, remoteInputHistory)) return true
        return false
    }

    private fun isSelfLabel(value: String?, selfName: String?): Boolean {
        val name = value?.trim().orEmpty()
        if (name.isEmpty()) return false
        if (!selfName.isNullOrBlank() && name.equals(selfName.trim(), ignoreCase = true)) return true
        return name.lowercase() in selfLabels
    }

    private fun hasSelfPrefix(value: String?): Boolean =
        !value.isNullOrBlank() && selfPrefix.containsMatchIn(value.trim())

    private fun matchesHistory(value: String?, history: Array<out CharSequence>?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isEmpty() || history.isNullOrEmpty()) return false
        return history.any { entry ->
            val item = entry.toString().trim()
            item.isNotEmpty() && (item.equals(text, ignoreCase = true) || text.endsWith(item, ignoreCase = true))
        }
    }
}
