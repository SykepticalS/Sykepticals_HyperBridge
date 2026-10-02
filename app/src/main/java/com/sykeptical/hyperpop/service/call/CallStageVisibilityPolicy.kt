package com.sykeptical.hyperpop.service.call

import com.sykeptical.hyperpop.models.CallStage

object CallStageVisibilityPolicy {
    fun stageFor(state: CallState): CallStage? = when (state) {
        CallState.INCOMING_RINGING -> CallStage.INCOMING
        CallState.OUTGOING_CALLING,
        CallState.OUTGOING_RINGING,
        CallState.CONNECTING -> CallStage.OUTGOING
        CallState.ACTIVE -> CallStage.ACTIVE
        CallState.ENDED -> null
    }

    fun isVisible(enabledStages: Set<CallStage>, state: CallState): Boolean {
        return stageFor(state)?.let(enabledStages::contains) == true
    }
}
