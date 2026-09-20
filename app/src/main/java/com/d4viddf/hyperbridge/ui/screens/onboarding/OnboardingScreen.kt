package com.d4viddf.hyperbridge.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend
import com.d4viddf.hyperbridge.root.RootShellService
import com.d4viddf.hyperbridge.util.DeviceUtils
import com.d4viddf.hyperbridge.xposed.runtime.ModuleServiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val module by ModuleServiceState.state.collectAsState()
    val backend = remember { SystemUiIslandBackend.get(context) }
    var rootReady by remember { mutableStateOf<Boolean?>(null) }
    var backendHealth by remember { mutableStateOf(backend.health()) }
    var detail by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        rootReady = RootShellService.isAvailable()
        while (true) {
            backend.ping()
            delay(1_000)
            backendHealth = backend.health()
            delay(2_000)
        }
    }

    val scopesReady = IslandProtocol.SYSTEM_UI_PACKAGE in module.scopes && IslandProtocol.XMSF_PACKAGE in module.scopes
    val environmentReady = DeviceUtils.isXiaomi && DeviceUtils.isCompatibleOS()
    val allReady = environmentReady && rootReady == true && module.available && module.apiVersion >= 101 &&
        scopesReady && backendHealth.available

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("HyperBridge privileged setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Root and modern LSPosed are required. Enable the SystemUI and XMSF scopes; no Android notification or overlay permission is used.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        StatusRow("Supported Xiaomi / HyperOS", environmentReady)
        StatusRow("Root access", rootReady == true, rootReady == null)
        StatusRow("LSPosed service (API ${module.apiVersion.takeIf { it > 0 } ?: "—"})", module.available && module.apiVersion >= 101)
        StatusRow("SystemUI scope", IslandProtocol.SYSTEM_UI_PACKAGE in module.scopes)
        StatusRow("XMSF scope", IslandProtocol.XMSF_PACKAGE in module.scopes)
        StatusRow("SystemUI notification hook", backendHealth.capabilities and IslandProtocol.CAP_NOTIFICATION_INGRESS != 0)
        StatusRow("Island backend", backendHealth.systemUiHookAlive)
        StatusRow("XMSF Focus authorization hook", backendHealth.xmsfHookAlive)
        StatusRow("Xiaomi Focus whitelist hook", backendHealth.capabilities and IslandProtocol.CAP_FOCUS_BYPASS != 0)
        StatusRow("Backend protocol", IslandProtocol.compatible(backendHealth.protocolVersion ?: -1))

        detail?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        if (module.available && !scopesReady) {
            OutlinedButton(
                onClick = {
                    (context.applicationContext as? HyperBridgeApplication)?.requestRequiredScopes { result ->
                        detail = result.exceptionOrNull()?.message
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Request required scopes") }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val result = RootShellService.restartPackages(setOf(IslandProtocol.SYSTEM_UI_PACKAGE, IslandProtocol.XMSF_PACKAGE))
                    detail = if (result.success) "Scopes restarted; waiting for hook handshake." else result.stderr
                    backend.ping()
                }
            },
            enabled = rootReady == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Restart scopes") }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onFinish, enabled = allReady, modifier = Modifier.fillMaxWidth()) {
            Text(if (allReady) "Continue" else "Complete setup above")
        }
    }
}

@Composable
private fun StatusRow(label: String, ready: Boolean, pending: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label)
            Icon(
                if (ready) Icons.Default.CheckCircle else Icons.Default.Error,
                if (pending) "Checking" else if (ready) "Ready" else "Unavailable",
                tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}
