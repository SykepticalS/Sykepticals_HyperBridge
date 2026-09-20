package com.d4viddf.hyperbridge.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.service.diagnostics.DiagnosticEvent
import com.d4viddf.hyperbridge.service.diagnostics.DiagnosticsStore
import com.d4viddf.hyperbridge.xposed.runtime.EnvironmentRuntime
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HyperBridge diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DiagnosticRow("Root", health.rootAvailable)
            DiagnosticRow("LSPosed service / API", health.moduleApiCompatible)
            DiagnosticRow("SystemUI scope", health.systemUiScope)
            DiagnosticRow("XMSF scope", health.xmsfScope)
            DiagnosticRow("SystemUI notification hook", ingressReady)
            DiagnosticRow("Island backend", dispatcherReady)
            DiagnosticRow("Focus hook", health.focusCompatible && health.xmsfHookConfigured)
            DiagnosticRow("Protocol compatibility", health.backendProtocolCompatible)
            Spacer(Modifier.height(12.dp))
            Text("Selected apps: ${selectedApps.size}")
            Text("Active Islands: ${state.activeIslands}")
            Text("Last classification: ${state.lastClassification ?: "—"}")
            Text("Last call state: ${state.lastCallState ?: "—"}")
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val export = buildDiagnosticExport(state.events)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("HyperBridge diagnostics", export))
                    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy diagnostics") }
            onReportError?.let { report ->
                Spacer(Modifier.height(8.dp))
                Button(onClick = report, modifier = Modifier.fillMaxWidth()) { Text("Report a problem") }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, ready: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
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

private fun buildDiagnosticExport(events: List<DiagnosticEvent>): String =
    (listOf("HyperBridge privileged diagnostics") + events.map { event ->
        val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(event.timestamp))
        listOfNotNull(time, event.classification, event.action, event.packageName, event.reason)
            .joinToString(" · ")
    }).joinToString("\n")
