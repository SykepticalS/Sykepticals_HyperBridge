package com.sykeptical.hyperpop.service.voice

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/**
 * Rewrites the progress and clock already stored in a voice Focus payload.
 * Playback ticks use this instead of rebuilding the island.
 */
object VoiceFocusProgressPatch {
    private val clock = Regex("""\d+:\d{2}(?: / \d+:\d{2})?""")

    fun apply(focusParam: String, percent: Int, playbackClock: String): String {
        if (focusParam.isBlank()) return focusParam
        return runCatching {
            val root = JsonParser.parseString(focusParam)
            write(root, percent.coerceIn(0, 100), playbackClock)
            Gson().toJson(root)
        }.getOrDefault(focusParam)
    }

    private fun write(element: JsonElement, percent: Int, playbackClock: String) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                obj.entrySet().toList().forEach { (name, value) ->
                    when {
                        name == "progress" && value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> {
                            val current = value.asInt
                            if (current in 0..100) obj.addProperty(name, percent)
                        }
                        value.isJsonPrimitive && value.asJsonPrimitive.isString &&
                            clock.matches(value.asString) -> obj.addProperty(name, playbackClock)
                        else -> write(value, percent, playbackClock)
                    }
                }
            }
            element.isJsonArray -> element.asJsonArray.forEach { write(it, percent, playbackClock) }
        }
    }
}
