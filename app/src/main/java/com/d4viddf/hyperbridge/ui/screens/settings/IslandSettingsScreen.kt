package com.d4viddf.hyperbridge.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.island.backend.HookConfigSync
import com.d4viddf.hyperbridge.models.IslandConfig
import com.d4viddf.hyperbridge.ui.components.IslandSettingsControl
import com.d4viddf.hyperbridge.ui.components.SectionLabel
import com.d4viddf.hyperbridge.ui.components.SettingsCard
import com.d4viddf.hyperbridge.ui.components.SettingsRow
import com.d4viddf.hyperbridge.ui.components.SettingsStack
import com.d4viddf.hyperbridge.ui.screens.theme.ShapeStyle
import com.d4viddf.hyperbridge.ui.screens.theme.getExpressiveShape
import com.d4viddf.hyperbridge.ui.theme.HyperBridgeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IslandSettingsScreen(
    onBack: () -> Unit,
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
        globalConfig = globalConfig,
        onBack = onBack,
        onUpdateConfig = { newConfig ->
            scope.launch { preferences.updateGlobalConfig(newConfig) }
        },
        visualTuning = { VisualTuningSection() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslandSettingsContent(
    globalConfig: IslandConfig,
    onBack: () -> Unit,
    onUpdateConfig: (IslandConfig) -> Unit,
    visualTuning: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.global_settings)) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            IslandSettingsControl(
                config = globalConfig,
                onUpdate = onUpdateConfig
            )
            visualTuning()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VisualTuningSection() {
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
    SettingsStack {
        SettingsCard(shape = getExpressiveShape(4, 0, ShapeStyle.Large)) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                SettingsRow(
                    icon = Icons.Default.Speed,
                    title = "Scrolling speed",
                    subtitle = "${speed.toInt()} px/sec"
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    onValueChangeFinished = { persist() },
                    valueRange = 20f..500f,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        SettingsCard(shape = getExpressiveShape(4, 1, ShapeStyle.Large)) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                SettingsRow(
                    icon = Icons.Default.Tune,
                    title = "Glow range",
                    subtitle = "${range.toInt()}%"
                )
                Slider(
                    value = range,
                    onValueChange = { range = it },
                    onValueChangeFinished = { persist() },
                    valueRange = 0f..100f,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        SettingsCard(shape = getExpressiveShape(4, 2, ShapeStyle.Large)) {
            SettingsRow(
                icon = Icons.Default.Palette,
                title = "Single-color glow",
                subtitle = "Use one color instead of a dynamic glow.",
                trailing = {
                    Switch(
                        checked = single,
                        onCheckedChange = {
                            single = it
                            persist(nextSingle = it)
                        }
                    )
                }
            )
        }
        SettingsCard(shape = getExpressiveShape(4, 3, ShapeStyle.Large)) {
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
    HyperBridgeTheme {
        IslandSettingsContent(
            globalConfig = IslandConfig(firstFloat = true, isShowShade = true, timeout = 5, floatTimeout = 6),
            onBack = {},
            onUpdateConfig = {}
        )
    }
}
