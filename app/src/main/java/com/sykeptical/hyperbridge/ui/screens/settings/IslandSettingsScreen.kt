package com.sykeptical.hyperbridge.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperbridge.R
import com.sykeptical.hyperbridge.data.AppPreferences
import com.sykeptical.hyperbridge.models.IslandConfig
import com.sykeptical.hyperbridge.ui.components.IslandSettingsControl
import com.sykeptical.hyperbridge.ui.components.formatSeconds
import com.sykeptical.hyperbridge.ui.components.timeoutSteps
import com.sykeptical.hyperbridge.ui.theme.HyperBridgeTheme
import kotlinx.coroutines.launch

@Composable
fun IslandSettingsScreen(
    onBack: () -> Unit,
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }

    val globalConfig by preferences.globalConfigFlow.collectAsState(initial = IslandConfig(
        isFloat = false,
        isShowShade = false,
        timeout = 10
    ))
    val screenRecordingTimeout by preferences.screenRecordingTimeoutFlow.collectAsState(initial = 4)

    IslandSettingsContent(
        globalConfig = globalConfig,
        onBack = onBack,
        onUpdateConfig = { newConfig ->
            scope.launch { preferences.updateGlobalConfig(newConfig) }
        },
        screenRecordingTimeout = screenRecordingTimeout,
        onScreenRecordingTimeoutChange = { scope.launch { preferences.setScreenRecordingTimeout(it) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslandSettingsContent(
    globalConfig: IslandConfig,
    onBack: () -> Unit,
    onUpdateConfig: (IslandConfig) -> Unit,
    screenRecordingTimeout: Int,
    onScreenRecordingTimeoutChange: (Int) -> Unit
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
                .padding(16.dp)
        ) {
            IslandSettingsControl(
                config = globalConfig,
                onUpdate = onUpdateConfig
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.system_island_expiration),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            SystemIslandTimeoutCard(
                title = stringResource(R.string.screen_recording_title),
                timeout = screenRecordingTimeout,
                icon = { Icon(Icons.Outlined.Videocam, null) },
                onTimeoutChange = onScreenRecordingTimeoutChange
            )
        }
    }
}

@Composable
private fun SystemIslandTimeoutCard(
    title: String,
    timeout: Int,
    icon: @Composable () -> Unit,
    onTimeoutChange: (Int) -> Unit
) {
    val index = timeoutSteps.indexOf(timeout).let { if (it >= 0) it else timeoutSteps.indexOf(4) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Column(Modifier.padding(start = 20.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.system_island_expiration_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                formatSeconds(timeout),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = index.toFloat(),
                onValueChange = { onTimeoutChange(timeoutSteps[it.toInt()]) },
                valueRange = 0f..(timeoutSteps.size - 1).toFloat(),
                steps = timeoutSteps.size - 2
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IslandSettingsScreenPreview() {
    HyperBridgeTheme {
        IslandSettingsContent(
            globalConfig = IslandConfig(isFloat = true, isShowShade = true, timeout = 5, floatTimeout = 6),
            onBack = {},
            onUpdateConfig = {},
            screenRecordingTimeout = 4,
            onScreenRecordingTimeoutChange = {}
        )
    }
}
