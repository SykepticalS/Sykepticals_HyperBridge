package com.d4viddf.hyperbridge.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.d4viddf.hyperbridge.MainActivity
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.data.db.AppDatabase
import com.d4viddf.hyperbridge.data.theme.RulesEngine
import com.d4viddf.hyperbridge.data.theme.ThemeRepository
import com.d4viddf.hyperbridge.data.widget.WidgetManager
import com.d4viddf.hyperbridge.models.ActiveIsland
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.models.IslandConfig
import com.d4viddf.hyperbridge.models.IslandLimitMode
import com.d4viddf.hyperbridge.models.NavContent
import com.d4viddf.hyperbridge.models.NotificationType
import com.d4viddf.hyperbridge.models.WidgetConfig
import com.d4viddf.hyperbridge.models.WidgetRenderMode
import com.d4viddf.hyperbridge.service.call.CallActionSignal
import com.d4viddf.hyperbridge.service.call.CallNotificationClassifier
import com.d4viddf.hyperbridge.service.call.CallNotificationSignals
import com.d4viddf.hyperbridge.service.call.CallSession
import com.d4viddf.hyperbridge.service.call.CallSessionInput
import com.d4viddf.hyperbridge.service.call.CallSessionTracker
import com.d4viddf.hyperbridge.service.message.MessageIdentity
import com.d4viddf.hyperbridge.service.message.MessageNotificationResolver
import com.d4viddf.hyperbridge.service.message.MessageNotificationSignals
import com.d4viddf.hyperbridge.service.diagnostics.DiagnosticsStore
import com.d4viddf.hyperbridge.service.translators.CallTranslator
import com.d4viddf.hyperbridge.service.translators.LiveUpdateTranslator
import com.d4viddf.hyperbridge.service.translators.MediaTranslator
import com.d4viddf.hyperbridge.service.translators.MessageTranslator
import com.d4viddf.hyperbridge.service.translators.NavTranslator
import com.d4viddf.hyperbridge.service.translators.ProgressTranslator
import com.d4viddf.hyperbridge.service.translators.DownloadTranslator
import com.d4viddf.hyperbridge.service.translators.StandardTranslator
import com.d4viddf.hyperbridge.service.translators.TimerTranslator
import com.d4viddf.hyperbridge.service.translators.WidgetTranslator
import com.d4viddf.hyperbridge.util.ShizukuManager
import com.d4viddf.hyperbridge.util.isPostNotificationsEnabled
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class NotificationReaderService : NotificationListenerService() {

    companion object {
        const val ACTION_RELOAD_THEME = "com.d4viddf.hyperbridge.ACTION_RELOAD_THEME"
        const val ACTION_PERFORM_MIGRATION = "com.d4viddf.hyperbridge.ACTION_PERFORM_MIGRATION"
    }

    private val TAG = "HyperBridgeDebug"
    private val EXTRA_ORIGINAL_KEY = "hyper_original_key"

    // --- CHANNELS ---
    private val NOTIFICATION_CHANNEL_ID = BridgeNotificationChannels.ACTIVE
    private val WIDGET_CHANNEL_ID = BridgeNotificationChannels.WIDGET
    private val LIVE_UPDATE_CHANNEL_ID = BridgeNotificationChannels.LIVE_UPDATE
    private val WATCH_RELAY_CHANNEL_ID = BridgeNotificationChannels.WATCH_RELAY
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    // --- STATE & CONFIG ---
    private var allowedPackageSet: Set<String> = emptySet()
    private var currentMode = IslandLimitMode.MOST_RECENT
    private var appPriorityList = emptyList<String>()
    private var globalBlockedTerms: Set<String> = emptySet()
    
    private var isDndModeEnabled = false
    private var autoDetectDnd = false

    // --- CACHES ---
    private data class RemovedSource(val observedAt: Long, val sourcePostTime: Long)

    private val recentlyRemovedKeys = ConcurrentHashMap<String, RemovedSource>()
    private val nativeIslands = ConcurrentHashMap.newKeySet<String>()
    private val activeIslands = ConcurrentHashMap<String, ActiveIsland>()
    private val activeTranslations = ConcurrentHashMap<String, Int>()
    private val reverseTranslations = ConcurrentHashMap<Int, String>()
    private val internalBridgeReplacements = InternalBridgeReplacementRegistry()
    private val sourceToLogicalKeys = ConcurrentHashMap<String, String>()
    private val processingJobs = ConcurrentHashMap<String, Job>()
    private val processingPostTimes = ConcurrentHashMap<String, Long>()
    private val sourceProcessingGeneration = SourceProcessingGeneration()
    private val expiredIslands = ExpiredIslandRegistry()
    private val timeoutJobs = ConcurrentHashMap<String, Job>()
    private val removalJobs = ConcurrentHashMap<String, Job>()
    private lateinit var permanentIslandManager: PermanentIslandManager
    private val intentionallyRemovedKeys = ConcurrentHashMap<String, Long>()
    private val widgetUpdateDebouncer = ConcurrentHashMap<Int, Long>()
    private val dismissedWidgetIds = ConcurrentHashMap.newKeySet<Int>()
    private val activeWidgets = ConcurrentHashMap.newKeySet<Int>()
    private val appLabelCache = object : LruCache<String, String>(128) {}

    private val MAX_ISLANDS = 9
    private val WIDGET_ID_BASE = 9000
    // Negative so these ids can never hit the >= WIDGET_ID_BASE branch in onNotificationRemoved
    private val WATCH_RELAY_ID_BASE = -20000
    private var watchRelaySlot = 0
    private val STANDARD_ISLAND_TIMEOUT_MS = 60_000L

    private lateinit var preferences: AppPreferences

    // --- THEME ENGINE ---
    private lateinit var themeRepository: ThemeRepository
    private lateinit var rulesEngine: RulesEngine
    private lateinit var callClassifier: CallNotificationClassifier
    private lateinit var presentationController: NotificationPresentationController
    private val callSessionTracker = CallSessionTracker()
    private val messageResolver = MessageNotificationResolver()
    private val notificationLifecycleMutex = Mutex()

    // Translators
    private lateinit var callTranslator: CallTranslator
    private lateinit var navTranslator: NavTranslator
    private lateinit var timerTranslator: TimerTranslator
    private lateinit var progressTranslator: ProgressTranslator
    private lateinit var downloadTranslator: DownloadTranslator
    private lateinit var standardTranslator: StandardTranslator
    private lateinit var messageTranslator: MessageTranslator
    private lateinit var mediaTranslator: MediaTranslator
    private lateinit var widgetTranslator: WidgetTranslator
    private lateinit var liveUpdateTranslator: LiveUpdateTranslator

    @Volatile
    private var isScreenOn = true

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                WidgetManager.init(this@NotificationReaderService)
                syncNotifications(refresh = true)
            } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                isScreenOn = true
                syncNotifications(refresh = true)
            } else if (intent.action == Intent.ACTION_SCREEN_OFF) {
                isScreenOn = false
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onCreate() {
        super.onCreate()
        
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(systemReceiver, filter)
        
        preferences = AppPreferences(applicationContext)
        createChannels()

        // [INIT] Theme Engine
        themeRepository = ThemeRepository(this)
        rulesEngine = RulesEngine()
        callClassifier = CallNotificationClassifier(
            answerKeywords = resources.getStringArray(R.array.call_keywords_answer).toList(),
            declineKeywords = resources.getStringArray(R.array.call_keywords_hangup).toList(),
            hangUpKeywords = resources.getStringArray(R.array.call_keywords_hangup).toList(),
            speakerKeywords = resources.getStringArray(R.array.call_keywords_speaker).toList()
        )
        presentationController = NotificationPresentationController(
            markIntentionallyRemoved = { key -> intentionallyRemovedKeys[key] = System.currentTimeMillis() },
            cancelOriginal = { key -> cancelNotification(key) },
            postWatchRelay = { source, title, text -> postWatchRelayNotification(source, title, text) }
        )

        // Pass ThemeRepository to Translators
        callTranslator = CallTranslator(this, themeRepository)
        navTranslator = NavTranslator(this, themeRepository)
        timerTranslator = TimerTranslator(this, themeRepository)
        progressTranslator = ProgressTranslator(this, themeRepository)
        downloadTranslator = DownloadTranslator(this, themeRepository)
        standardTranslator = StandardTranslator(this, themeRepository)
        messageTranslator = MessageTranslator(this, themeRepository)
        liveUpdateTranslator = LiveUpdateTranslator(this, themeRepository)

        mediaTranslator = MediaTranslator(this)
        widgetTranslator = WidgetTranslator(this)

        val userManager = getSystemService(USER_SERVICE) as android.os.UserManager
        if (userManager.isUserUnlocked) {
            WidgetManager.init(this)
        }

        permanentIslandManager = PermanentIslandManager(this, serviceScope, preferences)

        serviceScope.launch { preferences.allowedPackagesFlow.collectLatest { allowedPackageSet = it } }
        serviceScope.launch { preferences.limitModeFlow.collectLatest { currentMode = it } }
        serviceScope.launch { preferences.appPriorityListFlow.collectLatest { appPriorityList = it } }
        serviceScope.launch { preferences.globalBlockedTermsFlow.collectLatest { globalBlockedTerms = it } }
        serviceScope.launch { preferences.isDndModeEnabledFlow.collectLatest { isDndModeEnabled = it } }
        serviceScope.launch { preferences.autoDetectDndFlow.collectLatest { autoDetectDnd = it } }

        // Listen for Theme Changes
        serviceScope.launch {
            preferences.activeThemeIdFlow.collectLatest { themeId ->
                Log.d(TAG, "Service detected theme change: $themeId")
                if (themeId != null) {
                    themeRepository.activateTheme(themeId)
                } else {
                    themeRepository.activateTheme("")
                }
            }
        }

        // --- WIDGET LISTENER ---
        serviceScope.launch {
            WidgetManager.widgetUpdates.collect { updatedId ->
                if (dismissedWidgetIds.contains(updatedId)) return@collect
                val savedIds = preferences.savedWidgetIdsFlow.first()
                if (savedIds.contains(updatedId)) {
                    val config = preferences.getWidgetConfigFlow(updatedId).first()
                    if (shouldProcessWidgetUpdate(updatedId, config)) {
                        launch(Dispatchers.Main) {
                            processSingleWidget(updatedId, config)
                        }
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_TEST_WIDGET") {
            val widgetId = intent.getIntExtra("WIDGET_ID", -1)
            if (widgetId != -1) {
                dismissedWidgetIds.remove(widgetId)
                serviceScope.launch(Dispatchers.Main) {
                    val config = preferences.getWidgetConfigFlow(widgetId).first()
                    processSingleWidget(widgetId, config)
                }
            }
        } else if (intent?.action == ACTION_RELOAD_THEME) {
            serviceScope.launch {
                val themeId = preferences.activeThemeIdFlow.first()
                if (themeId != null) {
                    Log.d(TAG, "Hot-reloading theme: $themeId")
                    themeRepository.activateTheme(themeId)
                }
            }
        } else if (intent?.action == ACTION_PERFORM_MIGRATION) {
            serviceScope.launch(Dispatchers.IO) {
                AppDatabase.performMigration(applicationContext) { progress ->
                    launch(Dispatchers.Main) {
                        showMigrationProgress(progress)
                    }
                }
            }
        }
        return START_STICKY
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showMigrationProgress(progress: Int) {
        val title = getString(R.string.migration_title)
        val message = if (progress >= 100) getString(R.string.migration_complete) else getString(R.string.migration_message)
        val bridgeId = "migration_update".hashCode()

        serviceScope.launch {
            val useNative = getEffectiveEngine(packageName)
            
            if (useNative) {
                val notificationBuilder = liveUpdateTranslator.translateToLiveUpdate(
                    sbn = null,
                    channelId = LIVE_UPDATE_CHANNEL_ID,
                    type = NotificationType.PROGRESS,
                    navRight = null,
                    config = null
                )
                notificationBuilder.setContentTitle(title)
                notificationBuilder.setContentText(message)
                notificationBuilder.setProgress(100, progress, progress < 0)
                notificationBuilder.setOngoing(progress in 0..99)
                notificationBuilder.setSmallIcon(R.drawable.ic_launcher_foreground)
                notificationBuilder.setOnlyAlertOnce(true).setDefaults(0).setSound(null).setVibrate(null)

                val notification = notificationBuilder.build()
                ShizukuManager.notify(this@NotificationReaderService, bridgeId, notification)
            } else {
                val builder = HyperIslandNotification.Builder(this@NotificationReaderService, "migration", title)
                builder.setProgressBar(progress, "#007AFF")
                builder.setChatInfo(title, message, "migration_icon", packageName)
                builder.setShowNotification(true)
                builder.setIslandFirstFloat(false)

                val data = HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())

                val notificationBuilder = NotificationCompat.Builder(this@NotificationReaderService, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(progress in 0..99)
                    .setProgress(100, progress, progress < 0)
                    .setOnlyAlertOnce(true)
                    .setDefaults(0)
                    .setSound(null)
                    .setVibrate(null)
                    .addExtras(data.resources)

                val notification = notificationBuilder.build()
                notification.extras.putString("miui.focus.param", data.jsonParam)

                ShizukuManager.notify(this@NotificationReaderService, bridgeId, notification)
            }

            if (progress >= 100) {
                delay(3000)
                NotificationManagerCompat.from(this@NotificationReaderService).cancel(bridgeId)
            }
        }
    }

    // =========================================================================
    //  EFFECTIVE BEHAVIOR RESOLUTION (Theme > App > Global)
    // =========================================================================

    private fun getEffectiveTypes(pkg: String): Set<String> {
        val themeOverride = themeRepository.activeTheme.value?.apps?.get(pkg)
        val rawTypes = if (themeOverride?.activeNotificationTypes != null) {
            themeOverride.activeNotificationTypes
        } else {
            val localPref = preferences.getAppConfigSync(pkg)
            localPref ?: preferences.getGlobalNotificationTypesSync()
        }

        // Fallback: if PROGRESS is enabled but DOWNLOAD is missing, implicitly enable DOWNLOAD
        return if (rawTypes.contains("PROGRESS") && !rawTypes.contains("DOWNLOAD")) {
            rawTypes + "DOWNLOAD"
        } else {
            rawTypes
        }
    }

    private fun getEffectiveEngine(pkg: String): Boolean {
        val activeTheme = themeRepository.activeTheme.value

        // 1. Theme App Override (Creator explicitly configured this app)
        val themeAppOverride = activeTheme?.apps?.get(pkg)?.useNativeLiveUpdates
        if (themeAppOverride != null) return themeAppOverride

        // 2. User App Override (User explicitly configured this app via Home Screen)
        val userAppOverride = preferences.getAppEnginePreferenceSync(pkg)
        if (userAppOverride != null) return userAppOverride

        // 3. Theme Global Override (Creator explicitly forced an engine for the whole theme)
        val themeGlobalOverride = activeTheme?.global?.useNativeLiveUpdates
        if (themeGlobalOverride != null) return themeGlobalOverride

        // 4. User Global Fallback (The main Engine Setting on the Home Screen!)
        return preferences.useNativeLiveUpdatesSync()
    }

    private fun getEffectiveNav(pkg: String): Pair<NavContent, NavContent> {
        return preferences.getEffectiveNavLayoutSync(pkg)
    }

    // =========================================================================
    //  NOTIFICATION REMOVAL LOGIC
    // =========================================================================

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        sbn?.let {
            if (nativeIslands.remove(it.key)) {
                updatePermanentIsland()
            }

            val isOurApp = it.packageName == packageName
            val notifId = it.id
            val notifKey = it.key

            if (isOurApp) {
                val replacement = internalBridgeReplacements.consume(notifId, System.currentTimeMillis())
                if (replacement != null) {
                    Log.d(
                        TAG,
                        "MESSAGE REPLACE removal ignored logicalId=${replacement.logicalId.hashCode()} " +
                                "oldBridgeId=$notifId generation=${replacement.generation}"
                    )
                    return
                }
            }

            if (intentionallyRemovedKeys.remove(notifKey) != null) {
                return
            }

            recentlyRemovedKeys[notifKey] = RemovedSource(System.currentTimeMillis(), it.postTime)

            // A source app may replace a notification in-place. Do not let the delayed removal
            // callback for the older post cancel processing for the newer post using the same key.
            val queuedPostTime = processingPostTimes[notifKey]
            val sourceStillActive = isSourceNotificationActive(notifKey)
            val hasActiveReplacement = queuedPostTime != null && sourceStillActive
            if (!isOurApp && !sourceStillActive) {
                expiredIslands.removeSource(notifKey)
            }
            if (!hasActiveReplacement && (queuedPostTime == null || queuedPostTime <= it.postTime)) {
                sourceProcessingGeneration.remove(notifKey)
                processingPostTimes.remove(notifKey)
            }

            if (isOurApp) {
                // Only process user-initiated dismissals for our notifications. 
                // Ignore programmatic cancels (e.g., during updates or Shizuku workarounds).
                if (reason != REASON_CANCEL && reason != REASON_CANCEL_ALL) {
                    return
                }

                if (BridgeNotificationChannels.isWidget(it.notification.channelId)) {
                    val widgetId = notifId - WIDGET_ID_BASE
                    dismissedWidgetIds.add(widgetId)
                    activeWidgets.remove(widgetId)
                    updatePermanentIsland()
                    return
                }

                var originalKey = reverseTranslations[notifId]
                if (originalKey == null) {
                    originalKey = it.notification.extras.getString(EXTRA_ORIGINAL_KEY)
                }

                if (originalKey != null) {
                    Log.d(TAG, "Our notification $notifId removed. Cleaning up cache for $originalKey")
                    activeIslands[originalKey]?.let { recordSuppressedIsland(it, "dismissed") }
                    // [FIX] We no longer kill the source notification when our Island is dismissed or timed out
                    try {
                        activeIslands[originalKey]?.deleteIntent?.send()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending delete intent for original notification", e)
                    }
                    cleanupCache(originalKey)
                }
                return
            }

            val logicalKey = sourceToLogicalKeys[notifKey]
                ?: callSessionTracker.logicalIdForSource(notifKey)
                ?: notifKey

            if (activeTranslations.containsKey(logicalKey)) {
                val hyperId = activeTranslations[logicalKey] ?: return
                val islandType = activeIslands[logicalKey]?.type
                if (islandType == NotificationType.CALL) {
                    callSessionTracker.markSourceRemoved(notifKey, System.currentTimeMillis())
                }

                lateinit var job: Job
                job = serviceScope.launch(Dispatchers.IO) {
                    val appConfig = preferences.getAppIslandConfigSync(sbn.packageName)
                    val globalConfig = preferences.getGlobalConfigSync()
                    val finalConfig = appConfig.mergeWith(globalConfig)

                    val forceDismiss = islandType == NotificationType.CALL || 
                                       islandType == NotificationType.MEDIA || 
                                       islandType == NotificationType.NAVIGATION

                    if (finalConfig.dismissWithOriginal == true || forceDismiss) {
                        // Debounce updates if the app canceled it programmatically
                        if (islandType == NotificationType.CALL) {
                            kotlinx.coroutines.delay(1_000)
                        } else if (reason == REASON_APP_CANCEL) {
                            kotlinx.coroutines.delay(300)
                        }
                        notificationLifecycleMutex.withLock {
                            val current = activeIslands[logicalKey]
                            val sourceStillActive = isSourceNotificationActive(notifKey)
                            if (sourceStillActive || current == null || !NotificationLifecyclePolicy.isCurrentRemoval(
                                    activeSourceKey = current.sourceKey,
                                    activeSourcePostTime = current.sourcePostTime,
                                    removedSourceKey = notifKey,
                                    removedSourcePostTime = sbn.postTime
                                )
                            ) {
                                Log.d(
                                    TAG,
                                    "${islandType?.name ?: "UNKNOWN"} REMOVE reason=stale " +
                                            "sourceKey=${notifKey.hashCode()} logicalId=${logicalKey.hashCode()}"
                                )
                                return@withLock
                            }
                            timeoutJobs.remove(logicalKey)?.cancel()
                            try {
                                NotificationManagerCompat.from(this@NotificationReaderService).cancel(hyperId)
                            } catch (_: Exception) {}
                            Log.d(
                                TAG,
                                "${islandType?.name ?: "UNKNOWN"} REMOVE reason=$reason " +
                                        "sourceKey=${notifKey.hashCode()} logicalId=${logicalKey.hashCode()}"
                            )
                            cleanupCache(logicalKey)
                        }
                    }
                }
                removalJobs[logicalKey]?.cancel()
                removalJobs[logicalKey] = job
                job.invokeOnCompletion { removalJobs.remove(logicalKey, job) }
            }
        }
    }

    private fun cancelSourceNotification(targetKey: String) {
        try {
            val currentNotifications = try {
                activeNotifications
            } catch (_: Exception) {
                cancelNotification(targetKey)
                return
            }

            val targetSbn = currentNotifications.find { it.key == targetKey }
            cancelNotification(targetKey)

            if (targetSbn != null) {
                val groupKey = targetSbn.groupKey
                val pkg = targetSbn.packageName
                if (groupKey == null) return

                val remainingGroupMembers = currentNotifications.filter {
                    it.packageName == pkg &&
                            it.groupKey == groupKey &&
                            it.key != targetKey
                }

                if (remainingGroupMembers.size == 1) {
                    val survivor = remainingGroupMembers[0]
                    val isSummary = (survivor.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                    if (isSummary) {
                        cancelNotification(survivor.key)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during smart dismissal", e)
        }
    }

    private fun cleanupCache(originalKey: String) {
        val hyperId = activeTranslations[originalKey]
        val island = activeIslands.remove(originalKey)
        activeTranslations.remove(originalKey)
        sourceToLogicalKeys.entries.removeIf { it.value == originalKey }
        timeoutJobs[originalKey]?.cancel()
        timeoutJobs.remove(originalKey)
        removalJobs[originalKey]?.cancel()
        removalJobs.remove(originalKey)

        if (island?.type == NotificationType.CALL) {
            callSessionTracker.end(island.logicalId)
        }

        if (hyperId != null) {
            reverseTranslations.remove(hyperId, originalKey)
        }
        updatePermanentIsland()
    }

    private fun recordExpiredIsland(island: ActiveIsland) = recordSuppressedIsland(island, "expired")

    private fun recordSuppressedIsland(island: ActiveIsland, action: String) {
        if (island.type != NotificationType.MESSAGE && island.type != NotificationType.STANDARD) return
        expiredIslands.record(
            ExpiredIslandRecord(
                logicalId = island.logicalId,
                sourceKey = island.sourceKey,
                sourceFingerprint = sourceGenerationFingerprint(island.lastContentHash, island.sourcePostTime),
                expiredAt = System.currentTimeMillis()
            )
        )
        Log.d(
            TAG,
            "${island.type.name} ${action.uppercase()} sourceKey=${island.sourceKey.hashCode()} " +
                    "logicalId=${island.logicalId.hashCode()} bridgeId=${island.id}"
        )
        DiagnosticsStore.record(island.type.name, action, island.packageName)
    }

    private fun shouldSuppressExpiredSource(
        sbn: StatusBarNotification,
        type: NotificationType,
        logicalKey: String,
        contentHash: Int,
        recovery: Boolean
    ): Boolean {
        if (type != NotificationType.MESSAGE && type != NotificationType.STANDARD) return false
        return when (
            expiredIslands.evaluate(
                sbn.key,
                sourceGenerationFingerprint(contentHash, sbn.postTime),
                System.currentTimeMillis()
            )
        ) {
            ExpiredSourceDecision.NOT_EXPIRED -> false
            ExpiredSourceDecision.SUPPRESS_IDENTICAL -> {
                sourceToLogicalKeys.remove(sbn.key, logicalKey)
                Log.d(
                    TAG,
                    "${type.name} RECOVERY_SKIPPED_EXPIRED sourceKey=${sbn.key.hashCode()} " +
                            "logicalId=${logicalKey.hashCode()}"
                )
                DiagnosticsStore.record(
                    type.name,
                    if (recovery) "recovery-skipped-expired" else "expired-generation-ignored",
                    sbn.packageName
                )
                true
            }
            ExpiredSourceDecision.NEW_GENERATION -> {
                Log.d(
                    TAG,
                    "${type.name} NEW_GENERATION_AFTER_EXPIRY sourceKey=${sbn.key.hashCode()} " +
                            "logicalId=${logicalKey.hashCode()}"
                )
                DiagnosticsStore.record(type.name, "new-generation-after-expiry", sbn.packageName)
                false
            }
        }
    }

    private fun handlePostNotificationSideEffects(
        logicalKey: String,
        sourceKey: String,
        bridgeId: Int,
        generation: Long,
        config: IslandConfig,
        type: NotificationType,
        isLiveUpdate: Boolean,
        sbn: StatusBarNotification? = null,
        title: String = "",
        text: String = "",
        isMessageReplacement: Boolean = false
    ) {
        if (!isMessageReplacement) {
            presentationController.applyPostEffects(
                NotificationPresentationRequest(
                    originalKey = sourceKey,
                    bridgeId = bridgeId,
                    config = config,
                    type = type,
                    isLiveUpdate = isLiveUpdate,
                    sbn = sbn,
                    title = title,
                    text = text
                )
            )
        }

        // 2. Schedule timeout ONLY for Live Update notifications
        if (isLiveUpdate) {
            val timeoutSeconds = config.timeout ?: 0
            timeoutJobs.remove(logicalKey)?.cancel()
            if (timeoutSeconds > 0) {
                lateinit var job: Job
                job = serviceScope.launch {
                    delay((timeoutSeconds * 1000L).milliseconds)
                    notificationLifecycleMutex.withLock {
                        val current = activeIslands[logicalKey]
                        if (!IslandTimeoutPolicy.isCurrent(current?.generation, current?.id, generation, bridgeId)) return@withLock
                        current ?: return@withLock
                        Log.d(TAG, "${type.name} TIMEOUT bridgeId=$bridgeId logicalId=${logicalKey.hashCode()}")
                        recordExpiredIsland(current)
                        NotificationManagerCompat.from(this@NotificationReaderService).cancel(bridgeId)
                        cleanupCache(logicalKey)
                    }
                }
                timeoutJobs[logicalKey] = job
                job.invokeOnCompletion { timeoutJobs.remove(logicalKey, job) }
            }
        } else if (type == NotificationType.MESSAGE || type == NotificationType.STANDARD) {
            // HyperOS island-swipe only hides the island; the focus notification stays posted and no
            // removal callback fires, so an untimed island blocks the permanent island forever.
            timeoutJobs.remove(logicalKey)?.cancel()
            lateinit var job: Job
            job = serviceScope.launch {
                delay(STANDARD_ISLAND_TIMEOUT_MS)
                notificationLifecycleMutex.withLock {
                    val current = activeIslands[logicalKey]
                    if (!IslandTimeoutPolicy.isCurrent(current?.generation, current?.id, generation, bridgeId)) return@withLock
                    current ?: return@withLock
                    Log.d(TAG, "${type.name} TIMEOUT bridgeId=$bridgeId logicalId=${logicalKey.hashCode()}")
                    recordExpiredIsland(current)
                    NotificationManagerCompat.from(this@NotificationReaderService).cancel(bridgeId)
                    cleanupCache(logicalKey)
                }
            }
            timeoutJobs[logicalKey] = job
            job.invokeOnCompletion { timeoutJobs.remove(logicalKey, job) }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun postWatchRelayNotification(sbn: StatusBarNotification, title: String, text: String) {
        try {
            val appLabel = getCachedAppLabel(sbn.packageName)
            val relayId = WATCH_RELAY_ID_BASE - (watchRelaySlot++ and 0x0F)
            val notification = NotificationCompat.Builder(this, WATCH_RELAY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(if (title.isNotBlank()) "$appLabel · $title" else appLabel)
                .setContentText(text)
                .setSilent(true)
                .setAutoCancel(true)
                .setTimeoutAfter(10_000L)
                .build()
            NotificationManagerCompat.from(this).notify(relayId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting watch relay notification", e)
        }
    }

    private fun logStateChange(isLandscape: Boolean) {
        val orientation = if (isLandscape) "Landscape" else "Portrait"
        val isIslandExhibited = activeIslands.isNotEmpty() || activeWidgets.isNotEmpty() || nativeIslands.isNotEmpty() || permanentIslandManager.isIslandActive()
        val islandState = if (isIslandExhibited) "Showing Island" else "No Island"
        Log.d(TAG, "State: $orientation | $islandState")
    }

    private fun updatePermanentIsland() {
        permanentIslandManager.onActiveNotificationsChanged(activeIslands.size + activeWidgets.size, nativeIslands.isNotEmpty())
        DiagnosticsStore.setActiveIslands(activeIslands.size)
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        logStateChange(isLandscape)
    }

    private fun suppressPermanentIslandForPost(previous: ActiveIsland?) {
        val pendingCount = activeIslands.size + activeWidgets.size + if (previous == null) 1 else 0
        permanentIslandManager.onActiveNotificationsChanged(pendingCount, nativeIslands.isNotEmpty())
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        permanentIslandManager.onOrientationChanged()
        logStateChange(newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    // =========================================================================
    //  STANDARD NOTIFICATION LOGIC
    // =========================================================================

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            if (it.packageName != packageName) {
                val extras = it.notification.extras
                var isNative = false
                if (extras != null) {
                    if (extras.containsKey("miui.focus.param") || extras.containsKey("miui.system.focus.param")) {
                        isNative = true
                    }
                    val template = extras.getString(Notification.EXTRA_TEMPLATE)
                    if (template == "androidx.media.app.NotificationCompat\$MediaStyle" ||
                        template == "android.app.Notification\$MediaStyle") {
                        isNative = true
                    }
                }
                if (isNative) {
                    if (nativeIslands.add(it.key)) updatePermanentIsland()
                } else {
                    if (nativeIslands.remove(it.key)) updatePermanentIsland()
                }
            }

            enqueueSourceNotification(it)
        }
    }

    @SuppressLint("MissingPermission") // Guarded by isPostNotificationsEnabled before work is enqueued.
    private fun enqueueSourceNotification(sbn: StatusBarNotification, recovery: Boolean = false) {
        if (shouldIgnore(sbn.packageName) || !isAppAllowed(sbn.packageName)) return
        if (!isPostNotificationsEnabled(this)) {
            DiagnosticsStore.record("PERMISSION", "ignored", sbn.packageName, "post-notifications-missing")
            return
        }

        val sourceKey = sbn.key
        val quality = sourceCandidateQuality(sbn)
        val processingGeneration = sourceProcessingGeneration.next(sourceKey, quality)
        logSanitizedWhatsappCallback(sbn, quality, processingGeneration ?: sourceProcessingGeneration.current(sourceKey))
        if (processingGeneration == null) return
        processingPostTimes[sourceKey] = sbn.postTime
        val job = serviceScope.launch {
            notificationLifecycleMutex.withLock {
                if (!sourceProcessingGeneration.isCurrent(sourceKey, processingGeneration)) return@withLock
                processStandardNotification(sbn, recovery, processingGeneration)
            }
        }
        processingJobs[sourceKey] = job
        job.invokeOnCompletion {
            if (processingJobs.remove(sourceKey, job)) {
                processingPostTimes.remove(sourceKey)
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun processStandardNotification(
        rawSbn: StatusBarNotification,
        recovery: Boolean = false,
        processingGeneration: Long
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        val isSystemDndActive = manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val dndActive = isDndModeEnabled || (autoDetectDnd && isSystemDndActive)

        if (dndActive) {
            Log.d(TAG, "DND active. Skipping notification ${rawSbn.packageName}")
            return
        }

        val sbn = ensureValidSbn(rawSbn)
        if (!sourceProcessingGeneration.isCurrent(rawSbn.key, processingGeneration)) return

        try {
            val extras = sbn.notification.extras

            // Resolve rendered content before rules/type detection, but defer previous-state lookup
            // until the final logical identity has been selected.
            val resolvedContent = resolveNotificationContent(sbn)
            val typeBeforeRules = detectNotificationType(sbn)
            if (isJunkNotification(sbn, resolvedContent, typeBeforeRules)) {
                DiagnosticsStore.record(typeBeforeRules.name, "ignored", sbn.packageName, "junk-or-empty")
                return
            }
            if (recovery) {
                DiagnosticsStore.record(typeBeforeRules.name, "recovering", sbn.packageName)
            }
            val sourceTitleWasEmpty = resolvedContent.title.isEmpty()
            var effectiveTitle = resolvedContent.title
            val effectiveText = resolvedContent.text
            if (effectiveTitle.isEmpty()) {
                effectiveTitle = getCachedAppLabel(sbn.packageName)
            }

            val hasProgress = hasProgressNotification(sbn, effectiveTitle, effectiveText)
            if (effectiveTitle.isEmpty() && !hasProgress) return

            val appBlockedTerms = preferences.getAppBlockedTermsSync(sbn.packageName)
            if (appBlockedTerms.isNotEmpty()) {
                val content = "$effectiveTitle $effectiveText"
                if (appBlockedTerms.any { term -> content.contains(term, ignoreCase = true) }) return
            }

            val activeTheme = themeRepository.activeTheme.value
            val ruleMatch = rulesEngine.match(sbn, effectiveTitle, effectiveText, activeTheme)

            val type = if (ruleMatch?.targetLayout != null) {
                try { NotificationType.valueOf(ruleMatch.targetLayout) }
                catch (_: Exception) { typeBeforeRules }
            } else {
                typeBeforeRules
            }

            // --- LAYERED TRIGGERS LOGIC ---
            val effectiveTypes = getEffectiveTypes(sbn.packageName)
            if (!effectiveTypes.contains(type.name)) {
                Log.d(TAG, " ABORTING: Type $type disabled by user/theme for ${sbn.packageName}")
                return
            }

            val isSummary = type == NotificationType.MESSAGE && isGroupSummary(sbn)

            val logical = resolveLogicalNotification(sbn, type, effectiveTitle)
            val effectiveKey = logical.logicalId
            detachReassignedSource(sbn.key, effectiveKey)
            val previous = activeIslands[effectiveKey]
            if (type == NotificationType.MESSAGE && previous != null && sbn.postTime < previous.sourcePostTime) {
                Log.d(TAG, "MESSAGE skip stale update logical=${effectiveKey.hashCode()}")
                return
            }
            if (sourceTitleWasEmpty && previous?.title?.isNotEmpty() == true) {
                effectiveTitle = previous.title
            }

            removalJobs[effectiveKey]?.cancel()
            removalJobs.remove(effectiveKey)
            val presentationReason = NotificationLifecyclePolicy.presentationReason(
                hasPrevious = previous != null,
                recovery = recovery
            )
            val generation = (previous?.generation ?: 0L) + 1L
            // MESSAGE generations are rendered as fresh presentations. An identical repost is
            // still rejected by the semantic hash before anything is cancelled or posted.
            val isUpdate = !presentationReason.mayAutoExpand && !(type == NotificationType.MESSAGE && previous != null)
            val bridgeId = if (type == NotificationType.MESSAGE && previous != null) {
                allocateMessageBridgeId(effectiveKey, generation, previous.lastContentHash, previous.id)
            } else {
                previous?.id ?: effectiveKey.hashCode()
            }

            val appIslandConfig = preferences.getAppIslandConfigSync(sbn.packageName)
            val globalConfig = preferences.getGlobalConfigSync()
            val finalConfig = appIslandConfig.mergeWith(globalConfig)
            val picKey = "pic_${bridgeId}"

            // --- LAYERED ENGINE LOGIC ---
            val useLiveUpdates = getEffectiveEngine(sbn.packageName)

            if (useLiveUpdates) {
                Log.i(TAG, " POSTING Native Live Update -> ID: $bridgeId, Type: $type")

                // [FIX] Fetch the user's custom layout so the Live Update can use it!
                val navLayout = if (type == NotificationType.NAVIGATION) getEffectiveNav(sbn.packageName) else null

                // [FIX] Pass the type and the right layout to the translator
                val builder = liveUpdateTranslator.translateToLiveUpdate(
                    sbn = sbn,
                    channelId = LIVE_UPDATE_CHANNEL_ID,
                    type = type,
                    navRight = navLayout?.second,
                    config = finalConfig,
                    callSession = logical.callSession
                )

                builder.extras.putString(EXTRA_ORIGINAL_KEY, effectiveKey)

                builder.setOnlyAlertOnce(isUpdate)
                builder.setDefaults(0).setSound(null).setVibrate(null)

                val hasPermission = com.d4viddf.hyperbridge.util.XiaomiNotificationHelper.hasFocusPermission(this)
                if (!hasPermission && com.d4viddf.hyperbridge.util.XiaomiNotificationHelper.isSupportIsland()) {
                    serviceScope.launch {
                        preferences.setFeaturedPermissionWarning(true)
                    }
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra("open_troubleshoot", true)
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        android.R.drawable.ic_dialog_info,
                        getString(R.string.troubleshoot_featured_notification),
                        pendingIntent
                    )
                }

                val notification = builder.build()

                val newContentHash = computeContentHash(
                    sbn = sbn,
                    type = type,
                    title = effectiveTitle,
                    text = effectiveText,
                    renderedJson = null,
                    callSession = logical.callSession
                )
                if (shouldSuppressExpiredSource(sbn, type, effectiveKey, newContentHash, recovery)) return
                if (!ensureIslandCapacity(previous, type, sbn.packageName, logical, sbn.key, effectiveKey)) return
                bindSourceToLogicalKey(effectiveKey, sbn.key, previous)
                val decision = IslandUpdateResolver.decide(
                    logicalId = effectiveKey,
                    candidateBridgeId = bridgeId,
                    contentHash = newContentHash,
                    previous = previous?.toPreviousPresentation(),
                    notificationType = type,
                    presentationReason = presentationReason
                )
                if (decision.kind == IslandPresentationKind.UNCHANGED) {
                    logMessageLifecycle(sbn, type, effectiveKey, decision, previous)
                    logUpdateDecision(sbn, type, effectiveKey, decision)
                    refreshSourceAlias(effectiveKey, sbn, previous)
                    return
                }

                logMessageLifecycle(sbn, type, effectiveKey, decision, previous)
                if (!sourceProcessingGeneration.isCurrent(rawSbn.key, processingGeneration)) return
                suppressPermanentIslandForPost(previous)
                prepareMessageReplacement(effectiveKey, previous, decision, generation)
                dispatchIslandNotification(decision.bridgeId, notification, decision.onlyAlertOnce)
                expiredIslands.acceptNewGeneration(
                    sbn.key,
                    sourceGenerationFingerprint(newContentHash, sbn.postTime)
                )

                activeTranslations[effectiveKey] = decision.bridgeId
                reverseTranslations[decision.bridgeId] = effectiveKey
                activeIslands[effectiveKey] = ActiveIsland(
                    id = decision.bridgeId, type = type, postTime = System.currentTimeMillis(), sourcePostTime = sbn.postTime,
                    packageName = sbn.packageName, sourceKey = sbn.key, logicalId = effectiveKey,
                    groupKey = sbn.groupKey, isGroupSummary = isSummary, generation = generation,
                    title = effectiveTitle, text = effectiveText,
                    subText = "LiveUpdate", lastContentHash = newContentHash, deleteIntent = sbn.notification.deleteIntent
                )
                updatePermanentIsland()

                logUpdateDecision(sbn, type, effectiveKey, decision)
                handlePostNotificationSideEffects(
                    effectiveKey, sbn.key, decision.bridgeId, generation, finalConfig,
                    type, true, sbn, effectiveTitle, effectiveText,
                    isMessageReplacement = decision.cancelBeforeNotify
                )
                return
            }

            // --- LAYERED CUSTOM ISLAND LOGIC ---
            val data: HyperIslandData = when (type) {
                NotificationType.CALL -> callTranslator.translate(
                    sbn, picKey, finalConfig, activeTheme,
                    requireNotNull(logical.callSession), isUpdate
                )
                NotificationType.NAVIGATION -> {
                    // --- LAYERED NAVIGATION LOGIC ---
                    val navLayout = getEffectiveNav(sbn.packageName)
                    navTranslator.translate(sbn, picKey, finalConfig, navLayout.first, navLayout.second, activeTheme, isUpdate)
                }
                NotificationType.TIMER -> timerTranslator.translate(sbn, picKey, finalConfig, activeTheme, isUpdate)
                NotificationType.PROGRESS -> progressTranslator.translate(sbn, effectiveTitle, picKey, finalConfig, activeTheme, isUpdate)
                NotificationType.DOWNLOAD -> downloadTranslator.translate(sbn, effectiveTitle, picKey, finalConfig, activeTheme, isUpdate)
                NotificationType.MEDIA -> mediaTranslator.translate(sbn, picKey, finalConfig, isUpdate)
                NotificationType.MESSAGE -> messageTranslator.translate(sbn, effectiveTitle, effectiveText, picKey, finalConfig, activeTheme, isUpdate)
                else -> standardTranslator.translate(sbn, effectiveTitle, effectiveText, picKey, finalConfig, activeTheme, isUpdate)
            }

            val newContentHash = computeContentHash(
                sbn = sbn,
                type = type,
                title = effectiveTitle,
                text = effectiveText,
                renderedJson = data.jsonParam,
                callSession = logical.callSession
            )
            if (shouldSuppressExpiredSource(sbn, type, effectiveKey, newContentHash, recovery)) return
            if (!ensureIslandCapacity(previous, type, sbn.packageName, logical, sbn.key, effectiveKey)) return
            bindSourceToLogicalKey(effectiveKey, sbn.key, previous)
            val decision = IslandUpdateResolver.decide(
                logicalId = effectiveKey,
                candidateBridgeId = bridgeId,
                contentHash = newContentHash,
                previous = previous?.toPreviousPresentation(),
                notificationType = type,
                presentationReason = presentationReason
            )
            if (decision.kind == IslandPresentationKind.UNCHANGED) {
                logMessageLifecycle(sbn, type, effectiveKey, decision, previous)
                logUpdateDecision(sbn, type, effectiveKey, decision)
                refreshSourceAlias(effectiveKey, sbn, previous)
                return
            }

            kotlinx.coroutines.yield()

            val removedTime = recentlyRemovedKeys[rawSbn.key]
            if (removedTime != null &&
                System.currentTimeMillis() - removedTime.observedAt < 2000 &&
                removedTime.sourcePostTime >= rawSbn.postTime &&
                !isSourceNotificationActive(rawSbn.key)
            ) {
                Log.d(TAG, "Skipping post because notification was recently removed: ${rawSbn.key}")
                return
            }

            logMessageLifecycle(sbn, type, effectiveKey, decision, previous)
            if (!sourceProcessingGeneration.isCurrent(rawSbn.key, processingGeneration)) return
            suppressPermanentIslandForPost(previous)
            prepareMessageReplacement(effectiveKey, previous, decision, generation)
            Log.i(TAG, "UPDATE post pkg=${sbn.packageName} type=$type logical=${effectiveKey.hashCode()} id=${decision.bridgeId} kind=${decision.kind}")
            postStandardNotification(
                sbn, effectiveKey, decision.bridgeId, data, decision.onlyAlertOnce
            )
            expiredIslands.acceptNewGeneration(
                sbn.key,
                sourceGenerationFingerprint(newContentHash, sbn.postTime)
            )

            activeIslands[effectiveKey] = ActiveIsland(
                id = decision.bridgeId, type = type, postTime = System.currentTimeMillis(), sourcePostTime = sbn.postTime,
                packageName = sbn.packageName, sourceKey = sbn.key, logicalId = effectiveKey,
                groupKey = sbn.groupKey, isGroupSummary = isSummary, generation = generation,
                title = effectiveTitle, text = effectiveText,
                subText = "", lastContentHash = newContentHash, deleteIntent = sbn.notification.deleteIntent
            )
            activeTranslations[effectiveKey] = decision.bridgeId
            reverseTranslations[decision.bridgeId] = effectiveKey
            updatePermanentIsland()

            logUpdateDecision(sbn, type, effectiveKey, decision)
            handlePostNotificationSideEffects(
                effectiveKey, sbn.key, decision.bridgeId, generation, finalConfig,
                type, false, sbn, effectiveTitle, effectiveText,
                isMessageReplacement = decision.cancelBeforeNotify
            )

        } catch (e: Exception) {
            // Undo any predictive permanent-island suppression if posting failed before the
            // active maps were committed. Existing active islands still keep it suppressed.
            updatePermanentIsland()
            Log.e(TAG, "Error processing standard notification", e)
            DiagnosticsStore.record("ERROR", "processing-failed", rawSbn.packageName, e.javaClass.simpleName)
        }
    }

    private fun isDownloadNotification(sbn: StatusBarNotification, title: String, text: String): Boolean {
        val pkg = sbn.packageName.lowercase()
        val titleLower = title.lowercase()
        val textLower = text.lowercase()
        val channelId = sbn.notification.channelId?.lowercase() ?: ""
        
        val isMatch = if (pkg.contains("download") || pkg.contains("downloader") || pkg.contains("chrome") || 
            pkg.contains("browser") || pkg.contains("firefox") || pkg.contains("market") || 
            pkg.contains("vending") || pkg.contains("play.store") || pkg.contains("playstore") || 
            pkg.contains("store") || pkg.contains("fdroid") || pkg.contains("samsungapps") || 
            pkg.contains("mipicks") || pkg.contains("venezia") || pkg.contains("packageinstaller") || 
            pkg.contains("installer") || pkg.contains("gms") || channelId.contains("download") || 
            channelId.contains("install")) {
            true
        } else {
            val extras = sbn.notification.extras
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.lowercase() ?: ""
            val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.lowercase() ?: ""
            
            val downloadKeywords = listOf(
                // English
                "download", "install", "update", "updat", "upload", "transfer",
                // Spanish / Portuguese / Italian / French
                "descarg", "baix", "telecharg", "instal", "actuali", "carg", "subi", "transf",
                // German
                "laden", "gelad", "aktualis",
                // Polish
                "pobier", "pobran", "aktual",
                // Russian / Ukrainian
                "скач", "загруз", "устан", "обнов"
            )
            downloadKeywords.any { 
                titleLower.contains(it) || 
                textLower.contains(it) || 
                subText.contains(it) || 
                infoText.contains(it) 
            }
        }

        Log.d(TAG, "DOWNLOAD classify pkg=$pkg channel=${channelId.hashCode()} hasTitle=${title.isNotBlank()} hasText=${text.isNotBlank()} resolved=$isMatch")
        return isMatch
    }

    private fun hasProgressNotification(sbn: StatusBarNotification, title: String, text: String): Boolean {
        val extras = sbn.notification.extras
        val isDownload = isDownloadNotification(sbn, title, text)
        val isOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        return extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
                extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) ||
                (isDownload && extractTextPercentage(title, text) != null) ||
                (isDownload && isOngoing)
    }

    private fun extractTextPercentage(title: String?, text: String?): Int? {
        val pattern = Regex("""\b(\d{1,3})\s*%""")
        val textMatch = text?.let { pattern.find(it) }
        val titleMatch = title?.let { pattern.find(it) }
        val match = textMatch ?: titleMatch
        if (match != null) {
            val value = match.groupValues[1].toIntOrNull()
            if (value != null && value in 0..100) {
                return value
            }
        }
        return null
    }

    private fun resolveTitle(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
        val pkg = sbn.packageName

        if ((title.isEmpty() || title.equals(pkg, ignoreCase = true)) && !bigTitle.isNullOrEmpty()) {
            return bigTitle
        }
        if (title.equals(pkg, ignoreCase = true)) return ""
        return title
    }

    private fun resolveText(extras: Bundle): String {
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()

        if (!text.isNullOrEmpty()) return text
        return bigText ?: ""
    }

    private fun resolveNotificationContent(sbn: StatusBarNotification): ResolvedNotificationContent {
        val notification = sbn.notification
        val extras = notification.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val isMessageStyle = notification.category == Notification.CATEGORY_MESSAGE ||
                template.contains("MessagingStyle")
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.takeUnless { it.toString().trim().equals(sbn.packageName, ignoreCase = true) }
        return NotificationContentResolver.resolve(
            title = rawTitle,
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            messages = extractMessageContent(notification),
            isMessageStyle = isMessageStyle
        )
    }

    private fun sourceCandidateQuality(sbn: StatusBarNotification): SourceCandidateQuality {
        val notification = sbn.notification
        val extras = notification.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val hasPersistentState = notification.category == Notification.CATEGORY_CALL ||
                notification.category == Notification.CATEGORY_TRANSPORT ||
                notification.category == Notification.CATEGORY_NAVIGATION ||
                notification.category == Notification.CATEGORY_ALARM ||
                template.contains("MediaStyle") ||
                extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
                extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ||
                extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        return NotificationCandidatePolicy.quality(
            NotificationCandidateSignals(
                hasTitle = extras.getCharSequence(Notification.EXTRA_TITLE).isMeaningfulFor(sbn.packageName),
                hasText = extras.getCharSequence(Notification.EXTRA_TEXT).isMeaningfulFor(sbn.packageName),
                hasBigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG).isMeaningfulFor(sbn.packageName),
                hasBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT).isMeaningfulFor(sbn.packageName),
                hasMessagingStyleMessage = extractMessageContent(notification).any { it.text.isMeaningful() },
                hasSupportedPersistentState = hasPersistentState
            )
        )
    }

    private fun CharSequence?.isMeaningful(): Boolean = !this?.toString()?.trim().isNullOrEmpty()

    private fun String?.isMeaningful(): Boolean = !this?.trim().isNullOrEmpty()

    private fun CharSequence?.isMeaningfulFor(packageName: String): Boolean {
        val value = this?.toString()?.trim().orEmpty()
        return value.isNotEmpty() && !value.equals(packageName, ignoreCase = true)
    }

    private fun logSanitizedWhatsappCallback(
        sbn: StatusBarNotification,
        quality: SourceCandidateQuality,
        processingGeneration: Long?
    ) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val notification = sbn.notification
        val extras = notification.extras
        val messagesPresent = extractMessageContent(notification).any { it.text.isMeaningful() }
        Log.d(
            TAG,
            "WHATSAPP_CALLBACK pkg=${sbn.packageName} sourceKeyHash=${sbn.key.hashCode()} id=${sbn.id} " +
                    "tagHash=${sbn.tag?.hashCode()?.toString() ?: "none"} postTime=${sbn.postTime} " +
                    "category=${notification.category ?: "none"} " +
                    "template=${extras.getString(Notification.EXTRA_TEMPLATE) ?: "none"} " +
                    "channelIdHash=${notification.channelId?.hashCode()?.toString() ?: "none"} " +
                    "groupSummary=${if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) "yes" else "no"} " +
                    "title=${if (extras.containsKey(Notification.EXTRA_TITLE)) "yes" else "no"} " +
                    "text=${if (extras.containsKey(Notification.EXTRA_TEXT)) "yes" else "no"} " +
                    "bigTitle=${if (extras.containsKey(Notification.EXTRA_TITLE_BIG)) "yes" else "no"} " +
                    "bigText=${if (extras.containsKey(Notification.EXTRA_BIG_TEXT)) "yes" else "no"} " +
                    "messages=${if (messagesPresent) "yes" else "no"} quality=$quality " +
                    "generation=${processingGeneration ?: "none"}"
        )
    }

    private fun extractMessageContent(notification: Notification): List<MessageContentCandidate> {
        val publicMessages: List<MessageContentCandidate> = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                ?.messages
                ?.map { message ->
                    MessageContentCandidate(
                        sender = message.person?.name?.toString(),
                        text = message.text?.toString()
                    )
                }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (publicMessages.isNotEmpty()) return publicMessages

        return try {
            @Suppress("DEPRECATION")
            notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                ?.mapNotNull { it as? Bundle }
                ?.map { message ->
                    @Suppress("DEPRECATION")
                    val senderPerson = message.getParcelable<Person>("sender_person")
                    MessageContentCandidate(
                        sender = senderPerson?.name?.toString()
                            ?: message.getCharSequence("sender")?.toString(),
                        text = message.getCharSequence("text")?.toString()
                    )
                }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun ensureValidSbn(sbn: StatusBarNotification): StatusBarNotification {
        val content = resolveNotificationContent(sbn)
        val hasProgress = hasProgressNotification(sbn, content.title, content.text)
        if (hasProgress) return sbn

        val pkg = sbn.packageName

        val isSuspicious = (content.title.isEmpty() && !content.hasMessageContent) ||
                content.text.equals(pkg, ignoreCase = true)

        if (isSuspicious) {
            delay(150.milliseconds)
            try {
                val activeList = activeNotifications
                val updatedSbn = activeList?.firstOrNull { it.key == sbn.key }
                if (updatedSbn != null) return updatedSbn
            } catch (_: Exception) { }
        }
        return sbn
    }

    private data class LogicalNotification(
        val logicalId: String,
        val callSession: CallSession? = null
    )

    private fun resolveLogicalNotification(
        sbn: StatusBarNotification,
        type: NotificationType,
        title: String
    ): LogicalNotification {
        if (type == NotificationType.CALL) {
            val signals = buildCallSignals(sbn)
            val classification = callClassifier.classify(signals)
            val now = System.currentTimeMillis()
            val session = callSessionTracker.resolve(
                CallSessionInput(
                    sourceKey = sbn.key,
                    packageName = sbn.packageName,
                    notificationId = sbn.id,
                    notificationTag = sbn.tag,
                    groupKey = sbn.groupKey,
                    participantId = resolveCallParticipantId(sbn),
                    classification = classification,
                    showsChronometer = signals.showsChronometer,
                    chronometerBase = signals.whenTime,
                    observedAt = now
                )
            )
            Log.d(
                TAG,
                "CALL pkg=${sbn.packageName} source=${sbn.key.hashCode()} logical=${session.logicalCallId.hashCode()} " +
                        "state=${session.state} connectedAtSource=${session.connectedAtSource} " +
                        "actions=${signals.actions.size} chronometer=${signals.showsChronometer} reason=${classification.reason}"
            )
            DiagnosticsStore.record(
                classification = "CALL",
                action = "classified",
                packageName = sbn.packageName,
                reason = classification.reason,
                callState = session.state.name
            )
            return LogicalNotification(session.logicalCallId, session)
        }

        if (type == NotificationType.MESSAGE) {
            sourceToLogicalKeys[sbn.key]?.let { existingLogicalId ->
                val existing = activeIslands[existingLogicalId]
                if (existing?.type == NotificationType.MESSAGE && existing.packageName == sbn.packageName) {
                    return LogicalNotification(existingLogicalId)
                }
            }
            val identity = resolveMessageIdentity(sbn)
            Log.d(
                TAG,
                "MESSAGE pkg=${sbn.packageName} key=${sbn.key.hashCode()} group=${sbn.groupKey?.hashCode()} " +
                        "summary=${isGroupSummary(sbn)} logical=${identity.logicalId.hashCode()} identity=${identity.source}"
            )
            return LogicalNotification(identity.logicalId)
        }

        if (type == NotificationType.DOWNLOAD || type == NotificationType.PROGRESS) {
            val candidates = activeIslands.values.filter {
                it.packageName == sbn.packageName &&
                        (it.type == NotificationType.DOWNLOAD || it.type == NotificationType.PROGRESS)
            }
            val existing = if (candidates.size == 1) candidates.first() else candidates.find { it.title == title }
            if (existing != null) return LogicalNotification(existing.logicalId)
        }

        return LogicalNotification(sbn.key)
    }

    private fun resolveMessageIdentity(sbn: StatusBarNotification): MessageIdentity {
        val extras = sbn.notification.extras
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        return messageResolver.resolve(
            MessageNotificationSignals(
                packageName = sbn.packageName,
                notificationId = sbn.id,
                notificationTag = sbn.tag,
                shortcutId = sbn.notification.shortcutId,
                locusId = sbn.notification.locusId?.id,
                conversationTitle = conversationTitle,
                isGroupSummary = isGroupSummary(sbn)
            )
        )
    }

    private fun resolveCallParticipantId(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        val person = try {
            extras.getParcelable(Notification.EXTRA_CALL_PERSON, Person::class.java)
                ?: extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
                ?: extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java)?.firstOrNull()
        } catch (_: Exception) {
            null
        }
        val identity = person?.let(::personIdentity)
        return identity?.hashCode()?.toUInt()?.toString(16)
    }

    private fun personIdentity(person: Person): String? {
        return person.key?.takeIf { it.isNotBlank() }
            ?: person.uri?.takeIf { it.isNotBlank() }
            ?: person.name?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun isGroupSummary(sbn: StatusBarNotification): Boolean {
        return (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
    }

    private fun isSourceNotificationActive(sourceKey: String): Boolean {
        return try {
            activeNotifications?.any { it.key == sourceKey } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun bindSourceToLogicalKey(logicalKey: String, sourceKey: String, previous: ActiveIsland?) {
        if (previous != null && previous.sourceKey != sourceKey) {
            sourceToLogicalKeys.remove(previous.sourceKey, logicalKey)
        }
        sourceToLogicalKeys[sourceKey] = logicalKey
    }

    private fun detachReassignedSource(sourceKey: String, newLogicalKey: String) {
        val oldLogicalKey = sourceToLogicalKeys[sourceKey] ?: return
        if (oldLogicalKey == newLogicalKey) return
        val oldIsland = activeIslands[oldLogicalKey] ?: run {
            sourceToLogicalKeys.remove(sourceKey, oldLogicalKey)
            return
        }
        Log.d(
            TAG,
            "UPDATE source reassigned source=${sourceKey.hashCode()} old=${oldLogicalKey.hashCode()} new=${newLogicalKey.hashCode()}"
        )
        NotificationManagerCompat.from(this).cancel(oldIsland.id)
        cleanupCache(oldLogicalKey)
    }

    private fun refreshSourceAlias(
        logicalKey: String,
        sbn: StatusBarNotification,
        previous: ActiveIsland?
    ) {
        if (previous == null) return
        bindSourceToLogicalKey(logicalKey, sbn.key, previous)
        activeIslands[logicalKey] = previous.copy(
            sourceKey = sbn.key,
            sourcePostTime = sbn.postTime,
            groupKey = sbn.groupKey,
            deleteIntent = sbn.notification.deleteIntent
        )
    }

    private fun ActiveIsland.toPreviousPresentation(): PreviousIslandPresentation {
        return PreviousIslandPresentation(logicalId, id, lastContentHash)
    }

    private fun allocateMessageBridgeId(
        logicalKey: String,
        generation: Long,
        contentHashSeed: Int,
        previousBridgeId: Int
    ): Int {
        var attempt = 0
        while (true) {
            val candidate = MessageBridgeIdPolicy.candidate(logicalKey, generation, contentHashSeed, attempt++)
            if (candidate != previousBridgeId && !reverseTranslations.containsKey(candidate)) return candidate
        }
    }

    private fun prepareMessageReplacement(
        logicalKey: String,
        previous: ActiveIsland?,
        decision: IslandUpdateDecision,
        generation: Long
    ) {
        if (previous?.type != NotificationType.MESSAGE || !decision.cancelBeforeNotify) return

        timeoutJobs.remove(logicalKey)?.cancel()
        internalBridgeReplacements.mark(
            bridgeId = previous.id,
            logicalId = logicalKey,
            generation = generation,
            now = System.currentTimeMillis()
        )
        reverseTranslations.remove(previous.id, logicalKey)
        NotificationManagerCompat.from(this).cancel(previous.id)
        Log.d(
            TAG,
            "MESSAGE REPLACE logicalId=${logicalKey.hashCode()} oldBridgeId=${previous.id} " +
                    "newBridgeId=${decision.bridgeId} generation=$generation"
        )
        DiagnosticsStore.record(NotificationType.MESSAGE.name, "replace", previous.packageName)
    }

    private fun ensureIslandCapacity(
        previous: ActiveIsland?,
        type: NotificationType,
        packageName: String,
        logical: LogicalNotification,
        sourceKey: String,
        logicalKey: String
    ): Boolean {
        if (previous != null || activeIslands.size < MAX_ISLANDS) return true
        handleLimitReached(type, packageName)
        if (activeIslands.size < MAX_ISLANDS) return true
        logical.callSession?.let { callSessionTracker.end(it.logicalCallId) }
        sourceToLogicalKeys.remove(sourceKey, logicalKey)
        return false
    }

    private fun computeContentHash(
        sbn: StatusBarNotification,
        type: NotificationType,
        title: String,
        text: String,
        renderedJson: String?,
        callSession: CallSession?
    ): Int {
        val notification = sbn.notification
        val extras = notification.extras
        val actionState = (notification.actions ?: emptyArray()).map {
            listOf(
                it.semanticAction,
                it.title?.toString()?.hashCode(),
                it.actionIntent?.creatorPackage?.hashCode(),
                it.getIcon()?.hashCode()
            ).hashCode()
        }
        val visualState = listOf(
            visualObjectIdentity(notification.getLargeIcon()),
            visualObjectIdentity(notification.smallIcon),
            visualObjectIdentity(extras.get(Notification.EXTRA_PICTURE)),
            visualObjectIdentity(extras.get(Notification.EXTRA_LARGE_ICON)),
            resolvePersonVisualIdentity(extras)
        ).hashCode()
        return listOf(
            type,
            title,
            text,
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            actionState,
            extras.getInt(Notification.EXTRA_PROGRESS, 0),
            extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
            extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
            notification.`when`.takeIf { type == NotificationType.TIMER },
            notification.shortcutId,
            visualState,
            callSession?.state,
            callSession?.connectedAt,
            normalizeRenderedJson(type, renderedJson)
        ).hashCode()
    }

    private fun normalizeRenderedJson(type: NotificationType, renderedJson: String?): String? {
        val semanticJson = RenderedJsonNormalizer.normalize(renderedJson)
        return if (type == NotificationType.CALL) normalizeCallRenderedJson(semanticJson) else semanticJson
    }

    private fun normalizeCallRenderedJson(renderedJson: String?): String? {
        if (renderedJson == null) return null
        return renderedJson.replace(
            Regex("""(\"timer(?:When|Total|SystemCurrent)\"\s*:\s*)-?\d+"""),
            "$1<time>"
        )
    }

    private fun visualObjectIdentity(value: Any?): Int? {
        return when (value) {
            is android.graphics.Bitmap -> listOf(value.generationId, value.width, value.height).hashCode()
            is android.graphics.drawable.Icon -> value.toString().hashCode()
            else -> value?.hashCode()
        }
    }

    private fun resolvePersonVisualIdentity(extras: Bundle): Int? {
        val directPerson = try {
            extras.getParcelable(Notification.EXTRA_CALL_PERSON, Person::class.java)
                ?: extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        } catch (_: Exception) {
            null
        }
        directPerson?.icon?.let { return it.toString().hashCode() }

        return try {
            @Suppress("DEPRECATION")
            val message = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle
            @Suppress("DEPRECATION")
            val sender = message?.getParcelable<Person>("sender_person")
            sender?.icon?.toString()?.hashCode()
        } catch (_: Exception) {
            null
        }
    }

    private fun logUpdateDecision(
        sbn: StatusBarNotification,
        type: NotificationType,
        logicalKey: String,
        decision: IslandUpdateDecision
    ) {
        Log.d(
            TAG,
            "UPDATE pkg=${sbn.packageName} type=$type logical=${logicalKey.hashCode()} bridgeId=${decision.bridgeId} " +
                    "kind=${decision.kind} alertOnce=${decision.onlyAlertOnce} cancelBeforeNotify=${decision.cancelBeforeNotify}"
        )
        Log.d(
            TAG,
            "FLOAT reason=${decision.presentationReason} islandFirstFloat=${decision.presentationReason.mayAutoExpand} " +
                    "logical=${logicalKey.hashCode()} bridgeId=${decision.bridgeId}"
        )
        DiagnosticsStore.record(
            classification = type.name,
            action = decision.kind.name.lowercase(),
            packageName = sbn.packageName
        )
    }

    private fun logMessageLifecycle(
        sbn: StatusBarNotification,
        type: NotificationType,
        logicalKey: String,
        decision: IslandUpdateDecision,
        previous: ActiveIsland?
    ) {
        if (type != NotificationType.MESSAGE) return
        when (decision.kind) {
            IslandPresentationKind.NEW -> Log.d(
                TAG,
                "MESSAGE NEW package=${sbn.packageName} sourceKey=${sbn.key.hashCode()} " +
                        "bridgeId=${decision.bridgeId} logicalId=${logicalKey.hashCode()}"
            )
            IslandPresentationKind.UPDATE -> Log.d(
                TAG,
                "MESSAGE UPDATE package=${sbn.packageName} oldSourceKey=${previous?.sourceKey?.hashCode()} " +
                        "newSourceKey=${sbn.key.hashCode()} bridgeId=${decision.bridgeId} logicalId=${logicalKey.hashCode()}"
            )
            IslandPresentationKind.UNCHANGED -> Log.d(
                TAG,
                "MESSAGE UNCHANGED package=${sbn.packageName} sourceKey=${sbn.key.hashCode()} " +
                        "bridgeId=${decision.bridgeId} logicalId=${logicalKey.hashCode()}"
            )
        }
    }

    private fun buildCallSignals(sbn: StatusBarNotification): CallNotificationSignals {
        val n = sbn.notification
        val extras = n.extras
        val callType = try {
            if (extras.containsKey(Notification.EXTRA_CALL_TYPE)) {
                extras.getInt(Notification.EXTRA_CALL_TYPE, CallNotificationClassifier.CALL_TYPE_UNKNOWN)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

        return CallNotificationSignals(
            category = n.category,
            template = extras.getString(Notification.EXTRA_TEMPLATE),
            callType = callType,
            showsChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
            whenTime = n.`when`,
            actions = (n.actions ?: emptyArray()).map {
                CallActionSignal(
                    title = it.title?.toString().orEmpty(),
                    semanticAction = it.semanticAction,
                    hasPendingIntent = it.actionIntent != null
                )
            },
            isOngoingEvent = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        )
    }

    private fun detectNotificationType(sbn: StatusBarNotification): NotificationType {
        val n = sbn.notification
        val extras = n.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
        val isCall = callClassifier.classify(buildCallSignals(sbn)).isCall
        val isNav = n.category == Notification.CATEGORY_NAVIGATION || sbn.packageName.let { it.contains("maps") || it.contains("waze") }
        val isTimer = (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) || n.category == Notification.CATEGORY_ALARM) && n.`when` > 0
        val isMedia = template.contains("MediaStyle") || n.category == Notification.CATEGORY_TRANSPORT
        val isMessage = n.category == Notification.CATEGORY_MESSAGE || template.contains("MessagingStyle")
        
        val content = resolveNotificationContent(sbn)
        val title = content.title
        val text = content.text
        val isDownload = isDownloadNotification(sbn, title, text)
        val hasProgress = hasProgressNotification(sbn, title, text)

        return when {
            isCall -> NotificationType.CALL
            isNav -> NotificationType.NAVIGATION
            isTimer -> NotificationType.TIMER
            isMedia -> NotificationType.MEDIA
            isMessage -> NotificationType.MESSAGE
            hasProgress -> {
                if (isDownload) {
                    NotificationType.DOWNLOAD
                } else {
                    NotificationType.PROGRESS
                }
            }
            else -> NotificationType.STANDARD
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun postStandardNotification(
        sbn: StatusBarNotification,
        logicalKey: String,
        bridgeId: Int,
        data: HyperIslandData,
        shouldAlertOnce: Boolean
    ): Notification {
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_went_wrong))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(shouldAlertOnce)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)

        val extras = Bundle()
        extras.putString(EXTRA_ORIGINAL_KEY, logicalKey)
        builder.addExtras(extras)
        builder.addExtras(data.resources)

        val hasPermission = com.d4viddf.hyperbridge.util.XiaomiNotificationHelper.hasFocusPermission(this)
        if (!hasPermission) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("open_troubleshoot", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
            builder.addAction(
                android.R.drawable.ic_dialog_info,
                getString(R.string.troubleshoot_featured_notification),
                pendingIntent
            )
        } else {
            // Use the source PendingIntent directly. Notification trampolines are blocked on
            // modern Android and could prevent message Islands from opening their app.
            sbn.notification.contentIntent?.let(builder::setContentIntent)
        }

        val notification = builder.build()
        notification.extras.putString("miui.focus.param", data.jsonParam)

        // Same-ID notify is both the create and update operation. HyperOS can retain the existing
        // Focus island; cancel-before-notify would force a close/open presentation.
        dispatchIslandNotification(bridgeId, notification, shouldAlertOnce)
        return notification
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun dispatchIslandNotification(
        bridgeId: Int,
        notification: Notification,
        isUpdate: Boolean
    ) {
        if (isUpdate) {
            NotificationManagerCompat.from(this).notify(bridgeId, notification)
        } else {
            ShizukuManager.notifyInPlace(this, bridgeId, notification)
        }
    }

    // =========================================================================
    //  HELPERS & SETUP
    // =========================================================================

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val notifChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.channel_active_islands), NotificationManager.IMPORTANCE_HIGH).apply {
            description = getString(R.string.channel_active_islands_desc)
            setSound(null, null); enableVibration(false); setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(notifChannel)

        val widgetChannel = NotificationChannel(WIDGET_CHANNEL_ID, getString(R.string.channel_widgets), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.channel_widgets_desc)
            setSound(null, null); enableVibration(false); setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(widgetChannel)

        val liveUpdateChannel = NotificationChannel(LIVE_UPDATE_CHANNEL_ID, getString(R.string.channel_live_updates), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = getString(R.string.channel_live_updates_desc)
            setSound(null, null); enableVibration(false); setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(liveUpdateChannel)

        val watchRelayChannel = NotificationChannel(WATCH_RELAY_CHANNEL_ID, getString(R.string.channel_watch_relay), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.channel_watch_relay_desc)
            setSound(null, null); enableVibration(false); setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(watchRelayChannel)
    }

    private fun shouldProcessWidgetUpdate(widgetId: Int, config: WidgetConfig): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = widgetUpdateDebouncer[widgetId] ?: 0L
        val throttleTime = if (config.renderMode == WidgetRenderMode.SNAPSHOT) 1500L else 200L
        if (now - lastTime < throttleTime) return false
        widgetUpdateDebouncer[widgetId] = now
        return true
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun processSingleWidget(widgetId: Int, config: WidgetConfig) {
        try {
            val data = widgetTranslator.translate(widgetId)
            postWidgetNotification(WIDGET_ID_BASE + widgetId, data)
            activeWidgets.add(widgetId)
            updatePermanentIsland()
        } catch (e: Exception) { Log.e(TAG, "Failed widget $widgetId", e) }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun postWidgetNotification(notificationId: Int, data: HyperIslandData) {
        val builder = NotificationCompat.Builder(this, WIDGET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Widget Overlay").setContentText(getString(R.string.widget_went_wrong))
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true)
            .setOnlyAlertOnce(true).setDefaults(0).setSound(null).setVibrate(null)
            .addExtras(data.resources)

        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(pendingIntent)

        val notification = builder.build()
        notification.extras.putString("miui.focus.param", data.jsonParam)
        ShizukuManager.notify(this, notificationId, notification)
    }

    private fun handleLimitReached(newType: NotificationType, newPkg: String) {
        val oldest = activeIslands.minByOrNull { it.value.postTime } ?: return

        when (currentMode) {
            IslandLimitMode.FIRST_COME -> {
                // Ignore the new notification by removing it immediately (or simply returning, but returning here means the caller won't add it)
                // The logic in the caller says:
                // if (!isUpdate && activeIslands.size >= MAX_ISLANDS) {
                //    handleLimitReached(type, sbn.packageName)
                //    if (activeIslands.size >= MAX_ISLANDS) return
                // }
                // So if we do nothing here, the size remains >= MAX_ISLANDS, and the caller will return.
                return
            }
            IslandLimitMode.MOST_RECENT -> {
                NotificationManagerCompat.from(this).cancel(oldest.value.id)
                cleanupCache(oldest.key)
            }
            IslandLimitMode.PRIORITY -> {
                // Check if newPkg has higher priority than existing ones.
                // Priority is determined by its index in appPriorityList (lower index = higher priority).
                // If it's not in the list, it has the lowest priority (Int.MAX_VALUE).
                val newPriority = appPriorityList.indexOf(newPkg).let { if (it == -1) Int.MAX_VALUE else it }
                
                // Find the existing active island with the lowest priority (highest index value)
                val lowestPriorityIsland = activeIslands.maxByOrNull {
                    appPriorityList.indexOf(it.value.packageName).let { idx -> if (idx == -1) Int.MAX_VALUE else idx }
                }

                if (lowestPriorityIsland != null) {
                    val lowestPriority = appPriorityList.indexOf(lowestPriorityIsland.value.packageName).let { if (it == -1) Int.MAX_VALUE else it }
                    if (newPriority <= lowestPriority) {
                        // The new notification has equal or higher priority than the lowest existing one.
                        // Remove the lowest priority existing notification.
                        NotificationManagerCompat.from(this).cancel(lowestPriorityIsland.value.id)
                        cleanupCache(lowestPriorityIsland.key)
                    } else {
                        // The new notification has lower priority than all existing ones. Do nothing, which will ignore it.
                        return
                    }
                }
            }
        }
    }

    private fun isJunkNotification(
        sbn: StatusBarNotification,
        content: ResolvedNotificationContent,
        type: NotificationType
    ): Boolean {
        val notification = sbn.notification
        val extras = notification.extras
        val pkg = sbn.packageName

        val title = content.title
        val text = content.text

        val hasProgress = hasProgressNotification(sbn, title, text)
        val isSpecial = notification.category == Notification.CATEGORY_TRANSPORT || callClassifier.classify(buildCallSignals(sbn)).isCall ||
                notification.category == Notification.CATEGORY_NAVIGATION || extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true
        return NotificationAcceptancePolicy.isJunk(
            NotificationAcceptanceSignals(
                packageName = pkg,
                title = title,
                text = text,
                hasMessageContent = content.hasMessageContent,
                hasProgressOrSpecialState = hasProgress || isSpecial,
                containsBlockedTerm = globalBlockedTerms.any { "$title $text".contains(it, true) },
                isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
                isMessageType = type == NotificationType.MESSAGE
            )
        )
    }

    private fun getCachedAppLabel(pkg: String): String {
        synchronized(appLabelCache) {
            appLabelCache.get(pkg)?.let { return it }
        }
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            ""
        }
        synchronized(appLabelCache) { appLabelCache.put(pkg, label) }
        return label
    }

    private fun shouldIgnore(packageName: String): Boolean = packageName == this.packageName || packageName == "android" || packageName.contains("miui.notification")
    private fun isAppAllowed(packageName: String): Boolean = allowedPackageSet.contains(packageName)

    private var syncJob: Job? = null

    override fun onListenerConnected() { 
        Log.i(TAG, "HyperBridge Service Connected")
        DiagnosticsStore.setServiceConnected(true)
        DiagnosticsStore.record("SERVICE", "connected")
        syncNotifications(refresh = true)
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (true) {
                delay(60_000) // 1 minute periodic sync
                // Screen off: nothing to keep in sync visually, and SCREEN_ON runs a full
                // refresh sync on wake — skip the tick instead of waking up all night.
                if (isScreenOn) {
                    syncNotifications()
                }
            }
        }
    }

    private fun syncNotifications(refresh: Boolean = false) {
        val now = System.currentTimeMillis()
        recentlyRemovedKeys.entries.removeIf { now - it.value.observedAt > 10000 }
        intentionallyRemovedKeys.entries.removeIf { now - it.value > 10_000 }
        internalBridgeReplacements.prune(now)
        expiredIslands.prune(now)
        callSessionTracker.pruneStale(now)
        sourceToLogicalKeys.entries.removeIf { !activeIslands.containsKey(it.value) }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val currentNotifications = activeNotifications ?: return@launch
                val selectedPackages = preferences.allowedPackagesFlow.first()
                allowedPackageSet = selectedPackages
                val systemNotificationKeys = currentNotifications.map { it.key }.toSet()
                val postedWidgetIds = currentNotifications
                    .filter { it.packageName == packageName && BridgeNotificationChannels.isWidget(it.notification.channelId) }
                    .mapNotNull { (it.id - WIDGET_ID_BASE).takeIf { widgetId -> widgetId >= 0 } }
                    .toSet()
                activeWidgets.retainAll(postedWidgetIds)
                activeWidgets.addAll(postedWidgetIds)
                val savedWidgetIds = preferences.savedWidgetIdsFlow.first().toSet()
                widgetUpdateDebouncer.keys.removeIf { it !in savedWidgetIds }
                dismissedWidgetIds.removeIf { it !in savedWidgetIds }

                var nativeChanged = false
                for (sbn in currentNotifications) {
                    if (sbn.packageName != packageName) {
                        val extras = sbn.notification.extras
                        var isNative = false
                        if (extras != null) {
                            if (extras.containsKey("miui.focus.param") || extras.containsKey("miui.system.focus.param")) {
                                isNative = true
                            }
                            val template = extras.getString(Notification.EXTRA_TEMPLATE)
                            if (template == "androidx.media.app.NotificationCompat\$MediaStyle" ||
                                template == "android.app.Notification\$MediaStyle") {
                                isNative = true
                            }
                        }
                        if (isNative) {
                            if (nativeIslands.add(sbn.key)) nativeChanged = true
                        } else {
                            if (nativeIslands.remove(sbn.key)) nativeChanged = true
                        }
                    }
                }

                val currentNatives = nativeIslands.toList()
                for (key in currentNatives) {
                    if (!systemNotificationKeys.contains(key)) {
                        if (nativeIslands.remove(key)) nativeChanged = true
                    }
                }
                if (nativeChanged) updatePermanentIsland()

                val currentKeys = currentNotifications.map { it.key }.toSet()
                
                val keysToRemove = mutableListOf<String>()
                for ((logicalKey, activeIsland) in activeIslands) {
                    if (!currentKeys.contains(activeIsland.sourceKey)) {
                        val appConfig = preferences.getAppIslandConfigSync(activeIsland.packageName)
                        val globalConfig = preferences.getGlobalConfigSync()
                        val finalConfig = appConfig.mergeWith(globalConfig)

                        val forceDismiss = activeIsland.type == NotificationType.CALL || 
                                           activeIsland.type == NotificationType.MEDIA || 
                                           activeIsland.type == NotificationType.NAVIGATION

                        val canBeIntentionallyMirrored = activeIsland.type == NotificationType.MESSAGE ||
                                activeIsland.type == NotificationType.STANDARD

                        // Only message/standard notifications use the unprivileged cancel-and-mirror
                        // fallback. Other types should clean up when their source notification is gone.
                        if (!forceDismiss && canBeIntentionallyMirrored && finalConfig.removeOriginalNotification == true) {
                            continue
                        }

                        if (finalConfig.dismissWithOriginal == true || forceDismiss) {
                            keysToRemove.add(logicalKey)
                        }
                    }
                }

                for (key in keysToRemove) {
                    Log.d(TAG, "Sync: Found stuck notification $key, removing.")
                    val hyperId = activeTranslations[key]
                    if (hyperId != null) {
                        try {
                            NotificationManagerCompat.from(this@NotificationReaderService).cancel(hyperId)
                        } catch (_: Exception) {}
                    }
                    cleanupCache(key)
                }

                // Bridged notifications we no longer track (e.g. left over from a service restart)
                // keep their island slot occupied forever, since island-swipe never removes them.
                for (sbn in currentNotifications) {
                    if (sbn.packageName != packageName) continue
                    val id = sbn.id
                    if (id == PermanentIslandManager.PERMANENT_BRIDGE_ID) continue
                    if (BridgeNotificationChannels.isWidget(sbn.notification.channelId)) continue
                    if (BridgeNotificationChannels.isWatchRelay(sbn.notification.channelId)) continue
                    if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                    if (reverseTranslations.containsKey(id)) continue
                    if (System.currentTimeMillis() - sbn.postTime < 5000) continue
                    Log.d(TAG, "Sync: Reaping orphan bridge notification $id")
                    try {
                        NotificationManagerCompat.from(this@NotificationReaderService).cancel(id)
                    } catch (_: Exception) {}
                }

                // Full type detection can inspect actions, content and app metadata. Run it only
                // for discrete recovery passes, never for the ordinary 60-second bookkeeping tick.
                if (OngoingRecoveryPolicy.shouldClassifyShadeNotifications(refresh)) {
                    val eligibleSources = currentNotifications.filter {
                        it.packageName != packageName &&
                                it.packageName in selectedPackages &&
                                !shouldIgnore(it.packageName)
                    }
                    val mappedSourceKeys = sourceToLogicalKeys.keys + activeIslands.values.map { it.sourceKey }
                    val recoverableSources = eligibleSources.map { it to detectNotificationType(it) }
                        .filter { (_, type) -> type != NotificationType.MESSAGE && type != NotificationType.STANDARD }
                    val reconciliation = NotificationReconciliation.plan(
                        ReconciliationInput(
                            activeLogicalSources = activeIslands.mapValues { it.value.sourceKey },
                            currentSourceKeys = systemNotificationKeys,
                            trackedBridgeIds = reverseTranslations.keys,
                            postedBridgeIds = currentNotifications.filter { it.packageName == packageName }.map { it.id }.toSet(),
                            recoverableSourceKeys = recoverableSources.map { it.first.key }.toSet(),
                            mappedSourceKeys = mappedSourceKeys
                        )
                    )
                    recoverableSources
                        .filter { (source, _) -> source.key in reconciliation.missingSourceKeys }
                        .forEach { (source, type) ->
                            Log.d(TAG, "RECONCILE RECOVER_ONGOING sourceKey=${source.key.hashCode()}")
                            DiagnosticsStore.record(type.name, "recover-ongoing", source.packageName)
                            enqueueSourceNotification(source, recovery = true)
                        }
                }

                val islandPresent = currentNotifications.any {
                    it.packageName == packageName && it.id == PermanentIslandManager.PERMANENT_BRIDGE_ID
                }
                permanentIslandManager.reconcile(
                    activeIslands.size + activeWidgets.size,
                    nativeIslands.isNotEmpty(),
                    islandPresent,
                    refresh
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing notifications", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        syncJob?.cancel()
        syncJob = null
        DiagnosticsStore.setServiceConnected(false)
        DiagnosticsStore.record("SERVICE", "disconnected")
        try {
            requestRebind(android.content.ComponentName(this, NotificationReaderService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to request listener rebind", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(systemReceiver)
        syncJob?.cancel()
        DiagnosticsStore.setServiceConnected(false)
        callSessionTracker.clear()
        processingJobs.values.forEach { it.cancel() }
        timeoutJobs.values.forEach { it.cancel() }
        removalJobs.values.forEach { it.cancel() }
        processingJobs.clear()
        processingPostTimes.clear()
        timeoutJobs.clear()
        removalJobs.clear()
        sourceProcessingGeneration.clear()
        expiredIslands.clear()
        activeIslands.clear()
        activeTranslations.clear()
        reverseTranslations.clear()
        sourceToLogicalKeys.clear()
        recentlyRemovedKeys.clear()
        intentionallyRemovedKeys.clear()
        internalBridgeReplacements.clear()
        nativeIslands.clear()
        widgetUpdateDebouncer.clear()
        dismissedWidgetIds.clear()
        activeWidgets.clear()
        synchronized(appLabelCache) { appLabelCache.evictAll() }
        serviceScope.cancel() 
    }
}
