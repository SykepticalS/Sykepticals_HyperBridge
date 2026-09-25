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
    ): OwnedIslandVisualPlan {
        val originalTimeout = config.timeout ?: 10
        val marqueeEnabled = config.marqueeEnabled == true
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
            val json = fixTextButtonJson(jsonParam)
            val root = JsonParser.parseString(json).asJsonObject
            val paramV2 = root.getAsJsonObject("param_v2") ?: return json
            paramV2.addProperty("updatable", updatable)
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    /**
     * HyperOS V3 text buttons read `action` (extras key). The kit still emits
     * `actionIntent` + `actionIntentType`, which makes Reply taps no-ops.
     */
    fun fixTextButtonJson(jsonParam: String): String {
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            val paramV2 = root.getAsJsonObject("param_v2") ?: return jsonParam
            val buttons = paramV2.getAsJsonArray("textButton") ?: return jsonParam
            for (index in 0 until buttons.size()) {
                val button = buttons[index].asJsonObject
                val key = button.get("actionIntent")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.takeIf { it.isNotEmpty() } ?: continue
                if (!button.has("action") || button.get("action").asString.isNullOrEmpty()) {
                    button.addProperty("action", key)
                }
                button.remove("actionIntent")
                button.remove("actionIntentType")
            }
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    /**
     * Enables HyperOS' built-in TimerTextEffectView transition for every island TextInfo.
     *
     * Current Xiaomi builds deserialize the undocumented `turnAnim` member on compact
     * `textInfo` objects. Their own holder then installs TextChangeHelper and performs the
     * upward fade/translation transition when that text changes in place.
     */
    fun injectTextUpdateAnimation(
        jsonParam: String,
        enabled: Boolean = true,
        skipSides: Set<String> = emptySet(),
    ): String {
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            val paramV2 = root.getAsJsonObject("param_v2") ?: return jsonParam
            val paramIsland = paramV2.getAsJsonObject("param_island") ?: return jsonParam
            writeTextUpdateAnimation(paramIsland, enabled, skipSides)
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    /**
     * Returns whether the compact island payload owns text. Scrolling still follows the
     * user's marquee setting; the runtime hook measures the real Xiaomi view before moving text.
     */
    fun hasCompactText(jsonParam: String): Boolean {
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            val area = root.getAsJsonObject("param_v2")
                ?.getAsJsonObject("param_island")
                ?.getAsJsonObject("bigIslandArea")
                ?: return false
            listOf("imageTextInfoLeft", "imageTextInfoRight").any { sideName ->
                val textInfo = area.getAsJsonObject(sideName)?.getAsJsonObject("textInfo")
                    ?: return@any false
                listOf("title", "content", "frontTitle").any { field ->
                    textInfo.get(field)?.takeIf { it.isJsonPrimitive }?.asString?.isNotBlank() == true
                }
            }
        }.getOrDefault(false)
    }

    /**
     * HyperOS reads these from `param_v2`. Kit defaults and omitted-false values otherwise
     * re-expand an already visible island on the next notify().
     */
    fun injectFloatingFlags(
        jsonParam: String,
        enableFloat: Boolean,
        islandFirstFloat: Boolean = enableFloat,
        reopen: Boolean = enableFloat,
        expandedTimeMs: Int? = null,
    ): String {
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            val paramV2 = root.getAsJsonObject("param_v2") ?: return jsonParam
            paramV2.addProperty("enableFloat", enableFloat)
            paramV2.addProperty("islandFirstFloat", islandFirstFloat)
            paramV2.addProperty("reopen", reopen)
            if (expandedTimeMs != null) {
                paramV2.addProperty("expandedTime", expandedTimeMs)
            }
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    /**
     * Count-up islands keep a picture-only left slot. Put the caller name back on that side
     * without replacing the same-width timer on the right.
     */
    fun injectCompactLeftTitle(jsonParam: String, title: String): String {
        val compact = title.trim()
        if (compact.isEmpty()) return jsonParam
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            val left = root.getAsJsonObject("param_v2")
                ?.getAsJsonObject("param_island")
                ?.getAsJsonObject("bigIslandArea")
                ?.getAsJsonObject("imageTextInfoLeft")
                ?: return jsonParam
            val textInfo = left.getAsJsonObject("textInfo") ?: JsonObject().also {
                left.add("textInfo", it)
            }
            textInfo.addProperty("title", compact)
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

    private fun writeTextUpdateAnimation(
        element: com.google.gson.JsonElement,
        enabled: Boolean,
        skipSides: Set<String> = emptySet(),
        skipping: Boolean = false,
    ) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.entrySet().toList().forEach { (name, value) ->
                    val childSkipping = skipping || name in skipSides
                    if (name == "textInfo" && value.isJsonObject) {
                        value.asJsonObject.addProperty("turnAnim", enabled && !childSkipping)
                    }
                    writeTextUpdateAnimation(value, enabled, skipSides, childSkipping)
                }
            }
            element.isJsonArray -> element.asJsonArray.forEach {
                writeTextUpdateAnimation(it, enabled, skipSides, skipping)
            }
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
