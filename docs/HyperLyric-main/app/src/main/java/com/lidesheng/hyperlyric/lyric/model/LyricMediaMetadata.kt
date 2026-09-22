package com.lidesheng.hyperlyric.lyric.model

import android.media.session.MediaSession
import com.lidesheng.hyperlyric.common.media.MediaIdentity

/**
 * A source-independent snapshot of the media information that accompanies lyrics.
 *
 * Source implementations may only know part of this object. Missing fields are left null and
 * are filled from the current MediaSession by the root-side resolver.
 */
data class LyricMediaMetadata(
    val sourceId: String,
    val packageName: String? = null,
    val songId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val duration: Long? = null,
    /** Exact MediaSession identity when the source already owns a controller. */
    val sessionToken: MediaSession.Token? = null,
    /** Player-defined identity of the current media item, when available. */
    val mediaId: String? = null
) {

    /** Normalize source-defined text without applying player-specific parsing heuristics. */
    fun normalized(): LyricMediaMetadata = copy(
        packageName = packageName.normalizeMediaText(),
        songId = songId.normalizeMediaText(),
        title = title.normalizeMediaText(),
        artist = artist.normalizeMediaText(),
        album = album.normalizeMediaText(),
        duration = duration?.takeIf { it > 0L },
        mediaId = mediaId.normalizeMediaText()
    )

    fun toIdentity(defaultPackageName: String = packageName.orEmpty()): MediaIdentity =
        MediaIdentity(
            packageName = packageName ?: defaultPackageName,
            sessionToken = sessionToken,
            songId = songId,
            mediaId = mediaId
        ).normalized()
}

private val mediaTextWhitespace = Regex("\\s+")

private fun String?.normalizeMediaText(): String? = this
    ?.replace(mediaTextWhitespace, " ")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
