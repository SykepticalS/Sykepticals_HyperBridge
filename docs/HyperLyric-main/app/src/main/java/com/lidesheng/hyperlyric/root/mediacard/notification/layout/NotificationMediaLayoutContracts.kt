package com.lidesheng.hyperlyric.root.mediacard.notification.layout

import android.content.Context

internal object NotificationMediaConstraintSide {
    const val LEFT = 1
    const val RIGHT = 2
    const val TOP = 3
    const val BOTTOM = 4
    const val START = 6
    const val END = 7
}

internal interface NotificationMediaConstraintBridge {
    val supportsFullLayout: Boolean

    fun setVisibility(layout: Any, viewId: Int, visibility: Int)

    fun setGoneMargin(layout: Any, viewId: Int, side: Int, margin: Int)

    fun connect(layout: Any, startId: Int, startSide: Int, endId: Int, endSide: Int)

    fun setMargin(layout: Any, viewId: Int, side: Int, margin: Int)

    fun setHorizontalChainStyle(layout: Any, viewId: Int, chainStyle: Int)

    fun clear(layout: Any, viewId: Int, side: Int)

    fun constrainWidth(layout: Any, viewId: Int, width: Int)

    fun constrainHeight(layout: Any, viewId: Int, height: Int)
}

internal data class NotificationMediaLayoutResourceIds(
    val albumArt: Int,
    val albumArtImage: Int,
    val headerTitle: Int,
    val headerArtist: Int,
    val actions: Int,
    val action0: Int,
    val actionButtons: List<Int>,
    val coverSource: Int,
    val mediaSeamless: Int,
    val mediaProgressBar: Int,
    val mediaElapsedTime: Int,
    val mediaTotalTime: Int
) {
    companion object {
        fun from(context: Context): NotificationMediaLayoutResourceIds {
            val actionButtons = (0..4).map { index ->
                context.requireId("action$index")
            }
            return NotificationMediaLayoutResourceIds(
                albumArt = context.requireId("album_art"),
                albumArtImage = context.findIdOrNull("album_art_image"),
                headerTitle = context.requireId("header_title"),
                headerArtist = context.requireId("header_artist"),
                actions = context.requireId("actions"),
                action0 = actionButtons.first(),
                actionButtons = actionButtons,
                coverSource = context.requireId("icon"),
                mediaSeamless = context.requireId("media_seamless"),
                mediaProgressBar = context.findIdOrNull("media_progress_bar"),
                mediaElapsedTime = context.findIdOrNull("media_elapsed_time"),
                mediaTotalTime = context.findIdOrNull("media_total_time")
            )
        }

        @Suppress("DiscouragedApi")
        private fun Context.requireId(name: String): Int {
            val id = resources.getIdentifier(name, "id", packageName)
            require(id != 0) { "Missing SystemUI id resource: $name" }
            return id
        }

        @Suppress("DiscouragedApi")
        private fun Context.findIdOrNull(name: String): Int {
            return resources.getIdentifier(name, "id", packageName)
        }
    }
}

internal data class NotificationMediaLayoutEnvironment(
    val bridge: NotificationMediaConstraintBridge,
    val normalLayout: Any,
    val normalAlbumLayout: Any?,
    val ids: NotificationMediaLayoutResourceIds,
    val context: Context,
    val coverHidden: Boolean,
    val hideDevice: Boolean,
    val actionsOrder: Int
)

internal fun interface NotificationMediaLayoutPreset {
    fun apply(environment: NotificationMediaLayoutEnvironment)
}
