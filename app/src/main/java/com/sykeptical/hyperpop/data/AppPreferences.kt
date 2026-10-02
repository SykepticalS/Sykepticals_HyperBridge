package com.sykeptical.hyperpop.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.sykeptical.hyperpop.data.db.AppDatabase
import com.sykeptical.hyperpop.data.db.AppSetting
import com.sykeptical.hyperpop.data.db.SettingsDao
import com.sykeptical.hyperpop.data.db.SettingsKeys
import com.sykeptical.hyperpop.models.CallStage
import com.sykeptical.hyperpop.models.IslandConfig
import com.sykeptical.hyperpop.models.GlowMode
import com.sykeptical.hyperpop.models.IslandSceneBehavior
import com.sykeptical.hyperpop.models.IslandTextContent
import com.sykeptical.hyperpop.models.MarqueeDismissMode
import com.sykeptical.hyperpop.models.FloatConfigMigration
import com.sykeptical.hyperpop.models.IslandLimitMode
import com.sykeptical.hyperpop.models.NavContent
import com.sykeptical.hyperpop.models.NotificationType
import com.sykeptical.hyperpop.service.logincode.LoginCodeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private val Context.legacyDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class NotificationTypePreferenceSnapshot(
    val globalTypes: Set<String>,
    val appOverrides: Map<String, Set<String>>
)

data class CallStagePreferenceSnapshot(
    val globalStages: Set<String>,
    val appOverrides: Map<String, Set<String>>,
)

