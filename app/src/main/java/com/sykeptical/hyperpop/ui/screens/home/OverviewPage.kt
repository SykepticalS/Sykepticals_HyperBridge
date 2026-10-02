package com.sykeptical.hyperpop.ui.screens.home

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.ui.system.CyclingIslandDemo
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpNavRow
import com.sykeptical.hyperpop.ui.system.HpStatusRow
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.ui.system.SettingsPlace
import com.sykeptical.hyperpop.xposed.runtime.EnvironmentRuntime
import kotlinx.coroutines.delay

@Composable
fun OverviewPage(
    enabledApps: Int,
    onPlace: (SettingsPlace) -> Unit,
    onOpenApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            ready = EnvironmentRuntime.snapshot(context).privilegedReady
            delay(3000)
        }
    }
    val healthy = ready != false
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyperPopSpace.screen)
            .padding(top = 28.dp, bottom = 24.dp),
    ) {
        Text(stringResource(R.string.app_name), style = HyperPopType.largeTitle)
        Spacer(Modifier.height(20.dp))
        CyclingIslandDemo()
        Spacer(Modifier.height(20.dp))
        HpGroup {
            HpStatusRow(
                title = if (healthy) stringResource(R.string.home_ready) else stringResource(R.string.home_attention),
                value = if (ready == null) "…" else enabledApps.toString(),
                ok = if (ready == null) null else healthy,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (healthy) stringResource(R.string.home_ready_detail) else stringResource(R.string.home_attention_detail),
            style = HyperPopType.secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!healthy) {
            Spacer(Modifier.height(12.dp))
            HpGroup {
                HpNavRow(stringResource(R.string.settings_system), onClick = { onPlace(SettingsPlace.SYSTEM) })
            }
        }
        Spacer(Modifier.height(HyperPopSpace.groupGap))
        HpGroup {
            HpNavRow(stringResource(R.string.islands_title), onClick = { onPlace(SettingsPlace.ISLANDS) })
            HpNavRow(stringResource(R.string.tab_apps), onClick = onOpenApps)
            HpNavRow(stringResource(R.string.notifications_media), onClick = { onPlace(SettingsPlace.MEDIA) })
        }
    }
}
