package com.d4viddf.hyperbridge.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * HyperOS owns the shade row once `miui.focus.param` is present. `FocusTemplate` keeps the
 * first snapshot unless the payload is updatable and `sequence` is strictly newer than the
 * last one it accepted. A missing sequence is 0, so every later call or voice update compares
 * equal and the shade row never refreshes or clears. Xiaomi removes that row when a newer
 * payload sets `cancel` to true.
 *
 * Some builds read these fields from the root object and some from `param_v2`, so both are written.
 */
object FocusShadeUpdate {
    private val sequences = ConcurrentHashMap<String, AtomicLong>()

    fun nextSequence(sourceKey: String): Long {
        // Wall time survives a process restart, so a new process cannot reuse a sequence
        // HyperOS has already accepted. The counter breaks ties inside the same millisecond.
        val now = System.currentTimeMillis()
        val counter = sequences.computeIfAbsent(sourceKey) { AtomicLong(0) }
        while (true) {
            val current = counter.get()
            val next = max(current + 1L, now)
            if (counter.compareAndSet(current, next)) return next
        }
    }

    fun stamp(jsonParam: String, sequence: Long, cancel: Boolean = false): String {
        if (sequence <= 0L) return jsonParam
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            write(root, sequence, cancel)
            val paramV2 = root.getAsJsonObject("param_v2") ?: JsonObject().also {
                root.add("param_v2", it)
            }
            write(paramV2, sequence, cancel)
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    fun cancelParam(sequence: Long): String = stamp("""{"param_v2":{}}""", sequence, cancel = true)

    fun cancels(jsonParam: String?): Boolean {
        if (jsonParam.isNullOrBlank()) return false
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            cancels(root) || root.getAsJsonObject("param_v2")?.let(::cancels) == true
        }.getOrDefault(false)
    }

    private fun cancels(target: JsonObject): Boolean {
        return target.get("cancel")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true
    }

    private fun write(target: JsonObject, sequence: Long, cancel: Boolean) {
        target.addProperty("updatable", true)
        target.addProperty("sequence", sequence)
        if (cancel) target.addProperty("cancel", true) else target.remove("cancel")
    }
}
