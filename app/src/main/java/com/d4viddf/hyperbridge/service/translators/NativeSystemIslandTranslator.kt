package com.d4viddf.hyperbridge.service.translators

import android.content.Context
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.service.NativeSystemIslandEvent
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo

class NativeSystemIslandTranslator(context: Context) : BaseTranslator(context) {
    fun translate(
        event: NativeSystemIslandEvent,
        firstFloat: Boolean,
        isUpdate: Boolean,
    ): HyperIslandData {
        val title = event.left.ifBlank { event.notifyId }
        val content = event.right
        val builder = HyperIslandNotification.Builder(context, BUSINESS, title)
        builder.applyFloatingPresentation(firstFloat, floatOnUpdate = false, isUpdate)
        val presentation = IslandFloatingPresentationPolicy.resolve(firstFloat, false, isUpdate)
        val expandedTime = if (presentation.enableFloat) {
            event.durationMs.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        } else {
            0
        }
        builder.setIslandConfig(
            timeout = PERSISTENT_TIMEOUT_MS,
            dismissible = false,
            highlightColor = "#FFFFFF",
            expandedTimeMs = expandedTime,
        )
        builder.setShowNotification(false)
        builder.addPicture(getColoredPicture(PIC_KEY, R.drawable.ic_native_system_island, "#FFFFFF"))
        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_KEY),
                textInfo = IslandCompactLayout.text(IslandCompactLayout.compactLeftText(title)),
            ),
            right = if (content.isBlank()) {
                null
            } else {
                ImageTextInfoRight(type = 2, textInfo = TextInfo(title = content, content = null))
            },
        )
        builder.setSmallIsland(PIC_KEY)
        return HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())
    }

    companion object {
        private const val BUSINESS = "native_system_island"
        private const val PIC_KEY = "native_system"
        private const val PERSISTENT_TIMEOUT_MS = 86_400_000
    }
}
