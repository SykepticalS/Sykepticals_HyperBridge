package com.d4viddf.hyperbridge.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.d4viddf.hyperbridge.HyperBridgeApplication
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend
import com.d4viddf.hyperbridge.root.RootShellService
import com.d4viddf.hyperbridge.xposed.runtime.EnvironmentHealth
import com.d4viddf.hyperbridge.xposed.runtime.EnvironmentRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupHealthScreen(
    onBack: () -> Unit,
    onNavigateToBugReport: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var health by remember { mutableStateOf(EnvironmentRuntime.snapshot(context)) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            SystemUiIslandBackend.get(context).ping()
            health = EnvironmentRuntime.snapshot(context)
            delay(2_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privileged environment") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
        ) {
            HealthRow("Supported Xiaomi / HyperOS", health.supportedDevice)
            HealthRow("Root", health.rootAvailable)
            HealthRow("LSPosed service / API", health.moduleApiCompatible)
            HealthRow("SystemUI scope", health.systemUiScope)
            HealthRow("XMSF scope", health.xmsfScope)
            HealthRow("SystemUI notification hook", health.notificationIngressReady)
            HealthRow("Island backend", health.islandDispatcherReady)
            HealthRow("XMSF Focus hook configured", health.xmsfHookConfigured)
            HealthRow("Focus backend", health.focusCompatible)
            HealthRow("Backend protocol", health.backendProtocolCompatible)

            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    (context.applicationContext as? HyperBridgeApplication)?.requestRequiredScopes {
                        message = it.fold({ "Scopes granted. Restart them to load hooks." }, { error -> error.message })
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Request required scopes") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        val result = RootShellService.restartPackages(setOf(IslandProtocol.SYSTEM_UI_PACKAGE, IslandProtocol.XMSF_PACKAGE))
                        message = if (result.success) "SystemUI and XMSF restarted." else result.stderr
                    }
                },
                enabled = health.rootAvailable,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restart scopes") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onNavigateToBugReport, modifier = Modifier.fillMaxWidth()) { Text("Report a problem") }
        }
    }
}

@Composable
private fun HealthRow(label: String, ready: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Icon(
            if (ready) Icons.Default.CheckCircle else Icons.Default.Error,
            if (ready) "Ready" else "Unavailable",
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}