class AppPreferences internal constructor(
    private val dao: SettingsDao,
    private val legacyDataStore: DataStore<Preferences>?,
    context: Context?,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    constructor(context: Context) : this(
        dao = AppDatabase.getDatabase(context).settingsDao(),
        legacyDataStore = context.applicationContext.legacyDataStore,
        context = context
    )

    private val memoryCache = ConcurrentHashMap<String, String>()

    init {
        // --- MEMORY CACHE LOGIC ---
        scope.launch {
            dao.getAllFlow().collect { list ->
                val newCache = ConcurrentHashMap<String, String>()
                list.forEach { newCache[it.key] = it.value }
                memoryCache.clear()
                memoryCache.putAll(newCache)
            }
        }

        // --- MIGRATION LOGIC ---
        if (context != null && legacyDataStore != null) {
            scope.launch {
                try {
                    // Wait for user unlock before attempting to migrate from legacy DataStore (CE storage)
                    val userManager = context.getSystemService(Context.USER_SERVICE) as? android.os.UserManager
                    if (userManager != null && !userManager.isUserUnlocked) {
                        return@launch 
                    }

                // This legacy checkpoint used to replay all onboarding. Keep its marker, but
                // preserve setup state; popup control has its own focused upgrade gate.
                val lastResetVersion = dao.getSetting("onboarding_reset_version")?.toIntOrNull() ?: 0
                if (lastResetVersion < 19) {
                    dao.insert(AppSetting("onboarding_reset_version", "19"))
                }

                val isMigrated = dao.getSetting(SettingsKeys.MIGRATION_COMPLETE) == "true"
                if (!isMigrated) {
                    val legacyPrefs = legacyDataStore.data.first().asMap()
                    if (legacyPrefs.isNotEmpty()) {
                        legacyPrefs.forEach { (key, value) ->
                            val strValue = when (value) {
                                is Set<*> -> value.joinToString(",")
                                else -> value.toString()
                            }
                            dao.insert(AppSetting(key.name, strValue))
                        }
                        legacyDataStore.edit { it.clear() }
                    }
                    dao.insert(AppSetting(SettingsKeys.MIGRATION_COMPLETE, "true"))
                }

                if (dao.getSetting(SettingsKeys.POPUP_CONTROL_MIGRATION_COMPLETE) != "true") {
                    val setupComplete = dao.getSetting(SettingsKeys.SETUP_COMPLETE).toBoolean(false)
                    val intentionallyDisabled = dao.getSetting(SettingsKeys.POPUP_CONTROL_INTENTIONALLY_DISABLED)
                        .toBoolean(false)
                    @Suppress("DEPRECATION")
                    val obsoleteFloatingNoticeKey = SettingsKeys.FLOATING_SETUP_NOTICE_PENDING
                    dao.insert(AppSetting(obsoleteFloatingNoticeKey, "false"))
                    dao.insert(
                        AppSetting(
                            SettingsKeys.POPUP_CONTROL_UPGRADE_REQUIRED,
                            (setupComplete && !intentionallyDisabled).toString()
                        )
                    )
                    dao.insert(AppSetting(SettingsKeys.POPUP_CONTROL_MIGRATION_COMPLETE, "true"))
                }

                // Grant DOWNLOAD notification type if PROGRESS was previously enabled
                val isDownloadMigrated = dao.getSetting("download_type_migration_complete") == "true"
                if (!isDownloadMigrated) {
                    // 1. Global notification types migration
                    val globalTypesStr = dao.getSetting(GLOBAL_NOTIFICATION_TYPES_KEY)
                    if (globalTypesStr != null) {
                        val globalTypes = globalTypesStr.deserializeSet()
                        if (globalTypes.contains("PROGRESS") && !globalTypes.contains("DOWNLOAD")) {
                            val newGlobalTypes = globalTypes + "DOWNLOAD"
                            dao.insert(AppSetting(GLOBAL_NOTIFICATION_TYPES_KEY, newGlobalTypes.serialize()))
                        }
                    }

                    // 2. App-specific notification types migration
                    val suffixes = listOf("_float", "_shade", "_timeout", "_float_timeout", "_remove_notif", "_blocked", "_nav_left", "_nav_right", "_use_native")
                    val allSettings = dao.getAllSync()
                    allSettings.forEach { setting ->
                        val key = setting.key
                        if (key.startsWith("config_") && suffixes.none { key.endsWith(it) }) {
                            val types = setting.value.deserializeSet()
                            if (types.contains("PROGRESS") && !types.contains("DOWNLOAD")) {
                                val newTypes = types + "DOWNLOAD"
                                dao.insert(AppSetting(key, newTypes.serialize()))
                            }
                        }
                    }

                    dao.insert(AppSetting("download_type_migration_complete", "true"))
                }

                // Grant DOWNLOAD and MESSAGE to all active apps and globally
                val isDownloadMessageMigrated = dao.getSetting("download_message_migration_complete") == "true"
                if (!isDownloadMessageMigrated) {
                    // 1. Global notification types migration
                    val globalTypesStr = dao.getSetting(GLOBAL_NOTIFICATION_TYPES_KEY)
                    if (globalTypesStr != null) {
                        val globalTypes = globalTypesStr.deserializeSet()
                        val newGlobalTypes = globalTypes + "DOWNLOAD" + "MESSAGE"
                        dao.insert(AppSetting(GLOBAL_NOTIFICATION_TYPES_KEY, newGlobalTypes.serialize()))
                    }

                    // 2. Active apps migration
                    val allowedPackagesStr = dao.getSetting(SettingsKeys.ALLOWED_PACKAGES)
                    val allowedPackages = allowedPackagesStr.deserializeSet()
                    
                    allowedPackages.forEach { packageName ->
                        val key = "config_$packageName"
                        val configStr = dao.getSetting(key)
                        if (configStr != null) {
                            val types = configStr.deserializeSet()
                            val newTypes = types + "DOWNLOAD" + "MESSAGE"
                            dao.insert(AppSetting(key, newTypes.serialize()))
                        }
                    }

                    dao.insert(AppSetting("download_message_migration_complete", "true"))
                }

                if (dao.getSetting("voice_message_type_migration_complete") != "true") {
                    val globalTypesStr = dao.getSetting(GLOBAL_NOTIFICATION_TYPES_KEY)
                    if (globalTypesStr != null) {
                        val globalTypes = globalTypesStr.deserializeSet()
                        if (!globalTypes.contains("VOICE_MESSAGE")) {
                            dao.insert(AppSetting(GLOBAL_NOTIFICATION_TYPES_KEY, (globalTypes + "VOICE_MESSAGE").serialize()))
                        }
                    }
                    val suffixes = listOf(
                        "_float", "_shade", "_timeout", "_float_timeout", "_remove_notif",
                        "_blocked", "_nav_left", "_nav_right", "_use_native",
                        "_call_stages", "_call_focus_replacement", "_voice_focus_replacement",
                        "_first_float", "_float_on_update",
                    )
                    dao.getAllSync().forEach { setting ->
                        val key = setting.key
                        if (!key.startsWith("config_") || suffixes.any { key.endsWith(it) }) return@forEach
                        val types = setting.value.deserializeSet()
                        if (types.any { it == "MESSAGE" || it == "PROGRESS" || it == "STANDARD" } &&
                            !types.contains("VOICE_MESSAGE")
                        ) {
                            dao.insert(AppSetting(key, (types + "VOICE_MESSAGE").serialize()))
                        }
                    }
                    dao.insert(AppSetting("voice_message_type_migration_complete", "true"))
                }

                if (dao.getSetting(SettingsKeys.ISLAND_CONFIG_V2_MIGRATED) != "true") {
                    dao.getSetting(SettingsKeys.GLOBAL_FLOAT)?.let { old ->
                        val migrated = FloatConfigMigration.fromLegacy(old.toBooleanStrictOrNull())
                        migrated.first?.let { dao.insert(AppSetting(SettingsKeys.GLOBAL_FIRST_FLOAT, it.toString())) }
                        migrated.second?.let { dao.insert(AppSetting(SettingsKeys.GLOBAL_FLOAT_ON_UPDATE, it.toString())) }
                    }
                    dao.getAllSync().filter { it.key.startsWith("config_") && it.key.endsWith("_float") }
                        .forEach { old ->
                            val base = old.key.removeSuffix("_float")
                            val migrated = FloatConfigMigration.fromLegacy(old.value.toBooleanStrictOrNull())
                            migrated.first?.let { dao.insert(AppSetting("${base}_first_float", it.toString())) }
                            migrated.second?.let { dao.insert(AppSetting("${base}_float_on_update", it.toString())) }
                        }
                    dao.insert(AppSetting(SettingsKeys.ISLAND_CONFIG_V2_MIGRATED, "true"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

    // --- HELPERS ---
    private fun String?.toBoolean(default: Boolean = false): Boolean = this?.toBooleanStrictOrNull() ?: default
    private fun String?.toInt(default: Int = 0): Int = this?.toIntOrNull() ?: default
    private fun String?.toLong(default: Long = 0L): Long = this?.toLongOrNull() ?: default

    private fun Set<String>.serialize(): String = this.joinToString(",")
    private fun String?.deserializeSet(): Set<String> = this?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    private fun String?.deserializeList(): List<String> = this?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    private fun String?.deserializeCallStages(default: Set<CallStage>): Set<CallStage> {
        if (this == null) return default
        return deserializeSet().mapNotNull { value ->
            try { CallStage.valueOf(value) } catch (_: IllegalArgumentException) { null }
        }.toSet()
    }

    private suspend fun save(key: String, value: String) {
        memoryCache[key] = value
        dao.insert(AppSetting(key, value))
    }

    private suspend fun remove(key: String) {
        memoryCache.remove(key)
        dao.delete(key)
    }

    // --- CORE SETTINGS ---
    val allowedPackagesFlow: Flow<Set<String>> = dao.getSettingFlow(SettingsKeys.ALLOWED_PACKAGES).map { value ->
        value?.deserializeSet() ?: setOf(DEFAULT_SCREEN_RECORDER_PACKAGE)
    }
    val vpnIslandEnabledFlow: Flow<Boolean> = dao.getSettingFlow("vpn_island_enabled").map { it.toBoolean(true) }
    val isSetupComplete: Flow<Boolean> = dao.getSettingFlow(SettingsKeys.SETUP_COMPLETE).map { it.toBoolean(false) }
    val lastSeenVersion: Flow<Int> = dao.getSettingFlow(SettingsKeys.LAST_VERSION).map { it.toInt(0) }
    val popupControlEnabledFlow: Flow<Boolean> =
        dao.getSettingFlow(SettingsKeys.POPUP_CONTROL_ENABLED).map { it.toBoolean(false) }
    val popupControlUpgradeRequiredFlow: Flow<Boolean> =
        dao.getSettingFlow(SettingsKeys.POPUP_CONTROL_UPGRADE_REQUIRED).map { it.toBoolean(false) }
    val popupControlIntentionallyDisabledFlow: Flow<Boolean> =
        dao.getSettingFlow(SettingsKeys.POPUP_CONTROL_INTENTIONALLY_DISABLED).map { it.toBoolean(false) }

    suspend fun setSetupComplete(isComplete: Boolean) = save(SettingsKeys.SETUP_COMPLETE, isComplete.toString())
    suspend fun setLastSeenVersion(versionCode: Int) = save(SettingsKeys.LAST_VERSION, versionCode.toString())
    suspend fun setVpnIslandEnabled(enabled: Boolean) = save("vpn_island_enabled", enabled.toString())
    suspend fun setPriorityEduShown(shown: Boolean) = save(SettingsKeys.PRIORITY_EDU, shown.toString())

    suspend fun setPopupControlReady() {
        save(SettingsKeys.POPUP_CONTROL_ENABLED, "true")
        save(SettingsKeys.POPUP_CONTROL_UPGRADE_REQUIRED, "false")
        save(SettingsKeys.POPUP_CONTROL_INTENTIONALLY_DISABLED, "false")
        save(SettingsKeys.POPUP_CONTROL_OPT_IN_REQUESTED, "false")
    }

    suspend fun beginPopupControlSetup() {
        save(SettingsKeys.POPUP_CONTROL_OPT_IN_REQUESTED, "true")
        save(SettingsKeys.POPUP_CONTROL_INTENTIONALLY_DISABLED, "false")
    }

    suspend fun setPopupControlDisabledByUser() {
        save(SettingsKeys.POPUP_CONTROL_ENABLED, "false")
        save(SettingsKeys.POPUP_CONTROL_UPGRADE_REQUIRED, "false")
        save(SettingsKeys.POPUP_CONTROL_INTENTIONALLY_DISABLED, "true")
        save(SettingsKeys.POPUP_CONTROL_OPT_IN_REQUESTED, "false")
    }

    suspend fun setPopupControlNeedsRepair() {
        save(SettingsKeys.POPUP_CONTROL_ENABLED, "false")
        if (!popupControlIntentionallyDisabledSync()) {
            save(SettingsKeys.POPUP_CONTROL_UPGRADE_REQUIRED, "true")
        }
    }

    suspend fun acknowledgePopupControlUnsupported() {
        save(SettingsKeys.POPUP_CONTROL_ENABLED, "false")
        save(SettingsKeys.POPUP_CONTROL_UPGRADE_REQUIRED, "false")
        save(SettingsKeys.POPUP_CONTROL_OPT_IN_REQUESTED, "false")
    }

    fun popupControlEnabledSync(): Boolean =
        memoryCache[SettingsKeys.POPUP_CONTROL_ENABLED].toBoolean(false)

    suspend fun popupControlEnabled(): Boolean =
        dao.getSetting(SettingsKeys.POPUP_CONTROL_ENABLED).toBoolean(false)

    fun popupControlIntentionallyDisabledSync(): Boolean =
        memoryCache[SettingsKeys.POPUP_CONTROL_INTENTIONALLY_DISABLED].toBoolean(false)

    fun popupControlOptInRequestedSync(): Boolean =
        memoryCache[SettingsKeys.POPUP_CONTROL_OPT_IN_REQUESTED].toBoolean(false)

    fun popupSemanticRulesFingerprintSync(): String? =
        memoryCache[SettingsKeys.POPUP_SEMANTIC_RULES_FINGERPRINT]

    suspend fun setPopupSemanticRulesFingerprint(value: String) =
        save(SettingsKeys.POPUP_SEMANTIC_RULES_FINGERPRINT, value)

    val featuredPermissionWarningFlow: Flow<Boolean> = dao.getSettingFlow(SettingsKeys.FEATURED_PERMISSION_WARNING).map { it.toBoolean(false) }
    suspend fun setFeaturedPermissionWarning(show: Boolean) = save(SettingsKeys.FEATURED_PERMISSION_WARNING, show.toString())

    suspend fun toggleApp(packageName: String, isEnabled: Boolean) {
        val currentString = dao.getSetting(SettingsKeys.ALLOWED_PACKAGES)
        val currentSet = currentString?.deserializeSet() ?: setOf(DEFAULT_SCREEN_RECORDER_PACKAGE)
        val newSet = if (isEnabled) currentSet + packageName else currentSet - packageName
        save(SettingsKeys.ALLOWED_PACKAGES, newSet.serialize())
    }

    // --- LIMITS & PRIORITY ---
    val limitModeFlow: Flow<IslandLimitMode> = dao.getSettingFlow("limit_mode").map {
        try { IslandLimitMode.valueOf(it ?: IslandLimitMode.MOST_RECENT.name) } catch(_: Exception) { IslandLimitMode.MOST_RECENT }
    }
    val appPriorityListFlow: Flow<List<String>> = dao.getSettingFlow(SettingsKeys.PRIORITY_ORDER).map { it.deserializeList() }

    suspend fun setLimitMode(mode: IslandLimitMode) = save("limit_mode", mode.name)
    suspend fun setAppPriorityOrder(order: List<String>) = save(SettingsKeys.PRIORITY_ORDER, order.joinToString(","))

    // --- NOTIFICATION TYPES ---
    fun getAppConfig(packageName: String): Flow<Set<String>> {
        val legacyKey = "config_$packageName"
        return dao.getSettingFlow(legacyKey).map { str ->
            str?.deserializeSet() ?: NotificationType.configurableEntries.map { t -> t.name }.toSet()
        }
    }

    // --- ISLAND CONFIG (Standard Notifications) ---
    private fun sanitizeTimeout(raw: Long?): Long {
        val value = raw ?: 5L
        return if (value > 60) value / 1000 else value
    }

    val globalConfigFlow: Flow<IslandConfig> = combine(
        dao.getSettingFlow(SettingsKeys.GLOBAL_FIRST_FLOAT),
        dao.getSettingFlow(SettingsKeys.GLOBAL_SHADE),
        dao.getSettingFlow(SettingsKeys.GLOBAL_TIMEOUT),
        dao.getSettingFlow(SettingsKeys.GLOBAL_FLOAT_TIMEOUT),
        dao.getSettingFlow(SettingsKeys.GLOBAL_REMOVE_NOTIF),
        dao.getSettingFlow(SettingsKeys.GLOBAL_DISMISS_WITH_ORIGINAL),
        dao.getSettingFlow(SettingsKeys.GLOBAL_ENABLE_INLINE_REPLY),
        dao.getSettingFlow(SettingsKeys.GLOBAL_FLOAT_ON_UPDATE),
        dao.getSettingFlow(SettingsKeys.GLOBAL_MARQUEE),
        dao.getSettingFlow(SettingsKeys.GLOBAL_MARQUEE_DISMISS),
        dao.getSettingFlow(SettingsKeys.GLOBAL_LEFT_CONTENT),
        dao.getSettingFlow(SettingsKeys.GLOBAL_RIGHT_CONTENT),
        dao.getSettingFlow(SettingsKeys.GLOBAL_LEFT_EXPRESSION),
        dao.getSettingFlow(SettingsKeys.GLOBAL_RIGHT_EXPRESSION),
        dao.getSettingFlow(SettingsKeys.GLOBAL_ISLAND_GLOW),
        dao.getSettingFlow(SettingsKeys.GLOBAL_FOCUS_GLOW),
        dao.getSettingFlow(SettingsKeys.GLOBAL_ISLAND_GLOW_COLOR),
        dao.getSettingFlow(SettingsKeys.GLOBAL_FOCUS_GLOW_COLOR),
        dao.getSettingFlow(SettingsKeys.GLOBAL_FORCE_ISLAND_GLOW),
        dao.getSettingFlow(SettingsKeys.GLOBAL_FORCE_FOCUS_GLOW),
        dao.getSettingFlow(SettingsKeys.GLOBAL_CONTACT_PINK_GLOW),
        dao.getSettingFlow(SettingsKeys.GLOBAL_RESTORE_LOCKSCREEN),
        dao.getSettingFlow(SettingsKeys.GLOBAL_DND_BEHAVIOR),
        dao.getSettingFlow(SettingsKeys.GLOBAL_FULLSCREEN_BEHAVIOR),
        dao.getSettingFlow(SettingsKeys.GLOBAL_LANDSCAPE_BEHAVIOR),
    ) { args: Array<String?> ->
        IslandConfig(
            firstFloat = args[0]?.toBooleanStrictOrNull() ?: memoryCache[SettingsKeys.GLOBAL_FLOAT].toBoolean(true),
            isShowShade = args[1].toBoolean(false), timeout = args[2]?.toIntOrNull(),
            floatTimeout = args[3]?.toIntOrNull(), removeOriginalNotification = args[4]?.toBooleanStrictOrNull(),
            dismissWithOriginal = args[5]?.toBooleanStrictOrNull() ?: true,
            enableInlineReply = args[6]?.toBooleanStrictOrNull(), floatOnUpdate = args[7].toBoolean(false),
            marqueeEnabled = args[8].toBoolean(false), marqueeDismissMode = MarqueeDismissMode.parse(args[9]),
            leftContent = args[10]?.let { runCatching { IslandTextContent.valueOf(it) }.getOrNull() } ?: IslandTextContent.AUTOMATIC,
            rightContent = args[11]?.let { runCatching { IslandTextContent.valueOf(it) }.getOrNull() } ?: IslandTextContent.AUTOMATIC,
            leftCustomExpression = args[12], rightCustomExpression = args[13],
            islandGlowMode = GlowMode.parse(args[14]) ?: GlowMode.OFF, focusGlowMode = GlowMode.parse(args[15]) ?: GlowMode.OFF,
            islandGlowColor = args[16], focusGlowColor = args[17],
            forceIslandGlow = args[18].toBoolean(false), forceFocusGlow = args[19].toBoolean(false),
            contactPinkGlow = args[20].toBoolean(false),
            restoreLockscreen = args[21].toBoolean(false),
            dndBehavior = args[22]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() } ?: IslandSceneBehavior.SUPPRESS,
            fullscreenBehavior = args[23]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() } ?: IslandSceneBehavior.DEFAULT,
            landscapeBehavior = args[24]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() } ?: IslandSceneBehavior.DEFAULT,
        )
    }

    suspend fun updateGlobalConfig(config: IslandConfig) {
        config.firstFloat?.let { save(SettingsKeys.GLOBAL_FIRST_FLOAT, it.toString()) }
        config.floatOnUpdate?.let { save(SettingsKeys.GLOBAL_FLOAT_ON_UPDATE, it.toString()) }
        config.isShowShade?.let { save(SettingsKeys.GLOBAL_SHADE, it.toString()) }
        config.timeout?.let { save(SettingsKeys.GLOBAL_TIMEOUT, it.toString()) }
        config.floatTimeout?.let { save(SettingsKeys.GLOBAL_FLOAT_TIMEOUT, it.toString()) }
        config.removeOriginalNotification?.let { save(SettingsKeys.GLOBAL_REMOVE_NOTIF, it.toString()) }
        config.dismissWithOriginal?.let { save(SettingsKeys.GLOBAL_DISMISS_WITH_ORIGINAL, it.toString()) }
        config.enableInlineReply?.let { save(SettingsKeys.GLOBAL_ENABLE_INLINE_REPLY, it.toString()) }
        config.marqueeEnabled?.let { save(SettingsKeys.GLOBAL_MARQUEE, it.toString()) }
        config.marqueeDismissMode?.let { save(SettingsKeys.GLOBAL_MARQUEE_DISMISS, it.name) }
        config.leftContent?.let { save(SettingsKeys.GLOBAL_LEFT_CONTENT, it.name) }
        config.rightContent?.let { save(SettingsKeys.GLOBAL_RIGHT_CONTENT, it.name) }
        config.leftCustomExpression?.let { save(SettingsKeys.GLOBAL_LEFT_EXPRESSION, it) }
        config.rightCustomExpression?.let { save(SettingsKeys.GLOBAL_RIGHT_EXPRESSION, it) }
        config.islandGlowMode?.let { save(SettingsKeys.GLOBAL_ISLAND_GLOW, it.name) }
        config.focusGlowMode?.let { save(SettingsKeys.GLOBAL_FOCUS_GLOW, it.name) }
        config.islandGlowColor?.let { save(SettingsKeys.GLOBAL_ISLAND_GLOW_COLOR, it) }
        config.focusGlowColor?.let { save(SettingsKeys.GLOBAL_FOCUS_GLOW_COLOR, it) }
        config.forceIslandGlow?.let { save(SettingsKeys.GLOBAL_FORCE_ISLAND_GLOW, it.toString()) }
        config.forceFocusGlow?.let { save(SettingsKeys.GLOBAL_FORCE_FOCUS_GLOW, it.toString()) }
        config.contactPinkGlow?.let { save(SettingsKeys.GLOBAL_CONTACT_PINK_GLOW, it.toString()) }
        config.restoreLockscreen?.let { save(SettingsKeys.GLOBAL_RESTORE_LOCKSCREEN, it.toString()) }
        config.dndBehavior?.let { save(SettingsKeys.GLOBAL_DND_BEHAVIOR, it.name) }
        config.fullscreenBehavior?.let { save(SettingsKeys.GLOBAL_FULLSCREEN_BEHAVIOR, it.name) }
        config.landscapeBehavior?.let { save(SettingsKeys.GLOBAL_LANDSCAPE_BEHAVIOR, it.name) }
    }

    fun getAppIslandConfig(packageName: String): Flow<IslandConfig> {
        val keys = appIslandConfigKeys(packageName)
        return combine(*keys.map { dao.getSettingFlow(it) }.toTypedArray()) { args -> parseAppIslandConfig(args) }
    }

    suspend fun updateAppIslandConfig(packageName: String, config: IslandConfig) {
        val values = listOf(
            config.firstFloat, config.isShowShade, config.timeout, config.floatTimeout,
            config.removeOriginalNotification, config.dismissWithOriginal, config.enableInlineReply,
            config.floatOnUpdate, config.marqueeEnabled, config.marqueeDismissMode?.name,
            config.leftContent?.name, config.rightContent?.name, config.leftCustomExpression,
            config.rightCustomExpression, config.islandGlowMode?.name, config.focusGlowMode?.name,
            config.islandGlowColor, config.focusGlowColor, config.forceIslandGlow, config.forceFocusGlow,
            config.contactPinkGlow, config.restoreLockscreen, config.dndBehavior?.name, config.fullscreenBehavior?.name,
            config.landscapeBehavior?.name,
        )
        appIslandConfigKeys(packageName).zip(values).forEach { (key, value) ->
            if (value == null) remove(key) else save(key, value.toString())
        }
    }

    private fun appIslandConfigKeys(packageName: String): List<String> {
        val base = "config_${packageName}_"
        return listOf(
            "first_float", "shade", "timeout", "float_timeout", "remove_notif",
            "dismiss_with_original", "enable_inline_reply", "float_on_update", "marquee",
            "marquee_dismiss", "left_content", "right_content", "left_expression", "right_expression",
            "island_glow", "focus_glow", "island_glow_color", "focus_glow_color",
            "force_island_glow", "force_focus_glow", "contact_pink_glow", "restore_lockscreen", "dnd_behavior",
            "fullscreen_behavior", "landscape_behavior",
        ).map { base + it }
    }

    private fun parseAppIslandConfig(args: Array<String?>): IslandConfig = IslandConfig(
        firstFloat = args[0]?.toBooleanStrictOrNull(), isShowShade = args[1]?.toBooleanStrictOrNull(),
        timeout = args[2]?.toIntOrNull(), floatTimeout = args[3]?.toIntOrNull(),
        removeOriginalNotification = args[4]?.toBooleanStrictOrNull(),
        dismissWithOriginal = args[5]?.toBooleanStrictOrNull(), enableInlineReply = args[6]?.toBooleanStrictOrNull(),
        floatOnUpdate = args[7]?.toBooleanStrictOrNull(), marqueeEnabled = args[8]?.toBooleanStrictOrNull(),
        marqueeDismissMode = args[9]?.let(MarqueeDismissMode::parse),
        leftContent = args[10]?.let { runCatching { IslandTextContent.valueOf(it) }.getOrNull() },
        rightContent = args[11]?.let { runCatching { IslandTextContent.valueOf(it) }.getOrNull() },
        leftCustomExpression = args[12], rightCustomExpression = args[13],
        islandGlowMode = GlowMode.parse(args[14]), focusGlowMode = GlowMode.parse(args[15]),
        islandGlowColor = args[16], focusGlowColor = args[17],
        forceIslandGlow = args[18]?.toBooleanStrictOrNull(), forceFocusGlow = args[19]?.toBooleanStrictOrNull(),
        contactPinkGlow = args[20]?.toBooleanStrictOrNull(),
        restoreLockscreen = args[21]?.toBooleanStrictOrNull(),
        dndBehavior = args[22]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() },
        fullscreenBehavior = args[23]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() },
        landscapeBehavior = args[24]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() },
    )

    // --- SYSTEM ISLAND: SCREEN RECORDING ---
    val screenRecordingTimeoutFlow: Flow<Int> =
        dao.getSettingFlow(SettingsKeys.SCREEN_RECORDING_TIMEOUT).map { it.toInt(SYSTEM_ISLAND_DEFAULT_TIMEOUT) }

    suspend fun setScreenRecordingTimeout(seconds: Int) =
        save(SettingsKeys.SCREEN_RECORDING_TIMEOUT, seconds.toString())

    val screenRecordingLeftDesignFlow: Flow<com.sykeptical.hyperpop.models.ScreenRecordingLeftDesign> =
        dao.getSettingFlow(SettingsKeys.SCREEN_RECORDING_LEFT_DESIGN).map { value ->
            value?.let { runCatching { com.sykeptical.hyperpop.models.ScreenRecordingLeftDesign.valueOf(it) }.getOrNull() }
                ?: com.sykeptical.hyperpop.models.ScreenRecordingLeftDesign.ICON_AND_TEXT
        }

    val screenRecordingRightDesignFlow: Flow<com.sykeptical.hyperpop.models.ScreenRecordingRightDesign> =
        dao.getSettingFlow(SettingsKeys.SCREEN_RECORDING_RIGHT_DESIGN).map { value ->
            value?.let { runCatching { com.sykeptical.hyperpop.models.ScreenRecordingRightDesign.valueOf(it) }.getOrNull() }
                ?: com.sykeptical.hyperpop.models.ScreenRecordingRightDesign.TIMER
        }

    val screenRecordingDesignFlow: Flow<com.sykeptical.hyperpop.models.ScreenRecordingDesignConfig> =
        combine(screenRecordingLeftDesignFlow, screenRecordingRightDesignFlow) { left, right ->
            com.sykeptical.hyperpop.models.ScreenRecordingDesignConfig(left = left, right = right)
        }

    suspend fun setScreenRecordingLeftDesign(design: com.sykeptical.hyperpop.models.ScreenRecordingLeftDesign) =
        save(SettingsKeys.SCREEN_RECORDING_LEFT_DESIGN, design.name)

    suspend fun setScreenRecordingRightDesign(design: com.sykeptical.hyperpop.models.ScreenRecordingRightDesign) =
        save(SettingsKeys.SCREEN_RECORDING_RIGHT_DESIGN, design.name)

    val screenRecordingReplaceFloatingFlow: Flow<Boolean> =
        dao.getSettingFlow(SettingsKeys.SCREEN_RECORDING_REPLACE_FLOATING).map { it.toBoolean(true) }

    suspend fun setScreenRecordingReplaceFloating(enabled: Boolean) =
        save(SettingsKeys.SCREEN_RECORDING_REPLACE_FLOATING, enabled.toString())

    val screenRecordingImmediateStartFlow: Flow<Boolean> =
        dao.getSettingFlow(SettingsKeys.SCREEN_RECORDING_IMMEDIATE_START).map { it.toBoolean(true) }

    suspend fun setScreenRecordingImmediateStart(enabled: Boolean) =
        save(SettingsKeys.SCREEN_RECORDING_IMMEDIATE_START, enabled.toString())

    val screenRecordingCountdownEnabledFlow: Flow<Boolean> =
        dao.getSettingFlow(SettingsKeys.SCREEN_RECORDING_COUNTDOWN_ENABLED).map { it.toBoolean(true) }

    suspend fun setScreenRecordingCountdownEnabled(enabled: Boolean) =
        save(SettingsKeys.SCREEN_RECORDING_COUNTDOWN_ENABLED, enabled.toString())

    val screenRecordingIconStyleFlow: Flow<String> =
        dao.getSettingFlow(SettingsKeys.SCREEN_RECORDING_ICON_STYLE).map {
            it?.takeIf(String::isNotBlank) ?: "screen_recorder"
        }

    suspend fun setScreenRecordingIconStyle(style: String) =
        save(SettingsKeys.SCREEN_RECORDING_ICON_STYLE, style)

    // --- NAVIGATION ---
    val globalBlockedTermsFlow: Flow<Set<String>> = dao.getSettingFlow(SettingsKeys.GLOBAL_BLOCKED_TERMS).map { it.deserializeSet() }
    suspend fun setGlobalBlockedTerms(terms: Set<String>) = save(SettingsKeys.GLOBAL_BLOCKED_TERMS, terms.serialize())

    fun getAppBlockedTerms(packageName: String): Flow<Set<String>> {
        return dao.getSettingFlow("config_${packageName}_blocked").map { it.deserializeSet() }
    }
    suspend fun setAppBlockedTerms(packageName: String, terms: Set<String>) {
        save("config_${packageName}_blocked", terms.serialize())
    }

    val globalNavLayoutFlow: Flow<Pair<NavContent, NavContent>> = combine(
        dao.getSettingFlow(SettingsKeys.NAV_LEFT),
        dao.getSettingFlow(SettingsKeys.NAV_RIGHT)
    ) { l, r ->
        val left = try { NavContent.valueOf(l ?: NavContent.DISTANCE_ETA.name) } catch (_: Exception) { NavContent.DISTANCE_ETA }
        val right = try { NavContent.valueOf(r ?: NavContent.INSTRUCTION.name) } catch (_: Exception) { NavContent.INSTRUCTION }
        left to right
    }

    suspend fun setGlobalNavLayout(left: NavContent, right: NavContent) {
        save(SettingsKeys.NAV_LEFT, left.name)
        save(SettingsKeys.NAV_RIGHT, right.name)
    }

    fun getAppNavLayout(packageName: String): Flow<Pair<NavContent?, NavContent?>> {
        return combine(
            dao.getSettingFlow("config_${packageName}_nav_left"),
            dao.getSettingFlow("config_${packageName}_nav_right")
        ) { l, r ->
            val left = l?.let { try { NavContent.valueOf(it) } catch(_: Exception){null} }
            val right = r?.let { try { NavContent.valueOf(it) } catch(_: Exception){null} }
            left to right
        }
    }

    fun getEffectiveNavLayout(packageName: String): Flow<Pair<NavContent, NavContent>> {
        return combine(
            dao.getSettingFlow("config_${packageName}_nav_left"),
            dao.getSettingFlow("config_${packageName}_nav_right"),
            globalNavLayoutFlow
        ) { appL, appR, global ->
            val left = appL?.let { try { NavContent.valueOf(it) } catch(_: Exception){null} } ?: global.first
            val right = appR?.let { try { NavContent.valueOf(it) } catch(_: Exception){null} } ?: global.second
            left to right
        }
    }

    suspend fun updateAppNavLayout(packageName: String, left: NavContent?, right: NavContent?) {
        val lKey = "config_${packageName}_nav_left"
        val rKey = "config_${packageName}_nav_right"
        if (left != null) save(lKey, left.name) else remove(lKey)
        if (right != null) save(rKey, right.name) else remove(rKey)
    }

    // ========================================================================
    //                        Global Notification Types
    // ========================================================================

    val GLOBAL_NOTIFICATION_TYPES_KEY = "global_notification_types"

    val globalNotificationTypesFlow: Flow<Set<String>> = dao.getSettingFlow(GLOBAL_NOTIFICATION_TYPES_KEY).map { str ->
        str?.deserializeSet() ?: NotificationType.configurableEntries.map { it.name }.toSet()
    }

    val notificationTypePolicyFlow: Flow<NotificationTypePreferenceSnapshot> = dao.getAllFlow()
        .map { settings ->
            val values = settings.associate { it.key to it.value }
            val selectedPackages = values[SettingsKeys.ALLOWED_PACKAGES].deserializeSet()
            val appOverrides = selectedPackages.mapNotNull { packageName ->
                values["config_$packageName"]?.let { packageName to it.deserializeSet() }
            }.toMap()
            NotificationTypePreferenceSnapshot(
                globalTypes = values[GLOBAL_NOTIFICATION_TYPES_KEY]?.deserializeSet()
                    ?: NotificationType.configurableEntries.map { it.name }.toSet(),
                appOverrides = appOverrides
            )
        }
        .distinctUntilChanged()

    val callStagePolicyFlow: Flow<CallStagePreferenceSnapshot> = dao.getAllFlow()
        .map { settings ->
            val values = settings.associate { it.key to it.value }
            val global = values[GLOBAL_CALL_STAGES_KEY]?.deserializeSet()
                ?: CallStage.entries.map { it.name }.toSet()
            val overrides = values.mapNotNull { (key, value) ->
                if (!key.startsWith("config_") || !key.endsWith("_call_stages")) return@mapNotNull null
                val packageName = key.removePrefix("config_").removeSuffix("_call_stages")
                if (packageName.isEmpty()) null else packageName to value.deserializeSet()
            }.toMap()
            CallStagePreferenceSnapshot(global, overrides)
        }
        .distinctUntilChanged()

    suspend fun updateGlobalNotificationType(type: NotificationType, isEnabled: Boolean) {
        val currentStr = dao.getSetting(GLOBAL_NOTIFICATION_TYPES_KEY)
        val currentSet = currentStr?.deserializeSet() ?: NotificationType.configurableEntries.map { it.name }.toSet()
        val newSet = if (isEnabled) currentSet + type.name else currentSet - type.name
        save(GLOBAL_NOTIFICATION_TYPES_KEY, newSet.serialize())
    }

    // --- APP-SPECIFIC NOTIFICATION TYPES ---

    fun getAppConfigFlow(packageName: String): Flow<Set<String>?> {
        val legacyKey = "config_$packageName"
        return dao.getSettingFlow(legacyKey).map { str ->
            str?.deserializeSet()
        }
    }

    suspend fun updateAppConfig(packageName: String, type: NotificationType, isEnabled: Boolean) {
        val key = "config_$packageName"
        val currentStr = dao.getSetting(key)
        val currentSet = currentStr?.deserializeSet() ?: NotificationType.configurableEntries.map { it.name }.toSet()
        val newSet = if (isEnabled) currentSet + type.name else currentSet - type.name
        save(key, newSet.serialize())
    }

    // ========================================================================
    //                        Call Stages Configuration
    // ========================================================================

    val GLOBAL_CALL_STAGES_KEY = "global_call_stages"
    val GLOBAL_VOICE_COMPACT_DURATION_KEY = "global_voice_compact_duration"

    val globalVoiceCompactDurationFlow: Flow<Boolean> =
        dao.getSettingFlow(GLOBAL_VOICE_COMPACT_DURATION_KEY).map { it.toBoolean(false) }

    suspend fun setGlobalVoiceCompactDuration(enabled: Boolean) {
        save(GLOBAL_VOICE_COMPACT_DURATION_KEY, enabled.toString())
    }

    fun getAppVoiceCompactDurationFlow(packageName: String): Flow<Boolean?> {
        return dao.getSettingFlow("config_${packageName}_voice_compact_duration")
            .map { it?.toBooleanStrictOrNull() }
    }

    suspend fun setAppVoiceCompactDuration(packageName: String, enabled: Boolean) {
        save("config_${packageName}_voice_compact_duration", enabled.toString())
    }

    fun getEffectiveVoiceCompactDurationSync(packageName: String): Boolean {
        val appValue = memoryCache["config_${packageName}_voice_compact_duration"]?.toBooleanStrictOrNull()
        if (appValue != null) return appValue
        return memoryCache[GLOBAL_VOICE_COMPACT_DURATION_KEY].toBoolean(false)
    }

    val globalCallStagesFlow: Flow<Set<CallStage>> = dao.getSettingFlow(GLOBAL_CALL_STAGES_KEY).map { raw ->
        raw.deserializeCallStages(CallStage.entries.toSet())
    }

    suspend fun updateGlobalCallStage(stage: CallStage, isEnabled: Boolean) {
        val current = dao.getSetting(GLOBAL_CALL_STAGES_KEY)
            .deserializeCallStages(CallStage.entries.toSet())
        val updated = if (isEnabled) current + stage else current - stage
        save(GLOBAL_CALL_STAGES_KEY, updated.map { it.name }.toSet().serialize())
    }

    fun getAppCallStagesFlow(packageName: String): Flow<Set<CallStage>?> {
        return dao.getSettingFlow("config_${packageName}_call_stages").map { raw ->
            raw?.deserializeCallStages(emptySet())
        }
    }

    suspend fun updateAppCallStage(packageName: String, stage: CallStage, isEnabled: Boolean) {
        val key = "config_${packageName}_call_stages"
        val appValue = dao.getSetting(key)
        val inherited = dao.getSetting(GLOBAL_CALL_STAGES_KEY)
            .deserializeCallStages(CallStage.entries.toSet())
        val current = appValue.deserializeCallStages(inherited)
        val updated = if (isEnabled) current + stage else current - stage
        save(key, updated.map { it.name }.toSet().serialize())
    }

    // ========================================================================
    //                        LIVE UPDATE CONFIGURATION
    // ========================================================================

    private val USE_NATIVE_ENGINE = "use_native_live_updates"

    val useNativeLiveUpdates: Flow<Boolean> = dao.getSettingFlow(USE_NATIVE_ENGINE)
        .map { it?.toBoolean() ?: false }

    suspend fun setUseNativeLiveUpdates(value: Boolean) {
        save(USE_NATIVE_ENGINE, value.toString())
    }

    // ========================================================================
    //                        DND / GAME MODE CONFIGURATION
    // ========================================================================

    val isDndModeEnabledFlow: Flow<Boolean> = dao.getSettingFlow("dnd_mode_enabled").map { it.toBoolean(false) }
    suspend fun setDndModeEnabled(isEnabled: Boolean) = save("dnd_mode_enabled", isEnabled.toString())

    val autoDetectDndFlow: Flow<Boolean> = dao.getSettingFlow("auto_detect_dnd").map { it.toBoolean(false) }
    suspend fun setAutoDetectDnd(autoDetect: Boolean) = save("auto_detect_dnd", autoDetect.toString())

    // ========================================================================
    //                        LOGIN CODE EXTRACTOR
    // ========================================================================

    private val loginCodeKeys = listOf(
        SettingsKeys.LOGIN_CODE_ENABLED,
        SettingsKeys.LOGIN_CODE_COPY_ACTION,
        SettingsKeys.LOGIN_CODE_DISMISS_AFTER_COPY,
        SettingsKeys.LOGIN_CODE_GLOW,
        SettingsKeys.LOGIN_CODE_COMPACT,
        SettingsKeys.LOGIN_CODE_PACKAGES,
    )

    val loginCodeSettingsFlow: Flow<LoginCodeSettings> = combine(
        loginCodeKeys.map(dao::getSettingFlow)
    ) { values -> parseLoginCodeSettings(values.asList()) }.distinctUntilChanged()

    fun getLoginCodeSettingsSync(): LoginCodeSettings =
        parseLoginCodeSettings(loginCodeKeys.map { memoryCache[it] })

    suspend fun setLoginCodeEnabled(value: Boolean) = save(SettingsKeys.LOGIN_CODE_ENABLED, value.toString())
    suspend fun setLoginCodeCopyAction(value: Boolean) = save(SettingsKeys.LOGIN_CODE_COPY_ACTION, value.toString())
    suspend fun setLoginCodeDismissAfterCopy(value: Boolean) =
        save(SettingsKeys.LOGIN_CODE_DISMISS_AFTER_COPY, value.toString())
    suspend fun setLoginCodeGlow(value: Boolean) = save(SettingsKeys.LOGIN_CODE_GLOW, value.toString())
    suspend fun setLoginCodeCompact(value: Boolean) = save(SettingsKeys.LOGIN_CODE_COMPACT, value.toString())

    suspend fun setLoginCodeApp(packageName: String, enabled: Boolean) {
        val current = dao.getSetting(SettingsKeys.LOGIN_CODE_PACKAGES)
            ?.deserializeSet()
            ?: LoginCodeSettings.DEFAULT_PACKAGES
        save(SettingsKeys.LOGIN_CODE_PACKAGES, (if (enabled) current + packageName else current - packageName).serialize())
    }

    suspend fun resetLoginCodeApps() = remove(SettingsKeys.LOGIN_CODE_PACKAGES)

    /** [values] follow the order of [loginCodeKeys]. */
    private fun parseLoginCodeSettings(values: List<String?>) = LoginCodeSettings(
        enabled = values[0].toBoolean(true),
        copyAction = values[1].toBoolean(true),
        dismissAfterCopy = values[2].toBoolean(true), // TEMP-TEST
        glow = values[3].toBoolean(true),
        compactCode = values[4].toBoolean(true),
        packages = values[5]?.deserializeSet() ?: LoginCodeSettings.DEFAULT_PACKAGES,
    )

    // --- APP-SPECIFIC ENGINE OVERRIDES ---

    fun getAppEnginePreferenceFlow(packageName: String): Flow<Boolean?> {
        val key = "config_${packageName}_use_native"
        return dao.getSettingFlow(key).map { it?.toBooleanStrictOrNull() }
    }

    suspend fun updateAppEnginePreference(packageName: String, useNative: Boolean?) {
        val key = "config_${packageName}_use_native"
        if (useNative != null) {
            save(key, useNative.toString())
        } else {
            remove(key)
        }
    }

    // ========================================================================
    //                        SYNCHRONOUS CACHE GETTERS
    // ========================================================================

    fun getAppBlockedTermsSync(packageName: String): Set<String> {
        return memoryCache["config_${packageName}_blocked"].deserializeSet()
    }

    fun getAppIslandConfigSync(packageName: String): IslandConfig {
        val args = appIslandConfigKeys(packageName).map { memoryCache[it] }.toTypedArray()
        if (args[0] == null) args[0] = memoryCache["config_${packageName}_float"]
        return parseAppIslandConfig(args)
    }

    fun getGlobalConfigSync(): IslandConfig {
        return IslandConfig(
            firstFloat = memoryCache[SettingsKeys.GLOBAL_FIRST_FLOAT]?.toBooleanStrictOrNull()
                ?: memoryCache[SettingsKeys.GLOBAL_FLOAT].toBoolean(true),
            isShowShade = memoryCache[SettingsKeys.GLOBAL_SHADE].toBoolean(false),
            timeout = memoryCache[SettingsKeys.GLOBAL_TIMEOUT]?.toIntOrNull(),
            floatTimeout = memoryCache[SettingsKeys.GLOBAL_FLOAT_TIMEOUT]?.toIntOrNull(),
            removeOriginalNotification = memoryCache[SettingsKeys.GLOBAL_REMOVE_NOTIF]?.toBooleanStrictOrNull(),
            dismissWithOriginal = memoryCache[SettingsKeys.GLOBAL_DISMISS_WITH_ORIGINAL]?.toBooleanStrictOrNull() ?: true,
            enableInlineReply = memoryCache[SettingsKeys.GLOBAL_ENABLE_INLINE_REPLY]?.toBooleanStrictOrNull(),
            floatOnUpdate = memoryCache[SettingsKeys.GLOBAL_FLOAT_ON_UPDATE].toBoolean(false),
            marqueeEnabled = memoryCache[SettingsKeys.GLOBAL_MARQUEE].toBoolean(false),
            marqueeDismissMode = MarqueeDismissMode.parse(memoryCache[SettingsKeys.GLOBAL_MARQUEE_DISMISS]),
            leftContent = memoryCache[SettingsKeys.GLOBAL_LEFT_CONTENT]?.let { runCatching { IslandTextContent.valueOf(it) }.getOrNull() } ?: IslandTextContent.AUTOMATIC,
            rightContent = memoryCache[SettingsKeys.GLOBAL_RIGHT_CONTENT]?.let { runCatching { IslandTextContent.valueOf(it) }.getOrNull() } ?: IslandTextContent.AUTOMATIC,
            leftCustomExpression = memoryCache[SettingsKeys.GLOBAL_LEFT_EXPRESSION],
            rightCustomExpression = memoryCache[SettingsKeys.GLOBAL_RIGHT_EXPRESSION],
            islandGlowMode = GlowMode.parse(memoryCache[SettingsKeys.GLOBAL_ISLAND_GLOW]) ?: GlowMode.OFF,
            focusGlowMode = GlowMode.parse(memoryCache[SettingsKeys.GLOBAL_FOCUS_GLOW]) ?: GlowMode.OFF,
            islandGlowColor = memoryCache[SettingsKeys.GLOBAL_ISLAND_GLOW_COLOR],
            focusGlowColor = memoryCache[SettingsKeys.GLOBAL_FOCUS_GLOW_COLOR],
            forceIslandGlow = memoryCache[SettingsKeys.GLOBAL_FORCE_ISLAND_GLOW].toBoolean(false),
            forceFocusGlow = memoryCache[SettingsKeys.GLOBAL_FORCE_FOCUS_GLOW].toBoolean(false),
            contactPinkGlow = memoryCache[SettingsKeys.GLOBAL_CONTACT_PINK_GLOW].toBoolean(false),
            restoreLockscreen = memoryCache[SettingsKeys.GLOBAL_RESTORE_LOCKSCREEN].toBoolean(false),
            dndBehavior = memoryCache[SettingsKeys.GLOBAL_DND_BEHAVIOR]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() } ?: IslandSceneBehavior.SUPPRESS,
            fullscreenBehavior = memoryCache[SettingsKeys.GLOBAL_FULLSCREEN_BEHAVIOR]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() } ?: IslandSceneBehavior.DEFAULT,
            landscapeBehavior = memoryCache[SettingsKeys.GLOBAL_LANDSCAPE_BEHAVIOR]?.let { runCatching { IslandSceneBehavior.valueOf(it) }.getOrNull() } ?: IslandSceneBehavior.DEFAULT,
        )
    }

    fun getGlobalNavLayoutSync(): Pair<NavContent, NavContent> {
        val l = memoryCache[SettingsKeys.NAV_LEFT]
        val r = memoryCache[SettingsKeys.NAV_RIGHT]
        val left = try { NavContent.valueOf(l ?: NavContent.DISTANCE_ETA.name) } catch (_: Exception) { NavContent.DISTANCE_ETA }
        val right = try { NavContent.valueOf(r ?: NavContent.INSTRUCTION.name) } catch (_: Exception) { NavContent.INSTRUCTION }
        return left to right
    }

    fun getEffectiveNavLayoutSync(packageName: String): Pair<NavContent, NavContent> {
        val appL = memoryCache["config_${packageName}_nav_left"]
        val appR = memoryCache["config_${packageName}_nav_right"]
        val global = getGlobalNavLayoutSync()
        val left = appL?.let { try { NavContent.valueOf(it) } catch(_: Exception){null} } ?: global.first
        val right = appR?.let { try { NavContent.valueOf(it) } catch(_: Exception){null} } ?: global.second
        return left to right
    }

    fun getGlobalNotificationTypesSync(): Set<String> {
        val str = memoryCache[GLOBAL_NOTIFICATION_TYPES_KEY]
        return str?.deserializeSet() ?: NotificationType.configurableEntries.map { it.name }.toSet()
    }

    suspend fun getGlobalNotificationTypes(): Set<String> {
        val value = dao.getSetting(GLOBAL_NOTIFICATION_TYPES_KEY)
        return value?.deserializeSet() ?: NotificationType.configurableEntries.map { it.name }.toSet()
    }

    fun getAppConfigSync(packageName: String): Set<String>? {
        val str = memoryCache["config_$packageName"]
        return str?.deserializeSet()
    }

    suspend fun getAppConfigFresh(packageName: String): Set<String>? =
        dao.getSetting("config_$packageName")?.deserializeSet()

    fun getEffectiveCallStagesSync(packageName: String): Set<CallStage> {
        val appValue = memoryCache["config_${packageName}_call_stages"]
        val globalValue = memoryCache[GLOBAL_CALL_STAGES_KEY]
        return appValue.deserializeCallStages(
            globalValue.deserializeCallStages(CallStage.entries.toSet())
        )
    }

    fun getAppEnginePreferenceSync(packageName: String): Boolean? {
        return memoryCache["config_${packageName}_use_native"]?.toBooleanStrictOrNull()
    }

    fun useNativeLiveUpdatesSync(): Boolean {
        return memoryCache[USE_NATIVE_ENGINE]?.toBoolean() ?: false
    }

    fun isAppAllowedSync(packageName: String): Boolean {
        val raw = memoryCache[SettingsKeys.ALLOWED_PACKAGES] ?: return false
        return raw.deserializeSet().contains(packageName)
    }

    suspend fun isAppAllowed(packageName: String): Boolean =
        packageName in dao.getSetting(SettingsKeys.ALLOWED_PACKAGES).deserializeSet()

    fun getAppPriorityOrderSync(): List<String> {
        val raw = memoryCache[SettingsKeys.PRIORITY_ORDER]
        return raw.deserializeList()
    }

    fun getAppPriorityFast(packageName: String): Int {
        val priorityList = getAppPriorityOrderSync()
        val index = priorityList.indexOf(packageName)
        return if (index == -1) Int.MAX_VALUE else index
    }

    fun getLimitModeSync(): IslandLimitMode {
        val raw = memoryCache["limit_mode"]
        return try {
            IslandLimitMode.valueOf(raw ?: IslandLimitMode.MOST_RECENT.name)
        } catch (_: Exception) {
            IslandLimitMode.MOST_RECENT
        }
    }

    fun getGlobalBlockedTermsSync(): Set<String> {
        return memoryCache[SettingsKeys.GLOBAL_BLOCKED_TERMS].deserializeSet()
    }

    fun isBlockedTermFast(packageName: String, title: String, text: String): Boolean {
        val appBlocked = getAppBlockedTermsSync(packageName)
        val globalBlocked = getGlobalBlockedTermsSync()
        if (appBlocked.isEmpty() && globalBlocked.isEmpty()) return false

        val combinedContent = "$title $text"
        if (appBlocked.isNotEmpty() && appBlocked.any { combinedContent.contains(it, ignoreCase = true) }) {
            return true
        }
        if (globalBlocked.isNotEmpty() && globalBlocked.any { combinedContent.contains(it, ignoreCase = true) }) {
            return true
        }
        return false
    }

    fun isDndModeEnabledSync(): Boolean {
        return memoryCache["dnd_mode_enabled"]?.toBoolean() ?: false
    }

    fun autoDetectDndSync(): Boolean {
        return memoryCache["auto_detect_dnd"]?.toBoolean() ?: false
    }

    fun getScreenRecordingTimeoutSync(): Int =
        memoryCache[SettingsKeys.SCREEN_RECORDING_TIMEOUT].toInt(SYSTEM_ISLAND_DEFAULT_TIMEOUT)

    fun getScreenRecordingLeftDesignSync(): com.sykeptical.hyperpop.models.ScreenRecordingLeftDesign =
        memoryCache[SettingsKeys.SCREEN_RECORDING_LEFT_DESIGN]?.let {
            runCatching { com.sykeptical.hyperpop.models.ScreenRecordingLeftDesign.valueOf(it) }.getOrNull()
        } ?: com.sykeptical.hyperpop.models.ScreenRecordingLeftDesign.ICON_AND_TEXT

    fun getScreenRecordingRightDesignSync(): com.sykeptical.hyperpop.models.ScreenRecordingRightDesign =
        memoryCache[SettingsKeys.SCREEN_RECORDING_RIGHT_DESIGN]?.let {
            runCatching { com.sykeptical.hyperpop.models.ScreenRecordingRightDesign.valueOf(it) }.getOrNull()
        } ?: com.sykeptical.hyperpop.models.ScreenRecordingRightDesign.TIMER

    fun getScreenRecordingDesignSync(): com.sykeptical.hyperpop.models.ScreenRecordingDesignConfig =
        com.sykeptical.hyperpop.models.ScreenRecordingDesignConfig(
            left = getScreenRecordingLeftDesignSync(),
            right = getScreenRecordingRightDesignSync()
        )

    fun isVpnIslandEnabledSync(): Boolean = memoryCache["vpn_island_enabled"]?.toBoolean(true) ?: true


    companion object {
        const val SYSTEM_ISLAND_DEFAULT_TIMEOUT = 4
        private const val DEFAULT_SCREEN_RECORDER_PACKAGE = "com.miui.screenrecorder"
    }

    @androidx.annotation.VisibleForTesting
    internal fun putInCacheForTesting(key: String, value: String) {
        memoryCache[key] = value
    }

    @androidx.annotation.VisibleForTesting
    internal fun removeFromCacheForTesting(key: String) {
        memoryCache.remove(key)
    }

    @androidx.annotation.VisibleForTesting
    internal fun clearCacheForTesting() {
        memoryCache.clear()
    }
}
