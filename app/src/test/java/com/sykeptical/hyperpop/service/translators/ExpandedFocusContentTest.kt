package com.sykeptical.hyperpop.service.translators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedFocusContentTest {
    @Test
    fun voiceCardUsesTheExpandedTitleClockProgressAndAction() {
        val content = ExpandedFocusContent.voice(
            title = "Voice message from Ada",
            detail = "0:02 / 0:08",
            pictureKey = "pic_voice",
            progressPercent = 25,
            progressColor = "#007AFF",
            actionKeys = listOf("voice_voice"),
        )
        assertEquals("Voice message from Ada", content.title)
        assertEquals("0:02 / 0:08", content.detail)
        assertEquals("pic_voice", content.pictureKey)
        assertEquals(25, content.progressPercent)
        assertEquals("#007AFF", content.progressColor)
        assertEquals(listOf("voice_voice"), content.actionKeys)
        assertTrue(content.showInShade)
    }

    @Test
    fun callCardUsesTheExpandedTitleStateAndActionsWithoutProgress() {
        val content = ExpandedFocusContent.call(
            title = "Ada",
            detail = "Incoming call",
            pictureKey = "pic_call",
            actionKeys = listOf("act_answer", "act_decline"),
        )
        assertEquals("Ada", content.title)
        assertEquals("Incoming call", content.detail)
        assertNull(content.progressPercent)
        assertEquals(listOf("act_answer", "act_decline"), content.actionKeys)
        assertTrue(content.showInShade)
    }

    @Test
    fun liveTransferShowsProgressAndAFinishedTransferDropsIt() {
        val running = ExpandedFocusContent.transfer(
            title = "report.pdf",
            detail = "4 MB / 10 MB",
            pictureKey = "pic_download",
            percent = 140,
            showProgress = true,
            progressColor = "#34C759",
            actionKeys = listOf("act_cancel"),
        )
        assertEquals(100, running.progressPercent)
        assertEquals("#34C759", running.progressColor)
        assertEquals(listOf("act_cancel"), running.actionKeys)
        assertTrue(running.showInShade)

        val finished = ExpandedFocusContent.transfer(
            title = "report.pdf",
            detail = "Complete",
            pictureKey = "pic_download",
            percent = 100,
            showProgress = false,
            progressColor = "#34C759",
            actionKeys = emptyList(),
        )
        assertEquals("Complete", finished.detail)
        assertNull(finished.progressPercent)
        assertTrue(finished.showInShade)
    }
}
