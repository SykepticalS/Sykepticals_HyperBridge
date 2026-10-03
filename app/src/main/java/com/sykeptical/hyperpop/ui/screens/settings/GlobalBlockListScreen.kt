package com.sykeptical.hyperpop.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.ui.AppInfo
import com.sykeptical.hyperpop.ui.AppListViewModel
import com.sykeptical.hyperpop.ui.components.BlocklistEditor
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpNavRow
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpSectionTitle
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import kotlinx.coroutines.launch

/**
 * Main Screen: Global Rules + Entry point to App List
 */
@Composable
fun GlobalBlocklistScreen(
    onBack: () -> Unit,
    onNavigateToAppList: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val globalBlockedTerms by preferences.globalBlockedTermsFlow.collectAsState(initial = emptySet())

    HpScaffold(title = stringResource(R.string.blocked_terms), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HyperPopSpace.screen)
        ) {
            HpSectionTitle(stringResource(R.string.global_rules), first = true)
            HpGroup {
                Box(modifier = Modifier.padding(HyperPopSpace.rowHorizontal)) {
                    BlocklistEditor(
                        terms = globalBlockedTerms,
                        onUpdate = { scope.launch { preferences.setGlobalBlockedTerms(it) } }
                    )
                }
            }

            HpSectionTitle(stringResource(R.string.app_specific_rules))
            HpGroup {
                HpNavRow(
                    title = stringResource(R.string.app_specific_rules),
                    subtitle = stringResource(R.string.manage_app_rules_desc),
                    icon = Icons.Default.Apps,
                    onClick = onNavigateToAppList,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Secondary Screen: List of Apps to configure
 */
@Composable
fun BlocklistAppListScreen(
    onBack: () -> Unit,
    viewModel: AppListViewModel = viewModel()
) {
    val activeApps by viewModel.activeAppsState.collectAsState()
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }

    HpScaffold(title = stringResource(R.string.app_specific_rules), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = HyperPopSpace.screen, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeApps, key = { it.packageName }) { app ->
                AppBlockItem(app = app, viewModel = viewModel) { selectedApp = app }
            }
        }
    }

    // Edit Dialog
    if (selectedApp != null) {
        AppBlocklistDialog(
            app = selectedApp!!,
            viewModel = viewModel,
            onDismiss = { selectedApp = null }
        )
    }
}

@Composable
fun AppBlockItem(
    app: AppInfo,
    viewModel: AppListViewModel,
    onClick: () -> Unit
) {
    val terms by viewModel.getAppBlockedTerms(app.packageName).collectAsState(initial = emptySet())
    val count = terms.size

    val subtitle = if (count > 0) {
        stringResource(R.string.blocked_terms_count, count)
    } else {
        stringResource(R.string.no_active_rules)
    }

    val subtitleColor = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    // Expressive Card Item
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = subtitleColor
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AppBlocklistDialog(
    app: AppInfo,
    viewModel: AppListViewModel,
    onDismiss: () -> Unit
) {
    val blockedTerms by viewModel.getAppBlockedTerms(app.packageName).collectAsState(initial = emptySet())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.name) },
        text = {
            // Reusing existing editor component
            BlocklistEditor(
                terms = blockedTerms,
                onUpdate = { viewModel.updateAppBlockedTerms(app.packageName, it) }
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        }
    )
}