package com.d4viddf.hyperbridge.service

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusShadeUpdateTest {
    @Test
    fun stampMarksRootAndParamV2UpdatableWithAnIncreasingSequence() {
        val stamped = FocusShadeUpdate.stamp("""{"param_v2":{"business":"call"}}""", sequence = 2)
        val root = JsonParser.parseString(stamped).asJsonObject
        val paramV2 = root.getAsJsonObject("param_v2")
        assertTrue(root["updatable"].asBoolean)
        assertEquals(2L, root["sequence"].asLong)
        assertFalse(root.has("cancel"))
        assertTrue(paramV2["updatable"].asBoolean)
        assertEquals(2L, paramV2["sequence"].asLong)
        assertEquals("call", paramV2["business"].asString)
        assertFalse(paramV2.has("cancel"))
    }

    @Test
    fun cancelIsSetOnANewerPayloadAndClearedWhenTheRowShouldStay() {
        val ending = FocusShadeUpdate.stamp("""{"param_v2":{"updatable":true}}""", sequence = 4, cancel = true)
        val ended = JsonParser.parseString(ending).asJsonObject
        assertTrue(ended["cancel"].asBoolean)
        assertTrue(ended.getAsJsonObject("param_v2")["cancel"].asBoolean)

        val continued = FocusShadeUpdate.stamp(ending, sequence = 5, cancel = false)
        val live = JsonParser.parseString(continued).asJsonObject
        assertFalse(live.has("cancel"))
        assertFalse(live.getAsJsonObject("param_v2").has("cancel"))
        assertEquals(5L, live["sequence"].asLong)
    }

    @Test
    fun cancelParamIsAnUpdatableFocusPayload() {
        val root = JsonParser.parseString(FocusShadeUpdate.cancelParam(3)).asJsonObject
        assertTrue(root["cancel"].asBoolean)
        assertTrue(root["updatable"].asBoolean)
        assertEquals(3L, root.getAsJsonObject("param_v2")["sequence"].asLong)
    }

    @Test
    fun cancelsReadsEitherObjectAndIgnoresNonBooleans() {
        assertTrue(FocusShadeUpdate.cancels(FocusShadeUpdate.cancelParam(1)))
        assertFalse(FocusShadeUpdate.cancels("""{"param_v2":{"cancel":"true"}}"""))
        assertFalse(FocusShadeUpdate.cancels(null))
        assertFalse(FocusShadeUpdate.cancels("not-json"))
    }

    @Test
    fun malformedPayloadIsLeftUntouched() {
        assertEquals("not-json", FocusShadeUpdate.stamp("not-json", sequence = 1))
        assertEquals("""{"param_v2":{}}""", FocusShadeUpdate.stamp("""{"param_v2":{}}""", sequence = 0))
    }

    @Test
    fun sequencesIncreasePerSourceAndDoNotRestartAtZero() {
        val first = "source-a-${System.nanoTime()}"
        val second = "source-b-${System.nanoTime()}"
        val a1 = FocusShadeUpdate.nextSequence(first)
        val b1 = FocusShadeUpdate.nextSequence(second)
        val a2 = FocusShadeUpdate.nextSequence(first)
        assertTrue(a1 >= 1L)
        assertTrue(a2 > a1)
        assertTrue(b1 >= 1L)
    }
}
