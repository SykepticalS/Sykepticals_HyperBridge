package com.sykeptical.hyperpop.service.voice

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceFocusProgressPatchTest {
    @Test
    fun rewritesProgressAndClockWithoutTouchingTheTitle() {
        val json = """
            {"title":"Test","content":"0:01 / 0:40","progressInfo":{"progress":2},"note":"Voice message"}
        """.trimIndent()

        val patched = JsonParser.parseString(
            VoiceFocusProgressPatch.apply(json, 25, "0:10 / 0:40")
        ).asJsonObject

        assertEquals(25, patched.getAsJsonObject("progressInfo").get("progress").asInt)
        assertEquals("0:10 / 0:40", patched.get("content").asString)
        assertEquals("Test", patched.get("title").asString)
        assertEquals("Voice message", patched.get("note").asString)
    }

    @Test
    fun leavesAMillisecondProgressFieldAlone() {
        val patched = JsonParser.parseString(
            VoiceFocusProgressPatch.apply(
                """{"position":{"progress":35268},"circle":{"progress":2}}""",
                86,
                "0:35 / 0:40",
            )
        ).asJsonObject

        assertEquals(35268, patched.getAsJsonObject("position").get("progress").asInt)
        assertEquals(86, patched.getAsJsonObject("circle").get("progress").asInt)
    }
}
