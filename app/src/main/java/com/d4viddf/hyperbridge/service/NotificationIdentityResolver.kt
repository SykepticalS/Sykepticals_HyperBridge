package com.d4viddf.hyperbridge.service

/** Picks the person/title vs body so media-share DMs do not put two captions on the island. */
object NotificationIdentityResolver {
    private val mediaShareCaptions = listOf(
        "sent you a video",
        "sent you a post",
        "sent you a reel",
        "sent you a photo",
        "sent you a picture",
        "sent you a tiktok",
        "shared a video",
        "shared a photo",
        "shared a picture",
        "shared a post",
        "shared a reel",
        "shared a story",
        "shared a tiktok",
        "sent a video",
        "sent a photo",
        "sent a tiktok",
    )
    private val GROUP_UNREAD_COUNT = Regex(
        """\s*\(\d+\s+(?:new\s+)?messages?\)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun isMediaShareCaption(value: CharSequence?): Boolean {
        val lower = value.clean().lowercase()
        if (lower.isBlank()) return false
        return mediaShareCaptions.any { it in lower }
    }

    fun resolve(
        packageName: String = "",
        appLabel: String = "",
        title: CharSequence? = null,
        text: CharSequence? = null,
        bigTitle: CharSequence? = null,
        bigText: CharSequence? = null,
        conversationTitle: CharSequence? = null,
        personNames: List<String> = emptyList(),
        messageSender: CharSequence? = null,
        messageText: CharSequence? = null,
        subText: CharSequence? = null,
        summaryText: CharSequence? = null,
        infoText: CharSequence? = null,
        textLines: List<CharSequence?> = emptyList(),
        ticker: CharSequence? = null,
        shortcutLabel: CharSequence? = null,
        remoteTexts: List<String> = emptyList(),
        selfName: CharSequence? = null,
        isGroupConversation: Boolean = false,
    ): Pair<String, String> {
        val app = appLabel.clean()
        val self = selfName.clean()
        val names = personNames.map { it.clean() }.filter {
            it.isNotBlank() && !it.equals(app, true) && !it.equals(self, true)
        }
        val conversationRaw = firstDistinct(
            conversationTitle.clean().takeUnless { it.equals(app, true) || it.equals(self, true) }.orEmpty(),
            shortcutLabel.clean().takeUnless { it.equals(app, true) || it.equals(self, true) }.orEmpty(),
        )
        val conversation = stripUnreadCount(conversationRaw)
        val groupConversation = isGroupConversation ||
            looksLikeGroupConversation(conversationRaw)
        val tickerSender = parseTickerSender(ticker.clean(), app, self)
        val shareSender = parseShareSender(title.clean(), app, self)
            .ifBlank { parseShareSender(text.clean(), app, self) }
        val remoteName = remoteTexts.map { it.clean() }.firstOrNull {
            looksLikePersonName(it, app, self)
        }.orEmpty()
        val childSender = messageSender.clean().takeUnless {
            it.equals(app, true) || it.equals(self, true)
        }.orEmpty()
        val personSender = firstDistinct(
            childSender,
            names.firstOrNull().orEmpty(),
            tickerSender,
            shareSender,
            remoteName,
        )
        val rawTitle = firstDistinct(title.clean(), bigTitle.clean())
        // Multiple logged-in Instagram accounts label the shade as
        // "{account receiving}: {account sender}". Keep that pair. A single
        // account has no such prefix, so the sender stands alone. Groups
        // still identify the member who sent the message.
        val multiAccountTitle = instagramMultiAccountTitle(
            packageName = packageName,
            appLabel = app,
            rawTitle = rawTitle,
            receivingAccount = conversation,
            sender = personSender,
            groupConversation = groupConversation,
        )
        val directShareFromOther = !groupConversation &&
            multiAccountTitle.isBlank() &&
            personSender.isNotBlank() &&
            isDirectMediaShare(messageText, text, bigText, ticker)
        val sender = when {
            groupConversation -> personSender
            multiAccountTitle.isNotBlank() -> multiAccountTitle
            directShareFromOther -> personSender
            else -> firstDistinct(conversation, personSender)
        }
        val resolvedTitle = if (groupConversation) {
            personSender.ifBlank { conversation }.ifBlank { stripSenderPrefix(rawTitle, names) }
        } else {
            sender.ifBlank { stripSenderPrefix(rawTitle, names) }
        }.let(::stripUnreadCount)
        val latestLine = textLines.asReversed().firstOrNull { it.clean().isNotEmpty() }.clean()
        val body = buildList {
            add(messageText.clean())
            add(text.clean())
            add(bigText.clean())
            add(latestLine)
            add(summaryText.clean())
            add(infoText.clean())
            add(subText.clean())
            if (!isIdentityTitle(rawTitle, resolvedTitle, conversation, names, app)) {
                add(rawTitle)
            }
            remoteTexts.map { it.clean() }
                .filter { it.isNotBlank() && !looksLikePersonName(it, app, self) }
                .forEach(::add)
        }.firstOrNull { candidate ->
            candidate.isNotBlank() &&
                !candidate.equals(resolvedTitle, ignoreCase = true) &&
                !candidate.equals(app, ignoreCase = true) &&
                !candidate.equals(self, ignoreCase = true) &&
                !isIdentityTitle(candidate, resolvedTitle, conversation, names, app)
        }.orEmpty()
        return resolvedTitle to body
    }

    private fun instagramMultiAccountTitle(
        packageName: String,
        appLabel: String,
        rawTitle: String,
        receivingAccount: String,
        sender: String,
        groupConversation: Boolean,
    ): String {
        if (groupConversation || rawTitle.isBlank()) return ""
        val instagram = packageName == INSTAGRAM_PACKAGE || appLabel.equals("Instagram", true)
        if (!instagram) return ""
        if (receivingAccount.isBlank() || sender.isBlank() || receivingAccount.equals(sender, true)) return ""
        val compound = "$receivingAccount: $sender"
        return if (rawTitle.equals(compound, ignoreCase = true)) rawTitle else ""
    }

    private fun isDirectMediaShare(vararg values: CharSequence?): Boolean =
        values.any { isMediaShareCaption(it) }

    private fun looksLikeGroupConversation(conversationTitle: String): Boolean =
        GROUP_UNREAD_COUNT.containsMatchIn(conversationTitle)

    private fun stripUnreadCount(value: String): String =
        value.replace(GROUP_UNREAD_COUNT, "").trim()

    private fun isIdentityTitle(
        value: String,
        sender: String,
        conversation: String,
        names: List<String>,
        app: String,
    ): Boolean {
        if (value.isBlank()) return false
        if (value.equals(sender, true) || value.equals(conversation, true) || value.equals(app, true)) {
            return true
        }
        val stripped = stripUnreadCount(value)
        if (stripped.equals(sender, true) || stripped.equals(conversation, true)) return true
        if (names.any { value.equals(it, true) || stripped.equals(it, true) }) return true
        val prefix = stripUnreadCount(value.substringBefore(':', missingDelimiterValue = "").trim())
        if (prefix.isBlank() || prefix == stripped) return false
        return prefix.equals(sender, true) ||
            prefix.equals(conversation, true) ||
            names.any { prefix.equals(it, true) }
    }

    private fun stripSenderPrefix(title: String, names: List<String>): String {
        val prefix = title.substringBefore(':', missingDelimiterValue = "").trim()
        if (prefix.isBlank() || prefix == title) return title
        return if (names.any { prefix.equals(it, true) }) prefix else title
    }

    private fun parseTickerSender(ticker: String, app: String, self: String): String {
        val name = ticker.substringBefore(':', missingDelimiterValue = "").trim()
        return if (looksLikePersonName(name, app, self) && name != ticker.trim()) name else ""
    }

    private fun parseShareSender(value: String, app: String, self: String): String {
        val match = Regex(
            """^(.{1,48}?) (?:shared a (?:video|photo|picture|post|reel|story|tiktok)|sent you a (?:video|photo|picture|post|reel|tiktok)|sent a (?:video|photo|tiktok))\b""",
            RegexOption.IGNORE_CASE,
        ).find(value) ?: return ""
        return match.groupValues[1].trim().takeIf { looksLikePersonName(it, app, self) }.orEmpty()
    }

    internal fun looksLikePersonName(value: String, app: String = "", self: String = ""): Boolean {
        if (value.isBlank() || value.equals(app, true) || value.equals(self, true)) return false
        if (isMediaShareCaption(value) || value.contains('\n')) return false
        if ("->" in value || "→" in value) return false
        if (value.contains('.') && !value.contains(' ')) return false
        if (value.length > 40) return false
        val words = value.split(Regex("""\s+""")).filter { it.isNotBlank() }
        return words.size in 1..4 && !value.endsWith("...")
    }

    private const val INSTAGRAM_PACKAGE = "com.instagram.android"

    private fun firstDistinct(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun Any?.clean(): String = this?.toString()?.trim().orEmpty()
}
