package com.sykeptical.hyperpop.service

/**
 * WhatsApp and similar apps rewrite the conversation notification after an inline reply so the
 * latest line is the user's own message ("You: …"). That is not a new inbound event.
 */
object OutgoingReplyEchoDetector {
    private val selfLabels = setOf(
        "you", "me", "yo", "tú", "tu", "vous", "du", "eu", "io", "ich", "أنا",
    )
    private val selfPrefix = Regex(
        """^(you|me|yo|tú|tu|vous|du|eu|io|ich)\s*[:：]\s+.+""",
        RegexOption.IGNORE_CASE,
    )

    fun isOutgoingEcho(
        messages: List<MessageContentCandidate>,
        selfName: String?,
        title: String,
        text: String,
        remoteInputHistory: Array<out CharSequence>? = null,
        extras: List<String?> = emptyList(),
        conversationTitle: String? = null,
    ): Boolean {
        val latest = messages.lastOrNull { !it.sender.isNullOrBlank() || !it.text.isNullOrBlank() }
        if (latest != null) {
            // A 1:1 chat whose display name is a generic self word ("Me", "You") is the
            // other party. Name equality with that title is not an inline-reply echo.
            val counterparty = isCounterparty(latest.sender, conversationTitle)
            if (latest.isSelf && !counterparty) return true
            if (isSelfLabel(latest.sender, selfName) && !counterparty) return true
            if (hasSelfPrefix(latest.text, conversationTitle) || hasSelfPrefix(latest.sender, conversationTitle)) {
                return true
            }
            if (matchesHistory(latest.text, remoteInputHistory)) return true
        }
        if (isSelfLabel(title, selfName) && hasSelfPrefix(text, conversationTitle) && !isCounterparty(title, conversationTitle)) {
            return true
        }
        if (hasSelfPrefix(title, conversationTitle) || hasSelfPrefix(text, conversationTitle)) return true
        if (extras.any { hasSelfPrefix(it, conversationTitle) }) return true
        if (matchesHistory(text, remoteInputHistory)) return true
        return false
    }

    private fun isCounterparty(sender: String?, conversationTitle: String?): Boolean {
        val name = sender?.trim().orEmpty()
        val conversation = conversationTitle?.trim().orEmpty()
        return name.isNotEmpty() && conversation.isNotEmpty() && name.equals(conversation, ignoreCase = true)
    }

    private fun isSelfLabel(value: String?, selfName: String?): Boolean {
        val name = value?.trim().orEmpty()
        if (name.isEmpty()) return false
        if (!selfName.isNullOrBlank() && name.equals(selfName.trim(), ignoreCase = true)) return true
        return name.lowercase() in selfLabels
    }

    private fun hasSelfPrefix(value: String?, conversationTitle: String?): Boolean {
        val line = value?.trim().orEmpty()
        val match = selfPrefix.find(line) ?: return false
        return !isCounterparty(match.groupValues.getOrNull(1), conversationTitle)
    }

    private fun matchesHistory(value: String?, history: Array<out CharSequence>?): Boolean {
        val text = value?.trim().orEmpty()
        if (text.isEmpty() || history.isNullOrEmpty()) return false
        return history.any { entry ->
            val item = entry.toString().trim()
            item.isNotEmpty() && (item.equals(text, ignoreCase = true) || text.endsWith(item, ignoreCase = true))
        }
    }
}
