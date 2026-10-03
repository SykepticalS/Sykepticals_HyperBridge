package com.sykeptical.hyperpop.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.island.backend.HookConfigSync
import com.sykeptical.hyperpop.island.backend.SystemUiEngineCommands
import com.sykeptical.hyperpop.models.IslandConfig
import com.sykeptical.hyperpop.ui.components.IslandControlGroup
import com.sykeptical.hyperpop.ui.components.IslandSettingsControl
import com.sykeptical.hyperpop.ui.components.SectionLabel
import com.sykeptical.hyperpop.ui.components.SettingsCard
import com.sykeptical.hyperpop.ui.components.SettingsRow
import com.sykeptical.hyperpop.ui.components.SettingsStack
import com.sykeptical.hyperpop.ui.components.SettingsToggleCard
import com.sykeptical.hyperpop.ui.components.ShapeStyle
import com.sykeptical.hyperpop.ui.components.getExpressiveShape
import com.sykeptical.hyperpop.ui.system.HpApplyBanner
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpSlider
import com.sykeptical.hyperpop.ui.system.HpSwitch
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.ui.system.IslandDemo
import com.sykeptical.hyperpop.ui.system.IslandDemoKind
import com.sykeptical.hyperpop.ui.theme.HyperPopTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IslandSettingsScreen(
    section: String,
    onBack: () -> Unit,
    onOpenDnd: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }

    val globalConfig by preferences.globalConfigFlow.collectAsState(initial = IslandConfig(
        firstFloat = false,
        isShowShade = false,
        timeout = 10
    ))

    IslandSettingsContent(
        section = section,
        globalConfig = globalConfig,
        onBack = onBack,
        onOpenDnd = onOpenDnd,
        onUpdateConfig = { newConfig ->
            scope.launch { preferences.updateGlobalConfig(newConfig) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslandSettingsContent(
    section: String,
    globalConfig: IslandConfig,
    onBack: () -> Unit,
    onOpenDnd: () -> Unit,
    onUpdateConfig: (IslandConfig) -> Unit,
) {
    val title = when (section) {
        "timing" -> stringResource(R.string.islands_timing)
        "text" -> stringResource(R.string.islands_text)
        "glow" -> stringResource(R.string.islands_glow)
        "scenes" -> stringResource(R.string.islands_scenes)
        "motion" -> stringResource(R.string.islands_motion)
        "experiments" -> stringResource(R.string.advanced_experiments)
        else -> stringResource(R.string.islands_title)
    }
    val groups = when (section) {
        "timing" -> setOf(IslandControlGroup.Timing)
        "text" -> setOf(IslandControlGroup.Text)
        "glow" -> setOf(IslandControlGroup.Glow)
        "scenes" -> setOf(IslandControlGroup.Scenes)
        else -> emptySet()
    }
    HpScaffold(title = title, onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HyperPopSpace.screen, vertical = 8.dp)
        ) {
            if (section == "timing") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IslandDemo(IslandDemoKind.Message)
                }
            }
            if (groups.isNotEmpty()) {
                IslandSettingsControl(
                    config = globalConfig,
                    onUpdate = onUpdateConfig,
                    groups = groups,
                    showForceGlow = false,
                )
            }
            if (section == "scenes") {
                SettingsCard(shape = getExpressiveShape(1, 0, ShapeStyle.Large)) {
                    SettingsRow(
                        icon = Icons.Default.Tune,
                        title = stringResource(R.string.scenes_dnd_link),
                        subtitle = stringResource(R.string.dnd_mode_desc),
                        modifier = Modifier.clickable(onClick = onOpenDnd),
                    )
                }
            }
            if (section == "motion") MotionSection()
            if (section == "experiments") {
                Text(
                    stringResource(R.string.experiments_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ExperimentGlowSection(globalConfig, onUpdateConfig)
                VisualTuningSection(includeSpeed = false)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MotionSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(HookConfigSync.betterAnimationsEnabled(context)) }
    var pending by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(HookConfigSync.marqueeSpeed(context).toFloat()) }

    if (pending) {
        HpApplyBanner(
            text = stringResource(R.string.apply_systemui_hint),
            action = stringResource(R.string.apply_systemui),
            onApply = {
                SystemUiEngineCommands.reload(context)
                pending = false
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
    SectionLabel(stringResource(R.string.islands_motion), first = true)
    SettingsStack {
        SettingsCard(shape = getExpressiveShape(1, 0, ShapeStyle.Large)) {
            SettingsRow(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.better_animations),
                subtitle = stringResource(R.string.better_animations_desc),
                trailing = {
                    HpSwitch(
                        checked = enabled,
                        onCheckedChange = { next ->
                            enabled = next
                            HookConfigSync.setBetterAnimationsEnabled(context, next)
                            pending = true
                        },
                    )
                },
            )
        }
    }
    SectionLabel(stringResource(R.string.islands_text))
    SettingsCard(shape = getExpressiveShape(1, 0, ShapeStyle.Large)) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            SettingsRow(
                icon = Icons.Default.Speed,
                title = "Scrolling speed",
                subtitle = "${speed.toInt()} px/sec"
            )
            HpSlider(
                value = speed,
                onValueChange = { speed = it },
                onValueChangeFinished = {
                    HookConfigSync.setVisualTuning(
                        context,
                        speed.toInt(),
                        HookConfigSync.glowRange(context),
                        HookConfigSync.singleColorGlow(context),
                        HookConfigSync.glowBaseColor(context),
                    )
                },
                valueRange = 20f..500f,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun ExperimentGlowSection(config: IslandConfig, onUpdate: (IslandConfig) -> Unit) {
    SettingsStack {
        SettingsToggleCard(
            title = "Force island glow",
            subtitle = "Keep the collapsed glow visible when Xiaomi would fade it.",
            icon = Icons.Default.LightMode,
            checked = config.forceIslandGlow == true,
            onCheckedChange = { onUpdate(config.copy(forceIslandGlow = it)) },
            shape = getExpressiveShape(2, 0, ShapeStyle.Large),
        )
        SettingsToggleCard(
            title = "Force focus glow",
            subtitle = "Keep the expanded glow visible when Xiaomi would fade it.",
            icon = Icons.Default.LightMode,
            checked = config.forceFocusGlow == true,
            onCheckedChange = { onUpdate(config.copy(forceFocusGlow = it)) },
            shape = getExpressiveShape(2, 1, ShapeStyle.Large),
        )
    }
}

@Composable
private fun VisualTuningSection(includeSpeed: Boolean) {
    val context = LocalContext.current
    var speed by remember { mutableFloatStateOf(HookConfigSync.marqueeSpeed(context).toFloat()) }
    var range by remember { mutableFloatStateOf(HookConfigSync.glowRange(context).toFloat()) }
    var single by remember { mutableStateOf(HookConfigSync.singleColorGlow(context)) }
    var baseColor by remember { mutableStateOf(HookConfigSync.glowBaseColor(context)) }

    fun persist(
        nextSpeed: Int = speed.toInt(),
        nextRange: Int = range.toInt(),
        nextSingle: Boolean = single,
        nextColor: String = baseColor,
    ) {
        HookConfigSync.setVisualTuning(context, nextSpeed, nextRange, nextSingle, nextColor)
    }

    SectionLabel("Visual Tuning")
    val count = if (includeSpeed) 4 else 3
    SettingsStack {
        if (includeSpeed) {
        SettingsCard(shape = getExpressiveShape(count, 0, ShapeStyle.Large)) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                SettingsRow(
                    icon = Icons.Default.Speed,
                    title = "Scrolling speed",
                    subtitle = "${speed.toInt()} px/sec"
                )
                HpSlider(
                    value = speed,
                    onValueChange = { speed = it },
                    onValueChangeFinished = { persist() },
                    valueRange = 20f..500f,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        }
        SettingsCard(shape = getExpressiveShape(count, if (includeSpeed) 1 else 0, ShapeStyle.Large)) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                SettingsRow(
                    icon = Icons.Default.Tune,
                    title = "Glow range",
                    subtitle = "${range.toInt()}%"
                )
                HpSlider(
                    value = range,
                    onValueChange = { range = it },
                    onValueChangeFinished = { persist() },
                    valueRange = 0f..100f,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        SettingsCard(shape = getExpressiveShape(count, if (includeSpeed) 2 else 1, ShapeStyle.Large)) {
            SettingsRow(
                icon = Icons.Default.Palette,
                title = "Single-color glow",
                subtitle = "Use one color instead of a dynamic glow.",
                trailing = {
                    HpSwitch(
                        checked = single,
                        onCheckedChange = {
                            single = it
                            persist(nextSingle = it)
                        }
                    )
                }
            )
        }
        SettingsCard(shape = getExpressiveShape(count, if (includeSpeed) 3 else 2, ShapeStyle.Large)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = baseColor,
                    onValueChange = { value ->
                        baseColor = value
                    },
                    label = { Text("Glow base color") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                DebouncedStringCommit(baseColor) { persist(nextColor = it) }
            }
        }
    }
}

@Composable
private fun DebouncedStringCommit(value: String, onIdle: (String) -> Unit) {
    LaunchedEffect(value) {
        delay(350)
        onIdle(value)
    }
}

@Preview(showBackground = true)
@Composable
fun IslandSettingsScreenPreview() {
    HyperPopTheme {
        IslandSettingsContent(
            section = "timing",
            globalConfig = IslandConfig(firstFloat = true, isShowShade = true, timeout = 5, floatTimeout = 6),
            onBack = {},
            onOpenDnd = {},
            onUpdateConfig = {}
        )
    }
}
