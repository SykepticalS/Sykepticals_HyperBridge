package com.sykeptical.hyperpop.xposed.mediacard.island.compact

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactTitleTruncationTest {
    private fun widthOf(text: String): Float = text.length.toFloat()

    @Test
    fun shortTitleStaysWhole() {
        assertEquals("Song", CompactTitleTruncation.ellipsize("Song", 10f, ::widthOf))
    }

    @Test
    fun longTitleEndsWithAnEllipsis() {
        assertEquals("Song…", CompactTitleTruncation.ellipsize("Song title", 5f, ::widthOf))
    }

    @Test
    fun unmeasuredWidthKeepsTheTitle() {
        assertEquals("Song title", CompactTitleTruncation.ellipsize("Song title", 0f, ::widthOf))
    }

    @Test
    fun ellipsisAloneWhenOnlyOneCharacterFits() {
        assertEquals("…", CompactTitleTruncation.ellipsize("Song", 1f, ::widthOf))
    }
}
