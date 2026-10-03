package com.sykeptical.hyperpop.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Parcel
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.data.db.AppDatabase
import com.sykeptical.hyperpop.service.vpn.VpnIslandController
import com.sykeptical.hyperpop.models.ActiveIsland
import com.sykeptical.hyperpop.models.HyperIslandData
import com.sykeptical.hyperpop.models.IslandConfig
import com.sykeptical.hyperpop.models.IslandLimitMode
import com.sykeptical.hyperpop.models.NavContent
import com.sykeptical.hyperpop.models.NotificationType
import com.sykeptical.hyperpop.service.translators.CallTranslator
import com.sykeptical.hyperpop.service.translators.LiveUpdateTranslator
import com.sykeptical.hyperpop.service.translators.MediaTranslator
import com.sykeptical.hyperpop.service.translators.MessageTranslator
import com.sykeptical.hyperpop.service.translators.NavTranslator
import com.sykeptical.hyperpop.service.translators.ProgressTranslator
import com.sykeptical.hyperpop.service.translators.DownloadTranslator
import com.sykeptical.hyperpop.service.translators.IslandCompactLayout
import com.sykeptical.hyperpop.service.translators.IslandFloatingPresentationPolicy
import com.sykeptical.hyperpop.service.translators.StandardTranslator
import com.sykeptical.hyperpop.service.translators.TimerTranslator
import com.sykeptical.hyperpop.service.translators.VoiceMessageTranslator
import com.sykeptical.hyperpop.service.voice.MediaBackedVoiceClassifier
import com.sykeptical.hyperpop.service.voice.MediaBackedVoiceSignals
import com.sykeptical.hyperpop.service.voice.VoiceFocusProgressPatch
import com.sykeptical.hyperpop.service.voice.VoicePlaybackDecorationPolicy
import com.sykeptical.hyperpop.service.voice.VoicePlaybackDetector
import com.sykeptical.hyperpop.service.voice.VoicePlaybackSignals
import com.sykeptical.hyperpop.service.voice.VoicePlaybackUpdateGate
import com.sykeptical.hyperpop.service.voice.VoicePlaybackUpdateSample
import com.sykeptical.hyperpop.service.translators.ScreenRecordingTranslator
import com.sykeptical.hyperpop.debug.AgentDebugLog
import com.sykeptical.hyperpop.service.translators.ScreenRecordingSavedTranslator
import com.sykeptical.hyperpop.service.recording.ScreenRecordingClassifier
import com.sykeptical.hyperpop.service.recording.ScreenRecordingControlBackend
import com.sykeptical.hyperpop.service.recording.ScreenRecordingSavedIdentity
import com.sykeptical.hyperpop.service.recording.ScreenRecordingSemanticFingerprint
import com.sykeptical.hyperpop.service.recording.ScreenRecordingSession
import com.sykeptical.hyperpop.service.recording.ScreenRecordingSessionInput
import com.sykeptical.hyperpop.service.recording.ScreenRecordingSessionTracker
import com.sykeptical.hyperpop.service.recording.ScreenRecordingSignals
import com.sykeptical.hyperpop.service.recording.ScreenRecordingTimeoutPolicy
import com.sykeptical.hyperpop.service.recording.XiaomiScreenRecordingControlBackend
import com.sykeptical.hyperpop.island.backend.HookConfigSync
import com.sykeptical.hyperpop.island.backend.IslandBackend
import com.sykeptical.hyperpop.island.backend.IslandMetadata
import com.sykeptical.hyperpop.island.backend.IslandProtocol
import com.sykeptical.hyperpop.island.backend.IslandVisualExtras
import com.sykeptical.hyperpop.integration.xiaomi.buildJsonParam
import com.sykeptical.hyperpop.service.visual.AppIconPalette
import com.sykeptical.hyperpop.models.IslandGlowResolver
import com.sykeptical.hyperpop.models.IslandVisualMetadata
import com.sykeptical.hyperpop.models.CallStage
import com.sykeptical.hyperpop.models.MessageEventFingerprint
import com.sykeptical.hyperpop.models.MessageEventFingerprintSource
import com.sykeptical.hyperpop.service.call.CallActionSignal
import com.sykeptical.hyperpop.service.call.CallClassification
import com.sykeptical.hyperpop.service.call.CallNotificationClassifier
import com.sykeptical.hyperpop.service.call.CallNotificationSignals
import com.sykeptical.hyperpop.service.call.CallReplacementPolicy
import com.sykeptical.hyperpop.service.call.CallSession
import com.sykeptical.hyperpop.service.call.CallSessionInput
import com.sykeptical.hyperpop.service.call.CallState
import com.sykeptical.hyperpop.service.call.CallSessionTracker
import com.sykeptical.hyperpop.service.call.CallStageVisibilityPolicy
import com.sykeptical.hyperpop.service.diagnostics.DiagnosticsStore
import com.sykeptical.hyperpop.service.download.ChromeNotificationPolicy
import com.sykeptical.hyperpop.service.download.DownloadPausePolicy
import com.sykeptical.hyperpop.service.download.DownloadReplacementPolicy
import com.sykeptical.hyperpop.service.download.DownloadSessionInput
import com.sykeptical.hyperpop.service.download.DownloadSessionTracker
import com.sykeptical.hyperpop.service.message.MessageEventFallbackTracker
import com.sykeptical.hyperpop.service.message.MessageEventSignals
import com.sykeptical.hyperpop.service.message.MessageIdentity
import com.sykeptical.hyperpop.service.message.MessageNotificationResolver
import com.sykeptical.hyperpop.service.message.MessageNotificationSignals
import com.sykeptical.hyperpop.service.message.MessagePresentationFamilyTracker
import com.sykeptical.hyperpop.service.message.MessagePresentationSource
import com.sykeptical.hyperpop.service.message.MessageSourceQuality
import com.sykeptical.hyperpop.service.message.MessagingEventSignals
import com.sykeptical.hyperpop.service.message.isMessagingEvent
import com.sykeptical.hyperpop.receiver.LoginCodeCopyReceiver
import com.sykeptical.hyperpop.service.logincode.LoginCodeExtractor
import com.sykeptical.hyperpop.service.logincode.LoginCodePresentation
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * HyperPop's notification semantics and lifecycle engine.
 *
 * This is deliberately a normal object, not a notification-listener service. Source notifications
 * and removals are supplied by the injected SystemUI hook through a narrow processing bridge.
 */
