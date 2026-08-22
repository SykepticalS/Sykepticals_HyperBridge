package com.d4viddf.hyperbridge.service

import com.google.gson.JsonElement
import com.google.gson.JsonParser

/** Removes one-shot presentation metadata before semantic content comparison. */
object RenderedJsonNormalizer {
    private val presentationOnlyKeys = setOf("islandFirstFloat", "reopen", "presentationReason")

    fun normalize(json: String?): String? {
        if (json == null) return null
        return try {
            JsonParser.parseString(json).also(::removePresentationMetadata).toString()
        } catch (_: Exception) {
            json
        }
    }

    private fun removePresentationMetadata(element: JsonElement) {
        when {
            element.isJsonObject -> {
                val iterator = element.asJsonObject.entrySet().iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key in presentationOnlyKeys) iterator.remove()
                    else removePresentationMetadata(entry.value)
                }
            }
            element.isJsonArray -> element.asJsonArray.forEach(::removePresentationMetadata)
        }
    }
}
