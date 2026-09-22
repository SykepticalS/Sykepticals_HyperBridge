package com.lidesheng.hyperlyric.root.island.presentation

/**
 * Owns the small piece of mutable state shared by presentation callbacks.
 *
 * Playback changes invalidate pending callbacks through the revision counter. The state object
 * keeps that concurrency contract in one place while the coordinator remains responsible for
 * policy and view reconciliation.
 */
internal class IslandPresentationState {
    @Volatile
    private var playbackActive = true

    @Volatile
    private var presentationRevision = 0L

    private val lock = Any()

    fun updatePlaybackState(isPlaying: Boolean): Boolean {
        return synchronized(lock) {
            val changed = playbackActive != isPlaying
            playbackActive = isPlaying
            if (changed) presentationRevision++
            changed
        }
    }

    fun isPlaybackActive(): Boolean = playbackActive

    fun invalidatePresentation(): Long {
        return synchronized(lock) {
            ++presentationRevision
        }
    }

    fun currentRevision(): Long = presentationRevision

    fun isCurrentRevision(revision: Long): Boolean {
        return presentationRevision == revision
    }
}
