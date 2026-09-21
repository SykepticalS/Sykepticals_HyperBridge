package com.d4viddf.hyperbridge.island.backend

import android.os.Bundle
import com.d4viddf.hyperbridge.models.OwnedIslandVisualPlan

object IslandVisualExtras {
    fun apply(extras: Bundle, plan: OwnedIslandVisualPlan) {
        extras.putBoolean(IslandProtocol.EXTRA_MARQUEE_ENABLED, plan.marqueeEnabled)
        extras.putString(IslandProtocol.EXTRA_MARQUEE_MODE, plan.marqueeMode.name)
        extras.putInt(IslandProtocol.EXTRA_ORIGINAL_TIMEOUT, plan.originalTimeoutSeconds)
        extras.putBoolean(IslandProtocol.EXTRA_SOURCE_ONGOING, plan.sourceOngoing)
        extras.putString(IslandProtocol.EXTRA_GLOW_ISLAND_MODE, plan.glow.islandMode.name)
        extras.putString(IslandProtocol.EXTRA_GLOW_FOCUS_MODE, plan.glow.focusMode.name)
        writeString(extras, IslandProtocol.EXTRA_GLOW_ISLAND_COLOR, plan.glow.resolvedIslandColor())
        writeString(extras, IslandProtocol.EXTRA_GLOW_FOCUS_COLOR, plan.glow.resolvedFocusColor())
        writeString(extras, IslandProtocol.EXTRA_GLOW_DYNAMIC_COLOR, plan.glow.dynamicColor)
        extras.putBoolean(IslandProtocol.EXTRA_FORCE_ISLAND_GLOW, plan.glow.forceIsland)
        extras.putBoolean(IslandProtocol.EXTRA_FORCE_FOCUS_GLOW, plan.glow.forceFocus)
        extras.putBoolean(IslandProtocol.EXTRA_UPDATABLE, plan.updatable)
        writeString(extras, IslandProtocol.MIUI_BIG_ISLAND_EFFECT, plan.islandEffect)
        writeString(extras, IslandProtocol.MIUI_EFFECT, plan.focusEffect)
    }

    fun bridgeOwnedExtras(source: Bundle, target: Bundle) {
        for (key in OWNED_STRING_KEYS) {
            source.getString(key)?.let { target.putString(key, it) }
        }
        for (key in OWNED_BOOLEAN_KEYS) {
            if (source.containsKey(key)) target.putBoolean(key, source.getBoolean(key))
        }
        for (key in OWNED_INT_KEYS) {
            if (source.containsKey(key)) target.putInt(key, source.getInt(key))
        }
        for (key in OWNED_LONG_KEYS) {
            if (source.containsKey(key)) target.putLong(key, source.getLong(key))
        }
    }

    private fun writeString(extras: Bundle, key: String, value: String?) {
        if (value.isNullOrBlank()) extras.remove(key) else extras.putString(key, value)
    }

    private val OWNED_STRING_KEYS = arrayOf(
        IslandProtocol.EXTRA_OWNER,
        IslandProtocol.EXTRA_SOURCE_KEY,
        IslandProtocol.EXTRA_SOURCE_PACKAGE,
        IslandProtocol.EXTRA_SOURCE_CHANNEL,
        IslandProtocol.EXTRA_SEMANTIC_TYPE,
        IslandProtocol.EXTRA_MARQUEE_MODE,
        IslandProtocol.EXTRA_GLOW_ISLAND_MODE,
        IslandProtocol.EXTRA_GLOW_FOCUS_MODE,
        IslandProtocol.EXTRA_GLOW_ISLAND_COLOR,
        IslandProtocol.EXTRA_GLOW_FOCUS_COLOR,
        IslandProtocol.EXTRA_GLOW_DYNAMIC_COLOR,
        IslandProtocol.MIUI_BIG_ISLAND_EFFECT,
        IslandProtocol.MIUI_EFFECT,
    )

    private val OWNED_BOOLEAN_KEYS = arrayOf(
        IslandProtocol.EXTRA_MARQUEE_ENABLED,
        IslandProtocol.EXTRA_SOURCE_ONGOING,
        IslandProtocol.EXTRA_FORCE_ISLAND_GLOW,
        IslandProtocol.EXTRA_FORCE_FOCUS_GLOW,
        IslandProtocol.EXTRA_UPDATABLE,
        IslandProtocol.EXTRA_TEXT_UPDATE_ANIMATION,
    )

    private val OWNED_INT_KEYS = arrayOf(IslandProtocol.EXTRA_ORIGINAL_TIMEOUT)
    private val OWNED_LONG_KEYS = arrayOf(IslandProtocol.EXTRA_GENERATION)
}
