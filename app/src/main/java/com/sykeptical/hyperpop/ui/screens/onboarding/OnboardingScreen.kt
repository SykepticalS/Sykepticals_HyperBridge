package com.sykeptical.hyperpop.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.HyperPopApplication
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.island.backend.HookConfigSync
import com.sykeptical.hyperpop.island.backend.IslandProtocol
import com.sykeptical.hyperpop.island.backend.SystemUiEngineCommands
import com.sykeptical.hyperpop.island.backend.SystemUiIslandBackend
import com.sykeptical.hyperpop.models.GlowMode
import com.sykeptical.hyperpop.root.RootShellService
import com.sykeptical.hyperpop.ui.system.CyclingIslandDemo
import com.sykeptical.hyperpop.ui.system.HpButton
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpSegmented
import com.sykeptical.hyperpop.ui.system.HpStatusRow
import com.sykeptical.hyperpop.ui.system.HpSwitchRow
import com.sykeptical.hyperpop.ui.system.HyperPopColor
import com.sykeptical.hyperpop.ui.system.HyperPopMotion
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import com.sykeptical.hyperpop.ui.system.IslandDemo
import com.sykeptical.hyperpop.ui.system.IslandDemoKind
import com.sykeptical.hyperpop.ui.system.LocalReducedMotion
import com.sykeptical.hyperpop.ui.system.motionMillis
import com.sykeptical.hyperpop.util.DeviceUtils
import com.sykeptical.hyperpop.xposed.runtime.ModuleServiceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }
    var page by remember { mutableIntStateOf(0) }
    val reduced = LocalReducedMotion.current
    val duration = motionMillis(HyperPopMotion.page, reduced)
    val shift by animateDpAsState(
        targetValue = (page * 28).dp,
        animationSpec = tween(duration),
        label = "onboardingField",
    )
    val dim by animateFloatAsState(
        targetValue = 0.18f + page * 0.08f,
        animationSpec = tween(duration),
        label = "onboardingDim",
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = -shift.toPx() }
                .background(HyperPopColor.accent.copy(alpha = dim))
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
                .graphicsLayer { translationX = shift.toPx() / 2 }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
                .height(180.dp)
                .fillMaxWidth(0.7f)
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                slideInHorizontally(tween(duration)) { it / 4 } + fadeIn(tween(duration)) togetherWith
                    slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration))
            },
            label = "onboardingPage",
        ) { step ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HyperPopSpace.screen)
                    .padding(top = 28.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                when (step) {
                    0 -> WelcomePage()
                    1 -> ActionPage()
                    2 -> SystemPage()
                    3 -> ExperiencePage(prefs)
                    4 -> LookPage(prefs)
                    else -> ReadyPage()
                }
                Spacer(Modifier.height(24.dp))
                val last = step == 5
                HpButton(
                    text = stringResource(if (last) R.string.onboarding_enter else R.string.onboarding_next),
                    onClick = {
                        if (!last) {
                            page += 1
                        } else {
                            scope.launch {
                                SystemUiEngineCommands.reload(context)
                                onFinish()
                            }
                        }
                    },
                    enabled = step != 2 || systemReady(),
                )
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column {
        CyclingIslandDemo(glow = false, animated = true)
        Spacer(Modifier.height(36.dp))
        Text(stringResource(R.string.onboarding_welcome_title), style = HyperPopType.display)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_body),
            style = HyperPopType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionPage() {
    Column {
        IslandDemo(IslandDemoKind.Message)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_action_title), style = HyperPopType.largeTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboarding_action_body),
            style = HyperPopType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        CyclingIslandDemo()
    }
}

@Composable
private fun systemReady(): Boolean {
    val context = LocalContext.current
    val module by ModuleServiceState.state.collectAsState()
    val backend = remember { SystemUiIslandBackend.get(context) }
    var rootReady by remember { mutableStateOf<Boolean?>(null) }
    var health by remember { mutableStateOf(backend.health()) }
    LaunchedEffect(Unit) {
        rootReady = RootShellService.isAvailable()
        backend.ping()
        health = backend.health()
    }
    val scopes = IslandProtocol.SYSTEM_UI_PACKAGE in module.scopes && IslandProtocol.XMSF_PACKAGE in module.scopes
    return DeviceUtils.isXiaomi && DeviceUtils.isCompatibleOS() && rootReady == true &&
        module.available && module.apiVersion >= 101 && scopes && health.available
}

