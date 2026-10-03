package com.sykeptical.hyperpop.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.model.HyperPopBackup
import com.sykeptical.hyperpop.ui.components.ExpressiveGroupCard
import com.sykeptical.hyperpop.ui.components.ExpressiveSectionTitle
import com.sykeptical.hyperpop.ui.system.HpButton
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.util.BackupManager
import com.sykeptical.hyperpop.util.BackupSelection
import kotlinx.coroutines.launch

@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    backupManager: BackupManager,
    onBackupFileLoaded: (HyperPopBackup) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Export Selection State
    var exportSelection by remember { mutableStateOf(BackupSelection()) }
    var isProcessing by remember { mutableStateOf(false) }

    // --- LAUNCHERS ---

    // EXPORT: Changed MIME type to 'application/octet-stream' to prevent .json suffix
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val result = backupManager.performExport(uri, exportSelection)
                isProcessing = false
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar(context.getString(R.string.export_success))
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.export_failed, result.exceptionOrNull()?.message))
                }
            }
        }
    }

    // IMPORT: Open File (Allows .hbr or any file)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                val result = backupManager.readBackupFile(uri)
                isProcessing = false

                result.fold(
                    onSuccess = { backup ->
                        onBackupFileLoaded(backup)
                    },
                    onFailure = { error ->
                        snackbarHostState.showSnackbar(context.getString(R.string.import_failed, error.message))
                    }
                )
            }
        }
    }

    HpScaffold(title = stringResource(R.string.backup_restore_title), onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HyperPopSpace.screen)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(HyperPopSpace.rowHorizontal),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.backup_options_subtitle),
                        style = HyperPopType.body,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            ExpressiveSectionTitle(stringResource(R.string.backup_section_title))

            ExpressiveGroupCard {
                BackupOptionItem(
                    title = stringResource(R.string.option_app_settings),
                    subtitle = stringResource(R.string.option_app_settings_desc),
                    icon = Icons.Default.Settings,
                    checked = exportSelection.includeSettings,
                    onCheckedChange = { exportSelection = exportSelection.copy(includeSettings = it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                BackupOptionItem(
                    title = stringResource(R.string.option_blocklist),
                    subtitle = stringResource(R.string.option_blocklist_desc),
                    icon = Icons.Default.CheckCircle,
                    checked = exportSelection.includeBlocklist,
                    onCheckedChange = { exportSelection = exportSelection.copy(includeBlocklist = it) }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                BackupOptionItem(
                    title = stringResource(R.string.option_priorities),
                    subtitle = stringResource(R.string.option_priorities_desc),
                    icon = Icons.Default.CheckCircle,
                    checked = exportSelection.includePriorities,
                    onCheckedChange = { exportSelection = exportSelection.copy(includePriorities = it) }
                )
            }

            Spacer(modifier = Modifier.height(HyperPopSpace.groupGap))

            HpButton(
                text = stringResource(R.string.action_export),
                onClick = { exportLauncher.launch(BackupManager.generateFileName()) },
                enabled = !isProcessing,
            )
            Spacer(modifier = Modifier.height(8.dp))
            HpButton(
                text = stringResource(R.string.action_import),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                enabled = !isProcessing,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
fun BackupOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}