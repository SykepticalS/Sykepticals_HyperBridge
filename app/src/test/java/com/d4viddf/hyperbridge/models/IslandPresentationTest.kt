package com.d4viddf.hyperbridge.models

import org.junit.Assert.*
import org.junit.Test

class IslandPresentationTest {
    @Test fun legacyFloatMigratesWithoutEnablingUpdateFloat() {
        assertEquals(true to false, FloatConfigMigration.fromLegacy(true))
        assertEquals(false to false, FloatConfigMigration.fromLegacy(false))
        assertEquals(null to null, FloatConfigMigration.fromLegacy(null))
    }

    @Test fun appFieldsInheritIndependentlyAndOverridesAreNotSentinelBased() {
        val global = IslandConfig(firstFloat = true, floatOnUpdate = true, marqueeEnabled = true, islandGlowMode = GlowMode.ON)
        val app = IslandConfig(firstFloat = false)
        val merged = app.mergeWith(global)
        assertFalse(merged.firstFloat!!)
        assertTrue(merged.floatOnUpdate!!)
        assertTrue(merged.marqueeEnabled!!)
        assertTrue(app.hasOverrides())
        assertFalse(IslandConfig().hasOverrides())
    }

    @Test fun unsetGlowColorStaysUnsetSoXiaomiDefaultPaletteCanApply() {
        val merged = IslandConfig().mergeWith(IslandConfig(islandGlowMode = GlowMode.ON))
        assertEquals(GlowMode.ON, merged.islandGlowMode)
        assertNull(merged.islandGlowColor)
        assertNull(IslandGlowResolver.resolve(IslandConfig(islandGlowMode = GlowMode.ON), IslandConfig(), null).islandColor)
    }

    @Test fun onGlowUsesAppPaletteWhenNoManualColorIsSet() {
        val resolved = IslandGlowResolver.resolve(
            IslandConfig(islandGlowMode = GlowMode.ON, focusGlowMode = GlowMode.ON),
            IslandConfig(),
            "#25D366",
        )
        assertEquals("#25D366", resolved.resolvedIslandColor())
        assertEquals("#25D366", resolved.resolvedFocusColor())
        val islandOnly = IslandGlowResolver.resolve(
            IslandConfig(islandGlowMode = GlowMode.FOLLOW_DYNAMIC),
            IslandConfig(),
            "#25D366",
        )
        assertEquals("#25D366", islandOnly.resolvedIslandColor())
        assertEquals("#25D366", islandOnly.resolvedFocusColor())
        val custom = IslandGlowResolver.resolve(
            IslandConfig(islandGlowMode = GlowMode.ON, islandGlowColor = "#FF0000"),
            IslandConfig(),
            "#25D366",
        )
        assertEquals("#FF0000", custom.resolvedIslandColor())
    }

    @Test fun contactTitlesForcePinkGlowWhenEnabled() {
        val config = IslandConfig(
            islandGlowMode = GlowMode.ON,
            focusGlowMode = GlowMode.FOLLOW_DYNAMIC,
            islandGlowColor = "#00FF00",
            contactPinkGlow = true,
        )
        val matched = IslandGlowResolver.resolve(config, IslandConfig(), "#112233", "levixcs")
        assertEquals(IslandGlowResolver.CONTACT_PINK, matched.resolvedIslandColor())
        assertEquals(IslandGlowResolver.CONTACT_PINK, matched.resolvedFocusColor())
        assertEquals(IslandGlowResolver.CONTACT_PINK, IslandGlowResolver.resolve(config, IslandConfig(), "#112233", "Call from Nisamm").resolvedIslandColor())
        assertEquals("#00FF00", IslandGlowResolver.resolve(config, IslandConfig(), "#112233", "Ada").resolvedIslandColor())
        assertNull(IslandGlowResolver.contactPink("someone else"))
        assertEquals("#00FF00", IslandGlowResolver.resolve(config.copy(contactPinkGlow = false), IslandConfig(), "#112233", "levixcs").resolvedIslandColor())
        val forced = IslandGlowResolver.resolve(
            IslandConfig(islandGlowMode = GlowMode.OFF, focusGlowMode = GlowMode.OFF, contactPinkGlow = true),
            IslandConfig(),
            "#112233",
            "levixcs",
        )
        assertTrue(forced.islandEnabled)
        assertTrue(forced.focusEnabled)
        assertEquals(IslandGlowResolver.CONTACT_PINK, forced.resolvedIslandColor())
        assertNull(IslandGlowResolver.resolve(IslandConfig(islandGlowMode = GlowMode.OFF), IslandConfig(), "#112233", "levixcs").resolvedIslandColor())
    }

