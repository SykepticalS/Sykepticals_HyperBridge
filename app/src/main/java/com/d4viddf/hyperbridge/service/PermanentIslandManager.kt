package com.d4viddf.hyperbridge.service

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.island.backend.IslandMetadata
import com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


class PermanentIslandManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val preferences: AppPreferences
) {
    private val TAG = "HyperBridgeDebug"
    private val backend = SystemUiIslandBackend.get(context)

    companion object {
        const val PERMANENT_BRIDGE_ID = 9999
        const val PERMANENT_LOGICAL_TOKEN = "permanent"
        // The dismiss path posts 9999 right after cancelling the previous focus
        // island. Delaying the post lets HyperOS finish tearing that island down,
        // otherwise it can swallow the re-post and leave 9999 posted but hidden.
        private const val DISPATCH_DELAY_MS = 700L
    }

    private var isPermanentIslandEnabled = false
    private var isIslandActive = false
    private var currentRealNotifications = 0
    private var occupyingLogicalId: String? = null
    private var expansionLocked = true

    fun isIslandActive(): Boolean = synchronized(this) { isIslandActive }
    fun occupyingLogicalId(): String? = synchronized(this) { occupyingLogicalId }
    fun isExpansionLocked(): Boolean = synchronized(this) { expansionLocked }
    @Synchronized
    fun isSlotAvailable(): Boolean = desiredActive()
    private var hasNativeIsland = false
    private var currentWidth = 0
    private var isHideInLandscapeEnabled = false
    private var pendingDispatchJob: Job? = null

    init {
        scope.launch {
            preferences.isPermanentIslandEnabledFlow.collectLatest { enabled ->
                synchronized(this@PermanentIslandManager) {
                    if (isPermanentIslandEnabled != enabled) {
                        isPermanentIslandEnabled = enabled
                        updateStateLocked()
                    }
                }
            }
        }
        scope.launch {
            preferences.hidePermanentIslandLandscapeFlow.collectLatest { hide ->
                synchronized(this@PermanentIslandManager) {
                    if (isHideInLandscapeEnabled != hide) {
                        isHideInLandscapeEnabled = hide
                        updateStateLocked()
                    }
                }
            }
        }
        scope.launch {
            preferences.permanentIslandWidthFlow.collectLatest { width ->
                synchronized(this@PermanentIslandManager) {
                    if (currentWidth != width) {
                        currentWidth = width
                        if (isIslandActive && occupyingLogicalId == null) {
                            dispatchPermanentIsland()
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    fun onActiveNotificationsChanged(count: Int, hasNative: Boolean = false) {
        currentRealNotifications = count
        hasNativeIsland = hasNative
        updateStateLocked()
    }

    @Synchronized
    fun onOrientationChanged() {
        updateStateLocked()
    }

    // isIslandPresent reflects whether PERMANENT_BRIDGE_ID is actually posted right now.
    // Presence only proves the notification exists, NOT that its island is visible:
    // HyperOS can keep 9999 posted while hiding its island (e.g. a bridged focus island
    // superseded it, or a re-post landed too soon after a cancel). So on a discrete
    // transition (screen on / unlock / (re)connect) callers pass refresh=true to re-assert
    // the island even when present; the periodic tick passes false, trusting presence.
    // Bridged islands update 9999 in place instead of hiding it. Extra islands may post with
    // their own ids while that occupant is still active; the last remaining one folds back.
    private fun desiredActive(): Boolean {
        val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return isPermanentIslandEnabled && !hasNativeIsland && !(isHideInLandscapeEnabled && isLandscape)
    }

    @Synchronized
    fun occupy(logicalId: String, unlockExpansion: Boolean) {
        val jobToCancel = pendingDispatchJob
        pendingDispatchJob = null
        jobToCancel?.cancel()
        occupyingLogicalId = logicalId
        isIslandActive = true
        if (unlockExpansion) expansionLocked = false
    }

    @Synchronized
    fun lockExpansion() {
        expansionLocked = true
    }

    @Synchronized
    fun markPostedAbsent() {
        isIslandActive = false
    }

    @Synchronized
    fun restoreStub() {
        occupyingLogicalId = null
        expansionLocked = true
        if (desiredActive()) {
            val jobToCancel = pendingDispatchJob
            pendingDispatchJob = null
            jobToCancel?.cancel()
            if (isIslandActive) {
                dispatchPermanentIsland()
            } else {
                isIslandActive = true
                scheduleDispatchLocked()
            }
        } else {
            updateStateLocked()
        }
    }

    @Synchronized
    fun reconcile(count: Int, hasNative: Boolean, isIslandPresent: Boolean, refresh: Boolean) {
        currentRealNotifications = count
        hasNativeIsland = hasNative
        val shouldShow = desiredActive()
        val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (shouldShow && occupyingLogicalId != null) {
            isIslandActive = true
            return
        }
        if (shouldShow && isIslandPresent && refresh) {
            // Present but maybe not visible: re-assert in place (no remove first, so no
            // rapid cancel->post to swallow). Same id + content updates the residual island.
            val jobToCancel = pendingDispatchJob
            pendingDispatchJob = null
            jobToCancel?.cancel()
            dispatchPermanentIsland()
            isIslandActive = true
            return
        }
        isIslandActive = isIslandPresent
        Log.d(TAG, "updateState: shouldShow=$shouldShow, isLandscape=$isLandscape, isHideInLandscapeEnabled=$isHideInLandscapeEnabled")
        updateStateLocked()
    }

    private fun updateStateLocked() {
        if (desiredActive()) {
            if (occupyingLogicalId != null) {
                isIslandActive = true
                return
            }
            if (!isIslandActive) {
                isIslandActive = true
                scheduleDispatchLocked()
            }
        } else {
            occupyingLogicalId = null
            expansionLocked = true
            if (isIslandActive) {
                isIslandActive = false
                val jobToCancel = pendingDispatchJob
                pendingDispatchJob = null
                jobToCancel?.cancel()
                removePermanentIsland()
            }
        }
    }

    private fun scheduleDispatchLocked() {
        val jobToCancel = pendingDispatchJob
        pendingDispatchJob = null
        jobToCancel?.cancel()
        pendingDispatchJob = scope.launch {
            delay(DISPATCH_DELAY_MS.milliseconds)
            synchronized(this@PermanentIslandManager) {
                pendingDispatchJob = null
                // Re-check under the lock: the desired state may have flipped during the delay.
                if (desiredActive() && occupyingLogicalId == null) {
                    dispatchPermanentIsland()
                }
            }
        }
    }
    private fun dispatchPermanentIsland() {
        try {
            Log.d(TAG, "Dispatching permanent island")
            
            val builder = HyperIslandNotification.Builder(context, "permanent_island", "Permanent Island")
            
            // Should not be dismissible and shouldn't show in shade
            builder.setEnableFloat(false)
            builder.setIslandConfig(timeout = 86400000, dismissible = false, highlightColor = "#FFFFFF", expandedTimeMs = 0)
            builder.setShowNotification(false)
            builder.setReopen(true)
            builder.setIslandFirstFloat(false)

            // Only big paramislands with empty values for textonleft and picKey = null
            // Use width spaces to change width
            val emptyString = "\u00A0".repeat(currentWidth)
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(1, null, TextInfo(emptyString, emptyString)),
                right = null
            )
            builder.setSmallIsland("")

            val data = HyperIslandData(builder.buildResourceBundle(), builder.buildJsonParam())

            val notifBuilder = NotificationCompat.Builder(context, "hyper_bridge_notification_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Permanent Island")
                .setContentText("Empty Island")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)

            notifBuilder.addExtras(data.resources)

            val notification = notifBuilder.build()
            notification.extras.putString("miui.focus.param", data.jsonParam)

            backend.post(
                PERMANENT_BRIDGE_ID,
                notification,
                IslandMetadata(PERMANENT_LOGICAL_TOKEN, semanticType = "PERMANENT"),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching permanent island", e)
        }
    }

    private fun removePermanentIsland() {
        try {
            Log.d(TAG, "Removing permanent island")
            backend.cancel(PERMANENT_BRIDGE_ID, PERMANENT_LOGICAL_TOKEN)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing permanent island", e)
        }
    }
}
