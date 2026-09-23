package com.d4viddf.hyperbridge.xposed.mediacard.compat

import android.os.Bundle
import android.app.PendingIntent

internal object IslandProbeUtils {
    fun isMediaIsland(data: Any?): Boolean = extras(data)?.let {
        it.getParcelable("miui.pending.intent", PendingIntent::class.java) != null
    } == true

    fun getCurrentIslandData(owner: Any?): Any? = owner?.let { value ->
        runCatching {
            value.javaClass.methods.firstOrNull { it.name == "getCurrentIslandData" && it.parameterCount == 0 }
                ?.invoke(value)
        }.getOrNull()
    }

    private fun extras(data: Any?): Bundle? = data?.let { value ->
        runCatching {
            value.javaClass.methods.firstOrNull { it.name == "getExtras" && it.parameterCount == 0 }
                ?.invoke(value) as? Bundle
        }.getOrNull()
    }
}

internal object IslandAlbumCoverStyleHooker {
    fun onPlaybackStateChanged(isPlaying: Boolean) = Unit
}
