package com.sykeptical.hyperpop.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.ui.system.HpFocusBanner
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpNavRow
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.ui.system.SettingsCatalog
import com.sykeptical.hyperpop.ui.system.SettingsPlace

@Composable
fun SettingsRootScreen(
    onPlace: (SettingsPlace) -> Unit,
    onSearch: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyperPopSpace.screen)
            .padding(top = 28.dp, bottom = 24.dp),
    ) {
        Text(stringResource(R.string.tab_settings), style = HyperPopType.largeTitle)
        Spacer(Modifier.height(16.dp))
        HpGroup {
            HpNavRow(stringResource(R.string.settings_search), onClick = onSearch, icon = Icons.Default.Search)
        }
        Spacer(Modifier.height(HyperPopSpace.groupGap))
        HpGroup {
            HpNavRow(stringResource(R.string.islands_title), onClick = { onPlace(SettingsPlace.ISLANDS) })
            HpNavRow(stringResource(R.string.notifications_title), onClick = { onPlace(SettingsPlace.NOTIFICATIONS) })
            HpNavRow(stringResource(R.string.settings_system), onClick = { onPlace(SettingsPlace.SYSTEM) })
            HpNavRow(stringResource(R.string.settings_advanced), onClick = { onPlace(SettingsPlace.ADVANCED) })
            HpNavRow(stringResource(R.string.settings_about), onClick = { onPlace(SettingsPlace.ABOUT) })
        }
    }
}

@Composable
fun IslandsHubScreen(onBack: () -> Unit, onPlace: (SettingsPlace) -> Unit) {
    HubScreen(stringResource(R.string.islands_title), onBack) {
        HpNavRow(stringResource(R.string.islands_priority), onClick = { onPlace(SettingsPlace.PRIORITY) })
        HpNavRow(stringResource(R.string.islands_timing), onClick = { onPlace(SettingsPlace.TIMING) })
        HpNavRow(stringResource(R.string.islands_text), onClick = { onPlace(SettingsPlace.TEXT) })
        HpNavRow(stringResource(R.string.islands_glow), onClick = { onPlace(SettingsPlace.GLOW) })
        HpNavRow(stringResource(R.string.islands_scenes), onClick = { onPlace(SettingsPlace.SCENES) })
        HpNavRow(stringResource(R.string.islands_motion), onClick = { onPlace(SettingsPlace.MOTION) })
    }
}

@Composable
fun NotificationsHubScreen(onBack: () -> Unit, onPlace: (SettingsPlace) -> Unit) {
    HubScreen(stringResource(R.string.notifications_title), onBack) {
        HpNavRow(stringResource(R.string.login_code_title), onClick = { onPlace(SettingsPlace.LOGIN_CODES) })
        HpNavRow(stringResource(R.string.dnd_mode_title), onClick = { onPlace(SettingsPlace.DND) })
        HpNavRow(stringResource(R.string.nav_layout_title), onClick = { onPlace(SettingsPlace.NAVIGATION) })
        HpNavRow(stringResource(R.string.notifications_media), onClick = { onPlace(SettingsPlace.MEDIA) })
        HpNavRow(stringResource(R.string.blocked_terms), onClick = { onPlace(SettingsPlace.BLOCKLIST) })
    }
}

@Composable
fun AdvancedHubScreen(onBack: () -> Unit, onPlace: (SettingsPlace) -> Unit) {
    HubScreen(stringResource(R.string.settings_advanced), onBack) {
        HpNavRow(stringResource(R.string.advanced_experiments), onClick = { onPlace(SettingsPlace.EXPERIMENTS) })
        HpNavRow(stringResource(R.string.diagnostics_title), onClick = { onPlace(SettingsPlace.DIAGNOSTICS) })
        HpNavRow(stringResource(R.string.bug_report_entry_title), onClick = { onPlace(SettingsPlace.BUG_REPORT) })
        HpNavRow(stringResource(R.string.backup_restore_title), onClick = { onPlace(SettingsPlace.BACKUP) })
        HpNavRow(stringResource(R.string.screen_recording_title), onClick = { onPlace(SettingsPlace.SCREEN_RECORDING) })
    }
}

@Composable
fun SettingsSearchScreen(onBack: () -> Unit, onPlace: (SettingsPlace, String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = SettingsCatalog.entries.filter { entry ->
        val title = stringResource(entry.titleRes)
        query.isBlank() ||
            title.contains(query, ignoreCase = true) ||
            entry.keywords.any { it.contains(query, ignoreCase = true) } ||
            entry.keys.any { it.contains(query, ignoreCase = true) }
    }
    HpScaffold(title = stringResource(R.string.settings_search), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = HyperPopSpace.screen)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions.Default,
            )
            HpGroup {
                results.forEach { entry ->
                    HpNavRow(
                        title = stringResource(entry.titleRes),
                        subtitle = entry.keywords.firstOrNull(),
                        onClick = { onPlace(entry.place, entry.keys.firstOrNull()) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HubScreen(
    title: String,
    onBack: () -> Unit,
    rows: @Composable () -> Unit,
) {
    HpScaffold(title = title, onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = HyperPopSpace.screen)
                .verticalScroll(rememberScrollState()),
        ) {
            HpFocusBanner(title)
            HpGroup { rows() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsPlaceTitle(place: SettingsPlace): String = when (place) {
    SettingsPlace.ISLANDS -> stringResource(R.string.islands_title)
    else -> ""
}
