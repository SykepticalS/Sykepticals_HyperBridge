package com.sykeptical.hyperpop.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.sykeptical.hyperpop.data.model.HyperPopBackup
import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen : NavKey {
    @Serializable data object Onboarding : Screen
    @Serializable data object Home : Screen
    @Serializable data object Info : Screen
    @Serializable data object Setup : Screen
    @Serializable data object Licenses : Screen
    @Serializable data object Behavior : Screen
    @Serializable data object GlobalSettings : Screen
    @Serializable data object History : Screen
    @Serializable data object Backup : Screen
    @Serializable data class ImportPreview(val backup: HyperPopBackup) : Screen
    @Serializable data class NavCustomization(val packageName: String?) : Screen
    @Serializable data object AppPriority : Screen
    @Serializable data object GlobalBlocklist : Screen
    @Serializable data object BlocklistApps : Screen
    @Serializable data object IslandSettings : Screen
    @Serializable data object MediaCardSettings : Screen
    @Serializable data object DndSettings : Screen
    @Serializable data object LoginCodeSettings : Screen
    @Serializable data object BugReport : Screen
    @Serializable data object ScreenRecordingCustomization : Screen
    @Serializable data object Diagnostics : Screen
    @Serializable data class AppConfig(val packageName: String) : Screen
}

