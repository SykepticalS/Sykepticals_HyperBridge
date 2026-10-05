package com.sykeptical.hyperpop.ui.navigation

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.ui.screens.home.HomeScreen
import com.sykeptical.hyperpop.ui.screens.onboarding.OnboardingScreen
import com.sykeptical.hyperpop.ui.screens.settings.AdvancedHubScreen
import com.sykeptical.hyperpop.ui.screens.settings.AppConfigScreen
import com.sykeptical.hyperpop.ui.screens.settings.AppPriorityScreen
import com.sykeptical.hyperpop.ui.screens.settings.BackupSettingsScreen
import com.sykeptical.hyperpop.ui.screens.settings.BlocklistAppListScreen
import com.sykeptical.hyperpop.ui.screens.settings.BugReportScreen
import com.sykeptical.hyperpop.ui.screens.settings.ChangelogHistoryScreen
import com.sykeptical.hyperpop.ui.screens.settings.DiagnosticsScreen
import com.sykeptical.hyperpop.ui.screens.settings.GlobalBlocklistScreen
import com.sykeptical.hyperpop.ui.screens.settings.ImportPreviewScreen
import com.sykeptical.hyperpop.ui.screens.settings.InfoScreen
import com.sykeptical.hyperpop.ui.screens.settings.IslandSettingsScreen
import com.sykeptical.hyperpop.ui.screens.settings.IslandsHubScreen
import com.sykeptical.hyperpop.ui.screens.settings.LicensesScreen
import com.sykeptical.hyperpop.ui.screens.settings.MediaCardSettingsScreen
import com.sykeptical.hyperpop.ui.screens.settings.NavCustomizationScreen
import com.sykeptical.hyperpop.ui.screens.settings.NotificationsHubScreen
import com.sykeptical.hyperpop.ui.screens.settings.PrioritySettingsScreen
import com.sykeptical.hyperpop.ui.screens.settings.SettingsSearchScreen
import com.sykeptical.hyperpop.ui.screens.settings.SetupHealthScreen
import com.sykeptical.hyperpop.ui.system.SettingsFocus
import com.sykeptical.hyperpop.ui.system.SettingsPlace
import com.sykeptical.hyperpop.ui.screens.settings.PrioritySettingsScreen
import com.sykeptical.hyperpop.ui.screens.settings.SetupHealthScreen
import com.sykeptical.hyperpop.util.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun mainNavGraph(
    context: Context,
    scope: CoroutineScope,
    preferences: AppPreferences,
    navigator: Navigator<Screen>,
    backupManager: BackupManager,
    currentVersionCode: Int,
    onExit: () -> Unit
) = entryProvider {
    entry<Screen.Onboarding> {
        OnboardingScreen {
            scope.launch {
                preferences.setSetupComplete(true)
                navigator.finishOnboarding(Screen.Home)
            }
        }
    }
    entry<Screen.Home> {
        HomeScreen(
            onPlace = { navigator.open(it) },
            onSearch = { navigator.navigate(Screen.SettingsSearch) },
            onNavConfigClick = { pkg -> navigator.navigate(Screen.NavCustomization(pkg)) },
            onScreenRecordingConfigClick = { navigator.navigate(Screen.ScreenRecordingCustomization) },
            onAppConfigClick = { pkg -> navigator.navigate(Screen.AppConfig(pkg)) }
        )
    }

    entry<Screen.Info> {
        InfoScreen(
            onBack = { if (!navigator.goBack()) onExit() },
            onSetupClick = { navigator.navigate(Screen.Setup) },
            onLicensesClick = { navigator.navigate(Screen.Licenses) },
            onBehaviorClick = { navigator.navigate(Screen.Behavior) },
            onGlobalSettingsClick = { navigator.navigate(Screen.GlobalSettings) },
            onHistoryClick = { navigator.navigate(Screen.History) },
            onBlocklistClick = { navigator.navigate(Screen.GlobalBlocklist) },
            onBackupClick = { navigator.navigate(Screen.Backup) },
            onBugReportClick = { navigator.navigate(Screen.BugReport) },
            onDiagnosticsClick = { navigator.navigate(Screen.Diagnostics) }
        )
    }
    entry<Screen.GlobalSettings> {
        IslandsHubScreen(onBack = { navigator.goBack() }, onPlace = { navigator.open(it) })
    }
    entry<Screen.DndSettings> {
        com.sykeptical.hyperpop.ui.screens.settings.DndSettingsScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.LoginCodeSettings> {
        com.sykeptical.hyperpop.ui.screens.settings.LoginCodeSettingsScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.NavCustomization> { key ->
        NavCustomizationScreen(
            onBack = { navigator.goBack() },
            packageName = key.packageName
        )
    }
    entry<Screen.Setup> {
        SetupHealthScreen(
            onBack = { navigator.goBack() },
            onNavigateToBugReport = { navigator.navigate(Screen.BugReport) }
        )
    }
    entry<Screen.Diagnostics> {
        DiagnosticsScreen(
            onBack = { navigator.goBack() },
            onReportError = { navigator.navigate(Screen.BugReport) }
        )
    }
    entry<Screen.Licenses> {
        LicensesScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.Behavior> {
        PrioritySettingsScreen(
            onBack = { navigator.goBack() },
            onNavigateToPriorityList = { navigator.navigate(Screen.AppPriority) }
        )
    }
    entry<Screen.AppPriority> {
        AppPriorityScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.History> {
        ChangelogHistoryScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.GlobalBlocklist> {
        GlobalBlocklistScreen(
            onBack = { navigator.goBack() },
            onNavigateToAppList = { navigator.navigate(Screen.BlocklistApps) }
        )
    }
    entry<Screen.BlocklistApps> {
        BlocklistAppListScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.Backup> {
        BackupSettingsScreen(
            onBack = { navigator.goBack() },
            backupManager = backupManager,
            onBackupFileLoaded = { backup ->
                navigator.navigate(Screen.ImportPreview(backup))
            }
        )
    }
    entry<Screen.ImportPreview> { key ->
        val importSuccessMsg = stringResource(R.string.import_success)
        val importFailedMsg = stringResource(R.string.import_failed)
        ImportPreviewScreen(
            backupData = key.backup,
            onBack = { navigator.goBack() },
            onConfirmRestore = { selection ->
                scope.launch {
                    val result = backupManager.restoreBackup(key.backup, selection)
                    if (result.isSuccess) {
                        Toast.makeText(context, importSuccessMsg, Toast.LENGTH_LONG).show()
                        navigator.navigate(Screen.Home)
                    } else {
                        val error = result.exceptionOrNull()?.message ?: ""
                        Toast.makeText(context, importFailedMsg.format(error), Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
    entry<Screen.IslandSettings> {
        IslandsHubScreen(onBack = { navigator.goBack() }, onPlace = { navigator.open(it) })
    }
    entry<Screen.IslandSection> { key ->
        IslandSettingsScreen(
            section = key.id,
            onBack = { navigator.goBack() },
            onOpenDnd = { navigator.navigate(Screen.DndSettings) },
        )
    }
    entry<Screen.NotificationsHub> {
        NotificationsHubScreen(onBack = { navigator.goBack() }, onPlace = { navigator.open(it) })
    }
    entry<Screen.AdvancedHub> {
        AdvancedHubScreen(onBack = { navigator.goBack() }, onPlace = { navigator.open(it) })
    }
    entry<Screen.SettingsSearch> {
        SettingsSearchScreen(
            onBack = { navigator.goBack() },
            onPlace = { place, highlight ->
                SettingsFocus.key = highlight
                navigator.open(place)
            },
        )
    }
    entry<Screen.MediaCardSettings> {
        MediaCardSettingsScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.BugReport> {
        BugReportScreen(
            onBack = { navigator.goBack() },
            onNavigateToDiagnostics = { navigator.navigate(Screen.Diagnostics) }
        )
    }
    entry<Screen.ScreenRecordingCustomization> {
        com.sykeptical.hyperpop.ui.screens.settings.ScreenRecordingSettingsScreen(onBack = { navigator.goBack() })
    }
    entry<Screen.AppConfig> { key ->
        AppConfigScreen(
            packageName = key.packageName,
            onBack = { navigator.goBack() },
            onNavConfigClick = { pkg -> navigator.navigate(Screen.NavCustomization(pkg)) }
        )
    }
}

private fun Navigator<Screen>.open(place: SettingsPlace) {
    val route: Screen = when (place) {
        SettingsPlace.ISLANDS -> Screen.IslandSettings
        SettingsPlace.NOTIFICATIONS -> Screen.NotificationsHub
        SettingsPlace.SYSTEM -> Screen.Setup
        SettingsPlace.ADVANCED -> Screen.AdvancedHub
        SettingsPlace.ABOUT -> Screen.Info
        SettingsPlace.PRIORITY -> Screen.Behavior
        SettingsPlace.TIMING -> Screen.IslandSection("timing")
        SettingsPlace.TEXT -> Screen.IslandSection("text")
        SettingsPlace.GLOW -> Screen.IslandSection("glow")
        SettingsPlace.SCENES -> Screen.IslandSection("scenes")
        SettingsPlace.TWEAKS -> Screen.IslandSection("tweaks")
        SettingsPlace.EXPERIMENTS -> Screen.IslandSection("experiments")
        SettingsPlace.LOGIN_CODES -> Screen.LoginCodeSettings
        SettingsPlace.DND -> Screen.DndSettings
        SettingsPlace.NAVIGATION -> Screen.NavCustomization(null)
        SettingsPlace.MEDIA -> Screen.MediaCardSettings
        SettingsPlace.BLOCKLIST -> Screen.GlobalBlocklist
        SettingsPlace.DIAGNOSTICS -> Screen.Diagnostics
        SettingsPlace.BUG_REPORT -> Screen.BugReport
        SettingsPlace.BACKUP -> Screen.Backup
        SettingsPlace.SCREEN_RECORDING -> Screen.ScreenRecordingCustomization
        SettingsPlace.APPS -> Screen.Home
    }
    navigate(route)
}

