package com.sykeptical.hyperpop.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.ui.AppListViewModel
import com.sykeptical.hyperpop.ui.SystemIntegrationId
import com.sykeptical.hyperpop.ui.screens.settings.SettingsRootScreen
import com.sykeptical.hyperpop.ui.system.HpBottomBar
import com.sykeptical.hyperpop.ui.system.SettingsPlace

@Composable
fun HomeScreen(
    viewModel: AppListViewModel = viewModel(),
    onPlace: (SettingsPlace) -> Unit,
    onSearch: () -> Unit,
    onNavConfigClick: (String) -> Unit,
    onScreenRecordingConfigClick: () -> Unit = {},
    onAppConfigClick: (String) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val libraryApps by viewModel.libraryAppsState.collectAsState()
    val systemIntegrations by viewModel.systemIntegrationsState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val enabledCount = libraryApps.count { it.isBridged }

    Scaffold(
        bottomBar = {
            HpBottomBar(
                items = listOf(
                    stringResource(R.string.tab_home) to Icons.Default.Home,
                    stringResource(R.string.tab_apps) to Icons.Default.Apps,
                    stringResource(R.string.tab_settings) to Icons.Default.Settings,
                ),
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .fillMaxSize(),
        ) {
            when (selectedTab) {
                0 -> OverviewPage(
                    enabledApps = enabledCount,
                    onPlace = onPlace,
                    onOpenApps = { selectedTab = 1 },
                )
                1 -> LibraryPage(
                    apps = libraryApps,
                    isLoading = isLoading,
                    systemIntegrations = systemIntegrations,
                    viewModel = viewModel,
                    onConfig = { onAppConfigClick(it.packageName) },
                    onSystemConfig = { integration ->
                        if (integration.id == SystemIntegrationId.SCREEN_RECORDER) onScreenRecordingConfigClick()
                    },
                    showSettingsAction = false,
                )
                else -> SettingsRootScreen(onPlace = onPlace, onSearch = onSearch)
            }
        }
    }
}
