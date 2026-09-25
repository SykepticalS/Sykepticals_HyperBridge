package com.d4viddf.hyperbridge.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Source call/voice posts keep the app's own shade row. Focus extras are only for the island
 * swipe-up. `isShowNotification` is forced off so HyperOS does not swap that row for a Focus
 * card. `updatable`, a stable `orderId`, and a strictly increasing `sequence` let the island
 * refresh; `cancel` asks Xiaomi to drop it. Some builds read these fields on the root object
 * and some on `param_v2`, so both are written.
 */
object FocusShadeUpdate {
    private val sequences = ConcurrentHashMap<String, AtomicLong>()

    fun orderIdFor(sourceKey: String): String = "hb-${sourceKey.hashCode().toUInt().toString(16)}"

    fun nextSequence(sourceKey: String): Long {
        val now = System.currentTimeMillis()
        val counter = sequences.computeIfAbsent(sourceKey) { AtomicLong(0) }
        while (true) {
            val current = counter.get()
            val next = max(current + 1L, now)
            if (counter.compareAndSet(current, next)) return next
        }
    }

    fun stamp(
        jsonParam: String,
        sequence: Long,
        cancel: Boolean = false,
        orderId: String? = null,
    ): String {
        if (sequence <= 0L) return jsonParam
        return runCatching {
            val root = JsonParser.parseString(jsonParam).asJsonObject
            write(root, sequence, cancel, orderId)
            val paramV2 = root.getAsJsonObject("param_v2") ?: JsonObject().also {
                root.add("param_v2", it)
            }
            write(paramV2, sequence, cancel, orderId)
            Gson().toJson(root)
        }.getOrDefault(jsonParam)
    }

    fun stampForSource(jsonParam: String, sourceKey: String, cancel: Boolean = false): String =
        stamp(jsonParam, nextSequence(sourceKey), cancel, orderIdFor(sourceKey))

    fun cancelParam(sequence: Long, orderId: String? = null): String =
        stamp("""{"param_v2":{}}""", sequence, cancel = true, orderId = orderId)

    fun cancelForSource(sourceKey: String): String =
        cancelParam(nextSequence(sourceKey), orderIdFor(sourceKey))

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

    private fun write(target: JsonObject, sequence: Long, cancel: Boolean, orderId: String?) {
        target.addProperty("updatable", true)
        target.addProperty("isShowNotification", false)
        target.addProperty("sequence", sequence)
        if (!orderId.isNullOrBlank()) target.addProperty("orderId", orderId)
        if (cancel) target.addProperty("cancel", true) else target.remove("cancel")
    }
}
