package com.sykeptical.hyperpop.island.backend

import java.security.MessageDigest

object IslandOwnership {
    fun tag(logicalToken: String): String {
        val safe = logicalToken.trim().takeIf(String::isNotEmpty) ?: "unknown"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(safe.toByteArray(Charsets.UTF_8))
            .take(10)
            .joinToString("") { "%02x".format(it) }
        return "hyperpop:$digest"
    }

    fun acceptsCancel(currentGeneration: Long, requestedGeneration: Long): Boolean =
        requestedGeneration == Long.MAX_VALUE || requestedGeneration >= currentGeneration
}
