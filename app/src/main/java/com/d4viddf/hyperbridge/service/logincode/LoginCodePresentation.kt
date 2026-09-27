package com.d4viddf.hyperbridge.service.logincode

import com.d4viddf.hyperbridge.models.GlowMode
import com.d4viddf.hyperbridge.models.IslandGlowPresentation

/** How a detected code changes one island, resolved from [LoginCodeSettings]. */
data class LoginCodePresentation(
    val code: String,
    val copyAction: Boolean,
    val compactCode: Boolean,
    val glow: Boolean,
) {
    companion object {
        fun of(code: LoginCode, settings: LoginCodeSettings) = LoginCodePresentation(
            code = code.code,
            copyAction = settings.copyAction,
            compactCode = settings.compactCode,
            glow = settings.glow,
        )

        /**
         * Glow on the compact and expanded island with no color, so SystemUI keeps the stock
         * shader palette HyperOS uses for XiaoAI.
         */
        val NATIVE_GLOW = IslandGlowPresentation(
            islandMode = GlowMode.ON,
            focusMode = GlowMode.ON,
            islandColor = null,
            focusColor = null,
            dynamicColor = null,
            forceIsland = true,
            forceFocus = true,
        )
    }
}
