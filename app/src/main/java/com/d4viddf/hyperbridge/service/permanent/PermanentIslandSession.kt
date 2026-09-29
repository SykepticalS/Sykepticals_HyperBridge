package com.d4viddf.hyperbridge.service.permanent

/**
 * Generation-guarded state for one center-slot handoff.
 *
 * This class is deliberately Android-free so late launcher callbacks and interruption races can
 * be covered by ordinary unit tests.
 */
class PermanentIslandSession {
    sealed interface State {
        data object Blank : State
        data class Closing(
            val packageName: String,
            val sourceKey: String,
            val generation: Long,
            val started: Boolean,
        ) : State
        data class Showing(
            val packageName: String,
            val sourceKey: String,
            val generation: Long,
        ) : State
    }

    private var nextGeneration = 0L
    var state: State = State.Blank
        private set

    @Synchronized
    fun requestClose(packageName: String, sourceKey: String): Long {
        val generation = ++nextGeneration
        state = State.Closing(packageName, sourceKey, generation, started = false)
        return generation
    }

    @Synchronized
    fun markStarted(packageName: String): Long? {
        val current = state as? State.Closing ?: return null
        if (current.packageName != packageName || current.started) return null
        state = current.copy(started = true)
        return current.generation
    }

    @Synchronized
    fun complete(packageName: String): State.Showing? {
        val current = state as? State.Closing ?: return null
        if (current.packageName != packageName || !current.started) return null
        return State.Showing(
            packageName = current.packageName,
            sourceKey = current.sourceKey,
            generation = current.generation,
        ).also { state = it }
    }

    @Synchronized
    fun abort(packageName: String? = null): Boolean {
        val current = state as? State.Closing ?: return false
        if (packageName != null && current.packageName != packageName) return false
        state = State.Blank
        return true
    }

    @Synchronized
    fun clearIfForeground(packageName: String?): Boolean {
        if (packageName == null) return false
        val matches = when (val current = state) {
            is State.Closing -> current.packageName == packageName
            is State.Showing -> current.packageName == packageName
            State.Blank -> false
        }
        if (!matches) return false
        state = State.Blank
        return true
    }

    @Synchronized
    fun clearIfSource(sourceKey: String?): Boolean {
        if (sourceKey == null) return false
        val matches = when (val current = state) {
            is State.Closing -> current.sourceKey == sourceKey
            is State.Showing -> current.sourceKey == sourceKey
            State.Blank -> false
        }
        if (!matches) return false
        state = State.Blank
        return true
    }

    @Synchronized
    fun reset(): Boolean {
        val changed = state !is State.Blank
        state = State.Blank
        return changed
    }

    @Synchronized
    fun activeSourceKey(): String? = when (val current = state) {
        is State.Closing -> current.sourceKey
        is State.Showing -> current.sourceKey
        State.Blank -> null
    }

    @Synchronized
    fun activePackageName(): String? = when (val current = state) {
        is State.Closing -> current.packageName
        is State.Showing -> current.packageName
        State.Blank -> null
    }

    @Synchronized
    fun hasActiveSource(): Boolean = state !is State.Blank
}
