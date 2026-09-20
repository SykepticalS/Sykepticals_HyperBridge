package com.d4viddf.hyperbridge.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.d4viddf.hyperbridge.MainActivity
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.data.widget.WidgetManager
import com.d4viddf.hyperbridge.models.HyperIslandData
import com.d4viddf.hyperbridge.models.WidgetConfig
import com.d4viddf.hyperbridge.models.WidgetRenderMode
import com.d4viddf.hyperbridge.service.translators.WidgetTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WidgetOverlayService : Service() {

    companion object {
        const val TAG = "HyperWidgetService"
        const val WIDGET_CHANNEL_ID = "hyper_bridge_widget_channel"
        const val ACTION_TEST_WIDGET = "ACTION_TEST_WIDGET"
        const val ACTION_START_MONITORING = "ACTION_START_MONITORING"
        const val ACTION_KILL_ALL_WIDGETS = "ACTION_KILL_ALL_WIDGETS"
        const val ACTION_KILL_WIDGET = "ACTION_KILL_WIDGET"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val widgetUpdateDebouncer = ConcurrentHashMap<Int, Long>()

    private lateinit var preferences: AppPreferences
    private lateinit var widgetTranslator: WidgetTranslator
    private val islandBackend by lazy { com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend.get(this) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Widget Overlay Service Created")

        preferences = AppPreferences(applicationContext)
        widgetTranslator = WidgetTranslator(applicationContext)
        WidgetManager.init(applicationContext)

        startMonitoringWidgets()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TEST_WIDGET -> {
                val widgetId = intent.getIntExtra("WIDGET_ID", -1)
                if (widgetId != -1) {
                    // Manual test: Bypass throttler
                    serviceScope.launch(Dispatchers.Main) {
                        val config = preferences.getWidgetConfigFlow(widgetId).first()
                        processSingleWidget(widgetId, config, forceUpdate = true)
                    }
                }
            }
            ACTION_KILL_ALL_WIDGETS -> {
                // Instantly kill all active widgets
                serviceScope.launch(Dispatchers.IO) {
                    val savedIds = preferences.savedWidgetIdsFlow.first()
                    savedIds.forEach { id ->
                        islandBackend.cancel(9000 + id, "widget:${9000 + id}")
                        widgetUpdateDebouncer.remove(id)
                    }
                }
            }
            ACTION_KILL_WIDGET -> {
                // NEW: Instantly kill a specific widget
                val widgetId = intent.getIntExtra("WIDGET_ID", -1)
                if (widgetId != -1) {
                    islandBackend.cancel(9000 + widgetId, "widget:${9000 + widgetId}")
                    widgetUpdateDebouncer.remove(widgetId) // Clean up memory
                }
            }
            ACTION_START_MONITORING -> {
                // Ensure service is sticky
            }
        }
        return START_STICKY
    }

    private fun startMonitoringWidgets() {
        serviceScope.launch {
            WidgetManager.widgetUpdates.collect { updatedId ->
                // Check if we are tracking this widget
                val savedIds = preferences.savedWidgetIdsFlow.first()
                if (savedIds.contains(updatedId)) {
                    val config = preferences.getWidgetConfigFlow(updatedId).first()

                    // Check Throttling
                    if (shouldProcessWidgetUpdate(updatedId, config)) {
                        launch(Dispatchers.Main) {
                            processSingleWidget(updatedId, config, forceUpdate = false)
                        }
                    }
                }
            }
        }
    }

    /**
     * Prevents Snapshot widgets from flickering (updates every 1.5s max).
     * Interactive widgets are smoother (200ms).
     */
    private fun shouldProcessWidgetUpdate(widgetId: Int, config: WidgetConfig): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = widgetUpdateDebouncer[widgetId] ?: 0L

        // Snapshot logic often triggers every second (e.g. music seekbar updates),
        // causing full bitmap redraws which flicker. We slow this down.
        val throttleTime = if (config.renderMode == WidgetRenderMode.SNAPSHOT) 1500L else 200L

        if (now - lastTime < throttleTime) {
            return false
        }
        widgetUpdateDebouncer[widgetId] = now
        return true
    }

    private suspend fun processSingleWidget(widgetId: Int, config: WidgetConfig, forceUpdate: Boolean) {
        try {
            // Translate view to Island Data
            val data = widgetTranslator.translate(widgetId)

            // Post to specific Widget Channel
            postWidgetNotification(9000 + widgetId, data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render widget $widgetId", e)
        }
    }

    private fun postWidgetNotification(notificationId: Int, data: HyperIslandData) {
        val builder = NotificationCompat.Builder(this, WIDGET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Widget Overlay")
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority = no sound/peek
            .addExtras(data.resources)

        // Click Intent -> Open App
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(pendingIntent)

        val notification = builder.build()
        // Pass JSON param for the Island UI
        notification.extras.putString("miui.focus.param", data.jsonParam)

        islandBackend.post(
            notificationId,
            notification,
            com.d4viddf.hyperbridge.island.backend.IslandMetadata(
                logicalToken = "widget:$notificationId",
                semanticType = "WIDGET",
            ),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Widget Overlay Service Destroyed")
    }
}
