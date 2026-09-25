package com.d4viddf.hyperbridge.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.d4viddf.hyperbridge.HyperBridgeApplication
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.root.RootShellService
import com.d4viddf.hyperbridge.xposed.mediacard.MediaCardConstants as C
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class Choice(val value: Int, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCardSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(IslandProtocol.REMOTE_PREFS, Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRestart by remember { mutableStateOf(false) }
    var section by remember { mutableIntStateOf(0) }

    fun sync() = (context.applicationContext as? HyperBridgeApplication)?.syncHookConfig()
    fun saveBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        sync()
    }
    fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        sync()
    }

    var shadeLayout by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE)
    var shadeTheme by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME)
    var shadeAmbient by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE)
    var shadeBackground by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE)
    var shadeBlur by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR)
    var shadeAnimate by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION)
    var shadeInvert by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT)
    var shadeTone by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE)
    var shadeCover by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_COVER_STYLE)
    var shadeHideSource by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE)
    var shadeHideShadow by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW)
    var shadeDisableFlip by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP)
    var shadeHideDevice by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH)
    var shadeHideTime by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_TIME, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_TIME)
    var shadeHideCustom by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS)
    var shadeProgress by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE)
    var shadeGlow by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW)
    var shadeThumb by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE)
    var shadeAlignLeft by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT)
    var shadeActionOrder by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER)
    var switcherEnabled by prefs.rememberBool(C.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED)
    var switcherMode by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE)
    var switcherMax by prefs.rememberInt(C.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT, C.DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT)
    var keepAodExpanded by prefs.rememberBool(C.KEY_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING, C.DEFAULT_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING)

    val preview = MediaCardPreviewModel(
            showShadow = !shadeHideShadow,
            coverStyle = shadeCover,
            hideCoverSource = shadeHideSource,
            disableCoverFlip = shadeDisableFlip,
            hideDeviceSwitch = shadeHideDevice,
            hideCustomActions = shadeHideCustom,
            hideTime = shadeHideTime,
            actionOrder = shadeActionOrder,
            actionAlignLeft = shadeAlignLeft,
            cardTheme = shadeTheme,
            backgroundStyle = shadeBackground,
            backgroundBlur = shadeBlur,
            softCoverTone = shadeTone,
            ambientFlowMode = if (shadeBackground == 0) shadeAmbient else 0,
            waveProgress = shadeProgress == C.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE,
            verticalProgressThumb = shadeThumb == C.NOTIFICATION_MEDIA_THUMB_STYLE_VERTICAL,
            hideProgressThumb = shadeThumb == C.NOTIFICATION_MEDIA_THUMB_STYLE_HIDDEN,
        )

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Restart SystemUI?") },
            text = { Text("The status bar will reload so media card changes take effect. The screen goes black for a moment.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    scope.launch {
                        val result = RootShellService.restartPackages(setOf(IslandProtocol.SYSTEM_UI_PACKAGE))
                        if (!result.success) {
                            snackbarHostState.showSnackbar(result.stderr.ifBlank { "Couldn't restart SystemUI" })
                        }
                    }
                }) { Text("Restart") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Media cards") },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    FilledTonalIconButton(onClick = { confirmRestart = true }) {
                        Icon(Icons.Rounded.RestartAlt, "Restart SystemUI")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = section == 0,
                    onClick = { section = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Notification") }
                SegmentedButton(
                    selected = section == 1,
                    onClick = { section = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Island") }
            }
            Spacer(Modifier.height(12.dp))
            if (section == 1) {
                IslandExpandedMediaSettings()
                Spacer(Modifier.height(24.dp))
                return@Column
            }
            MediaCardPreview(preview)
            Text(
                "Options that do not apply to the current style stay hidden. Use the restart icon after you are done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
            )

            SectionTitle("Notification shade")
            ChoicePref("Layout style", shadeLayout, layoutChoices) { shadeLayout = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE, it) }
            BackgroundControls(
                background = shadeBackground,
                onBackground = { shadeBackground = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE, it) },
                theme = shadeTheme,
                onTheme = { shadeTheme = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_CARD_THEME, it) },
                ambient = shadeAmbient,
                ambientChoices = notificationAmbientChoices,
                onAmbient = { shadeAmbient = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE, it) },
                tone = shadeTone,
                onTone = { shadeTone = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE, it) },
                animate = shadeAnimate,
                onAnimate = { shadeAnimate = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION, it) },
                blur = shadeBlur,
                onBlur = { shadeBlur = it },
                onBlurCommit = { shadeBlur = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR, it) },
                invert = shadeInvert,
                onInvert = { shadeInvert = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT, it) },
            )
            ChoicePref("Album cover", shadeCover, coverChoices) { shadeCover = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_COVER_STYLE, it) }
            Show(shadeCover != C.NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN) {
                TogglePref("Hide cover source icon", shadeHideSource) { shadeHideSource = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE, it) }
                TogglePref("Hide cover shadow", shadeHideShadow) { shadeHideShadow = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW, it) }
                TogglePref("Disable cover flip animation", shadeDisableFlip) { shadeDisableFlip = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP, it) }
            }
            TogglePref("Hide output-device switch", shadeHideDevice) { shadeHideDevice = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH, it) }
            TogglePref("Hide progress time", shadeHideTime) { shadeHideTime = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_TIME, it) }
            TogglePref("Hide custom action buttons", shadeHideCustom) { shadeHideCustom = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS, it) }
            ChoicePref("Progress style", shadeProgress, progressChoices) { shadeProgress = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE, it) }
            Show(shadeProgress == C.NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT) {
                TogglePref("Progress-tail glow", shadeGlow) { shadeGlow = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW, it) }
            }
            Show(shadeProgress == C.NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE) {
                ChoicePref("Seek thumb", shadeThumb, thumbChoices) { shadeThumb = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE, it) }
            }
            TogglePref("Align action buttons left", shadeAlignLeft) { shadeAlignLeft = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT, it) }
            ChoicePref("Action-button order", shadeActionOrder, actionOrderChoices) { shadeActionOrder = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER, it) }

            SectionTitle("Notification card switching")
            TogglePref("Enable multi-media card switching", switcherEnabled) { switcherEnabled = it; saveBool(C.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED, it) }
            Show(switcherEnabled) {
                ChoicePref("Card display mode", switcherMode, switcherChoices) { switcherMode = it; saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE, it) }
                SliderPref(
                    "Maximum cards",
                    switcherMax,
                    C.MIN_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
                    C.MAX_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT,
                    onCommit = {
                        switcherMax = it
                        saveInt(C.KEY_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT, it)
                    },
                )
            }
            TogglePref("Keep media card expanded in full AOD", keepAodExpanded) { keepAodExpanded = it; saveBool(C.KEY_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING, it) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun IslandExpandedMediaSettings() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(IslandProtocol.REMOTE_PREFS, Context.MODE_PRIVATE)
    }
    fun sync() = (context.applicationContext as? HyperBridgeApplication)?.syncHookConfig()
    fun saveBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        sync()
    }
    fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        sync()
    }

    var layout by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE)
    var theme by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME)
    var ambient by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE)
    var background by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE)
    var blur by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR)
    var animate by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION)
    var invert by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT)
    var tone by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE)
    var cover by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE)
    var hideSource by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE)
    var disableFlip by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP)
    var hideDevice by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH)
    var hideTime by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME)
    var hideCustom by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS)
    var progress by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE)
    var glow by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW)
    var thumb by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE)
    var alignLeft by prefs.rememberBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT)
    var actionOrder by prefs.rememberInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER, C.DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER)

    val ambientPreview = when (ambient) {
        C.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT -> C.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DYNAMIC
        C.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR -> C.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR
        C.ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL -> C.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL
        else -> C.NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
    }
    MediaCardPreview(
        MediaCardPreviewModel(
            showShadow = false,
            coverStyle = cover,
            hideCoverSource = hideSource,
            disableCoverFlip = disableFlip,
            hideDeviceSwitch = hideDevice,
            hideCustomActions = hideCustom,
            hideTime = hideTime,
            actionOrder = actionOrder,
            actionAlignLeft = alignLeft,
            cardTheme = theme,
            backgroundStyle = background,
            backgroundBlur = blur,
            softCoverTone = tone,
            ambientFlowMode = if (background == 0) ambientPreview else 0,
            waveProgress = progress == C.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE,
            verticalProgressThumb = thumb == C.ISLAND_EXPANDED_MEDIA_THUMB_STYLE_VERTICAL,
            hideProgressThumb = thumb == C.ISLAND_EXPANDED_MEDIA_THUMB_STYLE_HIDDEN,
        )
    )
    SectionTitle("Island expanded player")
    ChoicePref("Layout style", layout, layoutChoices) { layout = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE, it) }
    BackgroundControls(
        background = background,
        onBackground = { background = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE, it) },
        theme = theme,
        onTheme = { theme = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME, it) },
        ambient = ambient,
        ambientChoices = islandAmbientChoices,
        onAmbient = { ambient = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE, it) },
        tone = tone,
        onTone = { tone = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE, it) },
        animate = animate,
        onAnimate = { animate = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION, it) },
        blur = blur,
        onBlur = { blur = it },
        onBlurCommit = { blur = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR, it) },
        invert = invert,
        onInvert = { invert = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT, it) },
    )
    ChoicePref("Album cover", cover, coverChoices) { cover = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE, it) }
    Show(cover != C.ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN) {
        TogglePref("Hide cover source icon", hideSource) { hideSource = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE, it) }
        TogglePref("Disable cover flip animation", disableFlip) { disableFlip = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP, it) }
    }
    TogglePref("Hide output-device switch", hideDevice) { hideDevice = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH, it) }
    TogglePref("Hide progress time", hideTime) { hideTime = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME, it) }
    TogglePref("Hide custom action buttons", hideCustom) { hideCustom = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS, it) }
    ChoicePref("Progress style", progress, progressChoices) { progress = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE, it) }
    Show(progress == C.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_DEFAULT) {
        TogglePref("Progress-tail glow", glow) { glow = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW, it) }
    }
    Show(progress == C.ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE) {
        ChoicePref("Seek thumb", thumb, thumbChoices) { thumb = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE, it) }
    }
    TogglePref("Align action buttons left", alignLeft) { alignLeft = it; saveBool(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT, it) }
    ChoicePref("Action-button order", actionOrder, actionOrderChoices) { actionOrder = it; saveInt(C.KEY_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER, it) }
}

