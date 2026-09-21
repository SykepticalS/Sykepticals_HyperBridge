package com.d4viddf.hyperbridge.models

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class OwnedIslandVisualPlan(
    val marqueeEnabled: Boolean,
    val marqueeMode: MarqueeDismissMode,
    val originalTimeoutSeconds: Int,
    val islandTimeoutSeconds: Int,
    val sourceOngoing: Boolean,
    val glow: IslandGlowPresentation,
    val updatable: Boolean = false,
) {
    val islandEffect: String?
        get() = IslandVisualMetadata.EFFECT_OUTER_GLOW.takeIf { glow.islandEnabled }
    val focusEffect: String?
        get() = IslandVisualMetadata.EFFECT_OUTER_GLOW.takeIf { glow.focusEnabled }
}

object IslandVisualMetadata {
    const val EFFECT_OUTER_GLOW = "outer_glow"

    fun plan(
        config: IslandConfig,
        glow: IslandGlowPresentation,
        keepPosted: Boolean,
        marqueeCapable: Boolean,
        updatable: Boolean = false,
        forceMarquee: Boolean = false,
    ): OwnedIslandVisualPlan {
        val originalTimeout = config.timeout ?: 10
        val marqueeEnabled = config.marqueeEnabled == true || forceMarquee
        val marqueeMode = config.marqueeDismissMode ?: MarqueeDismissMode.OFF
        val overrideTimeout = marqueeCapable && marqueeEnabled && marqueeMode.overridesTimeout && !keepPosted
        return OwnedIslandVisualPlan(
            marqueeEnabled = marqueeEnabled,
            marqueeMode = marqueeMode,
            originalTimeoutSeconds = originalTimeout,
            islandTimeoutSeconds = if (overrideTimeout) Int.MAX_VALUE else originalTimeout,
            sourceOngoing = keepPosted,
            glow = glow,
            updatable = updatable,
        )
    }

    fun injectFocusGlowJson(jsonParam: String, glow: IslandGlowPresentation): String = injectGlowJson(jsonParam, glow)

    /**
     * Xiaomi starts island glow from `param_island.outEffectSrc` and focus glow from
     * `param_v2.outEffectSrc`. Extras alone are not enough on current HyperOS builds.
     */
    fun injectGlowJson(jsonParam: String, glow: IslandGlowPresentation): String {
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            val paramV2 = root.getAsJsonObject("param_v2") ?: return jsonParam
            val paramIsland = paramV2.getAsJsonObject("param_island") ?: JsonObject().also {
                paramV2.add("param_island", it)
            }
            writeEffect(paramIsland, glow.islandEnabled, glow.resolvedIslandColor())
            writeEffect(paramV2, glow.focusEnabled, glow.resolvedFocusColor())
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    /**
     * HyperIsland always writes `param_v2.updatable`. Omit-false left kit defaults on, which
     * sent text islands through Xiaomi's promoted live-update path.
     */
    fun injectUpdatable(jsonParam: String, updatable: Boolean): String {
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            val paramV2 = root.getAsJsonObject("param_v2") ?: return jsonParam
            paramV2.addProperty("updatable", updatable)
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    fun injectProgressColor(jsonParam: String, color: String?): String {
        if (color.isNullOrBlank()) return jsonParam
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            writeProgressColors(root, color)
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    private fun writeProgressColors(element: com.google.gson.JsonElement, color: String) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (obj.has("progress") && obj.get("progress").isJsonPrimitive) {
                    obj.addProperty("colorProgress", color)
                    obj.addProperty("colorProgressEnd", color)
                    if (obj.has("colorReach")) obj.addProperty("colorReach", color)
                }
                obj.entrySet().forEach { writeProgressColors(it.value, color) }
            }
            element.isJsonArray -> element.asJsonArray.forEach { writeProgressColors(it, color) }
        }
    }

    private fun writeEffect(target: JsonObject, enabled: Boolean, color: String?) {
        if (enabled) {
            target.addProperty("outEffectSrc", EFFECT_OUTER_GLOW)
            if (!color.isNullOrBlank()) target.addProperty("outEffectColor", color) else target.remove("outEffectColor")
        } else {
            target.remove("outEffectSrc")
            target.remove("outEffectColor")
        }
    }
}
