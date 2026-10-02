package com.sykeptical.hyperpop.service.translators

/**
 * The shade Focus card and the expanded island read the same Focus payload. These fields are
 * that shared presentation: icon, title, detail, progress, color, and actions.
 */
data class ExpandedFocusContent(
    val title: String,
    val detail: String,
    val pictureKey: String,
    val progressPercent: Int?,
    val progressColor: String?,
    val actionKeys: List<String>,
) {
    val showInShade: Boolean get() = true

    companion object {
        fun voice(
            title: String,
            detail: String,
            pictureKey: String,
            progressPercent: Int,
            progressColor: String,
            actionKeys: List<String>,
        ): ExpandedFocusContent = ExpandedFocusContent(
            title = title,
            detail = detail,
            pictureKey = pictureKey,
            progressPercent = progressPercent.coerceIn(0, 100),
            progressColor = progressColor,
            actionKeys = actionKeys,
        )

        fun call(
            title: String,
            detail: String,
            pictureKey: String,
            actionKeys: List<String>,
        ): ExpandedFocusContent = ExpandedFocusContent(
            title = title,
            detail = detail,
            pictureKey = pictureKey,
            progressPercent = null,
            progressColor = null,
            actionKeys = actionKeys,
        )

        fun transfer(
            title: String,
            detail: String,
            pictureKey: String,
            percent: Int,
            showProgress: Boolean,
            progressColor: String,
            actionKeys: List<String>,
        ): ExpandedFocusContent = ExpandedFocusContent(
            title = title,
            detail = detail,
            pictureKey = pictureKey,
            progressPercent = if (showProgress) percent.coerceIn(0, 100) else null,
            progressColor = progressColor,
            actionKeys = actionKeys,
        )
    }
}
