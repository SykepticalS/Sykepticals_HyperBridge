package com.sykeptical.hyperpop.service

import java.util.concurrent.ConcurrentHashMap

/** Tracks the source notification whose inline-reply composer currently owns its timeout. */
internal class ReplyComposerHoldRegistry {
    private val sourceKeys = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var legacyGlobalHold = false

    fun update(open: Boolean, sourceKey: String?) {
        val key = sourceKey?.takeIf { it.isNotBlank() }
        if (key == null) {
            legacyGlobalHold = open
        } else if (open) {
            sourceKeys += key
        } else {
            sourceKeys -= key
        }
    }

    fun holds(sourceKey: String): Boolean = legacyGlobalHold || sourceKey in sourceKeys

    fun clear() {
        legacyGlobalHold = false
        sourceKeys.clear()
    }
}
