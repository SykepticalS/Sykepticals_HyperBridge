package com.d4viddf.hyperbridge.service.translators

import android.content.Context
import android.service.notification.StatusBarNotification
import com.d4viddf.hyperbridge.data.theme.ThemeRepository
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.models.IslandConfig
import com.d4viddf.hyperbridge.models.theme.HyperTheme
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification

class MessageTranslator(
    context: Context,
    repo: ThemeRepository
) : BaseTranslator(context, repo) {

    fun translate(
        sbn: StatusBarNotification,
        title: String,
        text: String,
        picKey: String,
        config: IslandConfig,
        theme: HyperTheme?,
        isUpdate: Boolean = false
    ): HyperIslandData {
        val highlightColor = resolveColor(theme, sbn.packageName, "#FFFFFF")
        val presentation = resolveIslandText(sbn, title, text, config, sender = title)
        val compact = compactIslandAssets(sbn, picKey, presentation)

        val builder = HyperIslandNotification.Builder(context, stableBusinessId(picKey), title)

        // --- CONFIGURATION ---
        builder.applyFloatingPresentation(config.firstFloat ?: false, config.floatOnUpdate ?: false, isUpdate)
        val floatPresentation = IslandFloatingPresentationPolicy.resolve(
            config.firstFloat ?: false,
            config.floatOnUpdate ?: false,
            isUpdate,
        )
        builder.setIslandConfig(
            timeout = config.timeout,
            dismissible = true,
            highlightColor = highlightColor,
            expandedTimeMs = floatPresentation.expandedTimeMs(config.floatTimeout),
        )
        builder.setShowNotification(config.isShowShade ?: false)

        val bridgeActions = extractBridgeActions(
            sbn = sbn,
            config = config,
            theme = theme
        )

        builder.addPicture(compact.avatar)
        compact.attachment?.let(builder::addPicture)
        // HyperIsland's notification_island template uses iconTextInfo, not chatInfo.
        // chatInfo is Xiaomi's IM/promoted path; SystemUI-owned proxies hit
        // PromotedNotificationParamUtils NPE + checkError and the island is deleted.
        builder.setIconTextInfo(
            picKey = compact.smallKey,
            title = title,
            content = text
        )
        builder.setBigIslandInfo(left = compact.left, right = compact.right)
        builder.setSmallIsland(compact.smallKey)

        // Add Actions
        if (bridgeActions.isNotEmpty()) {
            // [FIX] Specific configuration for Shade Text Buttons:
            // 1. actionBgColor = null -> Transparent Background
            // 2. titleColor = "#FFFFFF" -> White Text (Neutral/No Color)
            val textActions = bridgeActions.map { it.action }.map { original ->
                HyperAction(
                    key = original.key,
                    title = original.title,
                    icon = original.icon,
                    pendingIntent = original.pendingIntent,
                    actionIntentType = original.actionIntentType,
                    actionBgColor = null,
                    titleColor = "#FFFFFF"
                )
            }.toTypedArray()

            // Set actions visible in shade
            builder.setTextButtons(*textActions)

            // Register them internally
            textActions.forEach {
                builder.addHiddenAction(it)
            }

            // Register any custom icons if available
            bridgeActions.forEach {
                it.actionImage?.let { pic -> builder.addPicture(pic) }
            }
        }


        return HyperIslandData(
            builder.buildResourceBundle(),
            builder.buildJsonParam(),
            highlightColor,
        )
    }
}
