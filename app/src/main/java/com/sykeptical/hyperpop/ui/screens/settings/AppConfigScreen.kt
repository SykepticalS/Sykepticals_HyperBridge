package com.sykeptical.hyperpop.ui.screens.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.models.CallStage
import com.sykeptical.hyperpop.models.IslandConfig
import com.sykeptical.hyperpop.models.NotificationType
import com.sykeptical.hyperpop.ui.AppInfo
import com.sykeptical.hyperpop.ui.AppListViewModel
import com.sykeptical.hyperpop.ui.components.IslandSettingsControl
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpSwitch
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.ui.theme.HyperPopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Subscreens available within the App Configuration flow.
 * Group 1: Notification Types, Island Behavior, Blocked Terms
 * Includes notification behavior and optional translator extensions.
 */
enum class AppConfigSubscreen {
    NOTIFICATION_TYPES,
    ISLAND_BEHAVIOR,
    BLOCKED_TERMS,
    CUSTOM_TRANSLATORS
}

@Composable
fun AppConfigScreen(
    packageName: String,
    viewModel: AppListViewModel = viewModel(),
    onBack: () -> Unit,
    onNavConfigClick: (String) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context.applicationContext) }

    var currentSubscreen by remember { mutableStateOf<AppConfigSubscreen?>(null) }

    val effectiveConfig by viewModel.getEffectiveAppConfigFlow(packageName).collectAsState(initial = null)
    val activeTypes = effectiveConfig?.activeTypes ?: emptySet()
    val activeCallStages = effectiveConfig?.activeCallStages ?: CallStage.entries.toSet()
    val voiceCompactDuration = effectiveConfig?.voiceCompactDuration == true

    val appIslandConfig by viewModel.getAppIslandConfig(packageName).collectAsState(initial = IslandConfig())
    val globalConfig by viewModel.globalConfigFlow.collectAsState(
        initial = IslandConfig(firstFloat = true, isShowShade = true, timeout = 5)
    )

    val blockedTerms by viewModel.getAppBlockedTerms(packageName).collectAsState(initial = emptySet())

    val allowedPackages by preferences.allowedPackagesFlow.collectAsState(initial = emptySet())
    val isBridged = allowedPackages.contains(packageName)

    var appInfo by remember { mutableStateOf<AppInfo?>(null) }
    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            try {
                val ai = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(ai).toString()
                val iconDrawable = pm.getApplicationIcon(ai)
                val iconBmp = drawableToBitmap(iconDrawable)
                appInfo = AppInfo(
                    name = label,
                    packageName = packageName,
                    icon = iconBmp,
                    isBridged = isBridged,
                    isInstalled = true
                )
            } catch (_: Exception) {
                appInfo = AppInfo(
                    name = packageName,
                    packageName = packageName,
                    icon = null,
                    isBridged = isBridged,
                    isInstalled = false
                )
            }
        }
    }

    BackHandler(enabled = currentSubscreen != null) { currentSubscreen = null }

    Box(modifier = Modifier.fillMaxSize()) {
        AppConfigContent(
            appName = appInfo?.name ?: packageName,
            packageName = packageName,
            appIcon = appInfo?.icon,
            isBridged = isBridged,
            activeTypes = activeTypes,
            activeCallStages = activeCallStages,
            voiceCompactDuration = voiceCompactDuration,
            appIslandConfig = appIslandConfig,
            globalConfig = globalConfig,
            blockedTerms = blockedTerms,
            currentSubscreen = currentSubscreen,
            onNavigateSubscreen = { currentSubscreen = it },
            onBack = onBack,
            onToggleBridged = { enabled -> viewModel.toggleApp(packageName, enabled) },
            onToggleType = { type, enabled -> viewModel.updateAppConfig(packageName, type, enabled) },
            onToggleCallStage = { stage, enabled -> viewModel.updateAppCallStage(packageName, stage, enabled) },
            onToggleVoiceDuration = { enabled -> viewModel.updateAppVoiceCompactDuration(packageName, enabled) },
            onUpdateIslandConfig = { config -> viewModel.updateAppIslandConfig(packageName, config) },
            onUpdateBlockedTerms = { terms -> viewModel.updateAppBlockedTerms(packageName, terms) },
            onNavConfigClick = { onNavConfigClick(packageName) }
        )
    }
}

