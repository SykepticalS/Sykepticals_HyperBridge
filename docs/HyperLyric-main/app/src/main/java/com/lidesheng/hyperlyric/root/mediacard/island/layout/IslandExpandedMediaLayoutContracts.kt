package com.lidesheng.hyperlyric.root.mediacard.island.layout

import android.content.Context
import kotlin.math.roundToInt

internal object IslandExpandedMediaConstraintSide {
    const val LEFT = 1
    const val RIGHT = 2
    const val TOP = 3
    const val BOTTOM = 4
    const val START = 6
    const val END = 7
}

internal interface IslandExpandedMediaConstraintBridge {
    val supportsDeviceSlotReplacement: Boolean

    val supportsDimensionTuning: Boolean

    fun connect(layout: Any, startId: Int, startSide: Int, endId: Int, endSide: Int)

    fun setMargin(layout: Any, viewId: Int, side: Int, margin: Int)

    fun setGoneMargin(layout: Any, viewId: Int, side: Int, margin: Int)

    fun clear(layout: Any, viewId: Int, side: Int)

    fun setVisibility(layout: Any, viewId: Int, visibility: Int)

    fun setScale(layout: Any, viewId: Int, scale: Float)

    fun constrainWidth(layout: Any, viewId: Int, width: Int)

    fun constrainHeight(layout: Any, viewId: Int, height: Int)
}

internal data class IslandExpandedMediaLayoutResourceIds(
    val albumArt: Int,
    val albumArtImage: Int,
    val headerTitle: Int,
    val headerArtist: Int,
    val actions: Int,
    val action4: Int,
    val action0: Int,
    val actionButtons: List<Int>,
    val mediaSeamless: Int,
    val mediaProgressBar: Int,
    val mediaElapsedTime: Int,
    val mediaTotalTime: Int
) {
    companion object {
        fun from(context: Context): IslandExpandedMediaLayoutResourceIds {
            return IslandExpandedMediaLayoutResourceIds(
                albumArt = context.requireSystemUiId("album_art"),
                albumArtImage = context.findSystemUiId("album_art_image"),
                headerTitle = context.requireSystemUiId("header_title"),
                headerArtist = context.requireSystemUiId("header_artist"),
                actions = context.requireSystemUiId("actions"),
                action4 = context.requireSystemUiId("action4"),
                action0 = context.requireSystemUiId("action0"),
                actionButtons = (0..4).map { index ->
                    context.requireSystemUiId("action$index")
                },
                mediaSeamless = context.requireSystemUiId("media_seamless"),
                mediaProgressBar = context.requireSystemUiId("media_progress_bar"),
                mediaElapsedTime = context.requireSystemUiId("media_elapsed_time"),
                mediaTotalTime = context.requireSystemUiId("media_total_time")
            )
        }

        @Suppress("DiscouragedApi")
        private fun Context.requireSystemUiId(name: String): Int {
            val id = resources.getIdentifier(name, "id", packageName)
            require(id != 0) { "Missing SystemUI id resource: $name" }
            return id
        }

        @Suppress("DiscouragedApi")
        private fun Context.findSystemUiId(name: String): Int {
            return resources.getIdentifier(name, "id", packageName)
        }
    }
}

internal data class IslandExpandedMediaLayoutEnvironment(
    val bridge: IslandExpandedMediaConstraintBridge,
    val layout: Any,
    val ids: IslandExpandedMediaLayoutResourceIds,
    val context: Context,
    val coverHidden: Boolean,
    val hideDeviceSwitch: Boolean
)

internal fun IslandExpandedMediaConstraintBridge.clearVertical(layout: Any, viewId: Int) {
    clear(layout, viewId, IslandExpandedMediaConstraintSide.TOP)
    clear(layout, viewId, IslandExpandedMediaConstraintSide.BOTTOM)
}

internal fun IslandExpandedMediaConstraintBridge.clearHorizontal(layout: Any, viewId: Int) {
    clear(layout, viewId, IslandExpandedMediaConstraintSide.LEFT)
    clear(layout, viewId, IslandExpandedMediaConstraintSide.RIGHT)
    clear(layout, viewId, IslandExpandedMediaConstraintSide.START)
    clear(layout, viewId, IslandExpandedMediaConstraintSide.END)
}

internal fun IslandExpandedMediaConstraintBridge.clearAll(layout: Any, viewId: Int) {
    clearHorizontal(layout, viewId)
    clearVertical(layout, viewId)
}

internal fun Context.islandExpandedMediaDp(value: Float): Int {
    return (value * resources.displayMetrics.density).roundToInt()
}

@Suppress("DiscouragedApi")
internal fun Context.islandExpandedMediaDimenPx(name: String, fallbackDp: Float): Int {
    val id = resources.getIdentifier(name, "dimen", packageName)
    return if (id != 0) resources.getDimensionPixelSize(id) else {
        islandExpandedMediaDp(fallbackDp)
    }
}
