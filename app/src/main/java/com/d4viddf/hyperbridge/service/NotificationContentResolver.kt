package com.d4viddf.hyperbridge.service

data class MessageContentCandidate(
    val sender: String?,
    val text: String?
)

data class ResolvedNotificationContent(
    val title: String,
    val text: String,
    val hasMessageContent: Boolean
)

/** Resolves display content without depending on any app-specific notification shape. */
object NotificationContentResolver {
    fun resolve(
        title: CharSequence?,
        text: CharSequence?,
        bigTitle: CharSequence?,
        bigText: CharSequence?,
        messages: List<MessageContentCandidate>,
        isMessageStyle: Boolean
    ): ResolvedNotificationContent {
        val latestMessage = messages.lastOrNull { !it.text.toString().isBlank() }
        val resolvedTitle = title.clean()
            .ifEmpty { bigTitle.clean() }
            .ifEmpty { latestMessage?.sender.clean() }
        val resolvedText = text.clean()
            .ifEmpty { bigText.clean() }
            .ifEmpty { latestMessage?.text.clean() }
        return ResolvedNotificationContent(
            title = resolvedTitle,
            text = resolvedText,
            hasMessageContent = isMessageStyle && latestMessage != null
        )
    }

    private fun Any?.clean(): String = this?.toString()?.trim().orEmpty()
}