    @Test fun automaticAndCustomTextPresentationUseBothSides() {
        val source = IslandTextSource(title = "Conversation", content = "Latest message", app = "Chat", sender = "Ada")
        assertEquals(IslandTextPresentation("Ada", "Latest message"),
            IslandTextPresentationResolver.resolve(source, IslandTextContent.AUTOMATIC, IslandTextContent.AUTOMATIC))
        assertEquals("Chat: Conversation", IslandTextPresentationResolver.resolve(
            source, IslandTextContent.CUSTOM, IslandTextContent.NONE, "{app}: {title}").left)
    }

    @Test fun marqueeModesAndFallbackNeverDismissOngoing() {
        assertNull(MarqueeTimeoutPolicy.effectiveTimeout(10, MarqueeDismissMode.AFTER_ONE_OVERRIDE_TIMEOUT, true))
        assertEquals(10, MarqueeTimeoutPolicy.effectiveTimeout(10, MarqueeDismissMode.AFTER_ONE_OVERRIDE_TIMEOUT, false))
        assertTrue(MarqueeTimeoutPolicy.shouldDismiss(MarqueeDismissMode.AFTER_TWO, 2, false))
        assertFalse(MarqueeTimeoutPolicy.shouldDismiss(MarqueeDismissMode.AFTER_ONE, 3, true))
    }

    @Test fun explicitAppOffBeatsGlobalForceAndEffectsStayIndependent() {
        val global = IslandConfig(islandGlowMode = GlowMode.ON, focusGlowMode = GlowMode.ON,
            forceIslandGlow = true, forceFocusGlow = true)
        val resolved = IslandGlowResolver.resolve(global, IslandConfig(islandGlowMode = GlowMode.OFF), "#112233")
        assertFalse(resolved.islandEnabled)
        assertTrue(resolved.focusEnabled)
        assertFalse(resolved.forceIsland)
        assertTrue(resolved.forceFocus)
    }

    @Test fun dynamicColorFallsBackToManualAndParsingClamps() {
        val global = IslandConfig(islandGlowMode = GlowMode.FOLLOW_DYNAMIC, islandGlowColor = "#abcdef")
        assertEquals("#ABCDEF", IslandGlowResolver.resolve(global, IslandConfig(), null).resolvedIslandColor())
        assertEquals("#123456", IslandGlowResolver.resolve(global, IslandConfig(), "#123456").resolvedIslandColor())
        assertNull(IslandGlowResolver.normalizeColor("not-a-color"))
        assertEquals(0, IslandGlowResolver.clampRange(-9))
        assertEquals(100, IslandGlowResolver.clampRange(101))
    }

    @Test fun glowPlanWritesXiaomiEffectsOnlyWhenEnabled() {
        val off = IslandVisualMetadata.plan(
            IslandConfig(islandGlowMode = GlowMode.OFF, focusGlowMode = GlowMode.ON),
            IslandGlowResolver.resolve(IslandConfig(islandGlowMode = GlowMode.OFF, focusGlowMode = GlowMode.ON), IslandConfig(), "#112233"),
            keepPosted = false,
            marqueeCapable = true,
        )
        assertNull(off.islandEffect)
        assertEquals(IslandVisualMetadata.EFFECT_OUTER_GLOW, off.focusEffect)

        val explicitOff = IslandGlowResolver.resolve(
            IslandConfig(islandGlowMode = GlowMode.ON, forceIslandGlow = true),
            IslandConfig(islandGlowMode = GlowMode.OFF),
            "#445566",
        )
        val plan = IslandVisualMetadata.plan(IslandConfig(islandGlowMode = GlowMode.OFF), explicitOff, false, true)
        assertNull(plan.islandEffect)
        assertFalse(plan.glow.forceIsland)
    }

    @Test fun marqueeOverrideUsesMaxTimeoutButOngoingFallsBack() {
        val config = IslandConfig(
            timeout = 8,
            marqueeEnabled = true,
            marqueeDismissMode = MarqueeDismissMode.AFTER_ONE_OVERRIDE_TIMEOUT,
        )
        val glow = IslandGlowResolver.resolve(IslandConfig(), IslandConfig(), null)
        assertEquals(Int.MAX_VALUE, IslandVisualMetadata.plan(config, glow, keepPosted = false, marqueeCapable = true).islandTimeoutSeconds)
        assertEquals(8, IslandVisualMetadata.plan(config, glow, keepPosted = true, marqueeCapable = true).islandTimeoutSeconds)
        assertEquals(8, IslandVisualMetadata.plan(config, glow, keepPosted = false, marqueeCapable = false).islandTimeoutSeconds)
        assertEquals(8, IslandVisualMetadata.plan(config, glow, keepPosted = false, marqueeCapable = true).originalTimeoutSeconds)
    }

