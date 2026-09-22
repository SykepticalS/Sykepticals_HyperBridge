package com.lidesheng.hyperlyric.common.media

import android.media.session.MediaSession

/**
 * Identity of the player session and the media item it currently exposes.
 *
 * A session token identifies the player session. [songId] and [mediaId] identify the item when
 * either the lyric source or the player publishes a stable item id. Display metadata is
 * deliberately not part of this value because titles and artists may change presentation.
 */
data class MediaIdentity(
    val packageName: String = "",
    val sessionToken: MediaSession.Token? = null,
    val songId: String? = null,
    val mediaId: String? = null
) {
    fun normalized(): MediaIdentity = copy(
        packageName = packageName.trim(),
        songId = songId.normalizeIdentityText(),
        mediaId = mediaId.normalizeIdentityText()
    )

    /**
     * Fill only unknown fields from another snapshot. The receiver remains authoritative for
     * every field it already knows.
     */
    fun fillMissingFrom(fallback: MediaIdentity): MediaIdentity = copy(
        packageName = packageName.ifEmpty { fallback.packageName },
        sessionToken = sessionToken ?: fallback.sessionToken,
        songId = songId ?: fallback.songId,
        mediaId = mediaId ?: fallback.mediaId
    ).normalized()

    /**
     * Whether both snapshots prove that they belong to the same MediaSession. `null` means the
     * source did not expose enough information; a package name alone is never treated as proof.
     */
    fun sameSessionAs(other: MediaIdentity): Boolean? {
        val left = normalized()
        val right = other.normalized()
        if (left.packageName.isNotEmpty() && right.packageName.isNotEmpty() &&
            left.packageName != right.packageName
        ) {
            return false
        }
        if (left.sessionToken != null && right.sessionToken != null) {
            return left.sessionToken == right.sessionToken
        }
        return null
    }

    /**
     * Returns false only when both sides expose conflicting ownership or item identity. Missing
     * fields are intentionally treated as unknown so weaker sources can be completed later.
     */
    fun isCompatibleWith(other: MediaIdentity): Boolean {
        val left = normalized()
        val right = other.normalized()
        if (left.sameSessionAs(right) == false) return false
        if (left.songId != null && right.songId != null && left.songId != right.songId) {
            return false
        }
        if (left.mediaId != null && right.mediaId != null && left.mediaId != right.mediaId) {
            return false
        }
        return true
    }

    private fun String?.normalizeIdentityText(): String? = this
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "0" }
}