@Composable
fun AppConfigContent(
    appName: String,
    packageName: String,
    appIcon: Bitmap?,
    isBridged: Boolean,
    activeTypes: Set<String>,
    activeCallStages: Set<CallStage>,
    voiceCompactDuration: Boolean = false,
    appIslandConfig: IslandConfig,
    globalConfig: IslandConfig,
    blockedTerms: Set<String>,
    currentSubscreen: AppConfigSubscreen? = null,
    onNavigateSubscreen: (AppConfigSubscreen?) -> Unit = {},
    onBack: () -> Unit,
    onToggleBridged: (Boolean) -> Unit,
    onToggleType: (NotificationType, Boolean) -> Unit,
    onToggleCallStage: (CallStage, Boolean) -> Unit,
    onToggleVoiceDuration: (Boolean) -> Unit = {},
    onUpdateIslandConfig: (IslandConfig) -> Unit,
    onUpdateBlockedTerms: (Set<String>) -> Unit,
    onNavConfigClick: () -> Unit
) {
    val activeDesc = stringResource(R.string.cd_app_state_active)
    val inactiveDesc = stringResource(R.string.cd_app_state_inactive)
    val navEditDesc = stringResource(R.string.cd_nav_edit)
    val activeTypesSubtitle = stringResource(R.string.active_notifications_subtitle, activeTypes.size)
    val isUsingGlobal = !appIslandConfig.hasOverrides()
    val behaviorSubtitle = if (isUsingGlobal) {
        stringResource(R.string.use_global_default)
    } else {
        "${if (appIslandConfig.firstFloat == true) activeDesc else inactiveDesc} • ${appIslandConfig.timeout ?: 5}s"
    }
    val blockedSubtitle = stringResource(R.string.blocked_terms_count, blockedTerms.size)

    AnimatedContent(
        targetState = currentSubscreen,
        transitionSpec = {
            if (targetState != null && initialState == null) {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width / 3 } + fadeOut()
                )
            } else if (targetState == null && initialState != null) {
                (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width } + fadeOut()
                )
            } else {
                fadeIn().togetherWith(fadeOut())
            }
        },
        label = "AppConfigSubscreenTransition"
    ) { subscreen ->
        if (subscreen == null) {
            // =========================================================================
            // MAIN OVERVIEW SCREEN
            // =========================================================================
            HpScaffold(
                title = stringResource(R.string.app_config_title),
                onBack = onBack,
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = HyperPopSpace.screen, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.app_config_subtitle),
                            style = HyperPopType.secondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 4.dp),
                        )
                    }
                    // Header Overview Card
                    item {
                        AppHeaderCard(
                            appName = appName,
                            packageName = packageName,
                            appIcon = appIcon,
                            isBridged = isBridged,
                            onToggleBridged = onToggleBridged
                        )
                    }

                    // --- GROUP 1: NOTIFICATIONS & BEHAVIOR (3 connected items) ---
                    item {
                        val group1Items = listOf(
                            AppConfigSubscreen.NOTIFICATION_TYPES,
                            AppConfigSubscreen.ISLAND_BEHAVIOR,
                            AppConfigSubscreen.BLOCKED_TERMS
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            group1Items.forEach { route ->
                                when (route) {
                                    AppConfigSubscreen.NOTIFICATION_TYPES -> AppConfigOptionCard(
                                        title = stringResource(R.string.active_notifications_title),
                                        subtitle = activeTypesSubtitle,
                                        icon = Icons.Default.Notifications,
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = { onNavigateSubscreen(AppConfigSubscreen.NOTIFICATION_TYPES) }
                                    )
                                    AppConfigSubscreen.ISLAND_BEHAVIOR -> AppConfigOptionCard(
                                        title = stringResource(R.string.island_behavior_title),
                                        subtitle = behaviorSubtitle,
                                        icon = Icons.Outlined.DisplaySettings,
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = { onNavigateSubscreen(AppConfigSubscreen.ISLAND_BEHAVIOR) }
                                    )
                                    AppConfigSubscreen.BLOCKED_TERMS -> AppConfigOptionCard(
                                        title = stringResource(R.string.blocked_terms),
                                        subtitle = blockedSubtitle,
                                        icon = Icons.Default.Block,
                                        shape = RoundedCornerShape(16.dp),
                                        onClick = { onNavigateSubscreen(AppConfigSubscreen.BLOCKED_TERMS) }
                                    )
                                    else -> {}
                                }
                            }
                        }
                    }

                    // Optional translator extension.
                    item {
                        AppConfigOptionCard(
                            title = stringResource(R.string.custom_translators_title),
                            subtitle = stringResource(R.string.custom_translators_desc),
                            badge = stringResource(R.string.custom_translators_badge),
                            icon = Icons.Default.Extension,
                            shape = RoundedCornerShape(16.dp),
                            onClick = { onNavigateSubscreen(AppConfigSubscreen.CUSTOM_TRANSLATORS) }
                        )
                    }

                    item {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        } else {
            // =========================================================================
            // DEDICATED SUBSCREEN
            // =========================================================================
            when (subscreen) {
                AppConfigSubscreen.NOTIFICATION_TYPES -> {
                    SubscreenScaffold(
                        title = stringResource(R.string.active_notifications_title),
                        appName = appName,
                        onBack = { onNavigateSubscreen(null) }
                    ) {
                        AppNotificationTypesContent(
                            activeTypes = activeTypes,
                            activeCallStages = activeCallStages,
                            onToggleType = onToggleType,
                            onToggleCallStage = onToggleCallStage,
                            onNavConfigClick = onNavConfigClick,
                            navEditDesc = navEditDesc,
                            voiceCompactDuration = voiceCompactDuration,
                            onToggleVoiceDuration = onToggleVoiceDuration,
                        )
                    }
                }

                AppConfigSubscreen.ISLAND_BEHAVIOR -> {
                    SubscreenScaffold(
                        title = stringResource(R.string.island_behavior_title),
                        appName = appName,
                        onBack = { onNavigateSubscreen(null) }
                    ) {
                        AppBehaviorContent(
                            appConfig = appIslandConfig,
                            globalConfig = globalConfig,
                            onUpdate = onUpdateIslandConfig,
                            activeDesc = activeDesc,
                            inactiveDesc = inactiveDesc
                        )
                    }
                }

                AppConfigSubscreen.BLOCKED_TERMS -> {
                    SubscreenScaffold(
                        title = stringResource(R.string.blocked_terms),
                        appName = appName,
                        onBack = { onNavigateSubscreen(null) }
                    ) {
                        AppConfigBlockedTermsContent(
                            terms = blockedTerms,
                            onUpdate = onUpdateBlockedTerms
                        )
                    }
                }

                AppConfigSubscreen.CUSTOM_TRANSLATORS -> {
                    SubscreenScaffold(
                        title = stringResource(R.string.custom_translators_title),
                        appName = appName,
                        onBack = { onNavigateSubscreen(null) }
                    ) {
                        FutureFeaturePlaceholderCard(
                            title = stringResource(R.string.custom_translators_title),
                            badge = stringResource(R.string.custom_translators_badge),
                            icon = Icons.Default.Extension,
                            description = stringResource(R.string.custom_translators_desc)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// REUSABLE SUBSCREEN CONTAINER
// ------------------------------------------------------------------------------------------------

@Composable
fun SubscreenScaffold(
    title: String,
    appName: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    HpScaffold(
        title = title,
        onBack = onBack,
        actions = { actions() },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = HyperPopSpace.screen, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = appName,
                    style = HyperPopType.secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp),
                )
            }
            item {
                content()
            }
            item {
                Spacer(Modifier.height(80.dp))
            }
        }
            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                floatingActionButton()
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// EXPRESSIVE OPTION CARD
// ------------------------------------------------------------------------------------------------

@Composable
fun AppConfigOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    shape: Shape,
    badge: String? = null,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 88.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (badge != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            if (trailingContent != null) {
                trailingContent()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// HEADER OVERVIEW CARD
// ------------------------------------------------------------------------------------------------

@Composable
fun AppHeaderCard(
    appName: String,
    packageName: String,
    appIcon: Bitmap?,
    isBridged: Boolean,
    onToggleBridged: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon.asImageBitmap(),
                        contentDescription = appName,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = if (isBridged) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isBridged) stringResource(R.string.app_status_bridged) else stringResource(R.string.app_status_not_bridged),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBridged) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                HpSwitch(
                    checked = isBridged,
                    onCheckedChange = onToggleBridged,
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle bridging for $appName"
                    }
                )
            }

        }
    }
}