@Composable
private fun BackgroundControls(
    background: Int,
    onBackground: (Int) -> Unit,
    theme: Int,
    onTheme: (Int) -> Unit,
    ambient: Int,
    ambientChoices: List<Choice>,
    onAmbient: (Int) -> Unit,
    tone: Int,
    onTone: (Int) -> Unit,
    animate: Boolean,
    onAnimate: (Boolean) -> Unit,
    blur: Int,
    onBlur: (Int) -> Unit,
    onBlurCommit: (Int) -> Unit,
    invert: Boolean,
    onInvert: (Boolean) -> Unit,
) {
    val custom = background != 0
    ChoicePref("Background style", background, backgroundChoices, onBackground)
    Show(!custom) {
        ChoicePref("Card theme", theme, themeChoices, onTheme)
        ChoicePref("Ambient flow", ambient, ambientChoices, onAmbient)
    }
    Show(custom) {
        Show(background == C.NOTIFICATION_MEDIA_BACKGROUND_STYLE_SOFT_COVER) {
            ChoicePref("Soft-cover tone", tone, toneChoices, onTone)
        }
        TogglePref("Animate background transitions", animate, onAnimate)
        Show(background == C.NOTIFICATION_MEDIA_BACKGROUND_STYLE_BLURRED_COVER) {
            SliderPref("Background blur", blur, 1, 20, onBlur, onBlurCommit)
        }
        Show(background == C.NOTIFICATION_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT) {
            TogglePref("Auto-invert bright artwork", invert, onInvert)
        }
    }
}

