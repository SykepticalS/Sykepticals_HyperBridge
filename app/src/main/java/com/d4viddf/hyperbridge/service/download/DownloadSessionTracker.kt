package com.d4viddf.hyperbridge.service.download

object DownloadReplacementPolicy {
    /** Chrome/Play often cancel+repost the same file on a new notification slot. */
    const val MATCH_GRACE_MS = 2_500L
    const val REMOVAL_DELAY_MS = 2_750L
}

data class DownloadSessionInput(
    val sourceKey: String,
    val packageName: String,
    val notificationId: Int,
    val notificationTag: String?,
    val title: String,
    val text: String,
    val observedAt: Long,
    val finished: Boolean = false,
)

data class DownloadSession(
    val logicalId: String,
    val packageName: String,
    val sourceKey: String,
    val stableLabel: String,
    val slotId: String,
    val lastSeen: Long,
    val finished: Boolean,
    val replacementDeadline: Long? = null,
    val sourceReplacement: Boolean = false,
)

/**
 * Progress downloads (Chrome especially) churn StatusBarNotification keys while the file
 * stays the same. Logical identity is package + stable filename, with the Android slot as
 * a fallback so Play-style in-place updates keep working.
 */
class DownloadSessionTracker(
    private val replacementGraceMs: Long = DownloadReplacementPolicy.MATCH_GRACE_MS,
    private val staleSessionMs: Long = 12 * 60 * 60 * 1_000L,
) {
    private val sessions = LinkedHashMap<String, DownloadSession>()
    private val sourceIndex = HashMap<String, String>()

    @Synchronized
    fun resolve(input: DownloadSessionInput): DownloadSession {
        prune(input.observedAt)
        val label = DownloadIdentity.stableLabel(input.title, input.text)
        val slotId = DownloadIdentity.slotId(input.notificationId, input.notificationTag)

        sourceIndex[input.sourceKey]?.let { existingId ->
            sessions[existingId]?.let { current ->
                return bind(
                    current.copy(
                        sourceKey = input.sourceKey,
                        stableLabel = label.ifBlank { current.stableLabel },
                        slotId = slotId,
                        lastSeen = input.observedAt,
                        finished = input.finished,
                        replacementDeadline = null,
                        sourceReplacement = current.replacementDeadline != null,
                    )
                )
            }
        }

        val matched = sessions.values.firstOrNull { candidate ->
            candidate.packageName == input.packageName &&
                (
                    (label.isNotBlank() && candidate.stableLabel == label) ||
                        (label.isBlank() && candidate.slotId == slotId)
                ) &&
                (
                    candidate.replacementDeadline == null ||
                        input.observedAt <= candidate.replacementDeadline
                )
        }

        val session = if (matched != null) {
            sourceIndex.entries.removeIf { it.value == matched.logicalId && it.key != input.sourceKey }
            matched.copy(
                sourceKey = input.sourceKey,
                stableLabel = label.ifBlank { matched.stableLabel },
                slotId = slotId,
                lastSeen = input.observedAt,
                finished = input.finished,
                replacementDeadline = null,
                sourceReplacement = matched.sourceKey != input.sourceKey,
            )
        } else {
            val identity = if (label.isNotBlank()) {
                "file:$label"
            } else {
                "slot:$slotId"
            }
            DownloadSession(
                logicalId = "download:${input.packageName}:$identity",
                packageName = input.packageName,
                sourceKey = input.sourceKey,
                stableLabel = label,
                slotId = slotId,
                lastSeen = input.observedAt,
                finished = input.finished,
            )
        }
        return bind(session)
    }

    @Synchronized
    fun markSourceRemoved(sourceKey: String, now: Long): String? {
        val logicalId = sourceIndex[sourceKey] ?: return null
        val current = sessions[logicalId] ?: return null
        sessions[logicalId] = current.copy(replacementDeadline = now + replacementGraceMs)
        return logicalId
    }

    @Synchronized
    fun logicalIdForSource(sourceKey: String): String? = sourceIndex[sourceKey]

    @Synchronized
    fun end(logicalId: String) {
        val removed = sessions.remove(logicalId) ?: return
        sourceIndex.entries.removeIf { it.value == removed.logicalId }
    }

    @Synchronized
    fun clear() {
        sessions.clear()
        sourceIndex.clear()
    }

    private fun bind(session: DownloadSession): DownloadSession {
        sessions[session.logicalId] = session
        sourceIndex[session.sourceKey] = session.logicalId
        return session
    }

    private fun prune(now: Long) {
        val stale = sessions.values.filter { now - it.lastSeen > staleSessionMs }.map { it.logicalId }
        stale.forEach(::end)
        sessions.values.filter { deadline ->
            val until = deadline.replacementDeadline ?: return@filter false
            now > until
        }.forEach { expired ->
            sourceIndex.entries.removeIf {
                it.value == expired.logicalId && it.key == expired.sourceKey
            }
        }
    }
}

object DownloadIdentity {
    private val NOISE = Regex(
        """(?i)\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)\s*/\s*\d+(\.\d+)?\s*(b|kb|mb|gb|tb|kib|mib|gib)""" +
            """|(?i)\d+(\.\d+)?\s*(mb/s|kb/s|gb/s|mib/s|kib/s|mbps|kbps|gbps|m/s)""" +
            """|(?i)\d+\s*%""" +
            """|(?i)\bdownloading\b|\bdownload\b|\bqueued\b|\bpending\b|\bwaiting\b|\bpaused\b|\bcomplete\b|\bfinished\b|\bdone\b"""
    )

    fun slotId(notificationId: Int, notificationTag: String?): String =
        "$notificationId:${notificationTag.orEmpty()}"

    fun stableLabel(title: String, text: String): String {
        val fromTitle = normalize(title)
        if (fromTitle.isNotEmpty()) return fromTitle
        return normalize(text)
    }

    private fun normalize(value: String): String = stripPrefixes(value)
        .replace(NOISE, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .lowercase()

    private fun stripPrefixes(value: String): String {
        var result = value.trim()
        for (prefix in listOf("正在下载", "下载中", "下载", "Downloading", "Download")) {
            if (result.startsWith(prefix, ignoreCase = true)) {
                result = result.substring(prefix.length).trimStart(':', '：', ' ', '-')
                break
            }
        }
        return result.trim()
    }
}
