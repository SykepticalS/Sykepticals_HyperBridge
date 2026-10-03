package com.sykeptical.hyperpop.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R
import com.sykeptical.hyperpop.data.AppPreferences
import com.sykeptical.hyperpop.models.NavContent
import com.sykeptical.hyperpop.ui.components.NavDropdown
import com.sykeptical.hyperpop.ui.components.NavPreview
import com.sykeptical.hyperpop.ui.system.HpGroup
import com.sykeptical.hyperpop.ui.system.HpScaffold
import com.sykeptical.hyperpop.ui.system.HpSectionTitle
import com.sykeptical.hyperpop.ui.system.HyperPopSpace
import com.sykeptical.hyperpop.ui.system.HyperPopType
import kotlinx.coroutines.launch

@Composable
fun NavCustomizationScreen(
    onBack: () -> Unit,
    packageName: String? = null,
    showTopBar: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { AppPreferences(context) }

    val globalLayout by preferences.globalNavLayoutFlow.collectAsState(initial = NavContent.DISTANCE_ETA to NavContent.INSTRUCTION)

    val appLayout by if (packageName != null) {
        preferences.getAppNavLayout(packageName).collectAsState(initial = null to null)
    } else {
        remember { mutableStateOf<Pair<NavContent?, NavContent?>>(null to null) }
    }

    val isGlobalMode = packageName == null
    val isUsingGlobalDefault = !isGlobalMode && appLayout.first == null
    val currentLeft = appLayout.first ?: globalLayout.first
    val currentRight = appLayout.second ?: globalLayout.second

    if (showTopBar) {
        HpScaffold(title = stringResource(R.string.nav_layout_title), onBack = onBack) { padding ->
            NavCustomizationBody(
                padding = padding,
                isGlobalMode = isGlobalMode,
                isUsingGlobalDefault = isUsingGlobalDefault,
                currentLeft = currentLeft,
                currentRight = currentRight,
                onToggleGlobalDefault = {
                    scope.launch {
                        val pkg = packageName ?: return@launch
                        if (isUsingGlobalDefault) {
                            preferences.updateAppNavLayout(pkg, globalLayout.first, globalLayout.second)
                        } else {
                            preferences.updateAppNavLayout(pkg, null, null)
                        }
                    }
                },
                onSelectLeft = { newLeft ->
                    scope.launch {
                        if (isGlobalMode) preferences.setGlobalNavLayout(newLeft, currentRight)
                        else packageName?.let { preferences.updateAppNavLayout(it, newLeft, currentRight) }
                    }
                },
                onSelectRight = { newRight ->
                    scope.launch {
                        if (isGlobalMode) preferences.setGlobalNavLayout(currentLeft, newRight)
                        else packageName?.let { preferences.updateAppNavLayout(it, currentLeft, newRight) }
                    }
                },
            )
        }
    } else {
        NavCustomizationBody(
            padding = PaddingValues(0.dp),
            isGlobalMode = isGlobalMode,
            isUsingGlobalDefault = isUsingGlobalDefault,
            currentLeft = currentLeft,
            currentRight = currentRight,
            onToggleGlobalDefault = {
                scope.launch {
                    val pkg = packageName ?: return@launch
                    if (isUsingGlobalDefault) {
                        preferences.updateAppNavLayout(pkg, globalLayout.first, globalLayout.second)
                    } else {
                        preferences.updateAppNavLayout(pkg, null, null)
                    }
                }
            },
            onSelectLeft = { newLeft ->
                scope.launch {
                    if (isGlobalMode) preferences.setGlobalNavLayout(newLeft, currentRight)
                    else packageName?.let { preferences.updateAppNavLayout(it, newLeft, currentRight) }
                }
            },
            onSelectRight = { newRight ->
                scope.launch {
                    if (isGlobalMode) preferences.setGlobalNavLayout(currentLeft, newRight)
                    else packageName?.let { preferences.updateAppNavLayout(it, currentLeft, newRight) }
                }
            },
        )
    }
}

@Composable
private fun NavCustomizationBody(
    padding: PaddingValues,
    isGlobalMode: Boolean,
    isUsingGlobalDefault: Boolean,
    currentLeft: NavContent,
    currentRight: NavContent,
    onToggleGlobalDefault: () -> Unit,
    onSelectLeft: (NavContent) -> Unit,
    onSelectRight: (NavContent) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyperPopSpace.screen)
            .padding(bottom = 16.dp)
    ) {
        HpSectionTitle("Preview", first = true)
        NavPreview(currentLeft, currentRight)

        HpSectionTitle(stringResource(R.string.group_configuration))

        if (!isGlobalMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleGlobalDefault)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isUsingGlobalDefault, onCheckedChange = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.use_global_default), style = HyperPopType.settingLabel)
            }
        }

        val controlsEnabled = isGlobalMode || !isUsingGlobalDefault
        if (controlsEnabled) {
            HpGroup {
                Column(Modifier.padding(HyperPopSpace.rowHorizontal)) {
                    NavDropdown(
                        label = stringResource(R.string.left_content),
                        selected = currentLeft,
                        onSelect = onSelectLeft
                    )
                    Spacer(Modifier.height(16.dp))
                    NavDropdown(
                        label = stringResource(R.string.right_content),
                        selected = currentRight,
                        onSelect = onSelectRight
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(HyperPopSpace.groupGap))
        HpGroup {
            Column(Modifier.padding(HyperPopSpace.rowHorizontal)) {
                Text(
                    text = stringResource(R.string.good_to_know),
                    style = HyperPopType.settingLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.nav_layout_info),
                    style = HyperPopType.settingDescription,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
