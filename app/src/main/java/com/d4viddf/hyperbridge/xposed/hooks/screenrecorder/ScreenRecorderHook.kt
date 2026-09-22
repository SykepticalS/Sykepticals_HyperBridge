package com.d4viddf.hyperbridge.xposed.hooks.screenrecorder

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.edit
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.screenrecorder.RecorderSnapshot
import com.d4viddf.hyperbridge.screenrecorder.ScreenRecorderContract
import com.d4viddf.hyperbridge.service.recording.ScreenRecordingCapabilities
import com.d4viddf.hyperbridge.service.recording.ScreenRecordingSession
import com.d4viddf.hyperbridge.service.translators.ScreenRecordingTranslator
import com.d4viddf.hyperbridge.xposed.HookConfig
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.FileDescriptor
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object ScreenRecorderHook {
    private const val SETTINGS_ACTION = "android.service.quicksettings.action.QS_TILE_PREFERENCES"
    private const val RECORDER_SERVICE_ACTION = ScreenRecorderContract.RECORDER_SERVICE_ACTION
    private const val RECORDING_NOTIFICATION_ID = 110
    private const val QUICK_TILE_SERVICE = "com.miui.screenrecorder.service.QuickService"
    private const val RECORDER_SERVICE = "com.miui.screenrecorder.service.RecorderService"

    private val hookedTileClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val hookedRecorderServiceClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val hookedRecorderValidationMethods = ConcurrentHashMap.newKeySet<Method>()
    private val hookedNotifyMethods = ConcurrentHashMap.newKeySet<Method>()
    private val bypassNextLowBatteryWarning = AtomicBoolean(false)
    private val componentsDiscovered = AtomicBoolean(false)
    private val snapshotObserverInstalled = AtomicBoolean(false)

    @Volatile private var xposedModule: XposedModule? = null
    @Volatile private var recorderDialogVisible = false
    @Volatile private var recordingNotificationBuilder: WeakReference<Notification.Builder>? = null
    @Volatile private var recordingNotificationContext: WeakReference<Context>? = null
    @Volatile private var recordingNotificationBuilderMethod: Method? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingConfirmedStart: Runnable? = null
    private var pendingStartObserver: (() -> Unit)? = null

    fun install(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != ScreenRecorderContract.TARGET_PACKAGE) return
        xposedModule = module
        module.log("screen recorder replacement hook loaded")
        hookVideoEncoderSyncFrame(module)
        hookMediaMuxerOutput(module)
        hookMediaMuxerLifecycle(module)
        hookOverlayWindowCreation(module)
        hookNotificationManager(module)
        hookApplicationAttach(module, param.defaultClassLoader)
        bootstrapIfAlreadyAttached(module, param.defaultClassLoader)
    }

    private fun hookApplicationAttach(module: XposedModule, classLoader: ClassLoader) {
        val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java).apply {
            isAccessible = true
        }
        module.hook(attach).intercept { chain ->
            val result = chain.proceed()
            val context = chain.args.firstOrNull() as? Context ?: return@intercept result
            onRecorderContextReady(context, classLoader, module)
            result
        }
        runCatching {
            val onCreate = Application::class.java.getDeclaredMethod("onCreate")
            module.hook(onCreate).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Application)?.let { onRecorderContextReady(it, classLoader, module) }
                result
            }
        }
    }

    private fun bootstrapIfAlreadyAttached(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val method = activityThread.getDeclaredMethod("currentApplication").apply { isAccessible = true }
            (method.invoke(null) as? Application)?.let { onRecorderContextReady(it, classLoader, module) }
        }
    }

    private fun onRecorderContextReady(context: Context, classLoader: ClassLoader, module: XposedModule) {
        initializeControlClient(context, module)
        runCatching { discoverAndHookComponents(context, classLoader, module) }
            .onFailure { module.log("screen recorder component discovery failed: ${it.message}") }
    }

    /**
     * Package-loaded callbacks can run after Application.attach/onCreate on some recorder builds.
     * RecorderService is the authoritative lifecycle point, so initializing there as well prevents
     * an active recording from being left without a state-service connection and fallback island.
     */
    private fun initializeControlClient(context: Context, module: XposedModule) {
        ScreenRecorderControlClient.initialize(context) { command, extras ->
            handleControlCommand(context, command, extras, module)
        }
        if (Application.getProcessName() == ScreenRecorderContract.TARGET_PACKAGE &&
            snapshotObserverInstalled.compareAndSet(false, true)
        ) {
            ScreenRecorderControlClient.observe { snapshot ->
                refreshRecordingNotification(snapshot, module)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun discoverAndHookComponents(context: Context, classLoader: ClassLoader, module: XposedModule) {
        hookKnownRecorderClasses(classLoader, module)
        val packageInfo = context.packageManager.getPackageInfo(
            ScreenRecorderContract.TARGET_PACKAGE,
            PackageManager.GET_SERVICES,
        )
        val recorderServiceName = context.packageManager.resolveService(
            Intent(RECORDER_SERVICE_ACTION).setPackage(ScreenRecorderContract.TARGET_PACKAGE),
            0,
        )?.serviceInfo?.name
        packageInfo.services.orEmpty().forEach { serviceInfo ->
            val serviceClass = runCatching { classLoader.loadClass(serviceInfo.name) }.getOrNull()
                ?: return@forEach
            if (TileService::class.java.isAssignableFrom(serviceClass)) {
                hookConcreteTileService(module, serviceClass)
            }
            if (serviceInfo.name == recorderServiceName || isRecorderServiceClass(serviceClass)) {
                hookRecordingNotification(module, serviceClass)
            }
        }
        if (componentsDiscovered.compareAndSet(false, true) || hookedTileClasses.isNotEmpty()) {
            module.log(
                "screen recorder tiles=${hookedTileClasses.size} services=${hookedRecorderServiceClasses.size}",
            )
        }
    }

    private fun hookKnownRecorderClasses(classLoader: ClassLoader, module: XposedModule) {
        runCatching { classLoader.loadClass(QUICK_TILE_SERVICE) }.getOrNull()?.let {
            hookConcreteTileService(module, it)
        }
        runCatching { classLoader.loadClass(RECORDER_SERVICE) }.getOrNull()?.let {
            hookRecordingNotification(module, it)
        }
    }

    private fun hookConcreteTileService(module: XposedModule, serviceClass: Class<*>) {
        if (!hookedTileClasses.add(serviceClass)) return
        val onClick = findOnClick(serviceClass) ?: run {
            module.log("screen recorder tile has no onClick: ${serviceClass.name}")
            return
        }
        onClick.isAccessible = true
        module.hook(onClick).intercept { chain ->
            val service = chain.thisObject as? TileService ?: return@intercept chain.proceed()
            module.log("screen recorder intercepted ${service.javaClass.name}.onClick")
            runCatching {
                if (recorderDialogVisible) return@runCatching
                recorderDialogVisible = true
                ScreenRecorderControlClient.requestSnapshot { snapshot ->
                    presentRecorderPrompt(service, snapshot, module)
                }
            }.onFailure {
                recorderDialogVisible = false
                module.log("screen recorder tile dialog failed: ${it.message}")
                return@intercept chain.proceed()
            }
            null
        }
    }

    private fun findOnClick(serviceClass: Class<*>): Method? {
        var current: Class<*>? = serviceClass
        while (current != null && current != Any::class.java) {
            current.declaredMethods.firstOrNull { it.name == "onClick" && it.parameterCount == 0 }?.let {
                return it
            }
            current = current.superclass
        }
        return null
    }

    private fun presentRecorderPrompt(context: Context, snapshot: RecorderSnapshot, module: XposedModule) {
        fun present() {
            runCatching {
                if (HookConfig.screenRecorderImmediateStart() && !snapshot.isSessionActive) {
                    recorderDialogVisible = false
                    scheduleConfirmedStart(context)
                } else {
                    showRecorderDialog(context, resolveSettingsActivity(context), snapshot)
                }
            }.onFailure {
                recorderDialogVisible = false
                module.log("screen recorder prompt failed: ${it.stackTraceToString()}")
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            present()
        } else {
            Handler(Looper.getMainLooper()).post { present() }
        }
    }

    private fun hookOverlayWindowCreation(module: XposedModule) {
        val windowManagerClass = runCatching {
            Class.forName("android.view.WindowManagerImpl")
        }.getOrElse {
            module.log("WindowManagerImpl not found: ${it.message}")
            return
        }
        windowManagerClass.declaredMethods.filter { method ->
            method.name == "addView" &&
                method.parameterTypes.size == 2 &&
                View::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                ViewGroup.LayoutParams::class.java.isAssignableFrom(method.parameterTypes[1])
        }.forEach { method ->
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val view = chain.args.getOrNull(0) as? View
                val params = chain.args.getOrNull(1) as? WindowManager.LayoutParams
                val windowType = params?.type
                @Suppress("DEPRECATION")
                val isRecorderOverlay = when (windowType) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    -> true
                    else -> false
                }
                if (isRecorderOverlay && !RecorderOverlayGate.allows(view)) {
                    module.log("blocked recorder overlay ${view?.javaClass?.name} type=$windowType")
                    null
                } else {
                    chain.proceed()
                }
            }
        }
    }

    private fun showRecorderDialog(
        context: Context,
        settingsActivity: String?,
        recorderSnapshot: RecorderSnapshot,
    ) {
        val contentContext = screenRecorderContentContext(context)
        val text = screenRecorderUiText(contentContext)
        if (recorderSnapshot.isSessionActive) {
            ScreenRecorderDialogInjector.show(
                context = context,
                contentContext = contentContext,
                text = text,
                resolutions = emptyList(),
                sounds = emptyList(),
                initialResolution = "",
                initialSound = 0,
                recordingSnapshot = recorderSnapshot,
                onCancel = { recorderDialogVisible = false },
                onOpenSettings = {},
                onStart = { _, _, _ -> },
                onPause = { ScreenRecorderControlClient.pause() },
                onResume = { ScreenRecorderControlClient.resume() },
                onStop = {
                    recorderDialogVisible = false
                    cancelPendingConfirmedStart()
                    ScreenRecorderControlClient.stop()
                },
            )
            return
        }

        val prefs = recorderPreferences(context)
        val currentResolution = prefs.getString(ScreenRecorderContract.PREF_RESOLUTION, "").orEmpty()
        val discoveredResolutions = readRecorderResolutions(context)
        val resolutions = if (
            currentResolution.isNotBlank() &&
            discoveredResolutions.none { it.second == currentResolution }
        ) {
            listOf(text.currentSettingValue(formatResolution(currentResolution)) to currentResolution) +
                discoveredResolutions
        } else {
            discoveredResolutions
        }
        val currentSound = prefs.getString(ScreenRecorderContract.PREF_SOUND, "0")?.toIntOrNull() ?: 0
        val discoveredSounds = readRecorderSounds(context, currentSound, text)
        val sounds = if (discoveredSounds.none { it.second == currentSound }) {
            listOf(text.currentSetting to currentSound) + discoveredSounds
        } else {
            discoveredSounds
        }
        ScreenRecorderDialogInjector.show(
            context = context,
            contentContext = contentContext,
            text = text,
            resolutions = resolutions,
            sounds = sounds,
            initialResolution = currentResolution,
            initialSound = currentSound,
            recordingSnapshot = null,
            onCancel = { recorderDialogVisible = false },
            onOpenSettings = {
                recorderDialogVisible = false
                settingsActivity?.let { openRecorderSettings(context, it) }
            },
            onStart = { resolution, sound, motionPhoto ->
                recorderDialogVisible = false
                recorderPreferences(context).edit {
                    if (resolution.isNotBlank()) {
                        putString(ScreenRecorderContract.PREF_RESOLUTION, resolution)
                    }
                    putString(ScreenRecorderContract.PREF_SOUND, sound.toString())
                }
                MotionPhotoSession.arm(context, motionPhoto)
                scheduleConfirmedStart(context)
            },
            onPause = {},
            onResume = {},
            onStop = {},
        )
    }

    private fun resolveSettingsActivity(context: Context): String? =
        context.packageManager.resolveActivity(
            Intent(SETTINGS_ACTION).setPackage(ScreenRecorderContract.TARGET_PACKAGE),
            0,
        )?.activityInfo?.name

    private fun openRecorderSettings(context: Context, settingsActivity: String) {
        val intent = Intent(SETTINGS_ACTION).apply {
            setClassName(ScreenRecorderContract.TARGET_PACKAGE, settingsActivity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        PendingIntent.getActivity(
            context,
            0x4853,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ).send()
    }

    @Suppress("DEPRECATION")
    private fun readRecorderResolutions(context: Context): List<Pair<String, String>> {
        val suffix = when (Build.DEVICE) {
            "cappu" -> "_c9"
            "lotus" -> "_f9"
            else -> ""
        }
        val thresholds = readStringArray(context, "screenrecorder_settings_resolution${suffix}_values")
            .mapNotNull(String::toIntOrNull)
        val fullValues = readStringArray(context, "screenrecorder_settings_resolution${suffix}_full_values")
        val metrics = android.util.DisplayMetrics()
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay
            ?.getRealMetrics(metrics)
        val longEdge = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(2) and -2
        val shortEdge = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(2) and -2
        val values = linkedSetOf("$longEdge*$shortEdge")
        if (thresholds.size >= 3 && fullValues.size >= 3) {
            if (shortEdge > thresholds[1]) values += fullValues[1]
            if (shortEdge > thresholds[2]) values += fullValues[2]
        } else {
            if (shortEdge > 1080) values += "1920*1080"
            if (shortEdge > 720) values += "1280*720"
        }
        return values.map { value -> formatResolution(value) to value }
    }

    private fun readStringArray(context: Context, name: String): List<String> {
        val resourceId = context.resources.getIdentifier(
            name,
            "array",
            ScreenRecorderContract.TARGET_PACKAGE,
        )
        return if (resourceId == 0) {
            emptyList()
        } else {
            runCatching { context.resources.getStringArray(resourceId).toList() }.getOrDefault(emptyList())
        }
    }

    private fun readRecorderSounds(
        context: Context,
        currentSound: Int,
        text: ScreenRecorderUiText,
    ): List<Pair<String, Int>> {
        val labels = readStringArray(context, "screenrecorder_settings_sound")
        val values = readStringArray(context, "screenrecorder_settings_sound_values")
        val resourceOptions = labels.zip(values).mapNotNull { (label, value) ->
            value.toIntOrNull()?.let { label to it }
        }
        val supportsCombinedSound =
            currentSound == 3 || readSystemPropertyInt("ro.vendor.audio.screenrecorder.bothrecord") > 0
        if (resourceOptions.isNotEmpty()) {
            val options = resourceOptions.toMutableList()
            if (supportsCombinedSound && options.none { it.second == 3 }) {
                options += text.soundDeviceAndMicrophone to 3
            }
            return options
        }
        val fallback = mutableListOf(
            text.soundNone to 0,
            text.soundMicrophone to 1,
            text.soundDevice to 2,
        )
        if (supportsCombinedSound) fallback += text.soundDeviceAndMicrophone to 3
        return fallback
    }

    private fun readSystemPropertyInt(key: String): Int = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val getInt = systemProperties.getDeclaredMethod("getInt", String::class.java, Integer.TYPE)
        (getInt.invoke(null, key, 0) as? Int) ?: 0
    }.getOrDefault(0)

    private fun formatResolution(value: String): String = value.replace("*", " × ")

    private fun screenRecorderContentContext(context: Context): Context {
        val moduleContext = runCatching {
            context.createPackageContext(
                IslandProtocol.APP_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY,
            )
        }.getOrElse { firstError ->
            val info = moduleApplicationInfo() ?: throw firstError
            val resources = context.packageManager.getResourcesForApplication(info)
            object : android.content.ContextWrapper(context) {
                override fun getResources() = resources
                override fun getAssets() = resources.assets
                override fun getTheme(): android.content.res.Resources.Theme =
                    resources.newTheme().apply { applyStyle(android.R.style.Theme_DeviceDefault, true) }
            }
        }
        return InjectedResourceContext(context, moduleContext)
    }

    private fun moduleApplicationInfo(): android.content.pm.ApplicationInfo? {
        val module = xposedModule ?: return null
        return runCatching {
            module.javaClass.methods
                .first { it.name == "getApplicationInfo" && it.parameterCount == 0 }
                .invoke(module) as android.content.pm.ApplicationInfo
        }.getOrNull()
    }

    private fun screenRecorderUiText(context: Context): ScreenRecorderUiText {
        fun string(resourceId: Int, fallback: String): String =
            runCatching { context.getString(resourceId) }.getOrNull().orEmpty().ifBlank { fallback }
        return ScreenRecorderUiText(
            dialogTitle = string(R.string.screen_recorder_dialog_title, "Screen recording"),
            resolution = string(R.string.screen_recorder_resolution, "Resolution"),
            soundSource = string(R.string.screen_recorder_sound_source, "Audio source"),
            motionPhoto = string(R.string.screen_recorder_motion_photo, "Motion photo"),
            motionPhotoSummary = string(
                R.string.screen_recorder_motion_photo_summary,
                "Save as a motion photo when recording finishes",
            ),
            soundNone = string(R.string.screen_recorder_sound_none, "No audio"),
            soundMicrophone = string(R.string.screen_recorder_sound_microphone, "Microphone"),
            soundDevice = string(R.string.screen_recorder_sound_device, "Device audio"),
            soundDeviceAndMicrophone = string(
                R.string.screen_recorder_sound_device_and_microphone,
                "Device audio + microphone",
            ),
            moreSettings = string(R.string.screen_recorder_more_settings, "More settings"),
            moreSettingsSummary = string(
                R.string.screen_recorder_more_settings_summary,
                "Open Xiaomi Screen Recorder settings",
            ),
            start = string(R.string.screen_recorder_start, "Start recording"),
            currentSetting = string(R.string.screen_recorder_current_setting, "Current setting"),
            currentSettingValueFormat = string(
                R.string.screen_recorder_current_setting_value,
                "Current setting (%1\$s)",
            ),
            preparingTitle = string(R.string.screen_recorder_preparing_title, "Preparing to record"),
            recordingTitle = string(R.string.screen_recorder_recording_title, "Recording"),
            pausedTitle = string(R.string.screen_recorder_paused_title, "Paused"),
            recordedDurationFormat = string(R.string.screen_recorder_recorded_duration, "Recorded %1\$s"),
            preparing = string(R.string.screen_recorder_preparing, "Preparing"),
            pause = string(R.string.screen_recording_pause, "Pause"),
            resume = string(R.string.screen_recording_resume, "Resume"),
            stop = string(R.string.screen_recording_stop, "Stop"),
            cancel = string(R.string.cancel, "Cancel"),
            notificationRecordingTitle = string(
                R.string.screen_recorder_notification_recording_title,
                "Recording screen",
            ),
            notificationPausedTitle = string(
                R.string.screen_recorder_notification_paused_title,
                "Recording paused",
            ),
            notificationRecordingText = string(
                R.string.screen_recorder_notification_recording_text,
                "Pause or stop and save the recording",
            ),
            notificationPausedText = string(
                R.string.screen_recorder_notification_paused_text,
                "Tap to resume recording",
            ),
            notificationStop = string(R.string.screen_recorder_notification_stop, "Stop"),
        )
    }

    private fun isRecorderServiceClass(serviceClass: Class<*>): Boolean =
        serviceClass.declaredMethods.any {
            it.parameterCount == 0 && it.returnType == Notification.Builder::class.java
        } && serviceClass.declaredMethods.any {
            it.name == "onStartCommand" && it.parameterCount == 3
        }

    private fun hookRecordingNotification(module: XposedModule, serviceClass: Class<*>) {
        if (!hookedRecorderServiceClasses.add(serviceClass)) return
        hookLowBatteryWarningBypass(module, serviceClass)
        serviceClass.declaredMethods.firstOrNull { it.name == "onCreate" && it.parameterCount == 0 }?.let { onCreate ->
            onCreate.isAccessible = true
            module.hook(onCreate).intercept { chain ->
                val result = chain.proceed()
                val service = chain.thisObject as? Service
                if (service != null) {
                    initializeControlClient(service, module)
                    hookRuntimeRecorderValidation(module, serviceClass, service)
                }
                result
            }
        }
        serviceClass.declaredMethods.firstOrNull { it.name == "onStartCommand" && it.parameterCount == 3 }
            ?.let { onStartCommand ->
                onStartCommand.isAccessible = true
                module.hook(onStartCommand).intercept { chain ->
                    val service = chain.thisObject as? Service ?: return@intercept chain.proceed()
                    initializeControlClient(service, module)
                    val intent = chain.args.getOrNull(0) as? Intent ?: return@intercept chain.proceed()
                    if (intent.getBooleanExtra(ScreenRecorderContract.EXTRA_TOGGLE_PAUSE, false)) {
                        if (ScreenRecorderControlClient.snapshot.state == ScreenRecorderContract.STATE_PAUSED) {
                            ScreenRecorderControlClient.resume()
                        } else {
                            ScreenRecorderControlClient.pause()
                        }
                        return@intercept Service.START_NOT_STICKY
                    }
                    if (intent.getBooleanExtra(ScreenRecorderContract.EXTRA_CONTROL_STOP, false)) {
                        ScreenRecorderControlClient.stop()
                        return@intercept Service.START_NOT_STICKY
                    }
                    if (isRecorderControlIntent(intent)) {
                        applyStartOptions(service, intent.extras, module)
                        if (intent.getBooleanExtra(ScreenRecorderContract.EXTRA_CONFIRMED_START, false)) {
                            bypassNextLowBatteryWarning.set(true)
                        }
                        return@intercept chain.proceed()
                    }
                    if (HookConfig.screenRecorderImmediateStart() &&
                        !ScreenRecorderControlClient.snapshot.isSessionActive
                    ) {
                        scheduleConfirmedStart(service)
                        return@intercept Service.START_NOT_STICKY
                    }
                    if (recorderDialogVisible) return@intercept Service.START_NOT_STICKY
                    recorderDialogVisible = true
                    ScreenRecorderControlClient.requestSnapshot { snapshot ->
                        presentRecorderPrompt(service, snapshot, module)
                    }
                    Service.START_NOT_STICKY
                }
            }
        val builderMethod = serviceClass.declaredMethods.firstOrNull {
            it.parameterCount == 0 && it.returnType == Notification.Builder::class.java
        } ?: return
        builderMethod.isAccessible = true
        recordingNotificationBuilderMethod = builderMethod
        module.hook(builderMethod).intercept { chain ->
            val original = chain.proceed()
            val builder = original as? Notification.Builder ?: return@intercept original
            val context = chain.thisObject as? Context ?: return@intercept builder
            decorateRecordingNotification(builder, context, ScreenRecorderControlClient.snapshot)
            builder
        }
        serviceClass.declaredMethods.firstOrNull { it.name == "onDestroy" && it.parameterCount == 0 }?.let { onDestroy ->
            onDestroy.isAccessible = true
            module.hook(onDestroy).intercept { chain ->
                val result = chain.proceed()
                ScreenRecorderControlClient.reportIdle()
                recorderDialogVisible = false
                recordingNotificationBuilder = null
                recordingNotificationContext = null
                result
            }
        }
    }

    private fun hookLowBatteryWarningBypass(module: XposedModule, serviceClass: Class<*>) {
        hookRecorderValidationMethods(
            module,
            serviceClass.declaredFields.asSequence().map { it.type }.toList(),
            "declared fields",
        )
    }

    private fun hookRuntimeRecorderValidation(module: XposedModule, serviceClass: Class<*>, service: Service) {
        val runtimeTypes = serviceClass.declaredFields.asSequence().mapNotNull { field ->
            runCatching {
                field.isAccessible = true
                field.get(service)?.javaClass
            }.getOrNull()
        }.distinct().toList()
        hookRecorderValidationMethods(module, runtimeTypes, "runtime fields")
    }

    private fun hookRecorderValidationMethods(module: XposedModule, types: List<Class<*>>, source: String) {
        val validationMethods = types.asSequence()
            .filterNot { type ->
                type.isPrimitive ||
                    type.name.startsWith("android.") ||
                    type.name.startsWith("java.") ||
                    type.name.startsWith("kotlin.")
            }
            .flatMap { type -> type.declaredMethods.asSequence() }
            .filter { method ->
                Modifier.isPublic(method.modifiers) &&
                    method.returnType == java.lang.Boolean.TYPE &&
                    method.parameterTypes.contentEquals(arrayOf(java.lang.Boolean.TYPE, Integer.TYPE))
            }
            .distinct()
            .toList()
        validationMethods.forEach { method ->
            if (!hookedRecorderValidationMethods.add(method)) return@forEach
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                if (bypassNextLowBatteryWarning.compareAndSet(true, false)) {
                    val replacementArgs = chain.args.toTypedArray()
                    replacementArgs[0] = true
                    chain.proceed(replacementArgs)
                } else {
                    chain.proceed()
                }
            }
        }
        if (validationMethods.isEmpty()) {
            module.log("screen recorder low-battery validation not found in $source")
        }
    }

    private fun isRecorderControlIntent(intent: Intent): Boolean =
        intent.getBooleanExtra(ScreenRecorderContract.EXTRA_CONFIRMED_START, false) ||
            intent.getBooleanExtra(ScreenRecorderContract.EXTRA_TOGGLE_PAUSE, false) ||
            intent.getBooleanExtra(ScreenRecorderContract.EXTRA_CONTROL_STOP, false) ||
            intent.getBooleanExtra(ScreenRecorderContract.EXTRA_STOP_SCREENRECORDER, false) ||
            intent.getBooleanExtra("stop_self", false) ||
            intent.getBooleanExtra("do_nothing", false) ||
            intent.getBooleanExtra("is_screen_off_auto_stop", false)

    private fun decorateRecordingNotification(
        builder: Notification.Builder,
        context: Context,
        snapshot: RecorderSnapshot,
    ) {
        val text = screenRecorderUiText(context)
        val nowWallClock = System.currentTimeMillis()
        val durationMillis = snapshot.durationAt(android.os.SystemClock.elapsedRealtime())
        val timerWhen = nowWallClock - durationMillis
        val isPaused = snapshot.state == ScreenRecorderContract.STATE_PAUSED
        val pauseIntent = recorderControlIntent(context, 0x4851, ScreenRecorderContract.EXTRA_TOGGLE_PAUSE)
        val stopIntent = recorderControlIntent(context, 0x4852, ScreenRecorderContract.EXTRA_CONTROL_STOP)
        val pauseAction = Notification.Action.Builder(
            Icon.createWithResource(
                IslandProtocol.APP_PACKAGE,
                if (isPaused) R.drawable.ic_focus_resume_light else R.drawable.ic_focus_pause_light,
            ),
            if (isPaused) text.resume else text.pause,
            pauseIntent,
        ).build()
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(IslandProtocol.APP_PACKAGE, R.drawable.ic_screen_recording_stop_light),
            text.notificationStop,
            stopIntent,
        ).build()
        builder
            .setContentTitle(if (isPaused) text.notificationPausedTitle else text.notificationRecordingTitle)
            .setContentText(if (isPaused) text.notificationPausedText else text.notificationRecordingText)
            .setWhen(timerWhen)
            .setShowWhen(true)
            .setUsesChronometer(!isPaused)
            .setOnlyAlertOnce(true)
            .setActions(pauseAction, stopAction)
        if (HookConfig.replaceScreenRecorder()) {
            runCatching {
                val originalExtras = runCatching { Bundle(builder.build().extras) }.getOrElse { Bundle() }
                originalExtras.putAll(buildFocusExtras(context, snapshot, text, pauseIntent, stopIntent))
                builder.setExtras(originalExtras)
            }.onFailure {
                xposedModule?.log("screen recorder focus extras failed: ${it.message}")
            }
        }
        recordingNotificationBuilder = WeakReference(builder)
        recordingNotificationContext = WeakReference(context)
    }

    private fun hookNotificationManager(module: XposedModule) {
        NotificationManager::class.java.declaredMethods.filter { it.name == "notify" }.forEach { method ->
            if (!hookedNotifyMethods.add(method)) return@forEach
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                injectFocusOnNotify(chain.thisObject, chain.args.toTypedArray())
                chain.proceed()
            }
        }
    }

    private fun injectFocusOnNotify(thisObject: Any?, args: Array<out Any?>) {
        if (!HookConfig.replaceScreenRecorder()) return
        val notification = args.lastOrNull() as? Notification ?: return
        val id = recordingNotifyId(args) ?: return
        if (id != RECORDING_NOTIFICATION_ID) return
        if (!notification.extras.getString("miui.focus.param").isNullOrBlank()) return
        val context = recordingNotificationContext?.get()
            ?: notificationManagerContext(thisObject)
            ?: return
        val snapshot = ScreenRecorderControlClient.snapshot
        if (!snapshot.isSessionActive) return
        val text = screenRecorderUiText(context)
        val pauseIntent = recorderControlIntent(
            context,
            0x4851,
            ScreenRecorderContract.EXTRA_TOGGLE_PAUSE,
        )
        val stopIntent = recorderControlIntent(
            context,
            0x4852,
            ScreenRecorderContract.EXTRA_CONTROL_STOP,
        )
        runCatching {
            notification.extras.putAll(buildFocusExtras(context, snapshot, text, pauseIntent, stopIntent))
        }
    }

    private fun recordingNotifyId(args: Array<out Any?>): Int? = when (args.size) {
        2 -> args[0] as? Int
        3 -> args[1] as? Int
        else -> null
    }

    private fun notificationManagerContext(manager: Any?): Context? {
        val notificationManager = manager as? NotificationManager ?: return null
        return runCatching {
            val field = NotificationManager::class.java.getDeclaredField("mContext").apply {
                isAccessible = true
            }
            field.get(notificationManager) as? Context
        }.getOrNull()
    }

    private fun recorderControlIntent(context: Context, requestCode: Int, extra: String): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(RECORDER_SERVICE_ACTION).apply {
                setPackage(ScreenRecorderContract.TARGET_PACKAGE)
                putExtra(extra, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun buildFocusExtras(
        recorderContext: Context,
        snapshot: RecorderSnapshot,
        text: ScreenRecorderUiText,
        pauseIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Bundle {
        // These R values belong to HyperBridge. Resolving them through the recorder package
        // throws NotFoundException and causes Xiaomi's entire focus payload to be discarded.
        val context = screenRecorderContentContext(recorderContext)
        val session = recordingSessionFrom(snapshot)
        val compact = when {
            snapshot.state == ScreenRecorderContract.STATE_STARTING ->
                stringOrFallback(context, R.string.screen_recording_starting, "Starting…")
            snapshot.state == ScreenRecorderContract.STATE_PAUSED -> text.notificationPausedTitle
            else -> stringOrFallback(context, R.string.screen_recording_compact, "Recording..")
        }
        val expanded = when {
            snapshot.state == ScreenRecorderContract.STATE_STARTING -> compact
            snapshot.state == ScreenRecorderContract.STATE_PAUSED -> text.notificationPausedTitle
            else -> stringOrFallback(context, R.string.screen_recording_compact, "Recording..")
        }
        return ScreenRecordingTranslator(context).buildFocusExtras(
            session = session,
            compactText = compact,
            expandedText = expanded,
            picturePackage = IslandProtocol.APP_PACKAGE,
            notifyId = "${ScreenRecorderContract.TARGET_PACKAGE}$RECORDING_NOTIFICATION_ID",
            business = ScreenRecordingTranslator.BUSINESS,
            enableFloat = false,
            tickerIcon = screenRecorderTickerIcon(),
            pauseIntent = pauseIntent,
            stopIntent = stopIntent,
        )
    }

    private fun recordingSessionFrom(snapshot: RecorderSnapshot): ScreenRecordingSession {
        val startedAt = snapshot.startedAtWallClock.takeIf { it > 0L } ?: System.currentTimeMillis()
        return ScreenRecordingSession(
            logicalId = "screen-recording",
            sourceKey = "screen-recording",
            packageName = ScreenRecorderContract.TARGET_PACKAGE,
            startedAt = startedAt,
            capabilities = ScreenRecordingCapabilities(
                canStop = true,
                canPause = snapshot.state == ScreenRecorderContract.STATE_RECORDING ||
                    snapshot.state == ScreenRecorderContract.STATE_PAUSED,
                canResume = snapshot.state == ScreenRecorderContract.STATE_PAUSED,
            ),
            paused = snapshot.state == ScreenRecorderContract.STATE_PAUSED,
            countdownRemaining = snapshot.countdownRemaining.takeIf {
                snapshot.state == ScreenRecorderContract.STATE_STARTING
            } ?: 0,
        )
    }

    private fun screenRecorderTickerIcon(): Int =
        if (HookConfig.screenRecorderIconStyle() == ScreenRecorderContract.ICON_VOICE_RECORDER) {
            R.drawable.ic_focus_ticker_recorder
        } else {
            R.drawable.ic_screen_recording_ticker
        }

    private fun stringOrFallback(context: Context, resourceId: Int, fallback: String): String =
        runCatching { context.getString(resourceId) }.getOrNull().orEmpty().ifBlank { fallback }

    private fun refreshRecordingNotification(snapshot: RecorderSnapshot, module: XposedModule) {
        if (
            snapshot.state != ScreenRecorderContract.STATE_RECORDING &&
            snapshot.state != ScreenRecorderContract.STATE_PAUSED
        ) {
            return
        }
        val context = recordingNotificationContext?.get() ?: return
        val builder = recordingNotificationBuilder?.get()
            ?: runCatching {
                recordingNotificationBuilderMethod?.invoke(context) as? Notification.Builder
            }.getOrNull()
            ?: return
        runCatching {
            decorateRecordingNotification(builder, context, snapshot)
            context.getSystemService(NotificationManager::class.java)
                ?.notify(RECORDING_NOTIFICATION_ID, builder.build())
        }.onFailure {
            module.log("screen recorder notification refresh failed: ${it.message}")
        }
    }

    private fun requestRecorderStop(context: Context) {
        context.startService(Intent(RECORDER_SERVICE_ACTION).apply {
            setPackage(ScreenRecorderContract.TARGET_PACKAGE)
            putExtra(ScreenRecorderContract.EXTRA_STOP_SCREENRECORDER, true)
        })
    }

    private fun handleControlCommand(context: Context, command: Int, extras: Bundle?, module: XposedModule) {
        when (command) {
            ScreenRecorderContract.MSG_COMMAND_PAUSE -> MediaMuxerPauseGate.pause()
            ScreenRecorderContract.MSG_COMMAND_RESUME -> {
                MediaMuxerPauseGate.resume {
                    VideoEncoderSyncFrameRequester.requestSyncFrames()
                }
            }
            ScreenRecorderContract.MSG_COMMAND_STOP -> {
                cancelPendingConfirmedStart()
                if (Application.getProcessName() == ScreenRecorderContract.TARGET_PACKAGE) {
                    requestRecorderStop(context)
                }
            }
            ScreenRecorderContract.MSG_COMMAND_START -> {
                if (Application.getProcessName() == ScreenRecorderContract.TARGET_PACKAGE) {
                    applyStartOptions(context, extras, module)
                    requestRecorderStart(context)
                }
            }
        }
    }

    private fun applyStartOptions(context: Context, options: Bundle?, module: XposedModule) {
        if (options == null) return
        val editor = recorderPreferences(context).edit()
        var changed = false
        if (options.containsKey(ScreenRecorderContract.API_EXTRA_RESOLUTION)) {
            val resolution = options.getString(ScreenRecorderContract.API_EXTRA_RESOLUTION)
            if (!resolution.isNullOrBlank()) {
                editor.putString(ScreenRecorderContract.PREF_RESOLUTION, resolution)
                changed = true
            }
        }
        if (options.containsKey(ScreenRecorderContract.API_EXTRA_SOUND)) {
            editor.putString(
                ScreenRecorderContract.PREF_SOUND,
                options.getInt(ScreenRecorderContract.API_EXTRA_SOUND, 0).toString(),
            )
            changed = true
        }
        if (changed) editor.apply()
        if (options.containsKey(ScreenRecorderContract.API_EXTRA_MOTION_PHOTO)) {
            MotionPhotoSession.arm(
                context,
                options.getBoolean(ScreenRecorderContract.API_EXTRA_MOTION_PHOTO, false),
            )
        }
    }

    private fun scheduleConfirmedStart(context: Context) {
        cancelPendingConfirmedStart()
        xposedModule?.let { initializeControlClient(context, it) }
        val armed = AtomicBoolean(false)
        val start = Runnable {
            pendingConfirmedStart = null
            pendingStartObserver?.invoke()
            pendingStartObserver = null
            requestRecorderStart(context)
        }
        // The local snapshot is published before the service is bound, with no countdown.
        // Wait for the service echo that actually posts the island, then hold for the full
        // countdown so "Starting…" is on screen before MediaMuxer.start().
        pendingStartObserver = ScreenRecorderControlClient.observe { snapshot ->
            if (
                snapshot.state != ScreenRecorderContract.STATE_STARTING ||
                snapshot.countdownRemaining <= 0 ||
                !armed.compareAndSet(false, true)
            ) {
                return@observe
            }
            pendingConfirmedStart = start
            mainHandler.postDelayed(
                start,
                ScreenRecorderContract.COUNTDOWN_SECONDS * ScreenRecorderContract.COUNTDOWN_TICK_MS,
            )
        }
        ScreenRecorderControlClient.reportStarting()
    }

    private fun cancelPendingConfirmedStart() {
        pendingConfirmedStart?.let(mainHandler::removeCallbacks)
        pendingConfirmedStart = null
        pendingStartObserver?.invoke()
        pendingStartObserver = null
    }

    private fun requestRecorderStart(context: Context) {
        context.startService(Intent(RECORDER_SERVICE_ACTION).apply {
            setPackage(ScreenRecorderContract.TARGET_PACKAGE)
            putExtra(ScreenRecorderContract.EXTRA_IS_START_IMMEDIATELY, true)
            putExtra(ScreenRecorderContract.EXTRA_CONFIRMED_START, true)
        })
    }

    private fun hookMediaMuxerOutput(module: XposedModule) {
        runCatching {
            MediaMuxer::class.java.getDeclaredConstructor(String::class.java, Integer.TYPE)
        }.getOrNull()?.let { constructor ->
            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                val muxer = chain.thisObject as? MediaMuxer
                if (muxer != null) {
                    MotionPhotoSession.onMuxerCreated(muxer, chain.args.firstOrNull() as? String)
                }
                result
            }
        }
        runCatching {
            MediaMuxer::class.java.getDeclaredConstructor(FileDescriptor::class.java, Integer.TYPE)
        }.getOrNull()?.let { constructor ->
            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                val muxer = chain.thisObject as? MediaMuxer
                val descriptor = chain.args.firstOrNull() as? FileDescriptor
                if (muxer != null && descriptor != null) {
                    MotionPhotoSession.onMuxerCreated(
                        muxer,
                        MotionPhotoSession.pathFrom(descriptor),
                        descriptor,
                    )
                }
                result
            }
        }
    }

    private fun hookMediaMuxerLifecycle(module: XposedModule) {
        val addTrack = MediaMuxer::class.java.getDeclaredMethod("addTrack", MediaFormat::class.java)
        module.hook(addTrack).intercept { chain ->
            val result = chain.proceed()
            val trackIndex = result as? Int ?: return@intercept result
            val muxer = chain.thisObject as? MediaMuxer ?: return@intercept trackIndex
            val format = chain.args.firstOrNull() as? MediaFormat
            val mime = runCatching { format?.getString(MediaFormat.KEY_MIME) }.getOrNull()
            MediaMuxerPauseGate.onTrackAdded(muxer, trackIndex, mime)
            trackIndex
        }
        val start = MediaMuxer::class.java.getDeclaredMethod("start")
        module.hook(start).intercept { chain ->
            val result = chain.proceed()
            val muxer = chain.thisObject as? MediaMuxer ?: return@intercept result
            MediaMuxerPauseGate.onStarted(muxer)
            MotionPhotoSession.onMuxerStarted(muxer) { context ->
                requestRecorderStop(context)
            }
            ScreenRecorderControlClient.reportStarted()
            result
        }
        listOf("stop", "release").forEach { methodName ->
            val method = MediaMuxer::class.java.getDeclaredMethod(methodName)
            module.hook(method).intercept { chain ->
                val muxer = chain.thisObject as? MediaMuxer
                try {
                    chain.proceed()
                } finally {
                    if (methodName == "release" && muxer != null) {
                        MotionPhotoSession.onMuxerReleased(muxer) { }
                    }
                    if (muxer != null && MediaMuxerPauseGate.onStopped(muxer)) {
                        ScreenRecorderControlClient.reportIdle()
                    }
                }
            }
        }
        val writeSampleData = MediaMuxer::class.java.getDeclaredMethod(
            "writeSampleData",
            Integer.TYPE,
            ByteBuffer::class.java,
            MediaCodec.BufferInfo::class.java,
        )
        module.hook(writeSampleData).intercept { chain ->
            val muxer = chain.thisObject as? MediaMuxer ?: return@intercept chain.proceed()
            val info = chain.args.getOrNull(2) as? MediaCodec.BufferInfo ?: return@intercept chain.proceed()
            val trackIndex = chain.args.getOrNull(0) as? Int ?: return@intercept chain.proceed()
            if (MediaMuxerPauseGate.shouldDrop(muxer, trackIndex, info.flags)) return@intercept null
            val originalPresentationTime = info.presentationTimeUs
            info.presentationTimeUs = MediaMuxerPauseGate.adjustedPresentationTime(
                muxer,
                trackIndex,
                originalPresentationTime,
            )
            try {
                chain.proceed()
            } finally {
                info.presentationTimeUs = originalPresentationTime
            }
        }
    }

    private fun hookVideoEncoderSyncFrame(module: XposedModule) {
        MediaCodec::class.java.declaredMethods.filter { method ->
            method.name == "configure" &&
                method.parameterCount == 4 &&
                method.parameterTypes.firstOrNull() == MediaFormat::class.java &&
                method.parameterTypes.lastOrNull() == Integer.TYPE
        }.forEach { configure ->
            module.hook(configure).intercept { chain ->
                val result = chain.proceed()
                val codec = chain.thisObject as? MediaCodec ?: return@intercept result
                val format = chain.args.firstOrNull() as? MediaFormat
                val flags = chain.args.lastOrNull() as? Int ?: 0
                val mime = runCatching { format?.getString(MediaFormat.KEY_MIME) }.getOrNull()
                if (flags and MediaCodec.CONFIGURE_FLAG_ENCODE != 0 && mime?.startsWith("video/") == true) {
                    VideoEncoderSyncFrameRequester.register(codec)
                }
                result
            }
        }
        val start = MediaCodec::class.java.getDeclaredMethod("start")
        module.hook(start).intercept { chain ->
            val result = chain.proceed()
            (chain.thisObject as? MediaCodec)?.let { VideoEncoderSyncFrameRequester.setActive(it, true) }
            result
        }
        listOf("stop", "release").forEach { methodName ->
            val method = MediaCodec::class.java.getDeclaredMethod(methodName)
            module.hook(method).intercept { chain ->
                val codec = chain.thisObject as? MediaCodec
                try {
                    chain.proceed()
                } finally {
                    if (codec != null) {
                        if (methodName == "release") {
                            VideoEncoderSyncFrameRequester.remove(codec)
                        } else {
                            VideoEncoderSyncFrameRequester.setActive(codec, false)
                        }
                    }
                }
            }
        }
    }

    private fun recorderPreferences(context: Context) = context.getSharedPreferences(
        "${ScreenRecorderContract.TARGET_PACKAGE}_preferences",
        Context.MODE_PRIVATE,
    )
}
