package com.sykeptical.hyperpop.xposed.mediacard.compat

import android.app.Notification
import android.app.PendingIntent
import android.os.Bundle
import com.sykeptical.hyperpop.xposed.hooks.IslandOwnedNotification
import org.json.JSONObject
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

internal data class CompactMediaText(
    val title: String,
    val artist: String,
    val identity: String,
) {
    companion object {
        fun of(title: String, artist: String): CompactMediaText? {
            val cleanArtist = artist.trim()
            val cleanTitle = title.trim().ifBlank { cleanArtist }
            if (cleanTitle.isBlank()) return null
            return CompactMediaText(cleanTitle, cleanArtist, "$cleanTitle\u0000$cleanArtist")
        }
    }
}

internal object IslandProbeUtils {
    private const val PENDING_INTENT = "miui.pending.intent"
    private const val PACKAGE_NAME = "miui.pkg.name"

    private val islandDataGetters = ConcurrentHashMap<Class<*>, Optional<Method>>()
    @Volatile private var lastTemplateRaw: String? = null
    @Volatile private var lastTemplateText: Pair<String, String> = "" to ""

    fun isMediaIsland(data: Any?): Boolean = extras(data)?.let { hasPendingIntent(it) } == true

    /** App that owns the native media island. */
    fun packageName(data: Any?): String? {
        val bundle = extras(data) ?: return null
        bundle.getString(PACKAGE_NAME)?.takeIf { it.isNotBlank() }?.let { return it }
        IslandOwnedNotification.sbnFrom(bundle)?.packageName?.let { return it }
        return pendingIntent(bundle)?.creatorPackage
    }

    /** Title and artist carried by the island itself: the notification, then the template. */
    fun readCompactMediaText(data: Any?): CompactMediaText? {
        val bundle = extras(data) ?: return null
        if (!hasPendingIntent(bundle)) return null
        val notificationExtras = IslandOwnedNotification.sbnFrom(bundle)?.notification?.extras
        var title = notificationExtras.charSequence(Notification.EXTRA_TITLE)
        var artist = notificationExtras.charSequence(Notification.EXTRA_TEXT)
        if (title.isBlank() || artist.isBlank()) {
            val fromTemplate = templateText(bundle)
            if (title.isBlank()) title = fromTemplate.first
            if (artist.isBlank()) artist = fromTemplate.second
        }
        return CompactMediaText.of(title, artist)
    }

    private fun Bundle?.charSequence(key: String): String =
        this?.getCharSequence(key)?.toString()?.trim().orEmpty()

    fun getCurrentIslandData(owner: Any?): Any? {
        val value = owner ?: return null
        val method = islandDataGetters.getOrPut(value.javaClass) {
            Optional.ofNullable(
                value.javaClass.methods.firstOrNull { it.name == "getCurrentIslandData" && it.parameterCount == 0 },
            )
        }.orElse(null) ?: return null
        return runCatching { method.invoke(value) }.getOrNull()
    }

    private fun extras(data: Any?): Bundle? = IslandOwnedNotification.extrasFrom(data)

    private fun hasPendingIntent(bundle: Bundle): Boolean = pendingIntent(bundle) != null

    @Suppress("DEPRECATION")
    private fun pendingIntent(bundle: Bundle): PendingIntent? =
        runCatching { bundle.getParcelable(PENDING_INTENT, PendingIntent::class.java) }.getOrNull()
            ?: runCatching { bundle.getParcelable(PENDING_INTENT) as? PendingIntent }.getOrNull()

    private fun templateText(bundle: Bundle): Pair<String, String> {
        val raw = bundle.getString("miui.focus.param") ?: return "" to ""
        if (raw == lastTemplateRaw) return lastTemplateText
        val textInfo = runCatching {
            val json = JSONObject(raw)
            val island = json.optJSONObject("param_v2")?.optJSONObject("param_island")
                ?: json.optJSONObject("param_island")
            island?.optJSONObject("bigIslandArea")
                ?.optJSONObject("imageTextInfoLeft")
                ?.optJSONObject("textInfo")
        }.getOrNull()
        val parsed = if (textInfo == null) {
            "" to ""
        } else {
            textInfo.optString("title").trim() to textInfo.optString("content").trim()
        }
        lastTemplateText = parsed
        lastTemplateRaw = raw
        return parsed
    }
}

internal object IslandAlbumCoverStyleHooker {
    fun onPlaybackStateChanged(isPlaying: Boolean) = Unit
}
