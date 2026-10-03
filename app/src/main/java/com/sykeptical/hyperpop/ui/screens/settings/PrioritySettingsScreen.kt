package com.sykeptical.hyperpop.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.models.IslandLimitMode
import com.sykeptical.hyperpop.ui.system.HpChoiceRow
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpNavRow
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpSectionTitle
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import kotlinx.coroutines.launch

@Composable
fun PrioritySettingsScreen(
    onBack: () -> Unit,
    onNavigateToPriorityList: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }
    val currentMode by preferences.limitModeFlow.collectAsState(initial = IslandLimitMode.MOST_RECENT)

    HpScaffold(title = stringResource(R.string.island_behavior), onBack = onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = HyperPopSpace.screen)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = stringResource(R.string.limit_desc),
                style = HyperPopType.secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            )
            HpSectionTitle(stringResource(R.string.limit_strategy), first = true)
            HpGroup {
                IslandLimitMode.entries.forEach { mode ->
                    HpChoiceRow(
                        title = stringResource(mode.titleRes),
                        subtitle = stringResource(mode.descRes),
                        selected = currentMode == mode,
                        onClick = { scope.launch { preferences.setLimitMode(mode) } },
                    )
                }
            }
            if (currentMode == IslandLimitMode.PRIORITY) {
                Spacer(Modifier.height(HyperPopSpace.groupGap))
                HpGroup {
                    HpNavRow(
                        title = stringResource(R.string.configure_order),
                        onClick = onNavigateToPriorityList,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
