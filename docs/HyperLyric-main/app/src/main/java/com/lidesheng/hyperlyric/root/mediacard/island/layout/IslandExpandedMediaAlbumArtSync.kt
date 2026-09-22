package com.lidesheng.hyperlyric.root.mediacard.island.layout

import android.content.Context
import android.view.View

/**
 * Keeps the nested artwork image aligned with the outer album container.
 *
 * The island ConstraintSet only owns the root-level album container.  The
 * image is inflated below it and is clipped by the container's rounded outline,
 * so every style must update both bounds after applying its ConstraintSet.
 */
internal object IslandExpandedMediaAlbumArtSync {
    fun apply(
        player: View,
        context: Context,
        ids: IslandExpandedMediaLayoutResourceIds,
        sizeDp: Float
    ) {
        val size = context.islandExpandedMediaDp(sizeDp)
        updateSize(player.findViewById(ids.albumArt), size)
        if (ids.albumArtImage != 0) {
            updateSize(player.findViewById(ids.albumArtImage), size)
        }
    }

    private fun updateSize(view: View?, size: Int) {
        val params = view?.layoutParams ?: return
        if (params.width == size && params.height == size) return
        params.width = size
        params.height = size
        view.layoutParams = params
        view.requestLayout()
    }
}
