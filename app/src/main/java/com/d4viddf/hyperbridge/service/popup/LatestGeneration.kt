package com.d4viddf.hyperbridge.service.popup

import java.util.concurrent.ConcurrentHashMap

internal class LatestGeneration {
    private val values = ConcurrentHashMap<String, Long>()
    fun next(key: String): Long = values.merge(key, 1L, Long::plus) ?: 1L
    fun isLatest(key: String, generation: Long): Boolean = values[key] == generation
}
