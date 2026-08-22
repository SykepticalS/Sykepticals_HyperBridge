package com.d4viddf.hyperbridge.service.call

import java.util.concurrent.atomic.AtomicLong

enum class ConnectedAtSource {
    SOURCE_CHRONOMETER,
    EXPLICIT_ACTIVE_TRANSITION,
    LOCAL_STATE_TRANSITION
}

data class CallSessionInput(
    val sourceKey: String,
    val packageName: String,
    val notificationId: Int,
    val notificationTag: String?,
    val groupKey: String?,
    val participantId: String?,
    val classification: CallClassification,
    val showsChronometer: Boolean,
    val chronometerBase: Long,
    val observedAt: Long
)

data class CallSession(
    val logicalCallId: String,
    val packageName: String,
    val sourceKey: String,
    val stableNotificationId: String,
    val groupKey: String?,
    val participantId: String?,
    val state: CallState,
    val connectedAt: Long?,
    val connectedAtSource: ConnectedAtSource?,
    val lastSeen: Long,
    val showsChronometer: Boolean,
    val lastChronometerBase: Long?,
    val replacementDeadline: Long? = null
)

/** Keeps only small, derived call state; source keys are aliases, not permanent call identity. */
class CallSessionTracker(
    private val replacementGraceMs: Long = 1_000L,
    private val staleSessionMs: Long = 12 * 60 * 60 * 1_000L
) {
    private val sequence = AtomicLong()
    private val sessions = LinkedHashMap<String, CallSession>()
    private val sourceIndex = HashMap<String, String>()

    @Synchronized
    fun resolve(input: CallSessionInput): CallSession {
        prune(input.observedAt)
        val previous = findCandidate(input)
        val resolvedState = resolveState(previous, input)
        val logicalId = previous?.logicalCallId
            ?: "call:${input.packageName}:${sequence.incrementAndGet()}"

        if (previous != null && previous.sourceKey != input.sourceKey) {
            sourceIndex.remove(previous.sourceKey)
        }

        val session = CallSession(
            logicalCallId = logicalId,
            packageName = input.packageName,
            sourceKey = input.sourceKey,
            stableNotificationId = stableNotificationId(input),
            groupKey = input.groupKey,
            participantId = input.participantId,
            state = resolvedState.state,
            connectedAt = resolvedState.connectedAt,
            connectedAtSource = resolvedState.connectedAtSource,
            lastSeen = input.observedAt,
            showsChronometer = input.showsChronometer,
            lastChronometerBase = input.chronometerBase.takeIf { isPlausibleBase(it, input.observedAt) },
            replacementDeadline = null
        )
        sessions[logicalId] = session
        sourceIndex[input.sourceKey] = logicalId
        return session
    }

    @Synchronized
    fun markSourceRemoved(sourceKey: String, now: Long): String? {
        val logicalId = sourceIndex[sourceKey] ?: return null
        val current = sessions[logicalId] ?: return null
        sessions[logicalId] = current.copy(replacementDeadline = now + replacementGraceMs)
        return logicalId
    }

    @Synchronized
    fun logicalIdForSource(sourceKey: String): String? = sourceIndex[sourceKey]

    @Synchronized
    fun end(logicalCallId: String) {
        val removed = sessions.remove(logicalCallId) ?: return
        sourceIndex.entries.removeAll { it.value == logicalCallId || it.key == removed.sourceKey }
    }

    @Synchronized
    fun clear() {
        sessions.clear()
        sourceIndex.clear()
    }

    @Synchronized
    fun size(): Int = sessions.size

    @Synchronized
    fun pruneStale(now: Long) {
        prune(now)
    }

    private fun findCandidate(input: CallSessionInput): CallSession? {
        sourceIndex[input.sourceKey]
            ?.let(sessions::get)
            ?.takeIf { participantsCompatible(it.participantId, input.participantId) }
            ?.let { return it }

        val stableId = stableNotificationId(input)
        sessions.values.firstOrNull {
            it.packageName == input.packageName &&
                    it.stableNotificationId == stableId &&
                    participantsCompatible(it.participantId, input.participantId)
        }?.let { return it }

        val replacementCandidates = sessions.values.filter {
            it.packageName == input.packageName &&
                    it.replacementDeadline?.let { deadline -> input.observedAt <= deadline } == true &&
                    participantsCompatible(it.participantId, input.participantId)
        }
        val strongMatches = replacementCandidates.filter {
            sameNonBlank(it.groupKey, input.groupKey) ||
                    sameNonBlank(it.participantId, input.participantId)
        }
        return strongMatches.singleOrNull() ?: replacementCandidates.singleOrNull()
    }

    private fun resolveState(previous: CallSession?, input: CallSessionInput): ResolvedState {
        val classification = input.classification
        if (!classification.isCall) {
            return ResolvedState(CallState.ENDED, previous?.connectedAt, previous?.connectedAtSource)
        }

        if (previous?.state == CallState.ACTIVE) {
            return ResolvedState(CallState.ACTIVE, previous.connectedAt, previous.connectedAtSource)
        }

        val explicitActive = classification.activeEvidence == CallActiveEvidence.EXPLICIT_ONGOING_TYPE
        val plausibleBase = input.chronometerBase.takeIf {
            input.showsChronometer && isPlausibleBase(it, input.observedAt)
        }
        val transitionedBase = plausibleBase.takeIf {
            previous == null || !previous.showsChronometer || previous.lastChronometerBase != it
        }
        val chronometerTransition = classification.activeEvidence == CallActiveEvidence.CHRONOMETER &&
                previous != null &&
                (!previous.showsChronometer || previous.lastChronometerBase != plausibleBase)
        val answeredIncoming = previous?.state == CallState.INCOMING_RINGING &&
                !classification.hasAnswer && classification.hasDeclineOrHangUp

        if (explicitActive || chronometerTransition || answeredIncoming) {
            val connectedAt = when {
                previous == null && explicitActive -> plausibleBase ?: input.observedAt
                chronometerTransition -> transitionedBase ?: input.observedAt
                answeredIncoming -> transitionedBase ?: input.observedAt
                else -> input.observedAt
            }
            val source = when {
                connectedAt == transitionedBase || (previous == null && connectedAt == plausibleBase) -> ConnectedAtSource.SOURCE_CHRONOMETER
                explicitActive -> ConnectedAtSource.EXPLICIT_ACTIVE_TRANSITION
                else -> ConnectedAtSource.LOCAL_STATE_TRANSITION
            }
            return ResolvedState(CallState.ACTIVE, connectedAt, source)
        }

        if (previous == null &&
            classification.state == CallState.ACTIVE &&
            classification.activeEvidence == CallActiveEvidence.CHRONOMETER
        ) {
            // Some VoIP apps expose a chronometer from call initiation. A first observation with
            // only Hang Up is not enough evidence that the remote party answered.
            return ResolvedState(CallState.OUTGOING_CALLING, null, null)
        }

        return ResolvedState(classification.state, null, null)
    }

    private fun stableNotificationId(input: CallSessionInput): String {
        return input.notificationTag?.takeIf { it.isNotBlank() }?.let { "tag:$it" }
            ?: "id:${input.notificationId}"
    }

    private fun participantsCompatible(first: String?, second: String?): Boolean {
        return first == null || second == null || first == second
    }

    private fun sameNonBlank(first: String?, second: String?): Boolean {
        return !first.isNullOrBlank() && !second.isNullOrBlank() && first == second
    }

    private fun isPlausibleBase(base: Long, now: Long): Boolean {
        return base in (now - staleSessionMs)..(now + 5_000L)
    }

    private fun prune(now: Long) {
        val staleIds = sessions.values
            .filter { now - it.lastSeen > staleSessionMs }
            .map { it.logicalCallId }
        staleIds.forEach(::end)
    }

    private data class ResolvedState(
        val state: CallState,
        val connectedAt: Long?,
        val connectedAtSource: ConnectedAtSource?
    )
}
