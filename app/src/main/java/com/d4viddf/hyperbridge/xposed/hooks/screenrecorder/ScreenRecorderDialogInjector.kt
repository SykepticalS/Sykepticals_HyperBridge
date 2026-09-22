package com.d4viddf.hyperbridge.xposed.hooks.screenrecorder

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import com.d4viddf.hyperbridge.ui.theme.HyperBridgeTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.setViewTreeNavigationEventDispatcherOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.d4viddf.hyperbridge.screenrecorder.RecorderSnapshot
import com.d4viddf.hyperbridge.screenrecorder.ScreenRecorderContract
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal object RecorderOverlayGate {
    @Volatile
    private var allowedUntil = 0L

    fun open(durationMillis: Long = 750L) {
        allowedUntil = SystemClock.uptimeMillis() + durationMillis
    }

    fun close() {
        allowedUntil = 0L
    }

    fun allows(view: View?): Boolean =
        SystemClock.uptimeMillis() <= allowedUntil &&
            view?.javaClass?.name == "com.android.internal.policy.DecorView"
}

internal data class ScreenRecorderUiText(
    val dialogTitle: String,
    val resolution: String,
    val soundSource: String,
    val motionPhoto: String,
    val motionPhotoSummary: String,
    val soundNone: String,
    val soundMicrophone: String,
    val soundDevice: String,
    val soundDeviceAndMicrophone: String,
    val moreSettings: String,
    val moreSettingsSummary: String,
    val start: String,
    val currentSetting: String,
    val currentSettingValueFormat: String,
    val preparingTitle: String,
    val recordingTitle: String,
    val pausedTitle: String,
    val recordedDurationFormat: String,
    val preparing: String,
    val pause: String,
    val resume: String,
    val stop: String,
    val cancel: String,
    val notificationRecordingTitle: String,
    val notificationPausedTitle: String,
    val notificationRecordingText: String,
    val notificationPausedText: String,
    val notificationStop: String,
) {
    fun currentSettingValue(value: String): String =
        String.format(Locale.getDefault(), currentSettingValueFormat, value)

    fun recordedDuration(value: String): String =
        String.format(Locale.getDefault(), recordedDurationFormat, value)
}

internal object ScreenRecorderDialogInjector {
    fun show(
        context: Context,
        contentContext: Context,
        text: ScreenRecorderUiText,
        resolutions: List<Pair<String, String>>,
        sounds: List<Pair<String, Int>>,
        initialResolution: String,
        initialSound: Int,
        recordingSnapshot: RecorderSnapshot?,
        onCancel: () -> Unit,
        onOpenSettings: () -> Unit,
        onStart: (resolution: String, sound: Int, motionPhoto: Boolean) -> Unit,
        onPause: () -> Unit,
        onResume: () -> Unit,
        onStop: () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Handler(Looper.getMainLooper()).post {
                show(
                    context,
                    contentContext,
                    text,
                    resolutions,
                    sounds,
                    initialResolution,
                    initialSound,
                    recordingSnapshot,
                    onCancel,
                    onOpenSettings,
                    onStart,
                    onPause,
                    onResume,
                    onStop,
                )
            }
            return
        }
        val hostDialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        val mainHandler = Handler(Looper.getMainLooper())
        var animatedBackHandler: (() -> Unit)? = null
        lateinit var dismissHost: () -> Unit
        val viewTreeOwner = InjectedViewTreeOwner {
            val handler = animatedBackHandler
            if (handler != null) handler() else dismissHost()
        }
        lateinit var host: ComposeView
        var closeRequested = false
        var resourcesReleased = false

        fun releaseHostResources() {
            if (resourcesReleased) return
            resourcesReleased = true
            animatedBackHandler = null
            hostDialog.window?.setCallback(null)
            host.disposeComposition()
            viewTreeOwner.destroy()
        }

