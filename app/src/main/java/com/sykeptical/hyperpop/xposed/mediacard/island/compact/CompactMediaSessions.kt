package com.sykeptical.hyperpop.xposed.mediacard.island.compact

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import com.sykeptical.hyperpop.xposed.mediacard.MediaCardLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Title and artist per media app, straight from the active sessions SystemUI can see.
 * The native island has no song name of its own, and the session is the first to know
 * when the track changes.
 */
internal object CompactMediaSessions {
    private const val TAG = "CompactMediaSessions"

    data class Track(val title: String, val artist: String)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tracks = ConcurrentHashMap<String, Track>()
    private val callbacks = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private var manager: MediaSessionManager? = null
    @Volatile private var started = false
    @Volatile var onTrackChanged: ((String) -> Unit)? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        sync(controllers.orEmpty())
    }

    fun ensureStarted(context: Context) {
        if (started) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { ensureStarted(context) }
            return
        }
        started = true
        val service = context.getSystemService(MediaSessionManager::class.java) ?: return
        runCatching {
            service.addOnActiveSessionsChangedListener(sessionsListener, null, mainHandler)
            manager = service
            sync(service.getActiveSessions(null))
        }.onFailure { error -> MediaCardLog.w(TAG, "Media sessions unavailable", error) }
    }

    fun track(packageName: String): Track? = tracks[packageName]

    private fun sync(controllers: List<MediaController>) {
        val live = controllers.associateBy { it.sessionToken }
        callbacks.keys.filter { it !in live }.forEach { token ->
            callbacks.remove(token)?.let { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
        }
        controllers.forEach { controller ->
            val token = controller.sessionToken
            if (callbacks.containsKey(token)) return@forEach
            val packageName = controller.packageName
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    update(packageName, metadata)
                }

                override fun onSessionDestroyed() {
                    callbacks.remove(token)?.let { (dead, callback) ->
                        runCatching { dead.unregisterCallback(callback) }
                    }
                }
            }
            val registered = runCatching { controller.registerCallback(callback, mainHandler) }.isSuccess
            if (registered) callbacks[token] = controller to callback
            update(packageName, runCatching { controller.metadata }.getOrNull())
        }
    }

    private fun update(packageName: String, metadata: MediaMetadata?) {
        if (metadata == null) return
        val title = metadata.text(MediaMetadata.METADATA_KEY_TITLE)
            .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) }
        val artist = metadata.text(MediaMetadata.METADATA_KEY_ARTIST)
            .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) }
            .ifBlank { metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) }
        // Players clear metadata for a moment between tracks. Keep the last song until the next one arrives.
        if (title.isBlank() && artist.isBlank()) return
        val next = Track(title, artist)
        if (tracks.put(packageName, next) != next) onTrackChanged?.invoke(packageName)
    }

    private fun MediaMetadata.text(key: String): String =
        runCatching { getText(key)?.toString()?.trim() }.getOrNull().orEmpty()
}