    @Test fun onGlowWithoutManualColorWritesDynamicPaletteIntoJson() {
        val glow = IslandGlowResolver.resolve(
            IslandConfig(islandGlowMode = GlowMode.ON),
            IslandConfig(),
            "#25D366",
        )
        val patched = IslandVisualMetadata.injectGlowJson("""{"param_v2":{"param_island":{}}}""", glow)
        assertTrue(patched.contains("\"outEffectColor\":\"#25D366\""))
        assertEquals("#25D366", glow.resolvedIslandColor())
    }

    @Test fun injectUpdatableMarksFocusPayloadForInPlaceRefresh() {
        val json = """{"param_v2":{"param_island":{}}}"""
        val patched = IslandVisualMetadata.injectUpdatable(json, true)
        assertTrue(patched.contains("\"updatable\":true"))
        val notLive = IslandVisualMetadata.injectUpdatable(json, false)
        assertTrue(notLive.contains("\"updatable\":false"))
        assertFalse(notLive.contains("\"updatable\":true"))
    }

    @Test fun textUpdateAnimationEnablesEveryCompactTextInfoOnly() {
        val json = """{"param_v2":{"baseInfo":{"title":"Expanded"},"param_island":{"bigIslandArea":{"imageTextInfoLeft":{"textInfo":{"title":"Left"}},"imageTextInfoRight":{"textInfo":{"title":"Right","turnAnim":false}}},"smallIslandArea":{"picInfo":{"pic":"icon"}}}}}"""
        val patched = IslandVisualMetadata.injectTextUpdateAnimation(json)
        val root = com.google.gson.JsonParser.parseString(patched).asJsonObject
        val paramV2 = root.getAsJsonObject("param_v2")
        val big = paramV2.getAsJsonObject("param_island").getAsJsonObject("bigIslandArea")
        assertTrue(big.getAsJsonObject("imageTextInfoLeft").getAsJsonObject("textInfo")["turnAnim"].asBoolean)
        assertTrue(big.getAsJsonObject("imageTextInfoRight").getAsJsonObject("textInfo")["turnAnim"].asBoolean)
        assertFalse(paramV2.getAsJsonObject("baseInfo").has("turnAnim"))
    }

    @Test fun textUpdateAnimationFailsOpenForMalformedOrMissingIslandPayload() {
        assertEquals("not-json", IslandVisualMetadata.injectTextUpdateAnimation("not-json"))
        val focusOnly = """{"param_v2":{"baseInfo":{"title":"Expanded"}}}"""
        assertEquals(focusOnly, IslandVisualMetadata.injectTextUpdateAnimation(focusOnly))
    }

    @Test fun injectFloatingFlagsWritesEnableFloatFirstFloatAndReopen() {
        val json = """{"param_v2":{"param_island":{},"enableFloat":true,"islandFirstFloat":true,"reopen":true}}"""
        val patched = IslandVisualMetadata.injectFloatingFlags(json, enableFloat = false)
        val paramV2 = com.google.gson.JsonParser.parseString(patched).asJsonObject.getAsJsonObject("param_v2")
        assertFalse(paramV2["enableFloat"].asBoolean)
        assertFalse(paramV2["islandFirstFloat"].asBoolean)
        assertFalse(paramV2["reopen"].asBoolean)
        val expanded = IslandVisualMetadata.injectFloatingFlags(json, enableFloat = true, islandFirstFloat = true, reopen = true)
        val expandedV2 = com.google.gson.JsonParser.parseString(expanded).asJsonObject.getAsJsonObject("param_v2")
        assertTrue(expandedV2["enableFloat"].asBoolean)
        assertTrue(expandedV2["reopen"].asBoolean)
    }

    @Test fun glowIsolationNeverStartsFromADifferentIsland() {
        assertTrue(IslandGlowIsolation.shouldStartGlow(currentOwned = true, currentRequestsGlow = true))
        assertFalse(IslandGlowIsolation.shouldStartGlow(currentOwned = true, currentRequestsGlow = false))
        assertFalse(IslandGlowIsolation.shouldStartGlow(currentOwned = false, currentRequestsGlow = true))
        assertTrue(IslandGlowIsolation.shouldStopGlow(currentKnown = true, currentOwned = true, currentRequestsGlow = false))
        assertTrue(IslandGlowIsolation.shouldStopGlow(currentKnown = true, currentOwned = false, currentRequestsGlow = false))
        assertFalse(IslandGlowIsolation.shouldStopGlow(currentKnown = false, currentOwned = false, currentRequestsGlow = false))
        assertFalse(IslandGlowIsolation.canReuseRecentTarget("island-a", "island-b", currentRequestsGlow = false))
        assertTrue(IslandGlowIsolation.canReuseRecentTarget("island-a", "island-a", currentRequestsGlow = false))
        assertTrue(IslandGlowIsolation.canReuseRecentTarget("island-b", "island-a", currentRequestsGlow = true))
        assertFalse(IslandGlowIsolation.canReuseRecentTarget(null, "island-a", currentRequestsGlow = false))
    }