class NotificationProcessingEngine private constructor(
    appContext: Context,
    private val activeNotificationsProvider: () -> Array<StatusBarNotification>,
    private val cancelSourceNotificationByKey: (String) -> Unit,
    private val islandBackend: IslandBackend,
) : ContextWrapper(appContext) {

    companion object {
        const val ACTION_PERFORM_MIGRATION = "com.sykeptical.hyperpop.ACTION_PERFORM_MIGRATION"
        private val GMAIL_PACKAGES = setOf("com.google.android.gm")
        private const val REASON_CLICK = NotificationLifecyclePolicy.REASON_CLICK
        private const val LEGACY_PERMANENT_ANCHOR_ID = 0x48425049
        private const val LEGACY_PERMANENT_ANCHOR_TOKEN = "permanent-anchor"

        fun create(
            appContext: Context,
            activeNotificationsProvider: () -> Array<StatusBarNotification>,
            cancelSourceNotificationByKey: (String) -> Unit,
            islandBackend: IslandBackend,
        ): NotificationProcessingEngine = NotificationProcessingEngine(
            appContext.applicationContext,
            activeNotificationsProvider,
            cancelSourceNotificationByKey,
            islandBackend,
        ).apply { start() }
    }

    private val TAG = "HyperPopDebug"
    private val EXTRA_ORIGINAL_KEY = "hyper_original_key"
    /** Leaves room for the source notification already travelling in the same Binder call. */
    private val SOURCE_FOCUS_DECORATION_LIMIT = 256 * 1024

    // --- CHANNELS ---
    private val NOTIFICATION_CHANNEL_ID = BridgeNotificationChannels.ACTIVE
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
    private data class RemovedSource(
        val observedAt: Long,
        val sourcePostTime: Long,
        val reason: Int
    )

    private val recentlyRemovedKeys = ConcurrentHashMap<String, RemovedSource>()
    private val nativeIslands = ConcurrentHashMap.newKeySet<String>()
    private val activeIslands = ConcurrentHashMap<String, ActiveIsland>()
    private val activeTranslations = ConcurrentHashMap<String, Int>()
    private val reverseTranslations = ConcurrentHashMap<Int, String>()
    private val internalBridgeReplacements = InternalBridgeReplacementRegistry()
    private val sourceToLogicalKeys = ConcurrentHashMap<String, String>()
    private val processingJobs = ConcurrentHashMap<Long, Job>()
    private val sourceProcessingGeneration = SourceProcessingGeneration()
    private val messageEventTracker = MessageEventFallbackTracker()
    private val messageResolver = MessageNotificationResolver()
    private val messageFamilyTracker = MessagePresentationFamilyTracker()
    private val voicePlaybackUpdateGate = VoicePlaybackUpdateGate()
    private data class CachedVoiceFocus(
        val decoration: Bundle,
        val percent: Int,
        val clock: String,
        val publishedProgress: Int,
        val publishedMax: Int,
        val publishedAtElapsedMs: Long,
    )

    /** Last source-focus decoration for a voice island, keyed by its logical id. */
    private val voiceFocusDecorations = ConcurrentHashMap<String, CachedVoiceFocus>()
    private val expiredIslands = ExpiredIslandRegistry()
    private val timeoutJobs = ConcurrentHashMap<String, Job>()
    private val removalJobs = ConcurrentHashMap<String, Job>()
    @Volatile private var vpnIslandActive = false
    private lateinit var vpnIslandController: VpnIslandController
    private val intentionallyRemovedKeys = ConcurrentHashMap<String, Long>()
    /** Islands kept after shade clear-all or recents clear-all removed the source notification. */
    private val islandsRetainedWithoutSource = ConcurrentHashMap.newKeySet<String>()
    /** Sources whose island was dismissed or expired. Recovery must not post them again. */
    private val retiredSourceKeys = ConcurrentHashMap.newKeySet<String>()
    private val appLabelCache = ConcurrentHashMap<String, String>()
    private val notificationLifecycleMutex = Mutex()
    private val MAX_ISLANDS = 9
    private val WATCH_RELAY_ID_BASE = -20000
    private var watchRelaySlot = 0

    private lateinit var preferences: AppPreferences
    private lateinit var effectiveTypeResolver: EffectiveNotificationTypeResolver

    private lateinit var callClassifier: CallNotificationClassifier
    private val callSessionTracker = CallSessionTracker()
    private val screenRecordingSessionTracker = ScreenRecordingSessionTracker()
    private val downloadSessionTracker = DownloadSessionTracker()
    private lateinit var screenRecordingControlBackend: XiaomiScreenRecordingControlBackend

    // Translators
    private lateinit var callTranslator: CallTranslator
    private lateinit var navTranslator: NavTranslator
    private lateinit var timerTranslator: TimerTranslator
    private lateinit var progressTranslator: ProgressTranslator
    private lateinit var downloadTranslator: DownloadTranslator
    private lateinit var standardTranslator: StandardTranslator
    private lateinit var messageTranslator: MessageTranslator
    private lateinit var voiceMessageTranslator: VoiceMessageTranslator
    private lateinit var mediaTranslator: MediaTranslator
    private lateinit var liveUpdateTranslator: LiveUpdateTranslator
    private lateinit var screenRecordingTranslator: ScreenRecordingTranslator
    private lateinit var screenRecordingSavedTranslator: ScreenRecordingSavedTranslator

    @Volatile
    private var isScreenOn = true
    private val replyComposerHolds = ReplyComposerHoldRegistry()

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                syncNotifications(refresh = true)
            } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                isScreenOn = true
                syncNotifications(refresh = true)
            } else if (intent.action == Intent.ACTION_SCREEN_OFF) {
                isScreenOn = false
            }
        }
    }

    private val replyComposerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != IslandProtocol.ACTION_REPLY_COMPOSER) return
            replyComposerHolds.update(
                open = intent.getBooleanExtra(IslandProtocol.EXTRA_REPLY_COMPOSER_OPEN, false),
                sourceKey = intent.getStringExtra(IslandProtocol.EXTRA_SOURCE_KEY),
            )
        }
    }

    private val loginCodeCopiedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != LoginCodeCopyReceiver.ACTION_COPIED) return
            val sourceKey = intent.getStringExtra(LoginCodeCopyReceiver.EXTRA_SOURCE_KEY) ?: return
            if (!preferences.getLoginCodeSettingsSync().dismissAfterCopy) return
            serviceScope.launch { dismissAfterLoginCodeCopy(sourceKey) }
        }
    }

    private val packageLifecycleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            when (intent.action) {
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED -> Unit
            }
        }
    }

    private fun start() {
        
        val filter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(systemReceiver, filter)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            replyComposerReceiver,
            IntentFilter(IslandProtocol.ACTION_REPLY_COMPOSER),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            loginCodeCopiedReceiver,
            IntentFilter(LoginCodeCopyReceiver.ACTION_COPIED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            packageLifecycleReceiver,
            packageFilter,
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
        
        preferences = AppPreferences(applicationContext)
        callClassifier = CallNotificationClassifier(
            answerKeywords = resources.getStringArray(R.array.call_keywords_answer).toList(),
            declineKeywords = resources.getStringArray(R.array.call_keywords_hangup).toList(),
            hangUpKeywords = resources.getStringArray(R.array.call_keywords_hangup).toList(),
            muteKeywords = resources.getStringArray(R.array.call_keywords_mute).toList(),
            unmuteKeywords = resources.getStringArray(R.array.call_keywords_unmute).toList(),
            speakerKeywords = resources.getStringArray(R.array.call_keywords_speaker).toList()
        )

        callTranslator = CallTranslator(this)
        navTranslator = NavTranslator(this)
        timerTranslator = TimerTranslator(this)
        progressTranslator = ProgressTranslator(this)
        downloadTranslator = DownloadTranslator(this)
        standardTranslator = StandardTranslator(this)
        messageTranslator = MessageTranslator(this)
        voiceMessageTranslator = VoiceMessageTranslator(this)
        liveUpdateTranslator = LiveUpdateTranslator(this)

        mediaTranslator = MediaTranslator(this)
        screenRecordingTranslator = ScreenRecordingTranslator(this)
        screenRecordingSavedTranslator = ScreenRecordingSavedTranslator(this)
        screenRecordingControlBackend = XiaomiScreenRecordingControlBackend(this)

        effectiveTypeResolver = EffectiveNotificationTypeResolver(preferences)

        vpnIslandController = VpnIslandController(
            this,
            serviceScope,
            preferences,
            initialNotifications = activeNotificationsProvider,
            onIslandActiveChanged = { active ->
                vpnIslandActive = active
                updateIslandDiagnostics()
            }
        )
        vpnIslandController.start()

        serviceScope.launch {
            preferences.allowedPackagesFlow.collect { updated ->
                allowedPackageSet = updated
            }
        }
        serviceScope.launch { preferences.limitModeFlow.collectLatest { currentMode = it } }
        serviceScope.launch { preferences.appPriorityListFlow.collectLatest { appPriorityList = it } }
        serviceScope.launch { preferences.globalBlockedTermsFlow.collectLatest { globalBlockedTerms = it } }
        serviceScope.launch { preferences.isDndModeEnabledFlow.collectLatest { isDndModeEnabled = it } }
        serviceScope.launch { preferences.autoDetectDndFlow.collectLatest { autoDetectDnd = it } }

    }

    fun handleCommand(intent: Intent?) {
        if (intent?.action == ACTION_PERFORM_MIGRATION) {
            serviceScope.launch(Dispatchers.IO) {
                AppDatabase.performMigration(applicationContext) { progress ->
                    launch(Dispatchers.Main) {
                        showMigrationProgress(progress)
                    }
                }
            }
        }
    }

    /** Removes the notification left by versions that implemented the old permanent island. */
    private fun removeLegacyPermanentAnchor() {
        islandBackend.cancel(LEGACY_PERMANENT_ANCHOR_ID, LEGACY_PERMANENT_ANCHOR_TOKEN)
    }

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

                val notification = notificationBuilder.build()
                postIsland(bridgeId, notification, "migration_update", semanticType = NotificationType.PROGRESS)
            } else {
                val builder = HyperIslandNotification.Builder(this@NotificationProcessingEngine, "migration", title)
                builder.setProgressBar(progress, "#007AFF")
                builder.setChatInfo(title, message, "migration_icon", packageName)
                builder.setShowNotification(true)
                builder.setEnableFloat(true)
                builder.setIslandFirstFloat(true)
                builder.setReopen(true)

                val data = HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())

                val notificationBuilder = NotificationCompat.Builder(this@NotificationProcessingEngine, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(progress in 0..99)
                    .setProgress(100, progress, progress < 0)
                    .addExtras(data.resources)

                val notification = notificationBuilder.build()
                notification.extras.putString(
                    "miui.focus.param",
                    IslandVisualMetadata.injectFloatingFlags(
                        data.jsonParam,
                        enableFloat = true,
                        islandFirstFloat = true,
                        reopen = true,
                    ),
                )

                postIsland(bridgeId, notification, "migration_update", semanticType = NotificationType.PROGRESS)
            }

            if (progress >= 100) {
                delay(3000)
                islandBackend.cancel(bridgeId, "migration_update")
            }
        }
    }

    // =========================================================================
    //  EFFECTIVE BEHAVIOR RESOLUTION (App > Global)
    // =========================================================================

    private fun getEffectiveTypes(pkg: String): Set<String> {
        return effectiveTypeResolver.getEffectiveTypes(pkg)
    }

    private fun getEffectiveCallStages(pkg: String): Set<CallStage> = preferences.getEffectiveCallStagesSync(pkg)

    /** Xiaomi Featured Design is the only engine; stored Live Update preferences are ignored. */
    @Suppress("UNUSED_PARAMETER", "SameReturnValue")
    private fun getEffectiveEngine(pkg: String): Boolean = false

    private fun getEffectiveNav(pkg: String): Pair<NavContent, NavContent> {
        return preferences.getEffectiveNavLayoutSync(pkg)
    }

    // =========================================================================
    //  NOTIFICATION REMOVAL LOGIC
    // =========================================================================

    fun onNotificationRemoved(sbn: StatusBarNotification?, reason: Int) {
        sbn?.let {
            if (::vpnIslandController.isInitialized) vpnIslandController.onSourceNotificationRemoved(it)
            if (nativeIslands.remove(it.key)) {
                updateIslandDiagnostics()
            }

            val isOwnedBridge = isOwnedBridgeNotification(it)
            val notifId = it.id
            val notifKey = it.key
            if (!isOwnedBridge) voicePlaybackUpdateGate.remove(sourceSlotIdentity(it))

            if (isOwnedBridge) {
                if (NotificationLifecyclePolicy.preservesActiveIsland(reason)) {
                    val trackedKey = reverseTranslations[notifId]
                        ?: it.notification.extras.getString(EXTRA_ORIGINAL_KEY)
                        ?: it.notification.extras.getString(IslandProtocol.EXTRA_SOURCE_KEY)
                    if (!trackedKey.isNullOrBlank()) {
                        val island = activeIslands[trackedKey]
                        if (island == null) {
                            // The proxy outlived a dismiss or expiry. Clear-all must not post it again.
                            retiredSourceKeys.add(trackedKey)
                        } else {
                            islandsRetainedWithoutSource.add(trackedKey)
                        }
                    }
                    return
                }
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

            recentlyRemovedKeys[notifKey] = RemovedSource(System.currentTimeMillis(), it.postTime, reason)

            if (NotificationLifecyclePolicy.preservesActiveIsland(reason)) {
                val logicalKey = logicalKeyForRemovedSource(notifKey)
                if (activeIslands.containsKey(logicalKey) || activeTranslations.containsKey(logicalKey)) {
                    islandsRetainedWithoutSource.add(logicalKey)
                    Log.d(
                        TAG,
                        "KEEP island across recents cleanup reason=$reason sourceKey=${notifKey.hashCode()} " +
                            "logicalId=${logicalKey.hashCode()}"
                    )
                    return
                }
            }

            var messagingSource = false
            messageFamilyTracker.removeSource(notifKey)?.let { removal ->
                messagingSource = true
                val visibleConversationRemains = !removal.familyEnded &&
                    removal.visibleSourceRemains &&
                    messageFamilyTracker.visibleSourceKeys(removal.logicalId).any(::isSourceNotificationActive)
                if (visibleConversationRemains &&
                    !NotificationLifecyclePolicy.isUserInitiatedRemoval(reason)
                ) {
                    sourceToLogicalKeys.remove(notifKey, removal.logicalId)
                    removalJobs.remove(removal.logicalId)?.cancel()
                    Log.d(
                        TAG,
                        "MESSAGE ALIAS REMOVED sourceHash=${notifKey.hashCode()} " +
                                "logicalHash=${removal.logicalId.hashCode()} " +
                                "primaryRemoved=${removal.removedPrimary} familySurvives=true"
                    )
                    return
                }
                // Shade swipe and content tap dismiss the presented island even when hidden
                // conversation aliases remain. An app clear of the last visible notification
                // falls through, including when only a group summary is left in the shade.
            }

            if (isOwnedBridge) {
                // A content click removes auto-cancel bridge notifications just like a shade
                // dismissal. Programmatic replacement cancels are ignored.
                val wasContentClick = reason == REASON_CLICK
                if (!NotificationLifecyclePolicy.isUserInitiatedRemoval(reason)) {
                    return
                }
                val trackedKey = reverseTranslations[notifId]
                    ?: it.notification.extras.getString(EXTRA_ORIGINAL_KEY)
                    ?: it.notification.extras.getString(IslandProtocol.EXTRA_SOURCE_KEY)
                val currentIsland = trackedKey?.let(activeIslands::get)
                if (!wasContentClick && currentIsland != null && currentIsland.id != notifId) {
                    return
                }

                if (notifId == VpnIslandController.NOTIFICATION_ID) {
                    return
                }
                var originalKey = reverseTranslations[notifId]
                if (originalKey == null) {
                    originalKey = it.notification.extras.getString(EXTRA_ORIGINAL_KEY)
                        ?: it.notification.extras.getString(IslandProtocol.EXTRA_SOURCE_KEY)
                }

                if (originalKey != null) {
                    Log.d(TAG, "Our notification $notifId removed. Cleaning up cache for $originalKey")
                    val island = activeIslands[originalKey]
                    retireFromRecovery(island)
                    if (NotificationLifecyclePolicy.shouldDismissSourceAfterBridgeRemoval(
                            dismissSourceOnContentClick = island?.dismissSourceOnContentClick == true,
                            wasContentClick = wasContentClick
                        )
                    ) {
                        // The bridge uses the recorder's content PendingIntent, so opening it does
                        // not make Android auto-cancel the recorder's separate source notification.
                        // Retire that source explicitly after the bridge click.
                        island?.sourceKey?.let(::cancelSourceNotification)
                    } else {
                        // [FIX] We no longer kill the source notification when our Island is
                        // dismissed or timed out; only forward its normal deletion callback.
                        try {
                            island?.deleteIntent?.send()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error sending delete intent for original notification", e)
                        }
                    }
                    val generation = it.notification.extras.getLong(
                        IslandProtocol.EXTRA_GENERATION,
                        Long.MAX_VALUE,
                    )
                    try {
                        islandBackend.cancel(notifId, originalKey, generation)
                    } catch (_: Exception) {}
                    cleanupCache(originalKey)
                }
                return
            }

            val downloadLogicalId = downloadSessionTracker.logicalIdForSource(notifKey)
            val mappedKey = sourceToLogicalKeys[notifKey]
                ?: callSessionTracker.logicalIdForSource(notifKey)
                ?: screenRecordingSessionTracker.logicalIdForSource(notifKey)
                ?: downloadLogicalId
                ?: notifKey
            val trackedCallLogicalId = callSessionTracker.logicalIdForSource(notifKey)
            val trackedRecordingLogicalId = screenRecordingSessionTracker.logicalIdForSource(notifKey)
            val removalKeys = LinkedHashSet<String>()
            if (downloadLogicalId != null && activeTranslations.containsKey(downloadLogicalId)) {
                removalKeys.add(downloadLogicalId)
            }
            if (activeTranslations.containsKey(mappedKey)) {
                removalKeys.add(mappedKey)
            }

            if (removalKeys.isNotEmpty()) {
                for (logicalKey in removalKeys) {
                val hyperId = activeTranslations[logicalKey] ?: continue
                val island = activeIslands[logicalKey]
                val islandType = island?.type
                val regroupingProtected = messagingSource ||
                    islandType == NotificationType.MESSAGE ||
                    island?.messageEventFingerprint != null
                if (islandType == NotificationType.CALL) {
                    callSessionTracker.markSourceRemoved(notifKey, System.currentTimeMillis())
                }
                if (NotificationLifecyclePolicy.isProgressLifecycle(islandType)) {
                    downloadSessionTracker.markSourceRemoved(notifKey, System.currentTimeMillis())
                }

                lateinit var job: Job
                job = serviceScope.launch(Dispatchers.IO) {
                    val appConfig = preferences.getAppIslandConfigSync(sbn.packageName)
                    val globalConfig = preferences.getGlobalConfigSync()
                    val finalConfig = appConfig.mergeWith(globalConfig)

                    val replacementCancel = NotificationLifecyclePolicy.isAppCancellationReason(reason)
                    val shouldDismiss = reason == REASON_CLICK ||
                        NotificationLifecyclePolicy.shouldDismissIslandOnSourceRemoval(
                            type = islandType,
                            dismissWithOriginal = finalConfig.dismissWithOriginal == true,
                            isAppCancellation = replacementCancel,
                            regroupingProtected = regroupingProtected,
                        )

                    if (shouldDismiss) {
                        // An app clear may be a cancel-and-repost. Wait, then dismiss only if
                        // this exact source did not come back.
                        if (islandType == NotificationType.CALL) {
                            kotlinx.coroutines.delay(CallReplacementPolicy.REMOVAL_DELAY_MS)
                        } else if (NotificationLifecyclePolicy.isProgressLifecycle(islandType)) {
                            kotlinx.coroutines.delay(DownloadReplacementPolicy.REMOVAL_DELAY_MS)
                        } else if (replacementCancel && islandType != NotificationType.SCREEN_RECORDING) {
                            kotlinx.coroutines.delay(300)
                        }
                        notificationLifecycleMutex.withLock {
                            val current = activeIslands[logicalKey]
                            val progressLifecycle = NotificationLifecyclePolicy.isProgressLifecycle(islandType)
                            val progressStillLive = progressLifecycle &&
                                progressIslandStillLive(logicalKey, notifKey)
                            val sourceStillActive = !progressLifecycle && isSourceNotificationActive(notifKey)
                            val staleGeneration = current != null && !progressLifecycle &&
                                !NotificationLifecyclePolicy.isCurrentRemoval(
                                    activeSourceKey = current.sourceKey,
                                    activeSourcePostTime = current.sourcePostTime,
                                    removedSourceKey = notifKey,
                                    removedSourcePostTime = sbn.postTime
                                )
                            val sameSourceNewerGeneration = current != null &&
                                current.sourceKey == notifKey &&
                                current.sourcePostTime > sbn.postTime
                            val visibleConversationStillPosted = messageFamilyTracker
                                .visibleSourceKeys(logicalKey)
                                .any(::isSourceNotificationActive)
                            val keepIsland = if (messagingSource) {
                                NotificationLifecyclePolicy.shouldKeepMessageIslandAfterSourceRemoval(
                                    sourceStillActive = sourceStillActive,
                                    sameSourceNewerGeneration = sameSourceNewerGeneration,
                                    visibleConversationStillPosted = visibleConversationStillPosted,
                                    userInitiated = NotificationLifecyclePolicy.isUserInitiatedRemoval(reason),
                                )
                            } else {
                                progressStillLive || sourceStillActive || staleGeneration
                            }
                            if (keepIsland || current == null) {
                                Log.d(
                                    TAG,
                                    "${islandType?.name ?: "UNKNOWN"} REMOVE reason=stale " +
                                            "sourceKey=${notifKey.hashCode()} logicalId=${logicalKey.hashCode()}"
                                )
                                return@withLock
                            }
                            timeoutJobs.remove(logicalKey)?.cancel()
                            if (current.sourceFocus != true) {
                                try {
                                    islandBackend.cancel(hyperId)
                                } catch (_: Exception) {}
                            }
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
            } else if (trackedCallLogicalId != null) {
                callSessionTracker.markSourceRemoved(notifKey, System.currentTimeMillis())
                lateinit var job: Job
                job = serviceScope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(CallReplacementPolicy.REMOVAL_DELAY_MS)
                    notificationLifecycleMutex.withLock {
                        if (isSourceNotificationActive(notifKey) ||
                            callSessionTracker.logicalIdForSource(notifKey) != trackedCallLogicalId
                        ) {
                            return@withLock
                        }
                        callSessionTracker.end(trackedCallLogicalId)
                        sourceToLogicalKeys.entries.removeIf { it.value == trackedCallLogicalId }
                    }
                }
                removalJobs[trackedCallLogicalId]?.cancel()
                removalJobs[trackedCallLogicalId] = job
                job.invokeOnCompletion { removalJobs.remove(trackedCallLogicalId, job) }
            } else if (trackedRecordingLogicalId != null) {
                // A removal that wins the race with presentation must still retire this exact
                // recording generation. A reused source key with a newer postTime is preserved.
                screenRecordingSessionTracker.endSource(notifKey, sbn.postTime)
                sourceToLogicalKeys.remove(notifKey, trackedRecordingLogicalId)
            }
        }
    }

    private fun cancelSourceNotification(targetKey: String) {
        try {
            val currentNotifications = try {
                activeNotificationsProvider()
            } catch (_: Exception) {
                cancelSourceNotificationByKey(targetKey)
                return
            }

            val targetSbn = currentNotifications.find { it.key == targetKey }
            cancelSourceNotificationByKey(targetKey)

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
                        cancelSourceNotificationByKey(survivor.key)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during smart dismissal", e)
        }
    }

    private suspend fun dismissAfterLoginCodeCopy(sourceKey: String) {
        notificationLifecycleMutex.withLock {
            val logicalKey = sourceToLogicalKeys[sourceKey] ?: sourceKey
            val bridgeId = activeTranslations[logicalKey]
            if (bridgeId != null) {
                retireFromRecovery(activeIslands[logicalKey])
                runCatching { islandBackend.cancel(bridgeId) }
                cleanupCache(logicalKey)
            }
            intentionallyRemovedKeys[sourceKey] = System.currentTimeMillis()
            cancelSourceNotification(sourceKey)
            Log.d(TAG, "LOGIN CODE dismissed after copy sourceKey=${sourceKey.hashCode()}")
        }
    }

    private fun cleanupCache(originalKey: String, preserveCallSession: Boolean = false) {
        val hyperId = activeTranslations[originalKey]
        val island = activeIslands.remove(originalKey)
        activeTranslations.remove(originalKey)
        timeoutJobs.remove(originalKey)?.cancel()
        if (island?.type == NotificationType.CALL && !preserveCallSession) {
            callSessionTracker.end(island.logicalId)
        }
        if (island?.type == NotificationType.SCREEN_RECORDING) {
            screenRecordingSessionTracker.end(island.logicalId)
        }
        if (island != null && NotificationLifecyclePolicy.isProgressLifecycle(island.type)) {
            downloadSessionTracker.end(island.logicalId)
        }
        if (island?.type == NotificationType.VOICE_MESSAGE) {
            voiceFocusDecorations.remove(originalKey)
        }
        messageFamilyTracker.end(originalKey)
        islandsRetainedWithoutSource.remove(originalKey)

        if (hyperId != null) {
            reverseTranslations.remove(hyperId)
        }
        sourceToLogicalKeys.entries.removeIf { it.value == originalKey }
        updateIslandDiagnostics()
    }

    private fun handlePostNotificationSideEffects(
        originalKey: String,
        bridgeId: Int,
        generation: Long,
        config: IslandConfig,
        type: NotificationType,
        isLiveUpdate: Boolean,
        sbn: StatusBarNotification? = null,
        title: String = "",
        text: String = "",
        forceLifecycleTimeout: Boolean = false
    ) {
        // 1. Remove original if enabled. Source-focus types must stay posted: they carry the card.
        if (config.removeOriginalNotification == true &&
            type != NotificationType.MEDIA &&
            !NotificationLifecyclePolicy.carriesVisibleSourceFocus(type)
        ) {
            if (sbn != null && !isLiveUpdate && (type == NotificationType.MESSAGE || type == NotificationType.STANDARD)) {
                postWatchRelayNotification(sbn, title, text)
            }
            intentionallyRemovedKeys[originalKey] = System.currentTimeMillis()
            cancelSourceNotificationByKey(originalKey)
        }

        // 2. Lifecycle timeout using user-configured timeout
        val needsLifecycleTimeout = (isLiveUpdate ||
                type == NotificationType.MESSAGE || type == NotificationType.STANDARD ||
                forceLifecycleTimeout) &&
                type != NotificationType.CALL && type != NotificationType.MEDIA && type != NotificationType.NAVIGATION
        if (needsLifecycleTimeout && !marqueeOwnsTimeout(config, sbn)) {
            val timeoutMs = IslandTimeoutPolicy.durationMillis(config.timeout)
            timeoutJobs.remove(originalKey)?.cancel()
            if (timeoutMs == null) return

            lateinit var job: Job
            job = serviceScope.launch {
                var remaining = timeoutMs
                while (remaining > 0) {
                    val sourceKey = activeIslands[originalKey]?.sourceKey
                    if (replyComposerHolds.holds(originalKey) ||
                        (!sourceKey.isNullOrBlank() && replyComposerHolds.holds(sourceKey))
                    ) {
                        delay(150.milliseconds)
                        continue
                    }
                    val step = minOf(remaining, 250L)
                    delay(step.milliseconds)
                    remaining -= step
                }
                notificationLifecycleMutex.withLock {
                    val current = activeIslands[originalKey]
                    if (!IslandTimeoutPolicy.isCurrent(current?.generation, current?.id, generation, bridgeId)) return@withLock
                    current ?: return@withLock
                    Log.d(TAG, "${type.name} TIMEOUT bridgeId=$bridgeId logicalId=${originalKey.hashCode()}")
                    retireFromRecovery(current)
                    islandBackend.cancel(bridgeId)
                    cleanupCache(originalKey)
                }
            }
            timeoutJobs[originalKey] = job
            job.invokeOnCompletion { timeoutJobs.remove(originalKey, job) }
        }
    }

    private fun retireFromRecovery(island: ActiveIsland?) {
        if (island == null) return
        if (island.sourceKey.isNotBlank()) retiredSourceKeys.add(island.sourceKey)
        recordExpiredIsland(island)
    }

    private fun recordExpiredIsland(island: ActiveIsland) {
        if (island.type != NotificationType.MESSAGE && island.type != NotificationType.STANDARD) return
        expiredIslands.record(
            ExpiredIslandRecord(
                logicalId = island.logicalId,
                sourceKey = island.sourceKey,
                sourceFingerprint = sourceGenerationFingerprint(island.lastContentHash, island.sourcePostTime),
                expiredAt = System.currentTimeMillis(),
                messageEventFingerprint = island.messageEventFingerprint
            )
        )
        DiagnosticsStore.record(island.type.name, "expired", island.packageName)
    }

    private fun shouldSuppressExpiredSource(
        sbn: StatusBarNotification,
        type: NotificationType,
        logicalKey: String,
        contentHash: Int,
        recovery: Boolean,
        messageEventFingerprint: MessageEventFingerprint?
    ): Boolean {
        if (recovery && sbn.key in retiredSourceKeys) {
            if (expiredIslands.recoveryExpiry(
                    sbn.key,
                    System.currentTimeMillis(),
                    messageEventFingerprint,
                    logicalKey
                ) != RecoveryExpiry.NEW_EVENT
            ) {
                DiagnosticsStore.record(type.name, "recovery-skipped-expired", sbn.packageName)
                return true
            }
        }
        if (type != NotificationType.MESSAGE && type != NotificationType.STANDARD) return false
        if (recovery) {
            return when (
                expiredIslands.recoveryExpiry(
                    sbn.key,
                    System.currentTimeMillis(),
                    messageEventFingerprint,
                    logicalKey
                )
            ) {
                RecoveryExpiry.ABSENT -> false
                RecoveryExpiry.NEW_EVENT -> false
                RecoveryExpiry.SAME_EVENT -> {
                    sourceToLogicalKeys.remove(sbn.key, logicalKey)
                    DiagnosticsStore.record(type.name, "recovery-skipped-expired", sbn.packageName)
                    true
                }
            }
        }
        return when (
            expiredIslands.evaluate(
                sbn.key,
                sourceGenerationFingerprint(contentHash, sbn.postTime),
                System.currentTimeMillis(),
                messageEventFingerprint,
                logicalKey
            )
        ) {
            ExpiredSourceDecision.NOT_EXPIRED -> false
            ExpiredSourceDecision.SUPPRESS_IDENTICAL -> {
                sourceToLogicalKeys.remove(sbn.key, logicalKey)
                DiagnosticsStore.record(
                    type.name,
                    if (recovery) "recovery-skipped-expired" else "expired-generation-ignored",
                    sbn.packageName
                )
                true
            }
            ExpiredSourceDecision.NEW_GENERATION -> false
        }
    }

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
            postIsland(
                relayId,
                notification,
                logicalToken = "watch-relay:$relayId",
                source = sbn,
                semanticType = NotificationType.STANDARD,
                generation = sbn.postTime,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error posting watch relay notification", e)
        }
    }

    private fun logStateChange(isLandscape: Boolean) {
        val orientation = if (isLandscape) "Landscape" else "Portrait"
        val isIslandExhibited = activeIslands.isNotEmpty() ||
            vpnIslandActive || nativeIslands.isNotEmpty()
        val islandState = if (isIslandExhibited) "Showing Island" else "No Island"
        Log.d(TAG, "State: $orientation | $islandState")
    }

    private fun updateIslandDiagnostics() {
        DiagnosticsStore.setActiveIslands(activeIslands.size + if (vpnIslandActive) 1 else 0)
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        logStateChange(isLandscape)
    }

    private fun releasePreviousBridgeIfMoved(previous: ActiveIsland?, postedId: Int) {
        val previousId = previous?.id ?: return
        if (previousId == postedId) return
        reverseTranslations.remove(previousId)
        islandBackend.cancel(previousId)
    }

    private fun injectPresentationFloat(
        json: String,
        floatPresentation: com.sykeptical.hyperpop.service.translators.IslandFloatingPresentation,
        lockExpansion: Boolean,
    ): String = IslandVisualMetadata.injectFloatingFlags(
        json,
        enableFloat = floatPresentation.enableFloat,
        islandFirstFloat = floatPresentation.islandFirstFloat,
        reopen = floatPresentation.reopen,
        expandedTimeMs = if (lockExpansion) 0 else null,
    )

    fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        logStateChange(newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    }

    // =========================================================================
    //  STANDARD NOTIFICATION LOGIC
    // =========================================================================

    fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            if (::vpnIslandController.isInitialized) vpnIslandController.onSourceNotificationPosted(it)
            if (it.packageName != packageName) {
                val extras = it.notification.extras
                var isNative = false
                if (extras != null) {
                    if (extras.containsKey("miui.focus.param") || extras.containsKey("miui.system.focus.param")) {
                        isNative = true
                    }
                    if (countsAsNativeMediaIsland(it)) isNative = true
                }
                if (isNative) {
                    if (nativeIslands.add(it.key)) updateIslandDiagnostics()
                } else {
                    if (nativeIslands.remove(it.key)) updateIslandDiagnostics()
                }
            }

            enqueueSourceNotification(it)
        }
    }

    private fun marqueeCapabilitiesReady(): Boolean {
        val caps = islandBackend.health().capabilities
        return caps and IslandProtocol.CAP_MARQUEE != 0 && caps and IslandProtocol.CAP_ISLAND_DISMISS != 0
    }

    private fun marqueeOwnsTimeout(config: IslandConfig, sbn: StatusBarNotification?): Boolean {
        if (sbn == null) return false
        return IslandVisualMetadata.plan(
            config = config,
            glow = IslandGlowResolver.resolve(config, IslandConfig(), null),
            keepPosted = sourceStaysPosted(sbn),
            marqueeCapable = marqueeCapabilitiesReady(),
        ).islandTimeoutSeconds == Int.MAX_VALUE
    }

    private fun sourceStaysPosted(sbn: StatusBarNotification, type: NotificationType? = null): Boolean {
        val resolved = type ?: detectNotificationType(sbn)
        val sourceIsOngoing = sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        if (sourceIsOngoing || NotificationLifecyclePolicy.proxyStaysPosted(resolved)) return true
        return NotificationLifecyclePolicy.isProgressLifecycle(resolved) && !isFinishedProgress(sbn)
    }

    private fun isFinishedProgress(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        if (max > 0 && current >= max) return true
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return extractTextPercentage(title, text) == 100
    }

    private fun enqueueSourceNotification(sbn: StatusBarNotification, recovery: Boolean = false) {
        if (shouldIgnore(sbn.packageName)) return
        // Apps picked for the login code extractor get an island for code messages only.
        if (!isAppAllowed(sbn.packageName) && detectLoginCode(sbn) == null) return
        Log.d("HBLoginTest", "enqueue key=${sbn.key} code=${detectLoginCode(sbn) != null} recovery=$recovery") // TEMP-TEST
        if (!recovery) {
            DiagnosticsStore.record("CALLBACK", "received", sbn.packageName)
        }
        val sourceSlot = sourceSlotIdentity(sbn)
        if (!recovery) {
            throttledVoicePlaybackSample(sbn, sourceSlot)?.let { sample ->
                restampCachedVoiceFocus(sbn, sample)
                markSourceHeadsUpSuppressed(sbn)
                return
            }
        }
        val callbackObservedAt = System.currentTimeMillis()
        val rawQuality = sourceCandidateQuality(sbn)
        val processingGeneration = sourceProcessingGeneration.next(sourceSlot, rawQuality)
        runBlocking {
            val selectedSbn = ensureValidSbn(sbn, processingGeneration)
            val selectedQuality = sourceCandidateQuality(selectedSbn)
            sourceProcessingGeneration.consider(sourceSlot, processingGeneration, selectedQuality)
            if (!sourceProcessingGeneration.isCurrent(sourceSlot, processingGeneration)) {
                return@runBlocking
            }
            notificationLifecycleMutex.withLock {
                if (!sourceProcessingGeneration.isCurrent(sourceSlot, processingGeneration)) {
                    return@withLock
                }
                processStandardNotification(
                    rawSbn = sbn,
                    sbn = selectedSbn,
                    sourceSlot = sourceSlot,
                    recovery = recovery,
                    processingGeneration = processingGeneration,
                    callbackObservedAt = callbackObservedAt
                )
            }
        }
        run {
            sourceProcessingGeneration.finish(sourceSlot, processingGeneration)
        }
    }

    private fun sourceCandidateQuality(sbn: StatusBarNotification): SourceCandidateQuality =
        if (needsSourceRefresh(sbn)) SourceCandidateQuality.SPARSE else SourceCandidateQuality.USABLE

    private fun sourceSlotIdentity(sbn: StatusBarNotification): String =
        buildString {
            append(sbn.packageName.length).append(':').append(sbn.packageName)
            append('|').append(sbn.id)
            append('|').append(sbn.tag?.length ?: -1).append(':').append(sbn.tag.orEmpty())
        }

    private fun sourceCandidate(sbn: StatusBarNotification): SourceNotificationCandidate =
        SourceNotificationCandidate(
            sourceKey = sbn.key,
            packageName = sbn.packageName,
            notificationId = sbn.id,
            notificationTag = sbn.tag,
            postTime = sbn.postTime,
            quality = sourceCandidateQuality(sbn)
        )

    private fun needsSourceRefresh(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val extras = notification.extras
        val content = resolveNotificationContent(sbn)
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val hasProgressOrSpecialState = hasProgressNotification(sbn, content.title, content.text) ||
                notification.category == Notification.CATEGORY_CALL ||
                notification.category == Notification.CATEGORY_TRANSPORT ||
                notification.category == Notification.CATEGORY_NAVIGATION ||
                notification.category == Notification.CATEGORY_ALARM ||
                template.contains("MediaStyle") ||
                extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        return NotificationRefreshPolicy.shouldRefresh(
            NotificationRefreshSignals(
                packageName = sbn.packageName,
                title = content.title,
                text = content.text,
                hasMessageContent = content.hasMessageContent,
                hasProgressOrSpecialState = hasProgressOrSpecialState
            )
        )
    }

    private suspend fun ensureValidSbn(
        initialSbn: StatusBarNotification,
        processingGeneration: Long
    ): StatusBarNotification {
        var bestSbn = initialSbn
        val requested = sourceCandidate(initialSbn)
        repeat(NotificationRefreshPolicy.MAX_REFRESH_ATTEMPTS) {
            if (!needsSourceRefresh(bestSbn)) {
                return bestSbn
            }

            delay(NotificationRefreshPolicy.REFRESH_DELAY_MS.milliseconds)
            val active = try {
                activeNotificationsProvider().toList()
            } catch (_: Exception) {
                emptyList()
            }
            val exact = active.filter { it.key == initialSbn.key }
            val replacements = active.filter {
                it.key != initialSbn.key &&
                        SourceNotificationCandidatePolicy.isSameSlot(requested, sourceCandidate(it))
            }
            var refreshedSbn: StatusBarNotification? = null
            for (candidate in exact + replacements) {
                val selected = refreshedSbn
                if (selected == null || SourceNotificationCandidatePolicy.shouldPrefer(
                        current = sourceCandidate(selected),
                        incoming = sourceCandidate(candidate),
                        requestedSourceKey = initialSbn.key
                    )
                ) {
                    refreshedSbn = candidate
                }
            }
            if (refreshedSbn == null) {
                return@repeat
            }
            bestSbn = refreshedSbn
            if (!needsSourceRefresh(bestSbn)) {
                return bestSbn
            }
        }
        return bestSbn
    }

    private fun resolveNotificationContent(sbn: StatusBarNotification): ResolvedNotificationContent {
        val notification = sbn.notification
        val extras = notification.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val isMessageStyle = notification.category == Notification.CATEGORY_MESSAGE ||
                template.contains("MessagingStyle")
        val rawTitle = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.takeUnless { it.toString().trim().equals(sbn.packageName, ignoreCase = true) }
        val messages = extractMessageContent(notification)
        val textLines = try {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val base = NotificationContentResolver.resolve(
            title = rawTitle,
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            messages = messages,
            isMessageStyle = isMessageStyle,
            textLines = textLines,
        )
        val latestMessage = messages.lastOrNull {
            !it.sender.isNullOrBlank() || !it.text.isNullOrBlank()
        }
        val (title, text) = NotificationIdentityResolver.resolve(
            packageName = sbn.packageName,
            appLabel = getCachedAppLabel(sbn.packageName),
            title = rawTitle,
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?: extras.getCharSequence("android.hiddenConversationTitle")
                ?: extractMessagingConversationTitle(notification),
            personNames = extractPersonNames(notification),
            messageSender = latestMessage?.sender,
            messageText = latestMessage?.text,
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT),
            summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
            infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT),
            textLines = textLines,
            ticker = notification.tickerText,
            shortcutLabel = NotificationConversationShortcut.label(this, sbn),
            remoteTexts = NotificationRemoteViewsParser.collect(notification).texts,
            selfName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)
                ?: extractMessagingSelfName(notification),
            isGroupConversation = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
                extractMessagingIsGroup(notification),
        )
        val selfName = extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString()
            ?: extractMessagingSelfName(notification)
        return base.copy(
            title = title.ifBlank { base.title },
            text = text.ifBlank { base.text },
            latestMessageIsSelf = OutgoingReplyEchoDetector.isOutgoingEcho(
                messages = messages,
                selfName = selfName,
                title = title.ifBlank { base.title },
                text = text.ifBlank { base.text },
                remoteInputHistory = extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY),
                extras = listOf(
                    rawTitle?.toString(),
                    extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                    extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                    extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString(),
                    notification.tickerText?.toString(),
                    *textLines.map { it?.toString() }.toTypedArray(),
                ),
                conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                    ?: extractMessagingConversationTitle(notification)
                    ?: rawTitle?.toString(),
            ),
        )
    }

    private fun extractMessagingIsGroup(notification: Notification): Boolean = try {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?.isGroupConversation == true
    } catch (_: Exception) {
        false
    }

    private fun extractMessagingConversationTitle(notification: Notification): String? = try {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?.conversationTitle?.toString()
    } catch (_: Exception) {
        null
    }

    private fun extractMessagingSelfName(notification: Notification): String? = try {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?.user?.name?.toString()?.trim()
    } catch (_: Exception) {
        notification.extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString()?.trim()
    }

    private fun extractPersonNames(notification: Notification): List<String> {
        val extras = notification.extras
        val self = extractMessagingSelfName(notification)
        val names = linkedSetOf<String>()
        fun addName(value: CharSequence?) {
            val name = value?.toString()?.trim().orEmpty()
            if (name.isBlank()) return
            if (!self.isNullOrBlank() && name.equals(self, ignoreCase = true)) return
            names += name
        }
        runCatching { extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java) }
            .getOrNull()
            .orEmpty()
            .forEach { addName(it.name) }
        extractMessageContent(notification).forEach { addName(it.sender) }
        return names.toList()
    }

    private fun extractMessageContent(notification: Notification): List<MessageContentCandidate> {
        val fromStyle = try {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            val selfName = style?.user?.name?.toString()?.trim()
            val selfKey = style?.user?.key
            style?.messages
                ?.map { message ->
                    val person = message.person
                    val sender = person?.name?.toString()
                    val isSelf = (!selfKey.isNullOrBlank() && person?.key == selfKey) ||
                        (!selfName.isNullOrBlank() && sender?.trim().equals(selfName, ignoreCase = true))
                    MessageContentCandidate(
                        sender = sender,
                        text = message.text?.toString(),
                        timestamp = message.timestamp,
                        isSelf = isSelf,
                    )
                }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (fromStyle.isNotEmpty()) return fromStyle
        val selfName = extractMessagingSelfName(notification)
        return notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?.mapNotNull { parcelable ->
                val bundle = parcelable as? Bundle ?: return@mapNotNull null
                val sender = bundle.getCharSequence("sender")?.toString()
                    ?: runCatching {
                        bundle.getParcelable("sender_person", Person::class.java)?.name?.toString()
                    }.getOrNull()
                MessageContentCandidate(
                    sender = sender,
                    text = bundle.getCharSequence("text")?.toString(),
                    timestamp = bundle.getLong("time", 0L).takeIf { it > 0L },
                    isSelf = !selfName.isNullOrBlank() && sender?.trim().equals(selfName, ignoreCase = true),
                )
            }
            .orEmpty()
    }

    private fun isMessagingLifecycleEvent(
        sbn: StatusBarNotification,
        type: NotificationType,
        content: ResolvedNotificationContent
    ): Boolean {
        if (type != NotificationType.MESSAGE && type != NotificationType.STANDARD) return false

        val notification = sbn.notification
        val extras = notification.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val hasMessagePersonMetadata = try {
            extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java) != null ||
                    extras.getParcelableArrayList(
                        Notification.EXTRA_PEOPLE_LIST,
                        android.app.Person::class.java
                    )?.isNotEmpty() == true ||
                    extras.containsKey(Notification.EXTRA_MESSAGES)
        } catch (_: Exception) {
            extras.containsKey(Notification.EXTRA_MESSAGES)
        }
        val hasRemoteInputReply = (notification.actions ?: emptyArray()).any { action ->
            action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY ||
                    !action.remoteInputs.isNullOrEmpty()
        }

        return isMessagingEvent(
            MessagingEventSignals(
                packageName = sbn.packageName,
                isMessageNotificationType = type == NotificationType.MESSAGE,
                isStandardNotificationType = type == NotificationType.STANDARD,
                hasMessageCategory = notification.category == Notification.CATEGORY_MESSAGE,
                hasMessagingStyleTemplate = template.contains("MessagingStyle"),
                extractedMessageCount = content.messageCount,
                hasConversationShortcut = !notification.shortcutId.isNullOrBlank(),
                hasConversationLocus = !notification.locusId?.id.isNullOrBlank(),
                hasMessagePersonMetadata = hasMessagePersonMetadata,
                hasRemoteInputReply = hasRemoteInputReply,
                hasEmailCategory = notification.category == Notification.CATEGORY_EMAIL,
                hasUsefulContent = content.title.isNotBlank() && content.text.isNotBlank()
            )
        )
    }

    private fun resolveMessageIdentity(sbn: StatusBarNotification): MessageIdentity {
        val extras = sbn.notification.extras
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        return messageResolver.resolve(
            MessageNotificationSignals(
                packageName = sbn.packageName,
                notificationId = sbn.id,
                notificationTag = sbn.tag,
                shortcutId = sbn.notification.shortcutId,
                locusId = sbn.notification.locusId?.id,
                conversationTitle = conversationTitle,
                isGroupSummary = isGroupSummary
            )
        )
    }

    private fun gmailEmailContentFingerprint(
        sbn: StatusBarNotification,
        content: ResolvedNotificationContent
    ): Int? {
        if (sbn.packageName !in GMAIL_PACKAGES ||
            sbn.notification.category != Notification.CATEGORY_EMAIL
        ) {
            return null
        }
        val notification = sbn.notification
        val actions = (notification.actions ?: emptyArray()).map { action ->
            listOf(
                action.semanticAction,
                action.actionIntent != null,
                !action.remoteInputs.isNullOrEmpty()
            )
        }
        return listOf(
            content.title.hashCode(),
            content.text.hashCode(),
            notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.hashCode(),
            actions
        ).hashCode()
    }

    private fun resolveMessageEventFingerprint(
        sbn: StatusBarNotification,
        eventScope: String,
        content: ResolvedNotificationContent,
        effectiveTitle: String,
        effectiveText: String,
        processingGeneration: Long,
        callbackObservedAt: Long,
        recovery: Boolean
    ): MessageEventFingerprint? {
        val notification = sbn.notification
        return messageEventTracker.resolve(
            sourceKey = eventScope,
            contentHash = listOf(effectiveTitle, effectiveText, content.messageCount).hashCode(),
            signals = MessageEventSignals(
                latestMessageTimestamp = content.latestMessageTimestamp,
                messageCount = content.messageCount,
                notificationWhen = notification.`when`.takeIf { it > 0L },
                notificationWhenIsReliable = notification.`when` > 0L,
                sourcePostTime = sbn.postTime.takeIf { it > 0L }
            ),
            callbackGeneration = processingGeneration,
            observedAt = callbackObservedAt,
            recovery = recovery
        )
    }

    private suspend fun processStandardNotification(
        rawSbn: StatusBarNotification,
        sbn: StatusBarNotification,
        sourceSlot: String,
        recovery: Boolean = false,
        processingGeneration: Long,
        callbackObservedAt: Long,
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        val isSystemDndActive = manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val dndActive = preferences.isDndModeEnabledSync() || isDndModeEnabled || 
                ((preferences.autoDetectDndSync() || autoDetectDnd) && isSystemDndActive)
        val appBehaviorConfig = preferences.getAppIslandConfigSync(sbn.packageName)
        val globalBehaviorConfig = preferences.getGlobalConfigSync()
        val baseBehaviorConfig = appBehaviorConfig.mergeWith(globalBehaviorConfig)

        if (!recovery) retiredSourceKeys.remove(sbn.key)
        try {
            val extras = sbn.notification.extras
            val resolvedContent = resolveNotificationContent(sbn)
            val typeBeforeRules = detectNotificationType(sbn)

            var effectiveTitle = resolvedContent.title
            val effectiveText = resolvedContent.text
            if (effectiveTitle.isEmpty()) {
                effectiveTitle = getCachedAppLabel(sbn.packageName)
            }
            if (resolvedContent.latestMessageIsSelf) {
                markSourceHeadsUpSuppressed(sbn)
                return
            }

            val effectiveTypes = getEffectiveTypes(sbn.packageName)
            val hasDirectMessagingStyle = extras.getString(Notification.EXTRA_TEMPLATE)
                ?.contains("MessagingStyle") == true
            val semanticResult = SemanticNotificationResolver.resolve(
                rawType = typeBeforeRules,
                ruleTargetLayout = null,
                hasDirectMessagingStyle = hasDirectMessagingStyle,
                effectiveTypes = effectiveTypes
            )

            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val immersivePolicy = runCatching {
                android.provider.Settings.Global.getString(contentResolver, "policy_control").orEmpty().lowercase()
            }.getOrDefault("")
            val isFullscreen = immersivePolicy.contains("immersive.full") || immersivePolicy.contains("immersive.status")
            val sceneBehavior = when {
                dndActive -> baseBehaviorConfig.dndBehavior
                isFullscreen -> baseBehaviorConfig.fullscreenBehavior
                isLandscape -> baseBehaviorConfig.landscapeBehavior
                else -> com.sykeptical.hyperpop.models.IslandSceneBehavior.DEFAULT
            }
            if (sceneBehavior == com.sykeptical.hyperpop.models.IslandSceneBehavior.SUPPRESS) {
                Log.d(TAG, "DND active. Skipping notification ${rawSbn.packageName}")
                return
            }

            if (isVoicePlaybackShell(sbn, resolvedContent)) {
                markSourceHeadsUpSuppressed(sbn)
                DiagnosticsStore.record(typeBeforeRules.name, "ignored", sbn.packageName, "voice-playback-shell")
                return
            }

            if (isJunkNotification(sbn, resolvedContent)) {
                DiagnosticsStore.record(typeBeforeRules.name, "ignored", sbn.packageName, "junk-or-empty")
                return
            }

            if (sbn.packageName == ScreenRecordingClassifier.PACKAGE_NAME) {
                val flags = sbn.notification.flags
                // #region agent log
                AgentDebugLog.log(
                    "D",
                    "NotificationProcessingEngine.process",
                    "recorder notification",
                    "{\"id\":${sbn.id},\"type\":\"${typeBeforeRules.name}\",\"ongoing\":${(flags and Notification.FLAG_ONGOING_EVENT) != 0},\"fgs\":${(flags and Notification.FLAG_FOREGROUND_SERVICE) != 0},\"summary\":${(flags and Notification.FLAG_GROUP_SUMMARY) != 0}}",
                )
                // #endregion
            }
            val isActiveScreenRecording = typeBeforeRules == NotificationType.SCREEN_RECORDING
            val replaceScreenRecorder = HookConfigSync.replaceScreenRecorder(this)
            if (isActiveScreenRecording) {
                if (ScreenRecordingNotificationRoutingPolicy.shouldSuppressSourceHeadsUp(
                        isActiveScreenRecording = true,
                        replacementEnabled = replaceScreenRecorder,
                    )
                ) {
                    markSourceHeadsUpSuppressed(sbn)
                }
                DiagnosticsStore.record(
                    typeBeforeRules.name,
                    "ignored",
                    sbn.packageName,
                    if (replaceScreenRecorder) "dedicated-recorder-controller" else "recorder-replacement-disabled",
                )
                return
            }

            val hasProgress = hasProgressNotification(sbn, effectiveTitle, effectiveText)
            val isSavedScreenRecording = isSavedScreenRecordingNotification(sbn)
            if (sbn.packageName == ScreenRecordingClassifier.PACKAGE_NAME) {
                // #region agent log
                AgentDebugLog.log(
                    "E",
                    "NotificationProcessingEngine.process",
                    "saved classification",
                    "{\"id\":${sbn.id},\"saved\":$isSavedScreenRecording,\"type\":\"${typeBeforeRules.name}\"}",
                )
                // #endregion
            }
            if (!ScreenRecordingNotificationRoutingPolicy.shouldUseGenericPipeline(
                    isActiveScreenRecording = false,
                    isSavedScreenRecording = isSavedScreenRecording,
                )
            ) {
                return
            }
            if (
                effectiveTitle.isEmpty() &&
                !hasProgress &&
                typeBeforeRules != NotificationType.SCREEN_RECORDING &&
                !isSavedScreenRecording
            ) return

            if (preferences.isBlockedTermFast(sbn.packageName, effectiveTitle, effectiveText)) return

            val enabledType = semanticResult.enabledType
            if (enabledType == null) {
                Log.d(TAG, " ABORTING: Type ${semanticResult.detectedType} disabled by user for ${sbn.packageName}")
                DiagnosticsStore.record(semanticResult.detectedType.name, "ignored", sbn.packageName, "type-disabled")
                return
            }
            val type = enabledType
            val loginCode = if (type == NotificationType.STANDARD || type == NotificationType.MESSAGE) {
                detectLoginCode(sbn)
            } else {
                null
            }
            Log.d("HBLoginTest", "process key=${sbn.key} type=$type code=${loginCode != null} allowed=${isAppAllowed(sbn.packageName)} live=${getEffectiveEngine(sbn.packageName)}") // TEMP-TEST
            if (loginCode == null && !isAppAllowed(sbn.packageName)) return
            if (loginCode != null) {
                DiagnosticsStore.record(type.name, "login-code", sbn.packageName, "len=${loginCode.code.length}")
            }
            val isMessagingLifecycle = isMessagingLifecycleEvent(sbn, type, resolvedContent)

            var effectiveKey = sbn.key
            var messageEventFingerprint: MessageEventFingerprint? = null
            var callSession: CallSession? = null
            var retainedDownloadPercent: Int? = null
            var screenRecordingSession: ScreenRecordingSession? = null

            if (isSavedScreenRecording) {
                // Xiaomi reuses notification key/ID 111 for every completed recording. postTime is
                // the generation boundary, so a new saved file presents once while duplicate
                // callbacks for that same source generation remain idempotent.
                effectiveKey = ScreenRecordingSavedIdentity.logicalId(sbn.key, sbn.postTime)
            } else if (type == NotificationType.SCREEN_RECORDING) {
                val capabilities = screenRecordingControlBackend.probeCapabilities().also { probed ->
                    DiagnosticsStore.record(
                        classification = type.name,
                        action = "control-probed",
                        packageName = sbn.packageName,
                        reason = if (probed.canStop) "stop-available" else "visual-only"
                    )
                }
                val session = screenRecordingSessionTracker.resolve(
                    ScreenRecordingSessionInput(
                        sourceKey = sbn.key,
                        packageName = sbn.packageName,
                        sourcePostTime = sbn.postTime,
                        capabilities = capabilities,
                        paused = !extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, true)
                    )
                )
                effectiveKey = session.logicalId
                screenRecordingSession = session
            } else if (type == NotificationType.CALL) {
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
                        observedAt = now,
                        isVideoCall = signals.isVideoCall,
                        isRecovery = recovery
                    )
                )
                val participantPresent = !resolveCallParticipantId(sbn).isNullOrBlank()
                Log.d(
                    TAG,
                    "${if (signals.isVideoCall) "VIDEO CALL" else "CALL"} SIGNAL pkg=${sbn.packageName} " +
                            "sourceKeyHash=${sbn.key.hashCode()} id=${sbn.id} tagHash=${sbn.tag?.hashCode()} " +
                            "logicalIdHash=${session.logicalCallId.hashCode()} callType=${signals.callType} " +
                            "showsChronometer=${signals.showsChronometer} chronometerBase=${signals.whenTime} " +
                            "postTime=${sbn.postTime} groupHash=${sbn.groupKey?.hashCode()} " +
                            "ongoingFlag=${signals.isOngoingEvent} foreground=${signals.isForegroundService} " +
                            "video=${signals.isVideoCall} actionRoles=${classification.actionRoles} " +
                            "actionSemantics=${signals.actions.map { it.semanticAction }} " +
                            "remoteInput=${signals.actions.any { it.hasRemoteInput }} " +
                            "callPersonPresent=${if (participantPresent) "yes" else "no"} " +
                            "shortcutHash=${sbn.notification.shortcutId?.hashCode()} " +
                            "locusHash=${sbn.notification.locusId?.id?.hashCode()}"
                )
                Log.d(
                    TAG,
                    "${if (signals.isVideoCall) "VIDEO CALL" else "CALL"} TRANSITION " +
                            "previousState=${session.previousState} candidateState=${session.candidateState} " +
                            "resolvedState=${session.state} activeEvidence=${session.activeEvidence} " +
                            "connectedAtSource=${session.connectedAtSource} " +
                            "sourceReplacement=${if (session.sourceReplacement) "yes" else "no"} " +
                            "reason=${session.transitionReason}"
                )
                DiagnosticsStore.record(
                    classification = "CALL",
                    action = "classified",
                    packageName = sbn.packageName,
                    reason = classification.reason,
                    callState = session.state.name
                )
                effectiveKey = session.logicalCallId
                callSession = session
            } else if (NotificationLifecyclePolicy.isProgressLifecycle(type)) {
                val session = downloadSessionTracker.resolve(
                    DownloadSessionInput(
                        sourceKey = sbn.key,
                        packageName = sbn.packageName,
                        notificationId = sbn.id,
                        notificationTag = sbn.tag,
                        title = effectiveTitle,
                        text = effectiveText,
                        observedAt = System.currentTimeMillis(),
                        finished = isFinishedProgress(sbn),
                        progressPercent = reportedDownloadPercent(sbn, effectiveTitle, effectiveText),
                        paused = isPausedDownloadNotification(sbn, effectiveTitle, effectiveText),
                    )
                )
                effectiveKey = session.logicalId
                retainedDownloadPercent = session.progressPercent
            }

            if (isMessagingLifecycle) {
                sourceToLogicalKeys[sbn.key]?.let { existingLogicalId ->
                    val existing = activeIslands[existingLogicalId]
                    if (existing?.messageEventFingerprint != null && existing.packageName == sbn.packageName) {
                        effectiveKey = existingLogicalId
                    }
                }
                if (effectiveKey == sbn.key) {
                    val identity = resolveMessageIdentity(sbn)
                    effectiveKey = identity.logicalId
                }

                messageEventFingerprint = resolveMessageEventFingerprint(
                    sbn = sbn,
                    eventScope = effectiveKey,
                    content = resolvedContent,
                    effectiveTitle = effectiveTitle,
                    effectiveText = effectiveText,
                    processingGeneration = processingGeneration,
                    callbackObservedAt = callbackObservedAt,
                    recovery = recovery
                )

                val identity = resolveMessageIdentity(sbn)
                val family = messageFamilyTracker.resolve(
                    MessagePresentationSource(
                        sourceKey = sbn.key,
                        packageName = sbn.packageName,
                        proposedLogicalId = identity.logicalId,
                        identitySource = identity.source,
                        notificationType = type,
                        groupKey = sbn.groupKey,
                        isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
                        hasMessagingStyle = hasDirectMessagingStyle,
                        eventFingerprint = messageEventFingerprint,
                        notificationTag = sbn.tag,
                        sourcePostTime = sbn.postTime,
                        contentFingerprint = gmailEmailContentFingerprint(sbn, resolvedContent),
                        isEmailCategory = sbn.notification.category == Notification.CATEGORY_EMAIL
                    )
                )

                effectiveKey = family.logicalId
                if (family.shouldPresent) {
                    messageEventFingerprint = family.eventFingerprint
                }

                val existingIsland = activeIslands[effectiveKey]
                Log.d("HBLoginTest", "family key=${sbn.key} logical=$effectiveKey present=${family.shouldPresent} existing=${existingIsland != null} code=${loginCode != null}") // TEMP-TEST
                val incomingSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
                if (existingIsland != null && !family.shouldPresent) {
                    sourceToLogicalKeys[sbn.key] = effectiveKey
                    markSourceHeadsUpSuppressed(sbn)
                    return
                }
                if (!NotificationLifecyclePolicy.groupSummaryCanReplaceVisibleConversation(
                        incomingIsGroupSummary = incomingSummary,
                        visibleConversationSourceRemains = messageFamilyTracker.hasVisibleSource(effectiveKey),
                        islandAlreadyPresented = existingIsland != null,
                        sourceRemovalPending = removalJobs.containsKey(effectiveKey),
                    )
                ) {
                    sourceToLogicalKeys[sbn.key] = effectiveKey
                    markSourceHeadsUpSuppressed(sbn)
                    return
                }
            }

            sourceToLogicalKeys[sbn.key] = effectiveKey
            val finishedProgressUpdate = NotificationLifecyclePolicy.isProgressLifecycle(type) &&
                downloadSessionTracker.isFinished(effectiveKey)
            if (!finishedProgressUpdate) {
                removalJobs[effectiveKey]?.cancel()
                removalJobs.remove(effectiveKey)
            }
            val previous = activeIslands[effectiveKey]

            if (callSession != null && !CallStageVisibilityPolicy.isVisible(
                    getEffectiveCallStages(sbn.packageName),
                    callSession.state
                )
            ) {
                suppressCallStagePresentation(
                    sbn = sbn,
                    logicalKey = effectiveKey,
                    session = callSession,
                    previous = previous
                )
                return
            }

            val candidateBridgeId = previous?.id ?: effectiveKey.hashCode()
            val postedBridgeId = if (isMessagingLifecycle) {
                MessageBridgeIdPolicy.candidate(effectiveKey, processingGeneration, 0)
            } else {
                candidateBridgeId
            }
            val isUpdate = alreadyPostedIsland(effectiveKey, postedBridgeId, previous)

            if (isMessagingLifecycle && previous != null &&
                previous.sourceKey == sbn.key && sbn.postTime < previous.sourcePostTime
            ) {
                Log.d(TAG, "MESSAGE skip stale update logical=${effectiveKey.hashCode()}")
                return
            }

            val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            val incomingCallBanner = callSession?.state == CallState.INCOMING_RINGING &&
                sbn.notification.fullScreenIntent != null
            val replaceWithSourceFocus = NotificationLifecyclePolicy.carriesVisibleSourceFocus(type)
            val actionFingerprint = islandActionFingerprint(sbn, loginCode)
            val actionsChanged = NotificationActionIdentity.changed(previous?.actionFingerprint, actionFingerprint)
            val actionRefreshBridgeId = if (
                actionsChanged && previous != null
            ) {
                NotificationActionIdentity.refreshBridgeId(effectiveKey, actionFingerprint, previous.id)
            } else {
                null
            }
            if (actionsChanged) {
                Log.i(
                    TAG,
                    "ACTION REFRESH logical=${effectiveKey.hashCode()} " +
                        "from=${previous?.id} to=$actionRefreshBridgeId"
                )
            }

            // --- LAYERED ENGINE LOGIC ---
            // Native Live Updates cannot carry the Copy code button, glow or compact code.
            val useLiveUpdates = type != NotificationType.SCREEN_RECORDING &&
                    type != NotificationType.VOICE_MESSAGE &&
                    !isSavedScreenRecording &&
                    loginCode == null &&
                    !replaceWithSourceFocus &&
                    getEffectiveEngine(sbn.packageName)
            val finalConfig = baseBehaviorConfig.let { config ->
                config.copy(
                    timeout = ScreenRecordingTimeoutPolicy.resolve(
                        configuredTimeout = config.timeout,
                        systemScreenRecordingTimeout = preferences.getScreenRecordingTimeoutSync(),
                        isActiveRecording = type == NotificationType.SCREEN_RECORDING,
                        isSavedRecording = isSavedScreenRecording
                    )
                )
            }.let { config ->
                when (sceneBehavior) {
                    com.sykeptical.hyperpop.models.IslandSceneBehavior.SMALL_ONLY -> config.copy(firstFloat = false, floatOnUpdate = false)
                    com.sykeptical.hyperpop.models.IslandSceneBehavior.EXPAND -> config.copy(firstFloat = true, floatOnUpdate = true)
                    else -> config
                }
            }

            if (useLiveUpdates) {
                Log.i(TAG, " POSTING Native Live Update -> ID: $candidateBridgeId, Type: $type")
                val navLayout = if (type == NotificationType.NAVIGATION) getEffectiveNav(sbn.packageName) else null

                val builder = liveUpdateTranslator.translateToLiveUpdate(
                    sbn = sbn,
                    channelId = LIVE_UPDATE_CHANNEL_ID,
                    type = type,
                    navRight = navLayout?.second,
                    config = finalConfig
                )

                builder.extras.putString(EXTRA_ORIGINAL_KEY, sbn.key)

                val actualProgress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
                val actualMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
                val isIndeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
                val actionState = sbn.notification.actions?.joinToString { it.title?.toString() ?: "" } ?: ""

                val newContentHash = effectiveTitle.hashCode() * 31 +
                        effectiveText.hashCode() + actualProgress + actualMax +
                        isIndeterminate.hashCode() + actionState.hashCode()

                val presentationBridgeId = if (isMessagingLifecycle) {
                    MessageBridgeIdPolicy.candidate(
                        effectiveKey,
                        processingGeneration,
                        newContentHash,
                        messageEventFingerprint = messageEventFingerprint,
                    )
                } else {
                    candidateBridgeId
                }

                if (shouldSuppressExpiredSource(sbn, type, effectiveKey, newContentHash, recovery, messageEventFingerprint)) {
                    return
                }

                val decision = IslandUpdateResolver.decide(
                    logicalId = effectiveKey,
                    candidateBridgeId = presentationBridgeId,
                    contentHash = newContentHash,
                    previous = previousIslandPresentation(previous, isUpdate, effectiveKey, presentationBridgeId),
                    notificationType = type,
                    isMessagingEvent = isMessagingLifecycle,
                    messageEventFingerprint = messageEventFingerprint,
                    actionsChanged = actionsChanged,
                    actionRefreshBridgeId = actionRefreshBridgeId,
                )

                if (decision.kind == IslandPresentationKind.UNCHANGED) {
                    markSourceHeadsUpSuppressed(sbn, incomingCallBanner)
                    return
                }

                builder.setOnlyAlertOnce(decision.onlyAlertOnce)

                val notification = builder.build()
                val glow = IslandGlowResolver.resolve(
                    finalConfig,
                    IslandConfig(),
                    AppIconPalette.color(this, sbn.packageName),
                )
                val visualPlan = IslandVisualMetadata.plan(
                    config = finalConfig,
                    glow = glow,
                    keepPosted = sourceStaysPosted(sbn, type),
                    marqueeCapable = marqueeCapabilitiesReady(),
                    updatable = NotificationLifecyclePolicy.isProgressLifecycle(type),
                )
                IslandVisualExtras.apply(notification.extras, visualPlan)
                val postedId = decision.bridgeId
                val floatPresentation = IslandFloatingPresentationPolicy.resolve(
                    finalConfig.firstFloat ?: false,
                    finalConfig.floatOnUpdate ?: false,
                    isUpdate = decision.kind == IslandPresentationKind.UPDATE,
                    expansionLocked = false,
                )
                if (decision.kind == IslandPresentationKind.UPDATE) {
                    notification.extras.putBoolean("miui.island.updateNoFloat", true)
                }
                notification.extras.putBoolean("miui.enableFloat", floatPresentation.enableFloat)
                notification.extras.getString("miui.focus.param")?.let { json ->
                    notification.extras.putString(
                        "miui.focus.param",
                        injectPresentationFloat(
                            IslandVisualMetadata.injectUpdatable(json, visualPlan.updatable),
                            floatPresentation,
                            lockExpansion = false,
                        ),
                    )
                }

                if (decision.cancelBeforeNotify && previous?.id != postedId) {
                    previous?.id?.let { replacedBridgeId ->
                        internalBridgeReplacements.mark(replacedBridgeId, effectiveKey, processingGeneration, System.currentTimeMillis())
                        islandBackend.cancel(replacedBridgeId)
                        reverseTranslations.remove(replacedBridgeId)
                    }
                }

                val islandPostGeneration = System.currentTimeMillis()
                if (!postIsland(
                        postedId,
                        notification,
                        postedId.toString(),
                        sbn,
                        type,
                        islandPostGeneration,
                        inPlaceUpdate = isUpdate && !decision.cancelBeforeNotify,
                        incomingCallBanner = incomingCallBanner,
                    )) return

                expiredIslands.acceptNewGeneration(
                    sbn.key,
                    sourceGenerationFingerprint(newContentHash, sbn.postTime),
                    messageEventFingerprint,
                    effectiveKey
                )

                releasePreviousBridgeIfMoved(previous, postedId)

                activeTranslations[effectiveKey] = postedId
                reverseTranslations[postedId] = effectiveKey
                activeIslands[effectiveKey] = ActiveIsland(
                    id = postedId,
                    type = type,
                    postTime = System.currentTimeMillis(),
                    sourcePostTime = sbn.postTime,
                    packageName = sbn.packageName,
                    sourceKey = sbn.key,
                    logicalId = effectiveKey,
                    groupKey = sbn.groupKey,
                    isGroupSummary = isSummary,
                    generation = processingGeneration,
                    title = effectiveTitle,
                    text = effectiveText,
                    subText = "LiveUpdate",
                    lastContentHash = newContentHash,
                    actionFingerprint = actionFingerprint,
                    messageEventFingerprint = messageEventFingerprint,
                    deleteIntent = sbn.notification.deleteIntent
                )
                updateIslandDiagnostics()

                handlePostNotificationSideEffects(effectiveKey, postedId, processingGeneration, finalConfig, type, true, sbn, effectiveTitle, effectiveText)
                return
            }

            // --- LAYERED CUSTOM ISLAND LOGIC ---
            val picKey = IslandCompactLayout.pictureKey(effectiveKey)
            val keepPosted = sourceStaysPosted(sbn, type)
            val translationConfig = finalConfig.copy(
                timeout = IslandVisualMetadata.plan(
                    config = finalConfig,
                    glow = IslandGlowResolver.resolve(finalConfig, IslandConfig(), null),
                    keepPosted = keepPosted,
                    marqueeCapable = marqueeCapabilitiesReady(),
                ).islandTimeoutSeconds,
                floatOnUpdate = finalConfig.floatOnUpdate == true &&
                    NotificationLifecyclePolicy.allowsConfiguredUpdateExpansion(type),
            )
            var data: HyperIslandData = if (isSavedScreenRecording) {
                screenRecordingSavedTranslator.translate(sbn, picKey, translationConfig)
            } else when (type) {
                NotificationType.CALL -> callTranslator.translate(
                    sbn, picKey, translationConfig,
                    requireNotNull(callSession), isUpdate,
                    resolvedTitle = effectiveTitle
                )
                NotificationType.NAVIGATION -> {
                    val navLayout = getEffectiveNav(sbn.packageName)
                    navTranslator.translate(sbn, picKey, translationConfig, navLayout.first, navLayout.second, isUpdate)
                }
                NotificationType.TIMER -> timerTranslator.translate(sbn, picKey, translationConfig, isUpdate)
                NotificationType.PROGRESS -> progressTranslator.translate(sbn, effectiveTitle, picKey, translationConfig, isUpdate)
                NotificationType.DOWNLOAD -> downloadTranslator.translate(
                    sbn, effectiveTitle, picKey, translationConfig, isUpdate,
                    retainedPercent = retainedDownloadPercent,
                )
                NotificationType.MEDIA -> mediaTranslator.translate(sbn, picKey, translationConfig, isUpdate)
                NotificationType.SCREEN_RECORDING -> screenRecordingTranslator.translate(
                    requireNotNull(screenRecordingSession),
                    design = preferences.getScreenRecordingDesignSync()
                )
                NotificationType.MESSAGE -> messageTranslator.translate(
                    sbn, effectiveTitle, effectiveText, picKey, translationConfig, isUpdate,
                    loginCode = loginCode,
                )
                NotificationType.VOICE_MESSAGE -> voiceMessageTranslator.translate(
                    sbn,
                    effectiveTitle,
                    effectiveText,
                    picKey,
                    translationConfig,
                    isUpdate,
                    compactDuration = preferences.getEffectiveVoiceCompactDurationSync(sbn.packageName),
                )
                else -> standardTranslator.translate(
                    sbn, effectiveTitle, effectiveText, picKey, translationConfig, isUpdate,
                    loginCode = loginCode,
                )
            }

            val newContentHash = if (type == NotificationType.SCREEN_RECORDING && screenRecordingSession != null) {
                ScreenRecordingSemanticFingerprint.compute(
                    screenRecordingSession,
                    preferences.getScreenRecordingDesignSync()
                )
            } else {
                val normalizedJson = RenderedJsonNormalizer.normalize(data.jsonParam)
                normalizedJson?.hashCode() ?: data.jsonParam.hashCode()
            }

            val presentationBridgeId = if (isMessagingLifecycle) {
                MessageBridgeIdPolicy.candidate(
                    effectiveKey,
                    processingGeneration,
                    newContentHash,
                    messageEventFingerprint = messageEventFingerprint,
                )
            } else {
                candidateBridgeId
            }

            if (shouldSuppressExpiredSource(sbn, type, effectiveKey, newContentHash, recovery, messageEventFingerprint)) {
                return
            }

            val resolvedDecision = IslandUpdateResolver.decide(
                logicalId = effectiveKey,
                candidateBridgeId = presentationBridgeId,
                contentHash = newContentHash,
                previous = previousIslandPresentation(previous, isUpdate, effectiveKey, presentationBridgeId),
                notificationType = type,
                isMessagingEvent = isMessagingLifecycle,
                messageEventFingerprint = messageEventFingerprint,
                actionsChanged = actionsChanged,
                actionRefreshBridgeId = actionRefreshBridgeId,
            )
            // A code arriving in a conversation that already has an island must still expand and
            // glow like a new one, so it gets a fresh island instead of an in-place update.
            val decision = if (
                loginCode != null && previous != null && previous.loginCode != loginCode.code &&
                resolvedDecision.kind == IslandPresentationKind.UPDATE
            ) {
                resolvedDecision.copy(
                    kind = IslandPresentationKind.NEW,
                    bridgeId = NotificationActionIdentity.refreshBridgeId(effectiveKey, actionFingerprint, previous.id),
                    onlyAlertOnce = false,
                    presentationReason = IslandPresentationReason.NEW_EVENT,
                    cancelBeforeNotify = true,
                )
            } else {
                resolvedDecision
            }
            Log.d("HBLoginTest", "decision key=${sbn.key} logical=$effectiveKey kind=${decision.kind} resolved=${resolvedDecision.kind} id=${decision.bridgeId} prev=${previous?.id} actionsChanged=$actionsChanged code=${loginCode != null} type=$type") // TEMP-TEST

            val supportsSourceFocus = replaceWithSourceFocus
            val focusSemanticType = type
            val switchingFocusMode = supportsSourceFocus &&
                previous != null &&
                previous.sourceFocus != replaceWithSourceFocus
            if (decision.kind == IslandPresentationKind.UNCHANGED && !switchingFocusMode) {
                val focusRefreshed = !replaceWithSourceFocus || !supportsSourceFocus ||
                    attachSourceFocus(
                        sbn,
                        data,
                        effectiveTitle,
                        effectiveText,
                        translationConfig,
                        inPlaceUpdate = SourceFocusUpdatePolicy.isInPlace(decision.kind),
                        semanticType = focusSemanticType,
                        focusIdentity = effectiveKey,
                        connected = callSession?.state == CallState.ACTIVE,
                    )
                if (focusRefreshed) {
                    markSourceHeadsUpSuppressed(sbn, incomingCallBanner)
                    return
                }
            }

            if (replaceWithSourceFocus && supportsSourceFocus &&
                attachSourceFocus(
                    sbn,
                    data,
                    effectiveTitle,
                    effectiveText,
                    translationConfig,
                    inPlaceUpdate = SourceFocusUpdatePolicy.isInPlace(decision.kind),
                    semanticType = focusSemanticType,
                    focusIdentity = effectiveKey,
                    connected = callSession?.state == CallState.ACTIVE,
                )
            ) {
                if (previous != null && !previous.sourceFocus) {
                    internalBridgeReplacements.mark(
                        previous.id,
                        effectiveKey,
                        processingGeneration,
                        System.currentTimeMillis(),
                    )
                    islandBackend.cancel(previous.id)
                    reverseTranslations.remove(previous.id)
                }
                markSourceHeadsUpSuppressed(sbn, incomingCallBanner)
                expiredIslands.acceptNewGeneration(
                    sbn.key,
                    sourceGenerationFingerprint(newContentHash, sbn.postTime),
                    messageEventFingerprint,
                    effectiveKey,
                )
                activeTranslations[effectiveKey] = decision.bridgeId
                reverseTranslations[decision.bridgeId] = effectiveKey
                activeIslands[effectiveKey] = ActiveIsland(
                    id = decision.bridgeId,
                    type = type,
                    postTime = System.currentTimeMillis(),
                    sourcePostTime = sbn.postTime,
                    packageName = sbn.packageName,
                    sourceKey = sbn.key,
                    logicalId = effectiveKey,
                    groupKey = sbn.groupKey,
                    isGroupSummary = isSummary,
                    generation = processingGeneration,
                    title = effectiveTitle,
                    text = effectiveText,
                    subText = "",
                    lastContentHash = newContentHash,
                    actionFingerprint = actionFingerprint,
                    messageEventFingerprint = messageEventFingerprint,
                    callSession = callSession,
                    deleteIntent = sbn.notification.deleteIntent,
                    sourceFocus = true,
                )
                updateIslandDiagnostics()
                handlePostNotificationSideEffects(
                    originalKey = effectiveKey,
                    bridgeId = decision.bridgeId,
                    generation = processingGeneration,
                    config = finalConfig,
                    type = type,
                    isLiveUpdate = false,
                    sbn = sbn,
                    title = effectiveTitle,
                    text = effectiveText,
                )
                return
            }

            val postedId = decision.bridgeId

            // Same-conversation messages update the existing island in place. A new event from
            // another person still uses a different logical id and presents as NEW.
            if (decision.cancelBeforeNotify && previous?.id != postedId) {
                previous?.id?.let { replacedBridgeId ->
                    internalBridgeReplacements.mark(replacedBridgeId, effectiveKey, processingGeneration, System.currentTimeMillis())
                    islandBackend.cancel(replacedBridgeId)
                    reverseTranslations.remove(replacedBridgeId)
                }
            }

            Log.i(
                TAG,
                " POSTING Island -> ID: $postedId, kind=${decision.kind}, " +
                    "prevId=${previous?.id}, pic=$picKey, Type: $type, " +
                    if (loginCode != null) {
                        "login code island"
                    } else {
                        "FinalTitle: '$effectiveTitle', FinalText: '$effectiveText'"
                    }
            )
            val posted = postStandardNotification(
                sbn = sbn,
                bridgeId = postedId,
                data = data,
                title = effectiveTitle,
                text = effectiveText,
                shouldAlertOnce = decision.onlyAlertOnce,
                // The saved-recording notification owns a direct activity PendingIntent for the
                // captured video. Preserve it so tapping the confirmation island opens that file.
                suppressContentIntent = false,
                config = translationConfig,
                updatableOverride = NotificationLifecyclePolicy.isProgressLifecycle(type),
                inPlaceUpdate = isUpdate && !decision.cancelBeforeNotify,
                floatAsUpdate = decision.kind == IslandPresentationKind.UPDATE,
                lockExpansion = false,
                postGeneration = System.currentTimeMillis(),
                incomingCallBanner = incomingCallBanner,
                nativeGlow = loginCode?.glow == true,
            )
            if (!posted) return

            expiredIslands.acceptNewGeneration(
                sbn.key,
                sourceGenerationFingerprint(newContentHash, sbn.postTime),
                messageEventFingerprint,
                effectiveKey
            )

            releasePreviousBridgeIfMoved(previous, postedId)

            activeTranslations[effectiveKey] = postedId
            reverseTranslations[postedId] = effectiveKey
            activeIslands[effectiveKey] = ActiveIsland(
                id = postedId,
                type = type,
                postTime = System.currentTimeMillis(),
                sourcePostTime = sbn.postTime,
                packageName = sbn.packageName,
                sourceKey = sbn.key,
                logicalId = effectiveKey,
                groupKey = sbn.groupKey,
                isGroupSummary = isSummary,
                generation = processingGeneration,
                title = effectiveTitle,
                text = effectiveText,
                subText = "",
                lastContentHash = newContentHash,
                actionFingerprint = actionFingerprint,
                messageEventFingerprint = messageEventFingerprint,
                callSession = callSession,
                screenRecordingSession = screenRecordingSession,
                deleteIntent = sbn.notification.deleteIntent,
                dismissSourceOnContentClick = isSavedScreenRecording,
                loginCode = loginCode?.code,
            )
            updateIslandDiagnostics()

            handlePostNotificationSideEffects(
                originalKey = effectiveKey,
                bridgeId = postedId,
                generation = processingGeneration,
                config = finalConfig,
                type = type,
                isLiveUpdate = false,
                sbn = sbn,
                title = effectiveTitle,
                text = effectiveText,
                forceLifecycleTimeout = isSavedScreenRecording
            )

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error processing standard notification", e)
        }
    }

    private fun isDownloadNotification(sbn: StatusBarNotification, title: String, text: String): Boolean {
        val pkg = sbn.packageName.lowercase()
        val titleLower = title.lowercase()
        val textLower = text.lowercase()
        val channelId = sbn.notification.channelId?.lowercase() ?: ""
        
        if (ChromeNotificationPolicy.isIncognito(sbn.packageName, sbn.notification.channelId)) {
            return false
        }
        val isMatch = if (pkg.contains("download") || pkg.contains("downloader") ||
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

        Log.d(TAG, "🔍 isDownloadNotification check: pkg=$pkg, channelId='$channelId', title='$title', text='$text', resolved=$isMatch")
        return isMatch
    }

    private fun hasProgressNotification(sbn: StatusBarNotification, title: String, text: String): Boolean {
        if (ChromeNotificationPolicy.isIncognito(sbn.packageName, sbn.notification.channelId)) return false
        val extras = sbn.notification.extras
        val isDownload = isDownloadNotification(sbn, title, text)
        val isOngoing = (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val structural = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
                extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) ||
                (isDownload && extractTextPercentage(title, text) != null) ||
                (isDownload && isOngoing)
        if (structural) return true
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        return isPausedDownloadNotification(sbn, title, text) ||
            downloadSessionTracker.matchesLiveDownload(sbn.packageName, sbn.key, title, text)
    }

    private fun reportedDownloadPercent(sbn: StatusBarNotification, title: String, text: String): Int? {
        val extras = sbn.notification.extras
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        if (max > 0) {
            val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
            return ((current.toFloat() / max.toFloat()) * 100).toInt().coerceIn(0, 100)
        }
        return extractTextPercentage(title, text)
    }

    private fun isPausedDownloadNotification(sbn: StatusBarNotification, title: String, text: String): Boolean {
        val extras = sbn.notification.extras
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
        val actionTitles = sbn.notification.actions?.map { it.title?.toString().orEmpty() }.orEmpty()
        return DownloadPausePolicy.isPaused(
            isDownload = isDownloadNotification(sbn, title, text),
            title = title,
            text = text,
            actionTitles = actionTitles,
            extraText = "$subText $infoText",
            finished = isFinishedProgress(sbn),
            groupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
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
                    hasPendingIntent = it.actionIntent != null,
                    hasRemoteInput = !it.remoteInputs.isNullOrEmpty()
                )
            },
            isOngoingEvent = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            isForegroundService = (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
            isVideoCall = extras.getBoolean(Notification.EXTRA_CALL_IS_VIDEO, false),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
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

    private fun suppressCallStagePresentation(
        sbn: StatusBarNotification,
        logicalKey: String,
        session: CallSession,
        previous: ActiveIsland?
    ) {
        if (previous?.sourceFocus == true && session.state == CallState.ENDED) {
            // The shade row is the focus snapshot. A hang-up that only rewrites the dialer
            // notification is ignored until a newer payload sets cancel.
            attachSourceFocusCancellation(sbn, NotificationType.CALL, logicalKey)
        }
        if (previous?.type == NotificationType.CALL) {
            activeTranslations[logicalKey]?.let { bridgeId ->
                try {
                    islandBackend.cancel(bridgeId)
                } catch (_: Exception) {}
            }
            cleanupCache(logicalKey, preserveCallSession = true)
        }
        sourceToLogicalKeys[sbn.key] = logicalKey
        Log.d(
            TAG,
            "CALL STAGE HIDDEN pkg=${sbn.packageName} logicalIdHash=${logicalKey.hashCode()} " +
                    "state=${session.state} stage=${CallStageVisibilityPolicy.stageFor(session.state)}"
        )
        DiagnosticsStore.record(
            classification = "CALL",
            action = "stage-hidden",
            packageName = sbn.packageName,
            reason = CallStageVisibilityPolicy.stageFor(session.state)?.name,
            callState = session.state.name
        )
    }

    private suspend fun ensureValidSbn(sbn: StatusBarNotification): StatusBarNotification {
        val extras = sbn.notification.extras
        val title = resolveTitle(sbn)
        val text = resolveText(extras)
        val hasProgress = hasProgressNotification(sbn, title, text)
        if (hasProgress) return sbn

        val pkg = sbn.packageName

        val isSuspicious = title.isEmpty() || text.equals(pkg, ignoreCase = true)

        if (isSuspicious) {
            delay(150.milliseconds)
            try {
                val activeList = activeNotificationsProvider()
                val updatedSbn = activeList.firstOrNull { it.key == sbn.key }
                if (updatedSbn != null) return updatedSbn
            } catch (_: Exception) { }
        }
        return sbn
    }

    private fun detectNotificationType(sbn: StatusBarNotification): NotificationType {
        val n = sbn.notification
        val extras = n.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
        val isCall = callClassifier.classify(buildCallSignals(sbn)).isCall
        val isNav = n.category == Notification.CATEGORY_NAVIGATION || sbn.packageName.let { it.contains("maps") || it.contains("waze") }
        val isTimer = (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) || n.category == Notification.CATEGORY_ALARM) && n.`when` > 0
        val mediaTransport = template.contains("MediaStyle") || n.category == Notification.CATEGORY_TRANSPORT
        val mediaBackedVoice = MediaBackedVoiceClassifier.isVoiceMessage(mediaBackedVoiceSignals(sbn))
        val isMedia = mediaTransport && !mediaBackedVoice
        val isMessage = n.category == Notification.CATEGORY_MESSAGE || template.contains("MessagingStyle")
        
        val title = resolveTitle(sbn)
        val text = resolveText(extras)
        val continuesDownload = !ChromeNotificationPolicy.isIncognito(sbn.packageName, n.channelId) &&
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
            downloadSessionTracker.matchesLiveDownload(sbn.packageName, sbn.key, title, text)
        val isDownload = isDownloadNotification(sbn, title, text) || continuesDownload
        val hasProgress = hasProgressNotification(sbn, title, text) || continuesDownload
        val signals = voicePlaybackSignals(sbn, title, text)
        val isVoice = mediaBackedVoice || VoicePlaybackDetector.isVoicePlayback(signals)
        val isScreenRecording = ScreenRecordingClassifier.isScreenRecording(
            ScreenRecordingSignals(
                packageName = sbn.packageName,
                notificationId = sbn.id,
                channelId = n.channelId,
                isOngoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0,
                isForegroundService = (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
                isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            )
        )

        return RawNotificationTypeClassifier.classify(
            RawNotificationTypeSignals(
                isScreenRecording = isScreenRecording,
                isCall = isCall,
                isNavigation = isNav,
                isTimer = isTimer,
                isMedia = isMedia,
                isMessage = isMessage,
                hasProgress = hasProgress,
                isDownload = isDownload,
                isVoice = isVoice,
            )
        )
    }

    private fun isSavedScreenRecordingNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        return ScreenRecordingClassifier.isSavedScreenRecording(
            ScreenRecordingSignals(
                packageName = sbn.packageName,
                notificationId = sbn.id,
                channelId = notification.channelId,
                isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
                isForegroundService = (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
                isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            )
        )
    }

    private fun isScreenRecordingNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        return ScreenRecordingClassifier.isScreenRecording(
            ScreenRecordingSignals(
                packageName = sbn.packageName,
                notificationId = sbn.id,
                channelId = notification.channelId,
                isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
                isForegroundService = (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
                isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            )
        )
    }

    private fun postStandardNotification(
        sbn: StatusBarNotification,
        bridgeId: Int,
        data: HyperIslandData,
        title: String,
        text: String,
        shouldAlertOnce: Boolean,
        suppressContentIntent: Boolean = false,
        config: IslandConfig,
        updatableOverride: Boolean? = null,
        inPlaceUpdate: Boolean = false,
        floatAsUpdate: Boolean = inPlaceUpdate,
        lockExpansion: Boolean = false,
        postGeneration: Long = System.currentTimeMillis(),
        incomingCallBanner: Boolean = false,
        nativeGlow: Boolean = false,
    ): Boolean {
        val notification = assembleIslandNotification(
            sbn = sbn,
            data = data,
            title = title,
            text = text,
            shouldAlertOnce = shouldAlertOnce,
            suppressContentIntent = suppressContentIntent,
            config = config,
            updatableOverride = updatableOverride,
            inPlaceUpdate = floatAsUpdate,
            lockExpansion = lockExpansion,
            nativeGlow = nativeGlow,
        )
        val posted = postIsland(
            bridgeId,
            notification,
            bridgeId.toString(),
            sbn,
            detectNotificationType(sbn),
            postGeneration,
            inPlaceUpdate = inPlaceUpdate,
            incomingCallBanner = incomingCallBanner,
        )
        if (!posted) return false
        return true
    }

    private fun assembleIslandNotification(
        sbn: StatusBarNotification,
        data: HyperIslandData,
        title: String,
        text: String,
        shouldAlertOnce: Boolean,
        suppressContentIntent: Boolean = false,
        config: IslandConfig,
        updatableOverride: Boolean? = null,
        inPlaceUpdate: Boolean = false,
        lockExpansion: Boolean = false,
        nativeGlow: Boolean = false,
    ): Notification {
        val semanticType = detectNotificationType(sbn)
        val keepPosted = sourceStaysPosted(sbn, semanticType)
        val updatable = updatableOverride ?: (
            keepPosted || NotificationLifecyclePolicy.isProgressLifecycle(semanticType)
            )
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            // This proxy is also used for STANDARD/WhatsApp notifications. Showing the
            // diagnostic string here falsely reports an error every time such an island is
            // posted or opened.
            .setContentTitle(title.ifBlank { getCachedAppLabel(sbn.packageName) })
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // This is a SystemUI-owned transport notification, not the source notification.
            // Keep it stable until our lifecycle code explicitly removes it.  Making message
            // proxies auto-cancelable lets SystemUI retire the Focus entry while a same-key
            // notify update is being applied, which instantly kills an active WhatsApp island.
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(shouldAlertOnce)

        val extras = Bundle()
        extras.putString(EXTRA_ORIGINAL_KEY, sbn.key)
        builder.addExtras(extras)
        builder.addExtras(data.resources)

        if (!suppressContentIntent) {
            // Keep the source app as the PendingIntent creator. Routing an activity PendingIntent
            // through a HyperPop broadcast is a notification trampoline and modern Android
            // rejects that indirect launch when the big island is tapped.
            sbn.notification.contentIntent?.let(builder::setContentIntent)
        }

        val glowDynamicColor = if (semanticType == NotificationType.MEDIA) {
            AppIconPalette.prefer(data.accentColor, AppIconPalette.color(this, sbn.packageName))
        } else {
            AppIconPalette.color(this, sbn.packageName) ?: IslandGlowResolver.normalizeColor(data.accentColor)
        }
        val glow = if (nativeGlow) {
            LoginCodePresentation.NATIVE_GLOW
        } else {
            IslandGlowResolver.resolve(
                config,
                IslandConfig(),
                glowDynamicColor,
            )
        }
        val visualPlan = IslandVisualMetadata.plan(
            config = config,
            glow = glow,
            keepPosted = keepPosted,
            marqueeCapable = marqueeCapabilitiesReady(),
            updatable = updatable,
        )
        val floatPresentation = IslandFloatingPresentationPolicy.resolve(
            config.firstFloat ?: false,
            config.floatOnUpdate ?: false,
            isUpdate = inPlaceUpdate,
            expansionLocked = lockExpansion,
        )
        val notification = builder.build()
        notification.extras.putString(
            "miui.focus.param",
            injectPresentationFloat(
                IslandVisualMetadata.injectUpdatable(
                    IslandVisualMetadata.injectGlowJson(data.jsonParam, glow),
                    updatable,
                ),
                floatPresentation,
                lockExpansion,
            ),
        )
        IslandVisualExtras.apply(notification.extras, visualPlan)
        if (nativeGlow) notification.extras.putBoolean(IslandProtocol.EXTRA_GLOW_NATIVE, true)
        notification.extras.putBoolean("miui.enableFloat", floatPresentation.enableFloat)
        if (semanticType == NotificationType.CALL || semanticType == NotificationType.VOICE_MESSAGE) {
            notification.extras.putString("miui.pkg.name", sbn.packageName)
        }
        if (inPlaceUpdate || lockExpansion) {
            notification.extras.putBoolean("miui.island.updateNoFloat", true)
            notification.extras.putBoolean(IslandProtocol.EXTRA_TEXT_UPDATE_ANIMATION, true)
        }

        return notification
    }

    /**
     * Attaches the expanded Focus card to the source notification and leaves that notification
     * posted. HyperOS replaces the app shade row with this card. Returns false when the
     * decoration is too large to travel back over the processing Binder call; the caller then
     * posts the SystemUI proxy instead.
     */
    private fun attachSourceFocus(
        sbn: StatusBarNotification,
        data: HyperIslandData,
        title: String,
        text: String,
        config: IslandConfig,
        inPlaceUpdate: Boolean,
        semanticType: NotificationType = NotificationType.CALL,
        focusIdentity: String = sbn.key,
        connected: Boolean = false,
    ): Boolean {
        val notification = assembleIslandNotification(
            sbn = sbn,
            data = data,
            title = title,
            text = text,
            shouldAlertOnce = true,
            config = config,
            updatableOverride = true,
            inPlaceUpdate = inPlaceUpdate,
        )
        val extras = notification.extras
        val decoration = Bundle()
        for (key in extras.keySet()) {
            if (!isSourceFocusExtra(key)) continue
            when (val value = extras.get(key)) {
                is Bundle -> decoration.putBundle(key, Bundle(value))
                is String -> decoration.putString(key, value)
                is Boolean -> decoration.putBoolean(key, value)
                is Int -> decoration.putInt(key, value)
                is Long -> decoration.putLong(key, value)
                is android.os.Parcelable -> decoration.putParcelable(key, value)
            }
        }
        decoration.remove(IslandProtocol.EXTRA_OWNER)
        decoration.putBoolean(IslandProtocol.EXTRA_SOURCE_FOCUS, true)
        decoration.putBoolean(IslandProtocol.EXTRA_SOURCE_FOCUS_REPLACE_SHADE, true)
        decoration.putBoolean(
            IslandProtocol.EXTRA_SOURCE_FOCUS_ONGOING,
            SourceFocusShadePolicy.keepSourceOngoing(
                type = semanticType,
                finished = NotificationLifecyclePolicy.isProgressLifecycle(semanticType) &&
                    isFinishedProgress(sbn),
                cancelling = false,
            ),
        )
        decoration.putString(IslandProtocol.EXTRA_SOURCE_PACKAGE, sbn.packageName)
        // SystemUI names the island with MiuiBaseNotifUtil.getTargetPkg, which is this
        // notification's package. The exit animation matches that name to the closing app.
        decoration.putString("miui.pkg.name", sbn.packageName)
        decoration.putString(IslandProtocol.EXTRA_SEMANTIC_TYPE, semanticType.name)
        decoration.putBoolean(IslandProtocol.EXTRA_CALL_CONNECTED, connected)
        val focusParam = decoration.getString("miui.focus.param")
        if (focusParam.isNullOrBlank()) return false
        decoration.putString(
            "miui.focus.param",
            FocusShadeUpdate.stampForSource(focusParam, focusIdentity),
        )
        val size = runCatching { bundleSize(decoration) }.getOrElse { return false }
        if (size > SOURCE_FOCUS_DECORATION_LIMIT) {
            Log.w(TAG, "Source focus decoration is $size bytes; keeping the SystemUI proxy")
            return false
        }
        if (semanticType == NotificationType.VOICE_MESSAGE) {
            voiceFocusDecorations[focusIdentity] = CachedVoiceFocus(
                decoration = Bundle(decoration),
                percent = -1,
                clock = "",
                publishedProgress = 0,
                publishedMax = 0,
                publishedAtElapsedMs = 0L,
            )
        }
        sbn.notification.extras.putBundle(IslandProtocol.EXTRA_SOURCE_FOCUS_DECORATION, decoration)
        return true
    }

    /**
     * Asks HyperOS to drop the Focus shade card and island for this source notification.
     * The source notification itself is what the shade is showing, so a proxy cancel cannot
     * reach it.
     */
    private fun attachSourceFocusCancellation(
        sbn: StatusBarNotification,
        semanticType: NotificationType,
        focusIdentity: String = sbn.key,
    ) {
        val decoration = Bundle()
        decoration.putString(
            "miui.focus.param",
            FocusShadeUpdate.cancelForSource(focusIdentity),
        )
        decoration.putBoolean(IslandProtocol.EXTRA_SOURCE_FOCUS, true)
        decoration.putString(IslandProtocol.EXTRA_SOURCE_PACKAGE, sbn.packageName)
        decoration.putString("miui.pkg.name", sbn.packageName)
        decoration.putString(IslandProtocol.EXTRA_SEMANTIC_TYPE, semanticType.name)
        sbn.notification.extras.putBundle(IslandProtocol.EXTRA_SOURCE_FOCUS_DECORATION, decoration)
    }

    private fun isSourceFocusExtra(key: String): Boolean {
        if (key == IslandProtocol.EXTRA_OWNER || key == IslandProtocol.EXTRA_SOURCE_FOCUS_DECORATION) return false
        return key.startsWith("miui.focus") ||
            key.startsWith("miui.island") ||
            key == "miui.enableFloat" ||
            key.startsWith("hyperpop.") ||
            key == IslandProtocol.MIUI_BIG_ISLAND_EFFECT ||
            key == IslandProtocol.MIUI_EFFECT
    }

    private fun bundleSize(bundle: Bundle): Int {
        val parcel = Parcel.obtain()
        return try {
            bundle.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    // =========================================================================
    //  HELPERS & SETUP
    // =========================================================================

    private fun handleLimitReached(newType: NotificationType, newPkg: String) {
        val oldest = activeIslands.minByOrNull { it.value.postTime } ?: return

        val mode = preferences.getLimitModeSync()
        when (mode) {
            IslandLimitMode.FIRST_COME -> {
                // Ignore the new notification by removing it immediately (or simply returning, but returning here means the caller won't add it)
                return
            }
            IslandLimitMode.MOST_RECENT -> {
                islandBackend.cancel(oldest.value.id)
                cleanupCache(oldest.key)
            }
            IslandLimitMode.PRIORITY -> {
                // Check if newPkg has higher priority than existing ones.
                // Priority is determined by its index in appPriorityList (lower index = higher priority).
                // If it's not in the list, it has the lowest priority (Int.MAX_VALUE).
                val newPriority = preferences.getAppPriorityFast(newPkg).let {
                    if (it == Int.MAX_VALUE && appPriorityList.isNotEmpty()) {
                        appPriorityList.indexOf(newPkg).let { idx -> if (idx == -1) Int.MAX_VALUE else idx }
                    } else it
                }
                
                // Find the existing active island with the lowest priority (highest index value)
                val lowestPriorityIsland = activeIslands.maxByOrNull {
                    val pkg = it.value.packageName
                    preferences.getAppPriorityFast(pkg).let { p ->
                        if (p == Int.MAX_VALUE && appPriorityList.isNotEmpty()) {
                            appPriorityList.indexOf(pkg).let { idx -> if (idx == -1) Int.MAX_VALUE else idx }
                        } else p
                    }
                }

                if (lowestPriorityIsland != null) {
                    val lowestPkg = lowestPriorityIsland.value.packageName
                    val lowestPriority = preferences.getAppPriorityFast(lowestPkg).let { p ->
                        if (p == Int.MAX_VALUE && appPriorityList.isNotEmpty()) {
                            appPriorityList.indexOf(lowestPkg).let { idx -> if (idx == -1) Int.MAX_VALUE else idx }
                        } else p
                    }
                    if (newPriority <= lowestPriority) {
                        // The new notification has equal or higher priority than the lowest existing one.
                        // Remove the lowest priority existing notification.
                        islandBackend.cancel(lowestPriorityIsland.value.id)
                        cleanupCache(lowestPriorityIsland.key)
                    } else {
                        // The new notification has lower priority than all existing ones. Do nothing, which will ignore it.
                        return
                    }
                }
            }
        }
    }

    private fun isVoicePlaybackShell(
        sbn: StatusBarNotification,
        resolvedContent: ResolvedNotificationContent? = null,
    ): Boolean {
        val extras = sbn.notification.extras
        val title = (resolvedContent?.title ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())?.trim() ?: ""
        val text = (resolvedContent?.text ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())?.trim() ?: ""
        return VoicePlaybackDetector.isVoicePlaybackShell(voicePlaybackSignals(sbn, title, text))
    }

    private fun countsAsNativeMediaIsland(sbn: StatusBarNotification): Boolean {
        val template = sbn.notification.extras?.getString(Notification.EXTRA_TEMPLATE)
        return MediaBackedVoiceClassifier.countsAsNativeMediaTemplate(
            template = template,
            mediaBackedVoice = MediaBackedVoiceClassifier.isVoiceMessage(mediaBackedVoiceSignals(sbn)),
        )
    }

    private fun mediaBackedVoiceSignals(sbn: StatusBarNotification): MediaBackedVoiceSignals {
        val notification = sbn.notification
        val extras = notification.extras
        val template = extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return MediaBackedVoiceSignals(
            isMediaTransport = template.contains("MediaStyle") ||
                notification.category == Notification.CATEGORY_TRANSPORT,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty(),
            ticker = notification.tickerText?.toString().orEmpty(),
            actionLabels = notification.actions?.map { it.title?.toString().orEmpty() }.orEmpty(),
        )
    }

    private fun voicePlaybackSignals(
        sbn: StatusBarNotification,
        title: String,
        text: String,
    ): VoicePlaybackSignals {
        val notification = sbn.notification
        val extras = notification.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val remote = NotificationRemoteViewsParser.collect(notification)
        val extrasMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val extrasProgress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        return VoicePlaybackSignals(
            isMediaTransport = template.contains("MediaStyle") ||
                notification.category == Notification.CATEGORY_TRANSPORT,
            isDownload = isDownloadNotification(sbn, title, text),
            isMessage = notification.category == Notification.CATEGORY_MESSAGE ||
                template.contains("MessagingStyle"),
            progress = if (extrasMax > 0) extrasProgress else remote.progress,
            progressMax = if (extrasMax > 0) extrasMax else remote.progressMax,
            title = title,
            text = text,
            ticker = notification.tickerText?.toString().orEmpty(),
            remoteTexts = remote.texts,
            channelId = notification.channelId.orEmpty(),
            hasCustomView = notification.contentView != null ||
                extras.getBoolean("android.contains.customView", false),
        )
    }

    private fun isJunkNotification(sbn: StatusBarNotification, resolvedContent: ResolvedNotificationContent? = null): Boolean {
        val notification = sbn.notification
        val extras = notification.extras
        val pkg = sbn.packageName

        val title = (resolvedContent?.title ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())?.trim() ?: ""
        val text = (resolvedContent?.text ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())?.trim() ?: ""

        val hasProgress = hasProgressNotification(sbn, title, text)
        val isSpecial = notification.category == Notification.CATEGORY_TRANSPORT || callClassifier.classify(buildCallSignals(sbn)).isCall ||
                notification.category == Notification.CATEGORY_NAVIGATION || extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true ||
                isScreenRecordingNotification(sbn) || isSavedScreenRecordingNotification(sbn)
        if (hasProgress || isSpecial) return false
        if (title.isEmpty() && text.isEmpty()) return true
        if (title.equals(pkg, ignoreCase = true) || text.equals(pkg, ignoreCase = true)) return true
        val blockedTerms = preferences.getGlobalBlockedTermsSync().ifEmpty { globalBlockedTerms }
        if (blockedTerms.any { "$title $text".contains(it, true) }) return true

        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            val type = detectNotificationType(sbn)
            if (type != NotificationType.MESSAGE) return true
            if (text.isEmpty() || title.isEmpty()) return true
            // A summary with live children is a duplicate: messaging apps post the real
            // per-conversation notification plus an "N new messages" summary. With
            // "remove original notification" disabled the summary survives and would
            // become a second island. Only islandify a summary that stands alone
            // (some apps post only the summary).
            val group = notification.group
            if (group != null) {
                val hasLiveChild = try {
                    activeNotificationsProvider().any {
                        it.packageName == pkg && it.key != sbn.key &&
                            (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
                            it.notification.group == group
                    } == true
                } catch (_: Exception) { false }
                if (hasLiveChild) return true
            }
        }

        return false
    }

    private fun getCachedAppLabel(pkg: String): String = appLabelCache.getOrPut(pkg) {
        try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { "" }
    }

    private fun shouldIgnore(packageName: String): Boolean = packageName == this.packageName || packageName == "android" || packageName.contains("miui.notification")
    private fun isAppAllowed(packageName: String): Boolean =
        packageName == "com.android.shell" || // TEMP-TEST
        preferences.isAppAllowedSync(packageName) ||
            allowedPackageSet.contains(packageName) ||
            (packageName == ScreenRecordingClassifier.PACKAGE_NAME &&
                HookConfigSync.replaceScreenRecorder(this))

    /** Fingerprint of the buttons the island shows, which the Copy code button replaces. */
    private fun islandActionFingerprint(sbn: StatusBarNotification, loginCode: LoginCodePresentation?): Int =
        if (loginCode?.copyAction == true) {
            31 * LoginCodeCopyReceiver.ACTION_COPY.hashCode() + loginCode.code.hashCode()
        } else {
            NotificationActionIdentity.fingerprint(sbn)
        }

    private fun detectLoginCode(sbn: StatusBarNotification): LoginCodePresentation? {
        val settings = preferences.getLoginCodeSettingsSync()
        if (!settings.appliesTo(sbn.packageName)) return null
        val notification = sbn.notification ?: return null
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = notification.extras ?: return null
        val latestMessage = extractMessageContent(notification)
            .lastOrNull { !it.isSelf && !it.text.isNullOrBlank() }
            ?.text
        // Older conversation lines stay out: the newest message is the one carrying the code.
        val bodies = listOf(
            latestMessage,
            extras.getCharSequence(Notification.EXTRA_TITLE_BIG),
            extras.getCharSequence(Notification.EXTRA_TEXT),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            notification.tickerText,
        )
        val code = LoginCodeExtractor.extract(extras.getCharSequence(Notification.EXTRA_TITLE), bodies)
            ?: return null
        return LoginCodePresentation.of(code, settings)
    }

    private var syncJob: Job? = null
    fun onIngressConnected(preserveVisibleIslands: Boolean = false) {
        Log.i(TAG, "HyperPop SystemUI notification ingress connected preserve=$preserveVisibleIslands")
        removeLegacyPermanentAnchor()
        // Recents clear rebinds this listener while islands are already showing.
        // The first connection still clears leftovers and restores live sessions.
        if (!preserveVisibleIslands) {
            islandBackend.cancelAllOwned()
        }
        syncNotifications(refresh = true, restoreLiveSources = !preserveVisibleIslands)
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

    private fun syncNotifications(refresh: Boolean = false, restoreLiveSources: Boolean = false) {
        val now = System.currentTimeMillis()
        recentlyRemovedKeys.entries.removeIf { now - it.value.observedAt > 10000 }
        callSessionTracker.pruneStale(now)

        serviceScope.launch(Dispatchers.IO) {
            try {
                val currentNotifications = activeNotificationsProvider()
                val systemNotificationKeys = currentNotifications.map { it.key }.toSet()

                var nativeChanged = false
                for (sbn in currentNotifications) {
                    if (sbn.packageName != packageName) {
                        val extras = sbn.notification.extras
                        var isNative = false
                        if (extras != null) {
                            if (extras.containsKey("miui.focus.param") || extras.containsKey("miui.system.focus.param")) {
                                isNative = true
                            }
                            if (countsAsNativeMediaIsland(sbn)) isNative = true
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
                if (nativeChanged) updateIslandDiagnostics()

                if (restoreLiveSources) {
                    for (sbn in currentNotifications) {
                        if (!shouldRestoreLiveSource(sbn)) continue
                        enqueueSourceNotification(sbn, recovery = true)
                    }
                } else if (refresh) {
                    // SCREEN_ON already performs this reconciliation. Re-evaluate only CallStyle
                    // sources so an answer/connect transition withheld by the vendor while the
                    // display was off is recovered without polling, alarms, or wake locks.
                    for (sbn in currentNotifications) {
                        if (!isCallSource(sbn)) continue
                        enqueueSourceNotification(sbn, recovery = true)
                    }
                }

                val currentKeys = currentNotifications.map { it.key }.toSet()
                
                val keysToRemove = mutableListOf<String>()
                for ((originalKey, activeIsland) in activeIslands) {
                    if (!NotificationLifecyclePolicy.isTrackedSourcePresent(
                            logicalId = originalKey,
                            sourceKey = activeIsland.sourceKey,
                            currentSourceKeys = currentKeys
                        )
                    ) {
                        val appConfig = preferences.getAppIslandConfigSync(activeIsland.packageName)
                        val globalConfig = preferences.getGlobalConfigSync()
                        val finalConfig = appConfig.mergeWith(globalConfig)

                        val progressGraceExpired = NotificationLifecyclePolicy.isProgressLifecycle(activeIsland.type) &&
                            !downloadSessionTracker.holdForReplacement(originalKey, System.currentTimeMillis())
                        if (NotificationLifecyclePolicy.isProgressLifecycle(activeIsland.type) &&
                            !progressGraceExpired
                        ) {
                            scheduleProgressIslandDismissal(originalKey)
                        }
                        if (NotificationLifecyclePolicy.shouldReapMissingSource(
                                type = activeIsland.type,
                                removeOriginalNotification = finalConfig.removeOriginalNotification == true,
                                dismissWithOriginal = finalConfig.dismissWithOriginal == true,
                                retainedWithoutSource = originalKey in islandsRetainedWithoutSource,
                                replacementGraceExpired = progressGraceExpired,
                            )
                        ) {
                            keysToRemove.add(originalKey)
                        }
                    } else {
                        islandsRetainedWithoutSource.remove(originalKey)
                    }
                }

                for (key in keysToRemove) {
                    Log.d(TAG, "Sync: Found stuck notification $key, removing.")
                    val hyperId = activeTranslations[key]
                    if (hyperId != null) {
                        try {
                            islandBackend.cancel(hyperId)
                        } catch (_: Exception) {}
                    }
                    cleanupCache(key)
                }

                // Bridged notifications we no longer track (e.g. left over from a service restart)
                // keep their island slot occupied forever, since island-swipe never removes them.
                for (sbn in currentNotifications) {
                    if (sbn.packageName != packageName) continue
                    val id = sbn.id
                    // The VPN controller deliberately owns its notification outside the ordinary
                    // source-to-translation maps. Do not mistake it for an orphan during the
                    // reconciliation pass and cancel its backing island.
                    if (id == VpnIslandController.NOTIFICATION_ID) continue
                    if (id in (WATCH_RELAY_ID_BASE - 0x0F)..WATCH_RELAY_ID_BASE) continue
                    if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) continue
                    if (reverseTranslations.containsKey(id)) continue
                    if (System.currentTimeMillis() - sbn.postTime < 5000) continue
                    Log.d(TAG, "Sync: Reaping orphan bridge notification $id")
                    try {
                        islandBackend.cancel(id)
                    } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error syncing notifications", e)
            }
        }
    }

    private fun logicalKeyForRemovedSource(notifKey: String): String {
        return sourceToLogicalKeys[notifKey]
            ?: callSessionTracker.logicalIdForSource(notifKey)
            ?: screenRecordingSessionTracker.logicalIdForSource(notifKey)
            ?: downloadSessionTracker.logicalIdForSource(notifKey)
            ?: messageFamilyTracker.logicalIdForSource(notifKey)
            ?: notifKey
    }

    /** Live sessions are rebuilt after a process restart. Ordinary shade history is not. */
    private fun shouldRestoreLiveSource(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName || isOwnedBridgeNotification(sbn)) return false
        val notification = sbn.notification
        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return true
        if (flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return true
        when (notification.category) {
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_ALARM -> return true
        }
        val extras = notification.extras ?: return false
        if (extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.contains("MediaStyle") || template.contains("CallStyle") ||
            extras.containsKey(Notification.EXTRA_PROGRESS)
    }

    /** Progress-only voice ticks. Null means the notification still needs a full render. */
    private fun throttledVoicePlaybackSample(
        sbn: StatusBarNotification,
        sourceSlot: String,
    ): VoicePlaybackUpdateSample? {
        val logicalKey = sourceToLogicalKeys[sbn.key] ?: return null
        val activeType = activeIslands[logicalKey]?.type
        if (activeType != NotificationType.VOICE_MESSAGE) {
            voicePlaybackUpdateGate.remove(sourceSlot)
            if (VoicePlaybackDecorationPolicy.evictCachedDecoration(activeType)) {
                voiceFocusDecorations.remove(logicalKey)
            }
            return null
        }
        val extras = sbn.notification.extras
        val extrasMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val extrasProgress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val remote = if (extrasMax > 0) null else {
            NotificationRemoteViewsParser.playbackSnapshot(sbn.notification)
        }
        val structureFingerprint = listOf(
            sbn.notification.channelId,
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            sbn.notification.tickerText?.toString(),
            sbn.notification.actions.orEmpty().map { action ->
                listOf(action.title?.toString(), action.semanticAction, action.actionIntent != null)
            },
            remote?.structureFingerprint,
        ).hashCode()
        val sample = VoicePlaybackUpdateSample(
            progress = if (extrasMax > 0) extrasProgress else remote?.progress ?: 0,
            progressMax = if (extrasMax > 0) extrasMax else remote?.progressMax ?: 0,
            structureFingerprint = structureFingerprint,
        )
        if (voicePlaybackUpdateGate.shouldRender(sourceSlot, sample)) return null
        return sample
    }

    /**
     * Copies the decoration from the last real voice post onto this playback tick.
     * A changed percent or clock is written into that payload. The tick does not
     * translate, walk RemoteViews, or post a new island.
     */
    private fun restampCachedVoiceFocus(
        sbn: StatusBarNotification,
        sample: VoicePlaybackUpdateSample,
    ) {
        val logicalKey = sourceToLogicalKeys[sbn.key] ?: return
        val cached = voiceFocusDecorations[logicalKey] ?: return
        val percent = VoicePlaybackDetector.percent(sample.progress, sample.progressMax)
        val clock = VoicePlaybackDetector.playbackClock(sample.progress, sample.progressMax)
        val now = android.os.SystemClock.elapsedRealtime()
        val elapsed = if (cached.publishedAtElapsedMs <= 0L) {
            Long.MAX_VALUE
        } else {
            now - cached.publishedAtElapsedMs
        }
        val patch = VoicePlaybackDecorationPolicy.shouldPatchDisplayedProgress(
            cachedPercent = cached.percent,
            cachedClock = cached.clock,
            percent = percent,
            clock = clock,
            elapsedSincePatchMs = elapsed,
        )
        val decoration = if (patch) {
            val updated = patchVoiceFocusProgress(cached.decoration, logicalKey, percent, clock)
            voiceFocusDecorations[logicalKey] = CachedVoiceFocus(
                decoration = updated,
                percent = percent,
                clock = clock,
                publishedProgress = sample.progress,
                publishedMax = sample.progressMax,
                publishedAtElapsedMs = now,
            )
            updated
        } else {
            holdPublishedVoiceProgress(sbn, cached)
            cached.decoration
        }
        if (!VoicePlaybackDecorationPolicy.restampCachedDecoration(hasCachedDecoration = true)) return
        sbn.notification.extras.putBundle(
            IslandProtocol.EXTRA_SOURCE_FOCUS_DECORATION,
            Bundle(decoration),
        )
    }

    /**
     * Instagram keeps writing a new millisecond position into the notification.
     * SystemUI snapshots that value. Hold the last published position until the
     * next Focus refresh so those intermediate writes do not rebuild the card.
     */
    private fun holdPublishedVoiceProgress(
        sbn: StatusBarNotification,
        cached: CachedVoiceFocus,
    ) {
        if (cached.publishedMax <= 0) return
        val extras = sbn.notification.extras
        if (extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) <= 0) return
        extras.putInt(Notification.EXTRA_PROGRESS, cached.publishedProgress)
        extras.putInt(Notification.EXTRA_PROGRESS_MAX, cached.publishedMax)
    }

    private fun patchVoiceFocusProgress(
        source: Bundle,
        logicalKey: String,
        percent: Int,
        clock: String,
    ): Bundle {
        val copy = Bundle(source)
        val focus = copy.getString("miui.focus.param") ?: return copy
        val patched = VoiceFocusProgressPatch.apply(focus, percent, clock)
        if (patched == focus) return copy
        copy.putString("miui.focus.param", FocusShadeUpdate.stampForSource(patched, logicalKey))
        return copy
    }

    private fun isCallSource(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName || isOwnedBridgeNotification(sbn)) return false
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (notification.category == Notification.CATEGORY_CALL) return true
        val extras = notification.extras ?: return false
        return extras.containsKey(Notification.EXTRA_CALL_TYPE) ||
            extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("CallStyle")
    }

    private fun progressIslandStillLive(logicalKey: String, removedSourceKey: String): Boolean {
        if (downloadSessionTracker.hasLiveProgress(logicalKey, removedSourceKey)) return true
        val sourceKey = activeIslands[logicalKey]?.sourceKey ?: return false
        return isLiveDownloadSource(sourceKey)
    }

    /** A completion notice can reuse the slot after the progress notification is gone. */
    private fun isLiveDownloadSource(sourceKey: String): Boolean {
        val sbn = try {
            activeNotificationsProvider().firstOrNull { it.key == sourceKey }
        } catch (_: Exception) {
            null
        } ?: return false
        val type = detectNotificationType(sbn)
        return NotificationLifecyclePolicy.isProgressLifecycle(type) && !isFinishedProgress(sbn)
    }

    private fun scheduleProgressIslandDismissal(logicalKey: String) {
        if (removalJobs.containsKey(logicalKey)) return
        lateinit var job: Job
        job = serviceScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(DownloadReplacementPolicy.REMOVAL_DELAY_MS)
            notificationLifecycleMutex.withLock {
                val current = activeIslands[logicalKey] ?: return@withLock
                if (!NotificationLifecyclePolicy.isProgressLifecycle(current.type)) return@withLock
                if (progressIslandStillLive(logicalKey, current.sourceKey)) return@withLock
                if (current.sourceFocus != true) {
                    activeTranslations[logicalKey]?.let { hyperId ->
                        try {
                            islandBackend.cancel(hyperId)
                        } catch (_: Exception) {}
                    }
                }
                cleanupCache(logicalKey)
            }
        }
        removalJobs[logicalKey] = job
        job.invokeOnCompletion { removalJobs.remove(logicalKey, job) }
    }

    private fun isSourceNotificationActive(sourceKey: String): Boolean {
        return try {
            activeNotificationsProvider().any { it.key == sourceKey }
        } catch (_: Exception) {
            false
        }
    }

    private fun isOwnedBridgeNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return true
        return sbn.packageName == IslandProtocol.SYSTEM_UI_PACKAGE &&
            sbn.notification.extras.getString(IslandProtocol.EXTRA_OWNER) == IslandProtocol.OWNER
    }

    private fun alreadyPostedIsland(logicalId: String, bridgeId: Int, previous: ActiveIsland?): Boolean {
        if (previous != null) return true
        if (activeIslands.containsKey(logicalId)) return true
        if (activeTranslations.containsKey(logicalId)) return true
        return reverseTranslations.containsKey(bridgeId)
    }

    private fun previousIslandPresentation(
        previous: ActiveIsland?,
        alreadyPosted: Boolean,
        logicalId: String,
        bridgeId: Int,
    ): PreviousIslandPresentation? {
        if (previous != null) {
            return PreviousIslandPresentation(
                previous.logicalId,
                previous.id,
                previous.lastContentHash,
                previous.messageEventFingerprint,
            )
        }
        if (!alreadyPosted) return null
        val postedId = reverseTranslations.entries.firstOrNull { it.value == logicalId }?.key ?: bridgeId
        return PreviousIslandPresentation(logicalId, postedId, Int.MIN_VALUE)
    }

    private fun postIsland(
        id: Int,
        notification: Notification,
        logicalToken: String = id.toString(),
        source: StatusBarNotification? = null,
        semanticType: NotificationType? = null,
        generation: Long = source?.postTime ?: 0L,
        inPlaceUpdate: Boolean = false,
        incomingCallBanner: Boolean = false,
    ): Boolean {
        val metadata = IslandMetadata(
            logicalToken = logicalToken,
            sourceKey = source?.key ?: notification.extras.getString(EXTRA_ORIGINAL_KEY),
            sourcePackage = source?.packageName,
            sourceChannel = source?.notification?.channelId,
            semanticType = semanticType?.name,
            generation = generation,
        )
        val result = if (inPlaceUpdate) {
            islandBackend.update(id, notification, metadata)
        } else {
            islandBackend.post(id, notification, metadata)
        }
        if (result.isSuccess && source != null) markSourceHeadsUpSuppressed(source, incomingCallBanner)
        return result.isSuccess
    }

    private fun markSourceHeadsUpSuppressed(
        source: StatusBarNotification,
        incomingCallBanner: Boolean = false,
    ) {
        if (detectNotificationType(source) == NotificationType.CALL) {
            source.notification.extras.putBoolean(IslandProtocol.EXTRA_CALL_SHADE_DISMISSIBLE, true)
        }
        if (source.packageName == IslandProtocol.SYSTEM_UI_PACKAGE) return
        if (source.notification.fullScreenIntent != null && !incomingCallBanner) return
        source.notification.extras.putBoolean(IslandProtocol.EXTRA_SUPPRESS_SOURCE_HEADS_UP, true)
    }

    fun shutdown() {
        if (::vpnIslandController.isInitialized) vpnIslandController.stop()
        unregisterReceiver(systemReceiver)
        unregisterReceiver(replyComposerReceiver)
        replyComposerHolds.clear()
        unregisterReceiver(loginCodeCopiedReceiver)
        unregisterReceiver(packageLifecycleReceiver)
        syncJob?.cancel()
        callSessionTracker.clear()
        screenRecordingSessionTracker.clear()
        messageFamilyTracker.clear()
        messageEventTracker.clear()
        serviceScope.cancel() 
    }
}