// ------------------------------------------------------------------------------------------------
// NOTIFICATION TYPES CONTENT (Each in a clean separate container card)
// ------------------------------------------------------------------------------------------------

@Composable
fun AppNotificationTypesContent(
    activeTypes: Set<String>,
    activeCallStages: Set<CallStage>,
    onToggleType: (NotificationType, Boolean) -> Unit,
    onToggleCallStage: (CallStage, Boolean) -> Unit,
    onNavConfigClick: () -> Unit,
    navEditDesc: String,
    voiceCompactDuration: Boolean = false,
    onToggleVoiceDuration: (Boolean) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NotificationType.configurableEntries.forEach { type ->
            val isChecked = activeTypes.contains(type.name)
            val typeLabel = stringResource(type.labelRes)
            val switchDesc = if (isChecked) stringResource(R.string.cd_disable_type, typeLabel)
            else stringResource(R.string.cd_enable_type, typeLabel)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleType(type, !isChecked) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (type == NotificationType.NAVIGATION) {
                            IconButton(
                                onClick = onNavConfigClick,
                                modifier = Modifier.semantics { contentDescription = navEditDesc }
                            ) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        HpSwitch(
                            checked = isChecked,
                            onCheckedChange = { onToggleType(type, it) },
                            modifier = Modifier.semantics { contentDescription = switchDesc }
                        )
                    }

                    if (type == NotificationType.CALL && isChecked) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.call_stage_settings),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.call_stage_settings_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        CallStage.entries.forEach { stage ->
                            val stageEnabled = stage in activeCallStages
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleCallStage(stage, !stageEnabled) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(stage.labelRes),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(stage.descriptionRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Checkbox(
                                    checked = stageEnabled,
                                    onCheckedChange = { onToggleCallStage(stage, it) }
                                )
                            }
                        }
                    }
                }
            }
            if (type == NotificationType.VOICE_MESSAGE && isChecked) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleVoiceDuration(!voiceCompactDuration) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.voice_compact_duration),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.voice_compact_duration_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HpSwitch(
                        checked = voiceCompactDuration,
                        onCheckedChange = onToggleVoiceDuration
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// ISLAND BEHAVIOR CONTENT
// Shows current config (global or custom), any change auto-switches to custom,
// and has the "Use Global Defaults" toggle at the END in its own container card.
// ------------------------------------------------------------------------------------------------

@Composable
fun AppBehaviorContent(
    appConfig: IslandConfig,
    globalConfig: IslandConfig,
    onUpdate: (IslandConfig) -> Unit,
    activeDesc: String,
    inactiveDesc: String
) {
    val isUsingGlobal = !appConfig.hasOverrides()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // "Use Global Defaults" toggle as the FIRST option in its own separate container Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isUsingGlobal) {
                            onUpdate(globalConfig)
                        } else {
                            onUpdate(IslandConfig())
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.use_global_default),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isUsingGlobal) {
                            stringResource(R.string.appearance_use_defaults_desc)
                        } else {
                            stringResource(R.string.custom_behavior_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                HpSwitch(
                    checked = isUsingGlobal,
                    onCheckedChange = { useGlobal ->
                        if (useGlobal) {
                            onUpdate(IslandConfig())
                        } else {
                            onUpdate(globalConfig)
                        }
                    },
                    modifier = Modifier.semantics {
                        stateDescription = if (isUsingGlobal) activeDesc else inactiveDesc
                    }
                )
            }
        }

        // Elements show current configuration (global values if using global, or custom if customized)
        // If user moves or changes anything, onUpdate is called with isFloat set to non-null,
        // automatically turning the toggle to custom and saving as custom.
        IslandSettingsControl(
            config = appConfig,
            defaultConfig = globalConfig,
            onUpdate = onUpdate
        )
    }
}