@Composable
private fun SystemPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val module by ModuleServiceState.state.collectAsState()
    val backend = remember { SystemUiIslandBackend.get(context) }
    var rootReady by remember { mutableStateOf<Boolean?>(null) }
    var health by remember { mutableStateOf(backend.health()) }
    var details by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        rootReady = RootShellService.isAvailable()
        while (true) {
            backend.ping()
            health = backend.health()
            delay(2000)
        }
    }
    val deviceOk = DeviceUtils.isXiaomi && DeviceUtils.isCompatibleOS()
    val moduleOk = module.available && module.apiVersion >= 101
    val systemUi = IslandProtocol.SYSTEM_UI_PACKAGE in module.scopes
    val xmsf = IslandProtocol.XMSF_PACKAGE in module.scopes
    val islands = health.available
    val ready = deviceOk && rootReady == true && moduleOk && systemUi && xmsf && islands

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.onboarding_system_title), style = HyperPopType.largeTitle)
        Text(
            if (ready) stringResource(R.string.onboarding_system_ok) else stringResource(R.string.onboarding_system_problem),
            style = HyperPopType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.onboarding_system_body),
            style = HyperPopType.secondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HpGroup {
            HpStatusRow(stringResource(R.string.onboarding_system_title), if (ready) stringResource(R.string.onboarding_system_ok) else stringResource(R.string.onboarding_system_problem), ok = ready)
        }
        if (!systemUi || !xmsf) {
            HpButton(stringResource(R.string.onboarding_request_scopes), onClick = {
                (context.applicationContext as? HyperPopApplication)?.requestRequiredScopes { result ->
                    message = result.exceptionOrNull()?.message
                }
            })
        }
        HpButton(
            stringResource(R.string.onboarding_restart_scopes),
            onClick = {
                scope.launch {
                    val result = RootShellService.restartPackages(
                        setOf(IslandProtocol.SYSTEM_UI_PACKAGE, IslandProtocol.XMSF_PACKAGE),
                    )
                    message = if (result.success) null else result.stderr
                    backend.ping()
                }
            },
            enabled = rootReady == true,
        )
        Text(
            if (details) stringResource(R.string.onboarding_hide_details) else stringResource(R.string.onboarding_details),
            style = HyperPopType.button,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { details = !details }.padding(8.dp),
        )
        if (details) {
            HpGroup {
                HpStatusRow("HyperOS", if (deviceOk) "OK" else "No", ok = deviceOk)
                HpStatusRow("Root", if (rootReady == true) "OK" else "No", ok = rootReady == true)
                HpStatusRow("Module", if (moduleOk) "OK" else "No", ok = moduleOk)
                HpStatusRow("SystemUI", if (systemUi) "OK" else "No", ok = systemUi)
                HpStatusRow("XMSF", if (xmsf) "OK" else "No", ok = xmsf)
                HpStatusRow("Islands", if (islands) "OK" else "No", ok = islands)
            }
        }
        message?.let { Text(it, style = HyperPopType.caption, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ExperiencePage(prefs: AppPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val login by prefs.loginCodeSettingsFlow.collectAsState(initial = prefs.getLoginCodeSettingsSync())
    var animations by remember { mutableStateOf(HookConfigSync.betterAnimationsEnabled(context)) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        IslandDemo(IslandDemoKind.Music, animated = animations)
        Text(stringResource(R.string.onboarding_experience_title), style = HyperPopType.largeTitle)
        Text(stringResource(R.string.onboarding_experience_body), style = HyperPopType.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HpGroup {
            HpSwitchRow(
                title = stringResource(R.string.better_animations),
                subtitle = stringResource(R.string.better_animations_desc),
                checked = animations,
                onCheckedChange = {
                    animations = it
                    HookConfigSync.setBetterAnimationsEnabled(context, it)
                },
            )
            HpSwitchRow(
                title = stringResource(R.string.login_code_enabled),
                subtitle = stringResource(R.string.login_code_enabled_desc),
                checked = login.enabled,
                onCheckedChange = { enabled -> scope.launch { prefs.setLoginCodeEnabled(enabled) } },
            )
        }
    }
}

@Composable
private fun LookPage(prefs: AppPreferences) {
    val scope = rememberCoroutineScope()
    val config by prefs.globalConfigFlow.collectAsState(initial = null)
    val mode = config?.islandGlowMode ?: GlowMode.OFF
    val labels = listOf(
        stringResource(R.string.glow_off),
        stringResource(R.string.glow_on),
        stringResource(R.string.glow_follow),
    )
    val selected = when (mode) {
        GlowMode.OFF -> 0
        GlowMode.ON -> 1
        GlowMode.FOLLOW_DYNAMIC -> 2
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        IslandDemo(IslandDemoKind.Message, glow = mode != GlowMode.OFF)
        Text(stringResource(R.string.onboarding_look_title), style = HyperPopType.largeTitle)
        Text(stringResource(R.string.onboarding_look_body), style = HyperPopType.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HpSegmented(
            options = labels,
            selected = selected,
            onSelect = { index ->
            val next = when (index) {
                0 -> GlowMode.OFF
                1 -> GlowMode.ON
                else -> GlowMode.FOLLOW_DYNAMIC
            }
            val current = config ?: com.sykeptical.hyperpop.models.IslandConfig()
            scope.launch { prefs.updateGlobalConfig(current.copy(islandGlowMode = next)) }
            },
        )
    }
}

@Composable
private fun ReadyPage() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CyclingIslandDemo(glow = true, animated = true)
        Text(stringResource(R.string.onboarding_ready_title), style = HyperPopType.display)
        Text(stringResource(R.string.onboarding_ready_body), style = HyperPopType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
