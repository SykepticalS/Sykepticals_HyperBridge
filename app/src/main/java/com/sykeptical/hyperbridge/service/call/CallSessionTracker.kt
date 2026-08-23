package com.sykeptical.hyperbridge.service.call

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

enum class ConnectedAtSource {
    SOURCE_CHRONOMETER,
    OBSERVED_CONNECTION_TRANSITION
}

object CallReplacementPolicy {
    /** New source keys remain eligible to rebind to an awaiting logical call for this long. */
    const val MATCH_GRACE_MS = 2_500L

    /** Removal commits only after the replacement matching window has safely closed. */
    const val REMOVAL_DELAY_MS = 2_750L
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
    val hasConnectedControl: Boolean = false,
    val replacementDeadline: Long? = null,
    val previousState: CallState? = null,
    val candidateState: CallState = state,
    val activeEvidence: CallActiveEvidence = CallActiveEvidence.NONE,
    val sourceReplacement: Boolean = false
)

/** The translator must never create a timer for a pre-connected call state. */
object CallTimerPolicy {
    fun connectedAtForTimer(session: CallSession): Long? {
        check((session.state == CallState.ACTIVE) == (session.connectedAt != null)) {
            "ACTIVE and connectedAt must be present together"
        }
        return session.connectedAt.takeIf { session.state == CallState.ACTIVE }
    }
}

/** Keeps only small, derived call state; source keys are aliases, not permanent call identity. */
class CallSessionTracker(
    private val replacementGraceMs: Long = CallReplacementPolicy.MATCH_GRACE_MS,
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
            hasConnectedControl = input.classification.hasConnectedControl,
            replacementDeadline = null,
            previousState = previous?.state,
            candidateState = input.classification.state,
            activeEvidence = resolvedState.activeEvidence,
            sourceReplacement = previous != null && previous.sourceKey != input.sourceKey
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

        // A replacement may be posted just before Android delivers removal for the old source.
        // Rebind only on a unique, non-blank participant match; without identity evidence we wait
        // for markSourceRemoved so simultaneous calls are not collapsed together.
        sessions.values.filter {
            it.packageName == input.packageName &&
                    it.sourceKey != input.sourceKey &&
                    input.observedAt - it.lastSeen in 0..replacementGraceMs &&
                    sameNonBlank(it.participantId, input.participantId)
        }.singleOrNull()?.let { return it }

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
            return ResolvedState(
                CallState.ENDED,
                null,
                null,
                CallActiveEvidence.NONE
            )
        }

        if (previous?.state == CallState.ACTIVE) {
            return ResolvedState(
                CallState.ACTIVE,
                previous.connectedAt,
                previous.connectedAtSource,
                CallActiveEvidence.NONE
            )
        }

        val plausibleBase = input.chronometerBase.takeIf {
            input.showsChronometer && isPlausibleBase(it, input.observedAt)
        }
        val chronometerStarted = classification.activeEvidence == CallActiveEvidence.CHRONOMETER_PRESENT &&
                previous != null && !previous.showsChronometer && plausibleBase != null
        val chronometerBaseReset = classification.activeEvidence == CallActiveEvidence.CHRONOMETER_PRESENT &&
                previous?.showsChronometer == true &&
                previous.lastChronometerBase != null &&
                plausibleBase != null &&
                abs(previous.lastChronometerBase - plausibleBase) >= MATERIAL_BASE_CHANGE_MS
        val answeredIncoming = previous?.state == CallState.INCOMING_RINGING &&
                !classification.hasAnswer && classification.hasDeclineOrHangUp
        val connectedActionsAppeared = previous != null &&
                isOutgoingPreConnected(previous.state) &&
                !previous.hasConnectedControl &&
                classification.hasConnectedControl &&
                classification.hasDeclineOrHangUp

        if (chronometerStarted || chronometerBaseReset || answeredIncoming || connectedActionsAppeared) {
            val transitionEvidence = when {
                chronometerStarted -> CallActiveEvidence.CHRONOMETER_STARTED
                chronometerBaseReset -> CallActiveEvidence.CHRONOMETER_BASE_RESET
                answeredIncoming -> CallActiveEvidence.INCOMING_ANSWERED
                else -> CallActiveEvidence.CONNECTED_ACTIONS_APPEARED
            }
            val connectedAt = when {
                chronometerStarted || chronometerBaseReset -> requireNotNull(plausibleBase)
                else -> input.observedAt
            }
            val source = when {
                chronometerStarted || chronometerBaseReset -> ConnectedAtSource.SOURCE_CHRONOMETER
                else -> ConnectedAtSource.OBSERVED_CONNECTION_TRANSITION
            }
            return ResolvedState(CallState.ACTIVE, connectedAt, source, transitionEvidence)
        }

        if (previous == null && classification.activeEvidence == CallActiveEvidence.CHRONOMETER_PRESENT) {
            // Some VoIP apps expose a chronometer from call initiation. A first observation with
            // only Hang Up is not enough evidence that the remote party answered.
            val initialState = classification.state.takeUnless { it == CallState.ACTIVE }
                ?: CallState.OUTGOING_CALLING
            return ResolvedState(initialState, null, null, CallActiveEvidence.NONE)
        }

        // Some apps expose no observable answer boundary at all. In that case retaining Calling,
        // Ringing, or Connecting indefinitely is intentionally safer than fabricating a 00:00.
        return ResolvedState(
            preservePreConnectedProgress(previous?.state, classification.state),
            null,
            null,
            CallActiveEvidence.NONE
        )
    }

    private fun preservePreConnectedProgress(previous: CallState?, candidate: CallState): CallState {
        if (candidate == CallState.ACTIVE) {
            // ACTIVE is resolved only by the transition evidence above, never by a classifier's
            // presentation candidate alone.
            return previous?.takeIf { it == CallState.INCOMING_RINGING || isOutgoingPreConnected(it) }
                ?: CallState.CONNECTING
        }
        if (previous == CallState.INCOMING_RINGING && candidate != CallState.ENDED) {
            return CallState.INCOMING_RINGING
        }
        if (previous == null) return candidate
        if (!isOutgoingPreConnected(previous) || !isOutgoingPreConnected(candidate)) return candidate
        return if (outgoingRank(candidate) >= outgoingRank(previous)) candidate else previous
    }

    private fun isOutgoingPreConnected(state: CallState): Boolean {
        return state == CallState.OUTGOING_CALLING ||
                state == CallState.OUTGOING_RINGING ||
                state == CallState.CONNECTING
    }

    private fun outgoingRank(state: CallState): Int = when (state) {
        CallState.OUTGOING_CALLING -> 0
        CallState.OUTGOING_RINGING -> 1
        CallState.CONNECTING -> 2
        else -> -1
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
        val connectedAtSource: ConnectedAtSource?,
        val activeEvidence: CallActiveEvidence
    )

    private companion object {
        const val MATERIAL_BASE_CHANGE_MS = 1_000L
    }
}
