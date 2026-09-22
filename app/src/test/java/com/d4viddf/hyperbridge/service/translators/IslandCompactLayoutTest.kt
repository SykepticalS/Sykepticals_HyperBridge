package com.d4viddf.hyperbridge.service.translators

import com.d4viddf.hyperbridge.models.IslandTextContent
import com.d4viddf.hyperbridge.models.IslandTextPresentation
import com.d4viddf.hyperbridge.models.IslandTextPresentationResolver
import com.d4viddf.hyperbridge.models.IslandTextSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandCompactLayoutTest {
    @Test
    fun pictureKeyStaysOnTheLogicalConversation() {
        val logicalId = "conversation:whatsapp:ada"
        val first = IslandCompactLayout.pictureKey(logicalId)
        val second = IslandCompactLayout.pictureKey(logicalId)
        assertEquals(first, second)
        assertEquals("pic_${logicalId.hashCode()}", first)
        assertTrue(first != IslandCompactLayout.pictureKey("conversation:whatsapp:sam"))
    }

    @Test
    fun compactSidesPutPrimaryTextInTitle() {
        val presentation = IslandTextPresentationResolver.resolve(
            IslandTextSource(title = "Ada", content = "Hello there", sender = "Ada"),
            IslandTextContent.AUTOMATIC,
            IslandTextContent.AUTOMATIC,
        )
        val (left, right) = IslandCompactLayout.sides("pic", presentation)
        assertEquals("Ada", left.textInfo?.title)
        assertNull(left.textInfo?.content)
        assertEquals("Hello there", right.textInfo?.title)
        assertNull(right.textInfo?.content)
        assertEquals(2, right.type)
        assertNull(right.picInfo)
    }

    @Test
    fun compactRightImageUsesTrailingType2Icon() {
        val presentation = IslandTextPresentationResolver.resolve(
            IslandTextSource(title = "Ada", content = "Shared a video", sender = "Ada"),
            IslandTextContent.AUTOMATIC,
            IslandTextContent.AUTOMATIC,
        )
        val (_, right) = IslandCompactLayout.sides("pic", presentation, rightPicKey = "thumb")
        assertEquals(2, right.type)
        assertEquals("thumb", right.picInfo?.pic)
        assertEquals("Shared a video", right.textInfo?.title)
    }

    @Test
    fun leftTextLongerThanFifteenCharactersIsTruncatedAfterFifteen() {
        val original = "abcdefghijklmnop"
        assertEquals(16, original.length)
        val truncated = IslandCompactLayout.compactLeftText(original)
        assertEquals("abcdefghijklmno...", truncated)
        assertEquals(IslandCompactLayout.LEFT_MAX_CHARACTERS + 3, truncated.length)
        assertTrue(truncated.endsWith("..."))
        val left = IslandCompactLayout.left("pic", original)
        assertEquals(truncated, left.textInfo?.title)
        assertEquals("pic", left.picInfo?.pic)
        assertTrue(IslandCompactLayout.leftShouldMarquee(original))
    }

    @Test
    fun leftTextOfFifteenCharactersStillShows() {
        val value = "123456789012345"
        assertEquals(15, value.length)
        assertEquals(value, IslandCompactLayout.left("pic", value).textInfo?.title)
        assertTrue(IslandCompactLayout.leftShouldMarquee(value))
    }

    @Test
    fun leftTextAboveFourteenCharactersMarquees() {
        val value = "123456789012345"
        assertEquals(15, value.length)
        assertTrue(IslandCompactLayout.leftShouldMarquee(value))
        assertEquals(value, IslandCompactLayout.compactLeftText(value))
    }

    @Test
    fun leftTextAtFourteenCharactersDoesNotMarquee() {
        val value = "12345678901234"
        assertEquals(14, value.length)
        assertEquals(value, IslandCompactLayout.compactLeftText(value))
        assertFalse(IslandCompactLayout.leftShouldMarquee(value))
    }

    @Test
    fun leftTextCountsSpacesTowardTheTruncationLimit() {
        val longWithSpaces = "hello world 12345"
        assertEquals(17, longWithSpaces.length)
        val truncated = IslandCompactLayout.compactLeftText(longWithSpaces)
        assertEquals("hello world 123...", truncated)
        assertEquals(IslandCompactLayout.LEFT_MAX_CHARACTERS + 3, truncated.length)
        assertEquals(truncated, IslandCompactLayout.left("pic", longWithSpaces).textInfo?.title)
        assertTrue(IslandCompactLayout.leftShouldMarquee(longWithSpaces))

        val shown = "hello world 123"
        assertEquals(15, shown.length)
        assertEquals(shown, IslandCompactLayout.left("pic", shown).textInfo?.title)
        assertTrue(IslandCompactLayout.leftShouldMarquee(shown))
    }

    @Test
    fun rightTextStaysVisibleAndMarqueesWhenLong() {
        val longRight = "this subtitle is definitely longer than fourteen"
        val right = IslandCompactLayout.right(longRight)
        assertEquals(longRight, right.textInfo?.title)
        assertTrue(IslandCompactLayout.rightShouldMarquee(longRight))
        val truncatedLeft = IslandCompactLayout.sides(
            "pic",
            IslandTextPresentation(
                left = "abcdefghijklmnopqrstuvwxyz",
                right = longRight,
            ),
        )
        assertEquals("abcdefghijklmno...", truncatedLeft.first.textInfo?.title)
        assertTrue(IslandCompactLayout.leftShouldMarquee("abcdefghijklmnopqrstuvwxyz"))
        assertEquals(longRight, truncatedLeft.second.textInfo?.title)
    }
}