    @Test fun appIconPalettePrefersNonWhiteAccentThenIcon() {
        assertEquals("#25D366", com.d4viddf.hyperbridge.service.visual.AppIconPalette.prefer("#FFFFFF", "#25D366"))
        assertEquals("#112233", com.d4viddf.hyperbridge.service.visual.AppIconPalette.prefer("#112233", "#25D366"))
        assertEquals("#25D366", com.d4viddf.hyperbridge.service.visual.AppIconPalette.prefer(null, "#25D366"))
    }

    @Test fun islandAndFocusGlowJsonAreInjectedIndependentlyAndClearedWhenOff() {
        val json = """{"param_v2":{"param_island":{"highlightColor":"#111111"}}}"""
        val islandOnly = IslandGlowPresentation(GlowMode.ON, GlowMode.OFF, "#AABBCC", null, null, false, false)
        val islandPatched = IslandVisualMetadata.injectGlowJson(json, islandOnly)
        assertTrue(islandPatched.contains("\"outEffectSrc\":\"outer_glow\""))
        assertTrue(islandPatched.contains("\"outEffectColor\":\"#AABBCC\""))
        val islandObj = com.google.gson.JsonParser.parseString(islandPatched).asJsonObject
            .getAsJsonObject("param_v2")
        assertFalse(islandObj.has("outEffectSrc"))
        assertEquals("outer_glow", islandObj.getAsJsonObject("param_island").get("outEffectSrc").asString)

        val islandColorOnly = IslandGlowPresentation(GlowMode.FOLLOW_DYNAMIC, GlowMode.ON, "#AABBCC", null, "#25D366", false, false)
        assertEquals("#25D366", islandColorOnly.resolvedFocusColor())
        val both = IslandGlowPresentation(GlowMode.ON, GlowMode.FOLLOW_DYNAMIC, "#AABBCC", "#CCDDEE", "#123456", false, false)
        val patched = IslandVisualMetadata.injectGlowJson(json, both)
        assertTrue(patched.contains("\"outEffectSrc\":\"outer_glow\""))
        assertTrue(patched.contains("\"outEffectColor\":\"#123456\""))
        val disabled = IslandGlowPresentation(GlowMode.OFF, GlowMode.OFF, null, null, null, false, false)
        val cleared = IslandVisualMetadata.injectGlowJson(patched, disabled)
        val clearedV2 = com.google.gson.JsonParser.parseString(cleared).asJsonObject.getAsJsonObject("param_v2")
        assertFalse(clearedV2.has("outEffectSrc"))
        assertFalse(clearedV2.getAsJsonObject("param_island").has("outEffectSrc"))
    }

    @Test fun runtimeGlowTreatsMissingModeAsEffectAndExplicitOffWins() {
        assertTrue(IslandGlowResolver.runtimeEnabled(null, effectPresent = true, force = false))
        assertTrue(IslandGlowResolver.runtimeEnabled("ON", effectPresent = false, force = false))
        assertFalse(IslandGlowResolver.runtimeEnabled("OFF", effectPresent = true, force = true))
        assertTrue(IslandGlowResolver.runtimeEnabled(null, effectPresent = false, force = true))
    }

    @Test fun staleGenerationNeverMatchesCurrentIsland() {
        assertFalse(IslandGenerationGuard.isCurrent(expected = 1L, current = 2L, owned = true))
        assertFalse(IslandGenerationGuard.isCurrent(expected = 1L, current = 1L, owned = false))
        assertTrue(IslandGenerationGuard.isCurrent(expected = 9L, current = 9L, owned = true))
    }

    @Test fun textButtonActionIntentIsRewrittenToV3ActionKey() {
        val json = """{"param_v2":{"textButton":[{"actionIntent":"miui.focus.action_1","actionIntentType":2,"title":"Reply"}]}}"""
        val patched = IslandVisualMetadata.fixTextButtonJson(json)
        val button = com.google.gson.JsonParser.parseString(patched).asJsonObject
            .getAsJsonObject("param_v2").getAsJsonArray("textButton")[0].asJsonObject
        assertEquals("miui.focus.action_1", button.get("action").asString)
        assertFalse(button.has("actionIntent"))
        assertFalse(button.has("actionIntentType"))
    }
}
