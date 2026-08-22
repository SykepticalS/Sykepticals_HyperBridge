package com.d4viddf.hyperbridge.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderedJsonNormalizerTest {
    @Test
    fun newAndUpdatePresentationFlagsDoNotChangeSemanticPayload() {
        val newJson = """{"title":"Alice","text":"hello","islandFirstFloat":true,"reopen":true}"""
        val updateJson = """{"title":"Alice","text":"hello","islandFirstFloat":false}"""
        val newHash = RenderedJsonNormalizer.normalize(newJson).hashCode()
        val updateHash = RenderedJsonNormalizer.normalize(updateJson).hashCode()

        assertEquals(newHash, updateHash)
        val decision = IslandUpdateResolver.decide(
            logicalId = "conversation",
            candidateBridgeId = 99,
            contentHash = updateHash,
            previous = PreviousIslandPresentation("conversation", 42, newHash),
            presentationReason = IslandPresentationReason.CONTENT_UPDATE
        )
        assertEquals(IslandPresentationKind.UNCHANGED, decision.kind)
        assertEquals(42, decision.bridgeId)
    }
}
