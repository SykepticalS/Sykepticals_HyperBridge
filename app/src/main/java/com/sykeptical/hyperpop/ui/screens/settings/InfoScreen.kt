package com.sykeptical.hyperpop.ui.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.LocaleListCompat
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.ui.system.HpChoiceRow
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpNavRow
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpSectionTitle
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.util.DocumentationUrls
import com.sykeptical.hyperpop.util.parseBold

@Composable
fun InfoScreen(
    onBack: () -> Unit,
    onSetupClick: () -> Unit,
    onLicensesClick: () -> Unit,
    onBehaviorClick: () -> Unit,
    onGlobalSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onBlocklistClick: () -> Unit,
    onBackupClick: () -> Unit,
    onBugReportClick: () -> Unit = {},
    onDiagnosticsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var showLanguageDialog by remember { mutableStateOf(false) }

    val appVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" }
        catch (_: Exception) { "1.0.0" }
    }

    val appIconBitmap = remember(context) {
        try { context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap() }
        catch (_: Exception) { null }
    }

    HpScaffold(title = stringResource(R.string.settings), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = stringResource(R.string.logo_desc),
                    modifier = Modifier.size(96.dp).padding(bottom = 12.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = stringResource(R.string.logo_desc),
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                        .padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(stringResource(R.string.app_name), style = HyperPopType.title)
            Text(
                text = stringResource(R.string.developer_credit),
                style = HyperPopType.secondary,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.version_template, appVersion),
                style = HyperPopType.secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            SettingsSection(
                title = stringResource(R.string.group_about),
                items = listOf(
                    SettingsItemData(Icons.Default.Language, stringResource(R.string.language), stringResource(R.string.language_desc)) { showLanguageDialog = true },
                    SettingsItemData(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.documentation_title), stringResource(R.string.documentation_subtitle)) {
                        uriHandler.openUri(DocumentationUrls.DOCS)
                    },
                    SettingsItemData(Icons.Default.Person, stringResource(R.string.developer), stringResource(R.string.developer_subtitle)) { },
                    SettingsItemData(Icons.Default.History, stringResource(R.string.version_history), "0.1.0 - $appVersion", onHistoryClick),
                    SettingsItemData(Icons.Default.Description, stringResource(R.string.licenses), stringResource(R.string.licenses_subtitle), onLicensesClick),
                    SettingsItemData(Icons.Default.Security, stringResource(R.string.privacy_policy_title), stringResource(R.string.privacy_policy_subtitle)) { uriHandler.openUri(DocumentationUrls.PRIVACY_POLICY) }
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSection(
                title = stringResource(R.string.special_thanks),
                items = listOf(
                    SettingsItemData(
                        Icons.Default.Favorite,
                        stringResource(R.string.xmsf_workaround_credit_title),
                        stringResource(R.string.xmsf_workaround_credit_subtitle)
                    ) {
                        uriHandler.openUri("https://www.coolapk1s.com/feed/70418983")
                    }
                )
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.footer_made_with_love).parseBold(),
                style = HyperPopType.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showLanguageDialog) {
        LanguageSelectorDialog(onDismiss = { showLanguageDialog = false })
    }
}

data class SettingsItemData(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
fun SettingsSection(title: String, items: List<SettingsItemData>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = HyperPopSpace.screen)) {
        HpSectionTitle(title)
        HpGroup {
            items.forEach { item ->
                HpNavRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    icon = item.icon,
                    onClick = item.onClick,
                )
            }
        }
    }
}

@Composable
fun LanguageSelectorDialog(onDismiss: () -> Unit) {
    val languages = mapOf(
        stringResource(R.string.system_default) to "",
        "العربية" to "ar",
        "Bahasa Indonesia" to "id",
        "Čeština" to "cs",
        "Deutsch" to "de",
        "English" to "en",
        "Español" to "es",
        "Français" to "fr",
        "Italiano" to "it",
        "Magyar" to "hu",
        "日本語" to "ja",
        "Português (BR)" to "pt-BR",
        "Polski" to "pl",
        "Slovenčina" to "sk",
        "繁體中文 (TW)" to "zh-TW",
        "Korean" to "ko",
        "Русский" to "ru",
        "Türkçe" to "tr",
        "Українська" to "uk"
    )
    val currentAppLocales = AppCompatDelegate.getApplicationLocales()
    val initialTag = if (!currentAppLocales.isEmpty) currentAppLocales.toLanguageTags().split(",")[0] else ""
    val bestMatchKey = languages.entries.find { (_, tag) -> tag.isNotEmpty() && initialTag.startsWith(tag) }?.value ?: ""
    var selectedTag by remember { mutableStateOf(bestMatchKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HpGroup {
                    languages.forEach { (name, tag) ->
                        HpChoiceRow(
                            title = name,
                            selected = tag == selectedTag,
                            onClick = { selectedTag = tag },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val appLocale = if (selectedTag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(selectedTag)
                    AppCompatDelegate.setApplicationLocales(appLocale)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