        dismissHost = dismissHost@{
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post { dismissHost() }
                return@dismissHost
            }
            if (closeRequested) return@dismissHost
            closeRequested = true
            mainHandler.post {
                releaseHostResources()
                if (hostDialog.isShowing) hostDialog.dismiss()
            }
        }

        host = ComposeView(contentContext).apply {
            setViewTreeLifecycleOwner(viewTreeOwner)
            setViewTreeSavedStateRegistryOwner(viewTreeOwner)
            setViewTreeViewModelStoreOwner(viewTreeOwner)
            setViewTreeNavigationEventDispatcherOwner(viewTreeOwner)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewTreeOwner.lifecycle),
            )
            setContent {
                HyperBridgeTheme {
                    RecorderWindowDialog(
                        text = text,
                        resolutions = resolutions,
                        sounds = sounds,
                        initialResolution = initialResolution,
                        initialSound = initialSound,
                        recordingSnapshot = recordingSnapshot,
                        onCancel = {
                            dismissHost()
                            onCancel()
                        },
                        onOpenSettings = {
                            dismissHost()
                            onOpenSettings()
                        },
                        onStart = { resolution, sound, motionPhoto ->
                            dismissHost()
                            onStart(resolution, sound, motionPhoto)
                        },
                        onPause = onPause,
                        onResume = onResume,
                        onStop = {
                            dismissHost()
                            onStop()
                        },
                        onBackHandlerChanged = { animatedBackHandler = it },
                    )
                }
            }
        }
        hostDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        hostDialog.setContentView(
            host,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        hostDialog.setCancelable(false)
        hostDialog.setCanceledOnTouchOutside(false)
        hostDialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissHost()
                onCancel()
                true
            } else {
                false
            }
        }
        hostDialog.setOnDismissListener { releaseHostResources() }
        val tileService = unwrapTileService(context)
        hostDialog.window?.apply {
            if (tileService == null) {
                setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = AndroidColor.TRANSPARENT
            navigationBarColor = AndroidColor.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
            @Suppress("DEPRECATION")
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            attributes = attributes.apply {
                dimAmount = 0.22f
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        viewTreeOwner.resume()
        RecorderOverlayGate.open()
        if (tileService != null) {
            tileService.showDialog(hostDialog)
        }
        if (!hostDialog.isShowing) {
            hostDialog.show()
        }
        viewTreeOwner.attachBackInput(hostDialog.onBackInvokedDispatcher)
        hostDialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        Handler(Looper.getMainLooper()).postDelayed(RecorderOverlayGate::close, 750L)
    }
}

private fun unwrapTileService(context: Context): TileService? {
    var current: Context? = context
    while (current != null) {
        if (current is TileService) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

internal class InjectedResourceContext(
    base: Context,
    private val resourceContext: Context,
) : ContextWrapper(base) {
    override fun getResources(): Resources = resourceContext.resources
    override fun getAssets(): AssetManager = resourceContext.assets
    override fun getTheme(): Resources.Theme = resourceContext.theme
    override fun setTheme(resid: Int) {
        resourceContext.setTheme(resid)
    }

    override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
        InjectedResourceContext(
            baseContext.createConfigurationContext(overrideConfiguration),
            resourceContext.createConfigurationContext(overrideConfiguration),
        )
}

private class InjectedViewTreeOwner(
    private val onBackRequested: () -> Unit,
) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, NavigationEventDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val models = ViewModelStore()
    override val navigationEventDispatcher = NavigationEventDispatcher()
    private var backInput: OnBackInvokedDefaultInput? = null
    private val rootBackHandler = object : NavigationEventHandler<NavigationEventInfo>(
        initialInfo = NavigationEventInfo.None,
        isBackEnabled = true,
    ) {
        override fun onBackCompleted() {
            onBackRequested()
        }
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = models

    init {
        navigationEventDispatcher.addHandler(rootBackHandler)
    }

    fun resume() {
        if (registry.currentState == Lifecycle.State.INITIALIZED) {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun attachBackInput(dispatcher: android.window.OnBackInvokedDispatcher) {
        if (backInput != null) return
        backInput = OnBackInvokedDefaultInput(dispatcher).also {
            navigationEventDispatcher.addInput(it)
        }
    }

    fun destroy() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        navigationEventDispatcher.dispose()
        backInput = null
        models.clear()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecorderWindowDialog(
    text: ScreenRecorderUiText,
    resolutions: List<Pair<String, String>>,
    sounds: List<Pair<String, Int>>,
    initialResolution: String,
    initialSound: Int,
    recordingSnapshot: RecorderSnapshot?,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
    onStart: (resolution: String, sound: Int, motionPhoto: Boolean) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onBackHandlerChanged: ((() -> Unit)?) -> Unit,
) {
    val safeResolutions = resolutions.ifEmpty { listOf(text.currentSetting to initialResolution) }
    val safeSounds = sounds.ifEmpty {
        listOf(text.soundNone to 0, text.soundMicrophone to 1, text.soundDevice to 2)
    }
    var resolutionIndex by remember(safeResolutions, initialResolution) {
        mutableIntStateOf(safeResolutions.indexOfFirst { it.second == initialResolution }.coerceAtLeast(0))
    }
    var soundIndex by remember(safeSounds, initialSound) {
        mutableIntStateOf(safeSounds.indexOfFirst { it.second == initialSound }.coerceAtLeast(0))
    }
    var motionPhoto by remember { mutableStateOf(false) }
    val panelProgress = remember { Animatable(1f) }
    val animationScope = rememberCoroutineScope()
    var panelHeight by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    var liveRecorderSnapshot by remember(recordingSnapshot) { mutableStateOf(recordingSnapshot) }
    val outsideInteractionSource = remember { MutableInteractionSource() }
    val panelInteractionSource = remember { MutableInteractionSource() }
    var nowElapsed by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    DisposableEffect(recordingSnapshot) {
        val removeObserver = if (recordingSnapshot != null) {
            ScreenRecorderControlClient.observe { liveRecorderSnapshot = it }
        } else {
            null
        }
        onDispose { removeObserver?.invoke() }
    }

    LaunchedEffect(liveRecorderSnapshot?.state) {
        while (liveRecorderSnapshot?.isSessionActive == true) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(250L)
        }
    }

    LaunchedEffect(panelHeight) {
        if (panelHeight > 0 && !closing) {
            panelProgress.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        }
    }

    fun closeAfterAnimation(action: () -> Unit) {
        if (closing) return
        closing = true
        animationScope.launch {
            panelProgress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            action()
        }
    }

    LaunchedEffect(recordingSnapshot, liveRecorderSnapshot?.state) {
        if (recordingSnapshot != null && liveRecorderSnapshot?.state == ScreenRecorderContract.STATE_IDLE) {
            closeAfterAnimation(onCancel)
        }
    }

    DisposableEffect(onCancel, onBackHandlerChanged) {
        onBackHandlerChanged { closeAfterAnimation(onCancel) }
        onDispose { onBackHandlerChanged(null) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = outsideInteractionSource, indication = null) {
                    closeAfterAnimation(onCancel)
                },
        )
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { panelHeight = it.height }
                    .graphicsLayer { translationY = panelHeight * panelProgress.value }
                    .alpha(1f - panelProgress.value)
                    .clickable(interactionSource = panelInteractionSource, indication = null) {},
                colors = CardDefaults.cardColors(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = when {
                            recordingSnapshot == null -> text.dialogTitle
                            liveRecorderSnapshot?.state == ScreenRecorderContract.STATE_STARTING -> text.preparingTitle
                            liveRecorderSnapshot?.state == ScreenRecorderContract.STATE_PAUSED -> text.pausedTitle
                            else -> text.recordingTitle
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    if (recordingSnapshot != null) {
                        val snapshot = liveRecorderSnapshot ?: recordingSnapshot
                        Text(
                            text = text.recordedDuration(formatRecorderDuration(snapshot.durationAt(nowElapsed))),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    when (snapshot.state) {
                                        ScreenRecorderContract.STATE_PAUSED -> onResume()
                                        ScreenRecorderContract.STATE_RECORDING -> onPause()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = snapshot.state != ScreenRecorderContract.STATE_STARTING,
                            ) {
                                Text(
                                    when (snapshot.state) {
                                        ScreenRecorderContract.STATE_STARTING -> text.preparing
                                        ScreenRecorderContract.STATE_PAUSED -> text.resume
                                        else -> text.pause
                                    }
                                )
                            }
                            Button(
                                onClick = { closeAfterAnimation(onStop) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text.stop)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text.motionPhoto, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text.motionPhotoSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = motionPhoto, onCheckedChange = { motionPhoto = it })
                        }
                        if (!isLandscape) {
                            RecorderDropdown(
                                title = text.resolution,
                                items = safeResolutions.map { it.first },
                                selectedIndex = resolutionIndex,
                                onSelected = { resolutionIndex = it },
                            )
                        }
                        RecorderDropdown(
                            title = text.soundSource,
                            items = safeSounds.map { it.first },
                            selectedIndex = soundIndex,
                            onSelected = { soundIndex = it },
                        )
                        if (!isLandscape) {
                            TextButton(
                                onClick = { closeAfterAnimation(onOpenSettings) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(text.moreSettings)
                                    Text(
                                        text.moreSettingsSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = { closeAfterAnimation(onCancel) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text.cancel)
                            }
                            Button(
                                onClick = {
                                    closeAfterAnimation {
                                        onStart(
                                            safeResolutions[resolutionIndex].second,
                                            safeSounds[soundIndex].second,
                                            motionPhoto,
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text.start)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecorderDropdown(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        OutlinedTextField(
            value = items.getOrNull(selectedIndex).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(title) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun formatRecorderDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
}