// ------------------------------------------------------------------------------------------------
// BLOCKED TERMS CONTENT
// Input box container on top, and vertical list of blocked terms below in separate containers.
// ------------------------------------------------------------------------------------------------

@Composable
fun AppConfigBlockedTermsContent(
    terms: Set<String>,
    onUpdate: (Set<String>) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Container 1: Input Box Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.blocked_terms),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.blocked_terms_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.add_blocked_word)) },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (text.isNotBlank()) {
                                    onUpdate(terms + text.trim())
                                    text = ""
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.add),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotBlank()) {
                            onUpdate(terms + text.trim())
                            text = ""
                            keyboardController?.hide()
                        }
                    }),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }

        // Container 2: Vertical list of blocked terms below the box
        val termsList = terms.toList()
        if (termsList.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.no_blocked_terms),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.no_blocked_terms_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                termsList.forEachIndexed { _, term ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(Modifier.width(14.dp))

                            Text(
                                text = term,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { onUpdate(terms - term) },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.remove)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// FUTURE FEATURE PLACEHOLDER (Custom Design & Custom Translators)
// ------------------------------------------------------------------------------------------------

@Composable
fun FutureFeaturePlaceholderCard(
    title: String,
    badge: String,
    icon: ImageVector,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(14.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Track progress on GitHub: Issues #271, #272, #273.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) return drawable.bitmap
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

// ------------------------------------------------------------------------------------------------
// PREVIEWS
// ------------------------------------------------------------------------------------------------

@Preview(name = "Overview", showBackground = true)
@Composable
fun AppConfigOverviewPreview() {
    HyperPopTheme {
        SampleAppConfigContent(currentSubscreen = null)
    }
}

@Preview(name = "Subscreen: Notification Types", showBackground = true)
@Composable
fun AppConfigNotificationTypesPreview() {
    HyperPopTheme {
        SampleAppConfigContent(currentSubscreen = AppConfigSubscreen.NOTIFICATION_TYPES)
    }
}

@Preview(name = "Subscreen: Island Behavior", showBackground = true)
@Composable
fun AppConfigIslandBehaviorPreview() {
    HyperPopTheme {
        SampleAppConfigContent(currentSubscreen = AppConfigSubscreen.ISLAND_BEHAVIOR)
    }
}

@Preview(name = "Subscreen: Blocked Terms", showBackground = true)
@Composable
fun AppConfigBlockedTermsPreview() {
    HyperPopTheme {
        SampleAppConfigContent(currentSubscreen = AppConfigSubscreen.BLOCKED_TERMS)
    }
}

@Preview(name = "Subscreen: Custom Translators", showBackground = true)
@Composable
fun AppConfigCustomTranslatorsPreview() {
    HyperPopTheme {
        SampleAppConfigContent(currentSubscreen = AppConfigSubscreen.CUSTOM_TRANSLATORS)
    }
}

@Composable
private fun SampleAppConfigContent(currentSubscreen: AppConfigSubscreen?) {
    AppConfigContent(
        appName = "Spotify",
        packageName = "com.spotify.music",
        appIcon = null,
        isBridged = true,
        activeTypes = setOf(NotificationType.MEDIA.name, NotificationType.MESSAGE.name),
        activeCallStages = CallStage.entries.toSet(),
        appIslandConfig = IslandConfig(firstFloat = true, isShowShade = true, timeout = 5),
        globalConfig = IslandConfig(firstFloat = true, isShowShade = true, timeout = 5),
        blockedTerms = setOf("Ad", "Promo"),
        currentSubscreen = currentSubscreen,
        onNavigateSubscreen = {},
        onBack = {},
        onToggleBridged = {},
        onToggleType = { _, _ -> },
        onToggleCallStage = { _, _ -> },
        onUpdateIslandConfig = {},
        onUpdateBlockedTerms = {},
        onNavConfigClick = {}
    )
}
