package com.sykeptical.hyperpop.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.service.logincode.LoginCodeSettings
import com.sykeptical.hyperpop.ui.components.SectionLabel
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpSwitch
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LoginCodeApp(val packageName: String, val label: String, val icon: Bitmap?)

@Composable
fun LoginCodeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val settings by prefs.loginCodeSettingsFlow.collectAsState(initial = prefs.getLoginCodeSettingsSync())
    val installed by produceState<List<LoginCodeApp>?>(initialValue = null) {
        value = loadLaunchableApps(context)
    }
    var query by rememberSaveable { mutableStateOf("") }

    // Selection order is frozen when the list loads so toggling an app does not make it jump.
    val initialSelection = remember(installed) { settings.packages }
    val apps = remember(installed, query, initialSelection) {
        installed.orEmpty()
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
            .sortedWith(compareByDescending<LoginCodeApp> { it.packageName in initialSelection }.thenBy { it.label.lowercase() })
    }
    val featureAlpha = if (settings.enabled) 1f else 0.5f

    HpScaffold(title = stringResource(R.string.login_code_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = HyperPopSpace.screen, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.login_code_screen_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 16.dp),
                )
            }
            item {
                ToggleCard(
                    title = stringResource(R.string.login_code_enabled),
                    subtitle = stringResource(R.string.login_code_enabled_desc),
                    icon = Icons.Outlined.Password,
                    checked = settings.enabled,
                    shape = RoundedCornerShape(16.dp),
                    onChange = { scope.launch { prefs.setLoginCodeEnabled(it) } },
                )
            }
            item { SectionLabel(stringResource(R.string.login_code_when_detected)) }
            item {
                ToggleCard(
                    title = stringResource(R.string.login_code_copy_setting),
                    subtitle = stringResource(R.string.login_code_copy_setting_desc),
                    icon = Icons.Outlined.ContentCopy,
                    checked = settings.copyAction,
                    shape = RoundedCornerShape(16.dp),
                    enabled = settings.enabled,
                    modifier = Modifier.alpha(featureAlpha),
                    onChange = { scope.launch { prefs.setLoginCodeCopyAction(it) } },
                )
            }
            item {
                val dismissEnabled = settings.enabled && settings.copyAction
                ToggleCard(
                    title = stringResource(R.string.login_code_dismiss_setting),
                    subtitle = stringResource(R.string.login_code_dismiss_setting_desc),
                    icon = Icons.Outlined.ClearAll,
                    checked = settings.dismissAfterCopy,
                    shape = RoundedCornerShape(16.dp),
                    enabled = dismissEnabled,
                    modifier = Modifier.alpha(if (dismissEnabled) 1f else 0.5f),
                    onChange = { scope.launch { prefs.setLoginCodeDismissAfterCopy(it) } },
                )
            }
            item {
                ToggleCard(
                    title = stringResource(R.string.login_code_glow_setting),
                    subtitle = stringResource(R.string.login_code_glow_setting_desc),
                    icon = Icons.Outlined.AutoAwesome,
                    checked = settings.glow,
                    shape = RoundedCornerShape(16.dp),
                    enabled = settings.enabled,
                    modifier = Modifier.alpha(featureAlpha),
                    onChange = { scope.launch { prefs.setLoginCodeGlow(it) } },
                )
            }
            item {
                ToggleCard(
                    title = stringResource(R.string.login_code_compact_setting),
                    subtitle = stringResource(R.string.login_code_compact_setting_desc),
                    icon = Icons.Outlined.Pin,
                    checked = settings.compactCode,
                    shape = RoundedCornerShape(16.dp),
                    enabled = settings.enabled,
                    modifier = Modifier.alpha(featureAlpha),
                    onChange = { scope.launch { prefs.setLoginCodeCompact(it) } },
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                LoginCodePrivacyCard()
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        SectionLabel(
                            stringResource(R.string.login_code_apps) + " · " +
                                stringResource(R.string.login_code_selected_count, installed.orEmpty().count { it.packageName in settings.packages })
                        )
                    }
                    TextButton(
                        onClick = { scope.launch { prefs.resetLoginCodeApps() } },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text(stringResource(R.string.login_code_apps_reset)) }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.login_code_search_apps)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            if (installed == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            itemsIndexed(apps, key = { _, app -> app.packageName }) { _, app ->
                AppToggleRow(
                    app = app,
                    checked = app.packageName in settings.packages,
                    shape = RoundedCornerShape(16.dp),
                    onChange = { checked -> scope.launch { prefs.setLoginCodeApp(app.packageName, checked) } },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun LoginCodePrivacyCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    stringResource(R.string.login_code_privacy_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.login_code_privacy_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        onClick = { onChange(!checked) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            HpSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

@Composable
private fun AppToggleRow(
    app: LoginCodeApp,
    checked: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onChange: (Boolean) -> Unit,
) {
    Card(
        onClick = { onChange(!checked) },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Default.Android,
                    null,
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                        .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HpSwitch(checked = checked, onCheckedChange = onChange)
        }
    }
}

private suspend fun loadLaunchableApps(context: Context): List<LoginCodeApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val launchable = pm.queryIntentActivities(launcher, 0).map { it.activityInfo.packageName }
    // Some SMS apps ship without a launcher entry but are still worth offering.
    val candidates = (launchable + LoginCodeSettings.DEFAULT_PACKAGES).toSet() - context.packageName
    candidates.mapNotNull { pkg ->
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
        LoginCodeApp(
            packageName = pkg,
            label = pm.getApplicationLabel(info).toString(),
            icon = runCatching { pm.getApplicationIcon(info).toBitmap(96, 96) }.getOrNull(),
        )
    }
}
