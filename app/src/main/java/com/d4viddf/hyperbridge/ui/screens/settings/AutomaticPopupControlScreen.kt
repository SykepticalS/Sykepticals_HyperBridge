package com.d4viddf.hyperbridge.ui.screens.settings

import android.service.notification.NotificationListenerService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.Dialog
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.data.AppPreferences
import com.d4viddf.hyperbridge.service.NotificationReaderService
import com.d4viddf.hyperbridge.service.popup.CompanionAssociationManager
import com.d4viddf.hyperbridge.service.popup.PopupControlRuntime
import com.d4viddf.hyperbridge.service.popup.PopupSuppressionCapabilityState
import com.d4viddf.hyperbridge.service.popup.PopupOnboardingPolicy
import com.d4viddf.hyperbridge.util.isNotificationServiceEnabled
import kotlinx.coroutines.launch

@Composable
fun PopupControlSetupPanel(
    modifier: Modifier = Modifier,
    onStateChanged: (PopupSuppressionCapabilityState, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context.applicationContext) }
    val associationManager = remember { CompanionAssociationManager(context.applicationContext) }
    val enabled by preferences.popupControlEnabledFlow.collectAsState(initial = false)
    val intentionallyDisabled by preferences.popupControlIntentionallyDisabledFlow.collectAsState(initial = false)
    val apiVerified by PopupControlRuntime.apiVerification.collectAsState()
    val lastError by PopupControlRuntime.lastError.collectAsState()
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }

    fun requestVerification() {
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, NotificationReaderService::class.java)
            )
        }
        runCatching {
            context.startService(
                Intent(context, NotificationReaderService::class.java).apply {
                    action = NotificationReaderService.ACTION_VERIFY_POPUP_CONTROL
                }
            )
        }.onFailure(PopupControlRuntime::failed)
        refresh++
    }

    val associationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        requestVerification()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh++
                if (associationManager.hasAssociation() && isNotificationServiceEnabled(context)) {
                    requestVerification()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    @Suppress("UNUSED_EXPRESSION")
    refresh
    val state = associationManager.currentState(isNotificationServiceEnabled(context))
    LaunchedEffect(state, enabled, apiVerified, lastError) {
        onStateChanged(state, enabled)
    }

    val statusText = when {
        enabled && state == PopupSuppressionCapabilityState.READY -> stringResource(R.string.popup_control_status_ready)
        state == PopupSuppressionCapabilityState.NEEDS_NOTIFICATION_ACCESS -> stringResource(R.string.popup_control_status_notification_access)
        state == PopupSuppressionCapabilityState.NEEDS_ASSOCIATION -> stringResource(R.string.popup_control_status_association)
        state == PopupSuppressionCapabilityState.WAITING_FOR_LISTENER -> stringResource(R.string.popup_control_status_listener)
        state == PopupSuppressionCapabilityState.VERIFYING -> stringResource(R.string.popup_control_status_verifying)
        state == PopupSuppressionCapabilityState.UNSUPPORTED -> stringResource(R.string.popup_control_status_unsupported)
        state == PopupSuppressionCapabilityState.ERROR -> stringResource(R.string.popup_control_status_error)
        else -> stringResource(R.string.popup_control_status_available)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (enabled && state == PopupSuppressionCapabilityState.READY) Icons.Default.CheckCircle
                    else if (state == PopupSuppressionCapabilityState.ERROR) Icons.Default.Error
                    else Icons.Default.Link,
                    contentDescription = null,
                    tint = if (enabled && state == PopupSuppressionCapabilityState.READY) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(6.dp))
                Column {
                    Text(stringResource(R.string.popup_control_title), fontWeight = FontWeight.Bold)
                    Text(statusText, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                stringResource(R.string.popup_control_explanation),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.popup_control_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!enabled && state != PopupSuppressionCapabilityState.UNSUPPORTED &&
                state != PopupSuppressionCapabilityState.ERROR &&
                state != PopupSuppressionCapabilityState.WAITING_FOR_LISTENER
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            preferences.beginPopupControlSetup()
                            when (state) {
                                PopupSuppressionCapabilityState.NEEDS_NOTIFICATION_ACCESS ->
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                PopupSuppressionCapabilityState.NEEDS_ASSOCIATION -> associationManager.requestAssociation(
                                    onPending = { sender ->
                                        associationLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                    },
                                    onCreated = { requestVerification() },
                                    onFailure = { message ->
                                        PopupControlRuntime.failed(IllegalStateException(message?.toString() ?: "Association failed"))
                                        refresh++
                                    }
                                )
                                else -> requestVerification()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Link, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        stringResource(
                            if (intentionallyDisabled) R.string.popup_control_resume
                            else R.string.popup_control_setup
                        )
                    )
                }
            }

            if (state == PopupSuppressionCapabilityState.ERROR ||
                state == PopupSuppressionCapabilityState.WAITING_FOR_LISTENER
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            preferences.beginPopupControlSetup()
                            requestVerification()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.popup_control_repair))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomaticPopupControlScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context.applicationContext) }
    val enabled by preferences.popupControlEnabledFlow.collectAsState(initial = false)
    val intentionallyDisabled by preferences.popupControlIntentionallyDisabledFlow.collectAsState(initial = false)
    val verification by PopupControlRuntime.apiVerification.collectAsState()
    val lastError by PopupControlRuntime.lastError.collectAsState()
    val associationManager = remember { CompanionAssociationManager(context.applicationContext) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.popup_control_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PopupControlSetupPanel()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.popup_control_diagnostics), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(
                            R.string.popup_control_diagnostic_association,
                            associationManager.hasAssociation().toString()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(
                            R.string.popup_control_diagnostic_listener,
                            NotificationReaderService.isConnected.toString()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(
                            R.string.popup_control_diagnostic_probe,
                            lastError ?: verification?.toString() ?: "pending"
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (enabled || !intentionallyDisabled) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.startService(
                            Intent(context, NotificationReaderService::class.java).apply {
                                action = NotificationReaderService.ACTION_RESTORE_POPUP_CONTROL
                            }
                        )
                    }
                ) {
                    Icon(Icons.Default.Restore, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(stringResource(R.string.popup_control_restore_disable))
                }
            }
            Text(
                stringResource(R.string.popup_control_mixed_note),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AutomaticPopupControlOnboardingPage(
    onStateChanged: (PopupSuppressionCapabilityState, Boolean) -> Unit
) {
    Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.popup_control_onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.popup_control_onboarding_desc),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        PopupControlSetupPanel(onStateChanged = onStateChanged)
    }
}

