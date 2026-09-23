package com.d4viddf.hyperbridge.xposed.hooks

import android.graphics.Color
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.island.backend.IslandVisualExtras
import com.d4viddf.hyperbridge.models.GlowMode
import com.d4viddf.hyperbridge.models.IslandGlowResolver
import com.d4viddf.hyperbridge.models.IslandVisualMetadata
import org.json.JSONObject

internal data class OwnedIslandSnapshot(
    val extras: Bundle,
    val sbn: StatusBarNotification?,
) {
    val owner: String? get() = extras.getString(IslandProtocol.EXTRA_OWNER)
        ?: sbn?.notification?.extras?.getString(IslandProtocol.EXTRA_OWNER)
    val owned: Boolean get() = owner == IslandProtocol.OWNER
    val islandKey: String?
        get() = extras.getString(IslandProtocol.EXTRA_SOURCE_KEY)?.takeIf { it.isNotBlank() }
            ?: sbn?.key?.takeIf { it.isNotBlank() }
    val generation: Long
        get() = extras.getLong(IslandProtocol.EXTRA_GENERATION, Long.MIN_VALUE).takeUnless { it == Long.MIN_VALUE }
            ?: sbn?.notification?.extras?.getLong(IslandProtocol.EXTRA_GENERATION, Long.MIN_VALUE)
            ?: Long.MIN_VALUE
    val marqueeEnabled: Boolean get() = extras.getBoolean(IslandProtocol.EXTRA_MARQUEE_ENABLED, false)
    val islandGlowMode: GlowMode get() = GlowMode.parse(extras.getString(IslandProtocol.EXTRA_GLOW_ISLAND_MODE)) ?: GlowMode.OFF
    val focusGlowMode: GlowMode get() = GlowMode.parse(extras.getString(IslandProtocol.EXTRA_GLOW_FOCUS_MODE)) ?: GlowMode.OFF
    val forceIsland: Boolean get() = extras.getBoolean(IslandProtocol.EXTRA_FORCE_ISLAND_GLOW, false)
    val forceFocus: Boolean get() = extras.getBoolean(IslandProtocol.EXTRA_FORCE_FOCUS_GLOW, false)
    val islandEffect: Boolean
        get() = extras.getString(IslandProtocol.MIUI_BIG_ISLAND_EFFECT) == IslandProtocol.EFFECT_OUTER_GLOW ||
            jsonHasEffect(island = true)
    val focusEffect: Boolean
        get() = extras.getString(IslandProtocol.MIUI_EFFECT) == IslandProtocol.EFFECT_OUTER_GLOW ||
            jsonHasEffect(island = false)
    val islandGlowEnabled: Boolean
        get() = IslandGlowResolver.runtimeEnabled(
            extras.getString(IslandProtocol.EXTRA_GLOW_ISLAND_MODE),
            islandEffect,
            forceIsland,
        )
    val focusGlowEnabled: Boolean
        get() = IslandGlowResolver.runtimeEnabled(
            extras.getString(IslandProtocol.EXTRA_GLOW_FOCUS_MODE),
            focusEffect,
            forceFocus,
        )

    fun islandColorArgb(): Int? = resolveColor(islandGlowMode, IslandProtocol.EXTRA_GLOW_ISLAND_COLOR)
        ?: resolveColor(focusGlowMode, IslandProtocol.EXTRA_GLOW_FOCUS_COLOR)
    fun focusColorArgb(): Int? = resolveColor(focusGlowMode, IslandProtocol.EXTRA_GLOW_FOCUS_COLOR)
        ?: resolveColor(islandGlowMode, IslandProtocol.EXTRA_GLOW_ISLAND_COLOR)

    private fun resolveColor(mode: GlowMode, manualKey: String): Int? {
        val dynamic = IslandGlowResolver.normalizeColor(extras.getString(IslandProtocol.EXTRA_GLOW_DYNAMIC_COLOR))
        val manual = IslandGlowResolver.normalizeColor(extras.getString(manualKey))
        val value = when (mode) {
            GlowMode.FOLLOW_DYNAMIC -> dynamic ?: manual
            GlowMode.ON -> manual ?: dynamic
            GlowMode.OFF -> null
        }
        return value?.let { runCatching { Color.parseColor(it) }.getOrNull() }
    }

    private fun jsonHasEffect(island: Boolean): Boolean {
        val json = extras.getString("miui.focus.param")
            ?: sbn?.notification?.extras?.getString("miui.focus.param")
            ?: return false
        return runCatching {
            val paramV2 = JSONObject(json).optJSONObject("param_v2") ?: return false
            val source = if (island) paramV2.optJSONObject("param_island") ?: return false else paramV2
            source.optString("outEffectSrc") == IslandVisualMetadata.EFFECT_OUTER_GLOW
        }.getOrDefault(false)
    }
}

internal object IslandOwnedNotification {
    fun fromIslandData(data: Any?): OwnedIslandSnapshot? {
        val islandExtras = extrasFrom(data) ?: Bundle()
        val sbn = sbnFromData(data, islandExtras)
        val merged = Bundle()
        sbn?.notification?.extras?.let { merged.putAll(it) }
        merged.putAll(islandExtras)
        sbn?.notification?.extras?.let { IslandVisualExtras.bridgeOwnedExtras(it, merged) }
        if (merged.getString(IslandProtocol.EXTRA_OWNER).isNullOrBlank() && sbn == null) return null
        return OwnedIslandSnapshot(merged, sbn)
    }

    fun fromAnimationState(state: Any?): OwnedIslandSnapshot? {
        val data = listOf("getCurrentIslandData", "getIslandData", "getData", "getCurrentData")
            .firstNotNullOfOrNull { IslandHookReflection.invokeNoArg(state, it) }
        return fromIslandData(data)
    }

    fun extrasFrom(data: Any?): Bundle? {
        if (data == null) return null
        IslandHookReflection.invokeNoArg(data, "getExtras")?.let { if (it is Bundle) return it }
        IslandHookReflection.readField(data, "extras")?.let { if (it is Bundle) return it }
        IslandHookReflection.readField(data, "mExtras")?.let { if (it is Bundle) return it }
        IslandHookReflection.invokeNoArg(data, "getBundle")?.let { if (it is Bundle) return it }
        return null
    }

    @Suppress("DEPRECATION")
    fun sbnFrom(extras: Bundle?): StatusBarNotification? {
        if (extras == null) return null
        return runCatching {
            extras.getParcelable(IslandProtocol.MIUI_SBN, StatusBarNotification::class.java)
        }.getOrNull() ?: runCatching {
            extras.getParcelable(IslandProtocol.MIUI_SBN) as? StatusBarNotification
        }.getOrNull()
    }

    fun fromSbn(sbn: StatusBarNotification?): OwnedIslandSnapshot? {
        val extras = sbn?.notification?.extras ?: return null
        return OwnedIslandSnapshot(extras, sbn)
    }

    private fun sbnFromData(data: Any?, extras: Bundle?): StatusBarNotification? {
        sbnFrom(extras)?.let { return it }
        if (data == null) return null
        listOf("getSbn", "getStatusBarNotification", "getNotification").forEach { name ->
            when (val value = IslandHookReflection.invokeNoArg(data, name)) {
                is StatusBarNotification -> return value
            }
        }
        listOf("sbn", "mSbn", "mStatusBarNotification").forEach { name ->
            when (val value = IslandHookReflection.readField(data, name)) {
                is StatusBarNotification -> return value
            }
        }
        return null
    }
}
