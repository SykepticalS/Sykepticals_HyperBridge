package com.d4viddf.hyperbridge.service.animation

/** Guards the request/start/end callback sequence for one centered app-exit animation. */
class CenteredExitSession {
    private data class Pending(
        val packageName: String,
        val generation: Long,
        val started: Boolean,
    )

    private var nextGeneration = 0L
    private var pending: Pending? = null

    @Synchronized
    fun arm(packageName: String): Long {
        val generation = ++nextGeneration
        pending = Pending(packageName, generation, started = false)
        return generation
    }

    @Synchronized
    fun markStarted(packageName: String?): Long? {
        val current = pending ?: return null
        if (packageName != current.packageName || current.started) return null
        pending = current.copy(started = true)
        return current.generation
    }

    @Synchronized
    fun complete(packageName: String?): Long? {
        val current = pending ?: return null
        if (packageName != current.packageName || !current.started) return null
        pending = null
        return current.generation
    }

    @Synchronized
    fun abort(packageName: String? = null): Boolean {
        val current = pending ?: return false
        if (packageName != null && packageName != current.packageName) return false
        pending = null
        return true
    }
}
