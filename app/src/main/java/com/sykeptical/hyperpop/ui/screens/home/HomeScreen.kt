package com.sykeptical.hyperpop.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
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

@Composable
fun HomeScreen(
    viewModel: AppListViewModel = viewModel(),
    onSettingsClick: () -> Unit,
    onNavConfigClick: (String) -> Unit,
    onScreenRecordingConfigClick: () -> Unit = {},
    onAppConfigClick: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val activeApps by viewModel.activeAppsState.collectAsState()
    val libraryApps by viewModel.libraryAppsState.collectAsState()
    val systemIntegrations by viewModel.systemIntegrationsState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        bottomBar = {
            ShortNavigationBar {
                ShortNavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(if (selectedTab == 0) Icons.Filled.ToggleOn else Icons.Outlined.ToggleOff, null) },
                    label = { Text(stringResource(R.string.tab_active)) }
                )
                ShortNavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(if (selectedTab == 1) Icons.Filled.Apps else Icons.Outlined.Apps, null) },
                    label = { Text(stringResource(R.string.tab_library)) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(bottom = padding.calculateBottomPadding())
                .fillMaxSize()
        ) {
            when (selectedTab) {
                0 -> ActiveAppsPage(
                    apps = activeApps,
                    isLoading = isLoading,
                    systemIntegrations = systemIntegrations,
                    viewModel = viewModel,
                    onConfig = { onAppConfigClick(it.packageName) },
                    onSystemConfig = { integration ->
                        if (integration.id == com.sykeptical.hyperpop.ui.SystemIntegrationId.SCREEN_RECORDER) {
                            onScreenRecordingConfigClick()
                        }
                    },
                    onSettingsClick = onSettingsClick
                )

                else -> LibraryPage(
                    apps = libraryApps,
                    isLoading = isLoading,
                    systemIntegrations = systemIntegrations,
                    viewModel = viewModel,
                    onConfig = { onAppConfigClick(it.packageName) },
                    onSystemConfig = { integration ->
                        if (integration.id == com.sykeptical.hyperpop.ui.SystemIntegrationId.SCREEN_RECORDER) {
                            onScreenRecordingConfigClick()
                        }
                    },
                    onSettingsClick = onSettingsClick
                )
            }
        }
    }
}