@Composable
private fun Show(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        content = { content() },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp))
}

@Composable
private fun TogglePref(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    PreferenceCard(Modifier.clickable { onChange(!checked) }) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoicePref(title: String, value: Int, choices: List<Choice>, onChange: (Int) -> Unit) {
    val current = choices.firstOrNull { it.value == value } ?: choices.first()
    PreferenceCard(Modifier.clickable {
        val index = choices.indexOfFirst { it.value == value }.coerceAtLeast(0)
        onChange(choices[(index + 1) % choices.size].value)
    }) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(current.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SliderPref(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onPreview: (Int) -> Unit = {},
    onCommit: (Int) -> Unit,
) {
    var slider by remember { mutableFloatStateOf(value.toFloat()) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("$title: ${slider.roundToInt()}")
            Slider(
                value = slider,
                onValueChange = {
                    slider = it
                    onPreview(it.roundToInt())
                },
                onValueChangeFinished = { onCommit(slider.roundToInt()) },
                valueRange = min.toFloat()..max.toFloat(),
                steps = (max - min - 1).coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun PreferenceCard(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).then(modifier)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun SharedPreferences.rememberInt(key: String, default: Int): MutableIntState =
    remember(key) { mutableIntStateOf(getInt(key, default)) }

@Composable
private fun SharedPreferences.rememberBool(key: String, default: Boolean) =
    remember(key) { mutableStateOf(getBoolean(key, default)) }

private val layoutChoices = listOf(Choice(0, "System default"), Choice(1, "iOS"), Choice(2, "ColorOS"), Choice(3, "One UI"), Choice(4, "MIUI"), Choice(5, "PixelOS"))
private val themeChoices = listOf(Choice(0, "Follow system"), Choice(1, "Always light"), Choice(2, "Always dark"))
private val notificationAmbientChoices = listOf(Choice(0, "Disabled"), Choice(1, "Native ambient flow"), Choice(2, "Cover color"), Choice(3, "Full cover flow"))
private val islandAmbientChoices = listOf(Choice(0, "System default"), Choice(1, "Disabled"), Choice(2, "Cover color"), Choice(3, "Full cover flow"))
private val backgroundChoices = listOf(Choice(0, "Default"), Choice(1, "Cover collage"), Choice(2, "Blurred cover"), Choice(3, "Radial gradient"), Choice(4, "Linear gradient"), Choice(5, "Soft cover"))
private val toneChoices = listOf(Choice(0, "Light"), Choice(1, "Dark"), Choice(2, "Follow system"))
private val coverChoices = listOf(Choice(0, "Default"), Choice(1, "Circle"), Choice(2, "Rotating circle"), Choice(3, "Hidden"))
private val progressChoices = listOf(Choice(0, "Default"), Choice(1, "Wave"))
private val thumbChoices = listOf(Choice(0, "Default"), Choice(1, "Vertical"), Choice(2, "Hidden"))
private val actionOrderChoices = listOf(Choice(0, "Default"), Choice(1, "Custom button at far right"), Choice(2, "Play button at far left"))
private val switcherChoices = listOf(Choice(0, "Single-card view"), Choice(1, "Multi-card view"))
