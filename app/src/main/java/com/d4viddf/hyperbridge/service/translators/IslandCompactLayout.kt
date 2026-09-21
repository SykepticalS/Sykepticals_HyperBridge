package com.d4viddf.hyperbridge.service.translators

import com.d4viddf.hyperbridge.models.IslandTextPresentation
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

/** Compact Dynamic Island sides: Xiaomi renders `TextInfo.title` on the collapsed island. */
internal object IslandCompactLayout {
    /** Picture/business keys must stay on the logical conversation, not the changing bridge id. */
    fun pictureKey(logicalId: String): String = "pic_${logicalId.hashCode()}"

    /** Left text longer than this scrolls instead of clipping. Count includes spaces. */
    const val LEFT_MARQUEE_AFTER = 14
    /**
     * Visible left/title cap, including a trailing ellipsis when truncated.
     * Count includes spaces. Longer titles are truncated, not omitted.
     */
    const val LEFT_MAX_VISIBLE = 20
    private const val ELLIPSIS = "..."

    fun text(value: String): TextInfo = TextInfo(title = value, content = null)

    fun compactLeftText(value: String): String {
        if (value.length <= LEFT_MAX_VISIBLE) return value
        val keep = (LEFT_MAX_VISIBLE - ELLIPSIS.length).coerceAtLeast(0)
        return value.take(keep) + ELLIPSIS
    }

    fun leftShouldMarquee(value: String): Boolean {
        val compact = compactLeftText(value)
        return compact.length > LEFT_MARQUEE_AFTER
    }

    fun rightShouldMarquee(value: String): Boolean = value.length > LEFT_MARQUEE_AFTER

    fun hasOverflow(presentation: IslandTextPresentation): Boolean =
        leftShouldMarquee(presentation.left) || rightShouldMarquee(presentation.right)

    fun left(picKey: String, value: String): ImageTextInfoLeft {
        val compact = compactLeftText(value)
        return ImageTextInfoLeft(
            type = 1,
            picInfo = PicInfo(type = 1, pic = picKey),
            textInfo = if (compact.isEmpty()) null else text(compact),
        )
    }

    fun right(value: String, picKey: String? = null): ImageTextInfoRight =
        if (picKey.isNullOrBlank()) {
            ImageTextInfoRight(
                type = 2,
                textInfo = text(value),
            )
        } else {
            ImageTextInfoRight(
                type = 2,
                picInfo = PicInfo(type = 1, pic = picKey),
                textInfo = text(value),
            )
        }

    fun sides(
        picKey: String,
        presentation: IslandTextPresentation,
        rightPicKey: String? = null,
    ): Pair<ImageTextInfoLeft, ImageTextInfoRight> =
        left(picKey, presentation.left) to right(presentation.right, rightPicKey)
}
