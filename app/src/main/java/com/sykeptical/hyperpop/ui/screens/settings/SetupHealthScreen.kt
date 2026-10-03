package com.sykeptical.hyperpop.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.HyperPopApplication
import com.sykeptical.hyperpop.island.backend.IslandProtocol
import com.sykeptical.hyperpop.island.backend.SystemUiIslandBackend
import com.sykeptical.hyperpop.root.RootShellService
import com.sykeptical.hyperpop.ui.system.HpButton
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpStatusRow
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.xposed.runtime.EnvironmentRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    HpScaffold(title = "Privileged environment", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HyperPopSpace.screen)
                .verticalScroll(rememberScrollState()),
        ) {
            HpGroup {
                HpStatusRow("Supported Xiaomi / HyperOS", if (health.supportedDevice) "Ready" else "Unavailable", ok = health.supportedDevice)
                HpStatusRow("Root", if (health.rootAvailable) "Ready" else "Unavailable", ok = health.rootAvailable)
                HpStatusRow("LSPosed service / API", if (health.moduleApiCompatible) "Ready" else "Unavailable", ok = health.moduleApiCompatible)
                HpStatusRow("SystemUI scope", if (health.systemUiScope) "Ready" else "Unavailable", ok = health.systemUiScope)
                HpStatusRow("XMSF scope", if (health.xmsfScope) "Ready" else "Unavailable", ok = health.xmsfScope)
                HpStatusRow("SystemUI notification hook", if (health.notificationIngressReady) "Ready" else "Unavailable", ok = health.notificationIngressReady)
                HpStatusRow("Island backend", if (health.islandDispatcherReady) "Ready" else "Unavailable", ok = health.islandDispatcherReady)
                HpStatusRow("XMSF Focus hook configured", if (health.xmsfHookConfigured) "Ready" else "Unavailable", ok = health.xmsfHookConfigured)
                HpStatusRow("Focus backend", if (health.focusCompatible) "Ready" else "Unavailable", ok = health.focusCompatible)
                HpStatusRow("Backend protocol", if (health.backendProtocolCompatible) "Ready" else "Unavailable", ok = health.backendProtocolCompatible)
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = HyperPopType.secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
            }
            Spacer(Modifier.height(HyperPopSpace.groupGap))
            HpButton(
                text = "Request required scopes",
                onClick = {
                    (context.applicationContext as? HyperPopApplication)?.requestRequiredScopes {
                        message = it.fold({ "Scopes granted. Restart them to load hooks." }, { error -> error.message })
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            HpButton(
                text = "Restart scopes",
                enabled = health.rootAvailable,
                onClick = {
                    scope.launch {
                        val result = RootShellService.restartPackages(setOf(IslandProtocol.SYSTEM_UI_PACKAGE, IslandProtocol.XMSF_PACKAGE))
                        message = if (result.success) "SystemUI and XMSF restarted." else result.stderr
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            HpButton(text = "Report a problem", onClick = onNavigateToBugReport)
            Spacer(Modifier.height(24.dp))
        }
    }
}
