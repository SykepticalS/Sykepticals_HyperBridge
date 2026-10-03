package com.sykeptical.hyperpop.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.service.diagnostics.DiagnosticEvent
import com.sykeptical.hyperpop.service.diagnostics.DiagnosticsStore
import com.sykeptical.hyperpop.ui.system.HpButton
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpStatusRow
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.xposed.runtime.EnvironmentRuntime
import java.text.DateFormat
import java.util.Date

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    onReportError: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context.applicationContext) }
    val selectedApps by preferences.allowedPackagesFlow.collectAsState(initial = emptySet())
    val state by DiagnosticsStore.state.collectAsState()
    val health = EnvironmentRuntime.snapshot(context)
    val ingressReady = health.notificationIngressReady
    val dispatcherReady = health.islandDispatcherReady && health.systemUiHookAlive

    HpScaffold(title = "HyperPop diagnostics", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HyperPopSpace.screen)
                .verticalScroll(rememberScrollState()),
        ) {
            HpGroup {
                HpStatusRow("Root", if (health.rootAvailable) "Ready" else "Unavailable", ok = health.rootAvailable)
                HpStatusRow("LSPosed service / API", if (health.moduleApiCompatible) "Ready" else "Unavailable", ok = health.moduleApiCompatible)
                HpStatusRow("SystemUI scope", if (health.systemUiScope) "Ready" else "Unavailable", ok = health.systemUiScope)
                HpStatusRow("XMSF scope", if (health.xmsfScope) "Ready" else "Unavailable", ok = health.xmsfScope)
                HpStatusRow("SystemUI notification hook", if (ingressReady) "Ready" else "Unavailable", ok = ingressReady)
                HpStatusRow("Island backend", if (dispatcherReady) "Ready" else "Unavailable", ok = dispatcherReady)
                HpStatusRow(
                    "Focus hook",
                    if (health.focusCompatible && health.xmsfHookConfigured) "Ready" else "Unavailable",
                    ok = health.focusCompatible && health.xmsfHookConfigured,
                )
                HpStatusRow(
                    "Protocol compatibility",
                    if (health.backendProtocolCompatible) "Ready" else "Unavailable",
                    ok = health.backendProtocolCompatible,
                )
            }
            Spacer(Modifier.height(HyperPopSpace.groupGap))
            Text(
                "Selected apps: ${selectedApps.size}",
                style = HyperPopType.body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Text(
                "Active Islands: ${state.activeIslands}",
                style = HyperPopType.body,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Text(
                "Last classification: ${state.lastClassification ?: "—"}",
                style = HyperPopType.secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Text(
                "Last call state: ${state.lastCallState ?: "—"}",
                style = HyperPopType.secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
            Spacer(Modifier.height(HyperPopSpace.groupGap))
            HpButton(
                text = "Copy diagnostics",
                onClick = {
                    val export = buildDiagnosticExport(state.events)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("HyperPop diagnostics", export))
                    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                },
            )
            onReportError?.let { report ->
                Spacer(Modifier.height(8.dp))
                HpButton(text = "Report a problem", onClick = report)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun buildDiagnosticExport(events: List<DiagnosticEvent>): String =
    (listOf("HyperPop privileged diagnostics") + events.map { event ->
        val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(event.timestamp))
        listOfNotNull(time, event.classification, event.action, event.packageName, event.reason)
            .joinToString(" · ")
    }).joinToString("\n")