@Composable
fun AutomaticPopupControlUpgradeGate() {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context.applicationContext) }
    val required by preferences.popupControlUpgradeRequiredFlow.collectAsState(initial = false)
    val enabled by preferences.popupControlEnabledFlow.collectAsState(initial = false)
    val intentionallyDisabled by preferences.popupControlIntentionallyDisabledFlow.collectAsState(initial = false)
    val setupComplete by preferences.isSetupComplete.collectAsState(initial = false)
    val apiVerification by PopupControlRuntime.apiVerification.collectAsState()
    val associationManager = remember { CompanionAssociationManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var capability by remember { mutableStateOf(PopupSuppressionCapabilityState.VERIFYING) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    @Suppress("UNUSED_EXPRESSION")
    refresh
    val actualState = associationManager.currentState(isNotificationServiceEnabled(context))
    val shouldBlock = PopupOnboardingPolicy.shouldBlockUpgrade(
        setupComplete = setupComplete,
        upgradeRequired = required,
        enabled = enabled,
        intentionallyDisabled = intentionallyDisabled,
        state = actualState
    )

    if (shouldBlock) {
        Dialog(onDismissRequest = {}) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        stringResource(R.string.popup_control_upgrade_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(stringResource(R.string.popup_control_upgrade_desc))
                    PopupControlSetupPanel { state, _ -> capability = state }
                    if (capability == PopupSuppressionCapabilityState.UNSUPPORTED) {
                        FilledTonalButton(
                            onClick = { scope.launch { preferences.acknowledgePopupControlUnsupported() } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.popup_control_continue_degraded))
                        }
                    }
                }
            }
        }
    }
}
