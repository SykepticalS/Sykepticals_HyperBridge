package com.sykeptical.hyperpop.models

import java.util.LinkedHashMap

object ExpandedProgressAnimationPolicy {
    fun shouldAnimate(
        fromProgress: Int?,
        targetProgress: Int,
        maxProgress: Int,
    ): Boolean {
        val from = fromProgress ?: return false
        return maxProgress > 0 &&
            from in 0..maxProgress &&
            targetProgress in 0..maxProgress &&
            targetProgress > from
    }

    fun drawableLevel(progress: Int, maxProgress: Int): Int {
        if (maxProgress <= 0) return 0
        return ((progress.coerceIn(0, maxProgress).toLong() * 10_000L) / maxProgress)
            .toInt()
    }
}

/**
 * Xiaomi binds the real and fake expanded cards separately for one notification update.
 * Retain the same previous value for every bind in that update instead of letting the first
 * holder consume it and forcing the second holder to snap.
 */
class ExpandedProgressHistory(private val maxEntries: Int = 64) {
    private data class Entry(
        val updateId: Long,
        val targetProgress: Int,
        val previousProgress: Int?,
    )

    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized
    fun observe(sourceKey: String, updateId: Long, targetProgress: Int): Int? {
        val current = entries[sourceKey]
        if (current?.updateId == updateId && current.targetProgress == targetProgress) {
            return current.previousProgress
        }
        val previous = current?.targetProgress
        entries[sourceKey] = Entry(updateId, targetProgress, previous)
        while (entries.size > maxEntries) {
            entries.remove(entries.entries.first().key)
        }
        return previous
    }

    @Synchronized
    fun clear() = entries.clear()
}
