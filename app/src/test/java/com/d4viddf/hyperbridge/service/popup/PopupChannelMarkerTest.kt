package com.d4viddf.hyperbridge.service.popup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PopupChannelMarkerTest {
    @Test
    fun roundTripsEveryValidImportanceAndPreservesVisibleDescription() {
        for (importance in 0..5) {
            val encoded = PopupChannelMarker.encode("Visible description", importance)
            val decoded = PopupChannelMarker.decode(encoded)
            assertEquals("Visible description", decoded?.visibleDescription)
            assertEquals(importance, decoded?.originalImportance)
            assertEquals("Visible description", PopupChannelMarker.strip(encoded))
        }
    }

    @Test
    fun encodingTwiceDoesNotAppendMarkersForever() {
        val once = PopupChannelMarker.encode("Description", 4)
        val twice = PopupChannelMarker.encode(once, 4)
        assertEquals(once, twice)
    }

    @Test
    fun arbitraryRightToLeftMarksAreNotTreatedAsOwnership() {
        val legitimate = "Arabic metadata\u200F\u200F\u200F"
        assertNull(PopupChannelMarker.decode(legitimate))
        assertEquals(legitimate, PopupChannelMarker.strip(legitimate))
    }

    @Test
    fun malformedOrOutOfRangeSignatureIsRejected() {
        assertNull(PopupChannelMarker.decode("text\u2063\u2060\u2063x\u2060\u2063\u2060"))
        assertNull(PopupChannelMarker.decode("text\u2063\u2060\u2063\u200F\u200F\u200F\u200F\u200F\u200F\u200F\u2060\u2063\u2060"))
    }
}
