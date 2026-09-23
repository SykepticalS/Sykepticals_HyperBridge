package com.d4viddf.hyperbridge.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.models.GlowMode
import com.d4viddf.hyperbridge.models.IslandConfig
import com.d4viddf.hyperbridge.models.IslandSceneBehavior
import com.d4viddf.hyperbridge.models.IslandTextContent
import com.d4viddf.hyperbridge.models.MarqueeDismissMode
import com.d4viddf.hyperbridge.ui.screens.theme.ShapeStyle
import com.d4viddf.hyperbridge.ui.screens.theme.getExpressiveShape
import com.d4viddf.hyperbridge.ui.theme.HyperBridgeTheme
import kotlin.math.roundToInt

val timeoutSteps = listOf(
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 45,
    60, 300, 900, 1800, 3600
)
private val timePopUpSteps = listOf(
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30
)

@Composable
fun IslandSettingsControl(
    config: IslandConfig,
    defaultConfig: IslandConfig? = null,
    onUpdate: (IslandConfig) -> Unit
) {
    val displayConfig = if (defaultConfig != null) config.mergeWith(defaultConfig) else config

    val currentTimeout = displayConfig.timeout ?: 10
    val isTimeoutEnabled = currentTimeout > 0
    val isFloatEnabled = displayConfig.firstFloat ?: true
    val currentFloatTimeout = displayConfig.floatTimeout ?: 10
    val removeOriginalOn = displayConfig.removeOriginalNotification == true
    val showLeftCustom = displayConfig.leftContent == IslandTextContent.CUSTOM
    val showRightCustom = displayConfig.rightContent == IslandTextContent.CUSTOM
    val contentGroupSize = 2 + (if (showLeftCustom) 1 else 0) + (if (showRightCustom) 1 else 0)

    Column {
        SectionLabel(stringResource(R.string.global_behavior), first = true)
        SettingsCard(shape = groupedShape(1, 0)) {
            SettingsRow(
                icon = Icons.Default.AccessTime,
                title = stringResource(R.string.auto_hide_island),
                subtitle = if (isTimeoutEnabled) {
                    stringResource(R.string.hides_after_a_set_time)
                } else {
                    stringResource(R.string.behavior_hide_desc)
                },
                trailing = {
                    Switch(
                        checked = isTimeoutEnabled,
                        onCheckedChange = { enabled ->
                            val newTimeout = if (enabled) 5 else 0
                            onUpdate(config.copy(timeout = newTimeout))
                        }
                    )
                }
            )
            AnimatedVisibility(
                visible = isTimeoutEnabled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                DiscreteTimeoutSlider(
                    steps = timeoutSteps,
                    currentSeconds = currentTimeout,
                    valueLabel = { formatSeconds(it) },
                    description = stringResource(R.string.behavior_desc_hide_long),
                    onCommit = { selectedSeconds ->
                        onUpdate(config.copy(timeout = selectedSeconds))
                    }
                )
            }
        }

        SectionLabel(stringResource(R.string.xiaomi_featured_notifications))
        SettingsStack {
            SettingsCard(shape = groupedShape(3, 0)) {
                SettingsRow(
                    icon = Icons.Default.Visibility,
                    title = stringResource(R.string.setting_float),
                    subtitle = stringResource(R.string.setting_float_desc),
                    trailing = {
                        Switch(
                            checked = isFloatEnabled,
                            onCheckedChange = { onUpdate(config.copy(firstFloat = it)) }
                        )
                    }
                )
                AnimatedVisibility(
                    visible = isFloatEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    DiscreteTimeoutSlider(
                        steps = timePopUpSteps,
                        currentSeconds = currentFloatTimeout,
                        valueLabel = { stringResource(R.string.seconds_suffix, it) },
                        description = stringResource(R.string.setting_float_timeout_desc),
                        onCommit = { onUpdate(config.copy(floatTimeout = it)) }
                    )
                }
            }
            SettingsToggleCard(
                title = stringResource(R.string.setting_shade),
                subtitle = stringResource(R.string.setting_shade_desc),
                icon = Icons.Default.Layers,
                checked = displayConfig.isShowShade ?: false,
                onCheckedChange = { onUpdate(config.copy(isShowShade = it)) },
                shape = groupedShape(3, 1)
            )
            SettingsToggleCard(
                title = "Expand on updates",
                subtitle = "Expand the island again when an existing notification changes.",
                icon = Icons.Default.Update,
                checked = displayConfig.floatOnUpdate == true,
                onCheckedChange = { onUpdate(config.copy(floatOnUpdate = it)) },
                shape = groupedShape(3, 2)
            )
        }

        SectionLabel("Scrolling")
        SettingsStack {
            InheritedBooleanSettingCard(
                title = "Scrolling text",
                subtitle = "Scroll overflowing collapsed-island text in SystemUI.",
                icon = Icons.Default.TextFields,
                rawValue = config.marqueeEnabled,
                displayValue = displayConfig.marqueeEnabled == true,
                allowInherit = defaultConfig != null,
                shape = groupedShape(2, 0),
                onChange = { onUpdate(config.copy(marqueeEnabled = it)) },
            )
            EnumSettingCard(
                title = "Hide after scrolling",
                subtitle = "Dismiss the island after the text has finished scrolling.",
                icon = Icons.AutoMirrored.Filled.Notes,
                value = displayConfig.marqueeDismissMode ?: MarqueeDismissMode.OFF,
                values = MarqueeDismissMode.entries,
                shape = groupedShape(2, 1),
                onChange = { onUpdate(config.copy(marqueeDismissMode = it)) },
                label = { mode ->
                    when (mode) {
                        MarqueeDismissMode.OFF -> "Off"
                        MarqueeDismissMode.AFTER_ONE -> "After 1 scroll"
                        MarqueeDismissMode.AFTER_TWO -> "After 2 scrolls"
                        MarqueeDismissMode.AFTER_ONE_OVERRIDE_TIMEOUT -> "After 1 scroll, hide immediately"
                        MarqueeDismissMode.AFTER_TWO_OVERRIDE_TIMEOUT -> "After 2 scrolls, hide immediately"
                        MarqueeDismissMode.WAIT_FOR_RIGHT_SCROLL -> "Hold timeout until right text finishes"
                    }
                }
            )
        }

        SectionLabel("Island Content")
        SettingsStack {
            EnumSettingCard(
                title = "Left content",
                subtitle = "What appears on the left side of the collapsed island.",
                icon = Icons.AutoMirrored.Filled.Notes,
                value = displayConfig.leftContent ?: IslandTextContent.AUTOMATIC,
                values = IslandTextContent.entries,
                shape = groupedShape(contentGroupSize, 0),
                onChange = { onUpdate(config.copy(leftContent = it)) }
            )
            EnumSettingCard(
                title = "Right content",
                subtitle = "What appears on the right side of the collapsed island.",
                icon = Icons.AutoMirrored.Filled.Notes,
                value = displayConfig.rightContent ?: IslandTextContent.AUTOMATIC,
                values = IslandTextContent.entries,
                shape = groupedShape(contentGroupSize, 1),
                onChange = { onUpdate(config.copy(rightContent = it)) }
            )
            if (showLeftCustom) {
                CompactTextSetting(
                    title = "Left expression",
                    value = displayConfig.leftCustomExpression.orEmpty(),
                    shape = groupedShape(contentGroupSize, 2),
                    onChange = { onUpdate(config.copy(leftCustomExpression = it)) }
                )
            }
            if (showRightCustom) {
                CompactTextSetting(
                    title = "Right expression",
                    value = displayConfig.rightCustomExpression.orEmpty(),
                    shape = groupedShape(contentGroupSize, if (showLeftCustom) 3 else 2),
                    onChange = { onUpdate(config.copy(rightCustomExpression = it)) }
                )
            }
        }

        SectionLabel("Outer Glow")
        SettingsStack {
            InheritedEnumSettingCard(
                title = "Island outer glow",
                subtitle = "Glow around the small island and the compact island.",
                icon = Icons.Default.LightMode,
                rawValue = config.islandGlowMode,
                displayValue = displayConfig.islandGlowMode ?: GlowMode.OFF,
                values = GlowMode.entries,
                allowInherit = defaultConfig != null,
                shape = groupedShape(7, 0),
                onChange = { onUpdate(config.copy(islandGlowMode = it)) },
                label = ::prettyGlowLabel,
            )
            CompactTextSetting(
                title = "Island glow color",
                value = displayConfig.islandGlowColor.orEmpty(),
                icon = Icons.Default.Palette,
                shape = groupedShape(7, 1),
                onChange = { onUpdate(config.copy(islandGlowColor = it.ifBlank { null })) }
            )
            SettingsToggleCard(
                title = "Force island glow",
                subtitle = "Keep the collapsed glow visible when Xiaomi would fade it.",
                icon = Icons.Default.LightMode,
                checked = displayConfig.forceIslandGlow == true,
                onCheckedChange = { onUpdate(config.copy(forceIslandGlow = it)) },
                shape = groupedShape(7, 2)
            )
            InheritedEnumSettingCard(
                title = "Focus / expanded outer glow",
                subtitle = "Glow around the expanded island.",
                icon = Icons.Default.LightMode,
                rawValue = config.focusGlowMode,
                displayValue = displayConfig.focusGlowMode ?: GlowMode.OFF,
                values = GlowMode.entries,
                allowInherit = defaultConfig != null,
                shape = groupedShape(7, 3),
                onChange = { onUpdate(config.copy(focusGlowMode = it)) },
                label = ::prettyGlowLabel,
            )
            CompactTextSetting(
                title = "Focus glow color",
                value = displayConfig.focusGlowColor.orEmpty(),
                icon = Icons.Default.Palette,
                shape = groupedShape(7, 4),
                onChange = { onUpdate(config.copy(focusGlowColor = it.ifBlank { null })) }
            )
            SettingsToggleCard(
                title = "Force focus glow",
                subtitle = "Keep the expanded glow visible when Xiaomi would fade it.",
                icon = Icons.Default.LightMode,
                checked = displayConfig.forceFocusGlow == true,
                onCheckedChange = { onUpdate(config.copy(forceFocusGlow = it)) },
                shape = groupedShape(7, 5)
            )
            SettingsToggleCard(
                title = "Pink glow for levixcs / Nisamm",
                subtitle = "Always use pink glow for those names, even when outer glow is off.",
                icon = Icons.Default.Palette,
                checked = displayConfig.contactPinkGlow == true,
                onCheckedChange = { onUpdate(config.copy(contactPinkGlow = it)) },
                shape = groupedShape(7, 6)
            )
        }

        SectionLabel("System Behavior")
        SettingsStack {
            SettingsToggleCard(
                title = "Restore native lockscreen behavior",
                subtitle = "Do not replace private content while the lockscreen requires redaction.",
                icon = Icons.Default.Lock,
                checked = displayConfig.restoreLockscreen == true,
                onCheckedChange = { onUpdate(config.copy(restoreLockscreen = it)) },
                shape = groupedShape(4, 0)
            )
            EnumSettingCard(
                title = "Do not disturb",
                subtitle = "How the island behaves while DND is active.",
                icon = Icons.Default.DoNotDisturb,
                value = displayConfig.dndBehavior ?: IslandSceneBehavior.SUPPRESS,
                values = IslandSceneBehavior.entries,
                shape = groupedShape(4, 1),
                onChange = { onUpdate(config.copy(dndBehavior = it)) }
            )
            EnumSettingCard(
                title = "Fullscreen",
                subtitle = "How the island behaves in fullscreen apps.",
                icon = Icons.Default.Fullscreen,
                value = displayConfig.fullscreenBehavior ?: IslandSceneBehavior.DEFAULT,
                values = IslandSceneBehavior.entries,
                shape = groupedShape(4, 2),
                onChange = { onUpdate(config.copy(fullscreenBehavior = it)) }
            )
            EnumSettingCard(
                title = "Landscape",
                subtitle = "How the island behaves in landscape orientation.",
                icon = Icons.Default.ScreenRotation,
                value = displayConfig.landscapeBehavior ?: IslandSceneBehavior.DEFAULT,
                values = IslandSceneBehavior.entries,
                shape = groupedShape(4, 3),
                onChange = { onUpdate(config.copy(landscapeBehavior = it)) }
            )
        }

        SectionLabel(stringResource(R.string.notification_management))
        SettingsStack {
            SettingsToggleCard(
                title = stringResource(R.string.remove_original_notification),
                subtitle = stringResource(R.string.remove_original_notification_desc),
                icon = Icons.Default.DeleteSweep,
                checked = removeOriginalOn,
                onCheckedChange = { onUpdate(config.copy(removeOriginalNotification = it)) },
                shape = groupedShape(3, 0)
            )
            SettingsToggleCard(
                title = stringResource(R.string.dismiss_with_original),
                subtitle = stringResource(R.string.dismiss_with_original_desc),
                icon = Icons.Default.DeleteSweep,
                checked = displayConfig.dismissWithOriginal ?: false,
                enabled = !removeOriginalOn,
                onCheckedChange = { onUpdate(config.copy(dismissWithOriginal = it)) },
                shape = groupedShape(3, 1)
            )
            SettingsToggleCard(
                title = stringResource(R.string.enable_inline_reply),
                subtitle = stringResource(R.string.enable_inline_reply_desc),
                icon = Icons.AutoMirrored.Filled.Reply,
                checked = displayConfig.enableInlineReply ?: true,
                enabled = !removeOriginalOn,
                onCheckedChange = { onUpdate(config.copy(enableInlineReply = it)) },
                shape = groupedShape(3, 2)
            )
        }
        AnimatedVisibility(
            visible = removeOriginalOn,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = stringResource(R.string.remove_original_notification_hidden_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DiscreteTimeoutSlider(
    steps: List<Int>,
    currentSeconds: Int,
    valueLabel: @Composable (Int) -> String,
    description: String,
    onCommit: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var sliderIndex by remember {
        mutableFloatStateOf(stepIndex(steps, currentSeconds))
    }

    LaunchedEffect(currentSeconds) {
        if (!dragging) {
            sliderIndex = stepIndex(steps, currentSeconds)
        }
    }

    val displaySeconds = steps[sliderIndex.roundToInt().coerceIn(0, steps.lastIndex)]

    Column(modifier = Modifier.padding(start = 56.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        Text(
            text = valueLabel(displaySeconds),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = sliderIndex,
            onValueChange = { index ->
                dragging = true
                sliderIndex = index
            },
            onValueChangeFinished = {
                dragging = false
                val selectedSeconds = steps[sliderIndex.roundToInt().coerceIn(0, steps.lastIndex)]
                onCommit(selectedSeconds)
            },
            valueRange = 0f..(steps.size - 1).toFloat(),
            steps = (steps.size - 2).coerceAtLeast(0)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun stepIndex(steps: List<Int>, currentSeconds: Int): Float =
    steps.indexOf(currentSeconds).coerceAtLeast(0).toFloat()

@Composable
fun SectionLabel(title: String, first: Boolean = false) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = if (first) 4.dp else 20.dp,
            bottom = 8.dp
        )
    )
}

@Composable
fun SettingsStack(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

private fun groupedShape(groupSize: Int, index: Int): Shape =
    getExpressiveShape(groupSize, index, ShapeStyle.Large)

@Composable
fun SettingsCard(
    shape: Shape,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun <T : Enum<T>> EnumSettingCard(
    title: String,
    value: T,
    values: List<T>,
    shape: Shape,
    onChange: (T) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    label: (T) -> String = { prettyEnumLabel(it) },
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = { expanded = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            SettingsRow(
                icon = icon ?: Icons.AutoMirrored.Filled.Notes,
                title = title,
                subtitle = subtitle ?: label(value),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subtitle != null) {
                            Text(
                                text = label(value),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                values.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(label(option)) },
                        onClick = {
                            expanded = false
                            onChange(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTextSetting(
    title: String,
    value: String,
    shape: Shape,
    onChange: (String) -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Filled.Notes,
) {
    SettingsCard(shape = shape) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(icon)
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    label = { Text(title) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    }
}

@Composable
private fun InheritedBooleanSettingCard(
    title: String,
    rawValue: Boolean?,
    displayValue: Boolean,
    allowInherit: Boolean,
    shape: Shape,
    onChange: (Boolean?) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = when {
        allowInherit && rawValue == null -> "Use global"
        displayValue -> "On"
        else -> "Off"
    }
    Card(
        onClick = { expanded = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            SettingsRow(
                icon = icon ?: Icons.AutoMirrored.Filled.Notes,
                title = title,
                subtitle = subtitle ?: shown,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subtitle != null) {
                            Text(
                                text = shown,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (allowInherit) {
                    DropdownMenuItem(
                        text = { Text("Use global") },
                        onClick = {
                            expanded = false
                            onChange(null)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("On") },
                    onClick = {
                        expanded = false
                        onChange(true)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Off") },
                    onClick = {
                        expanded = false
                        onChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun <T : Enum<T>> InheritedEnumSettingCard(
    title: String,
    rawValue: T?,
    displayValue: T,
    values: List<T>,
    allowInherit: Boolean,
    shape: Shape,
    onChange: (T?) -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    label: (T) -> String = { prettyEnumLabel(it) },
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (allowInherit && rawValue == null) "Use global" else label(displayValue)
    Card(
        onClick = { expanded = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            SettingsRow(
                icon = icon ?: Icons.AutoMirrored.Filled.Notes,
                title = title,
                subtitle = subtitle ?: shown,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subtitle != null) {
                            Text(
                                text = shown,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (allowInherit) {
                    DropdownMenuItem(
                        text = { Text("Use global") },
                        onClick = {
                            expanded = false
                            onChange(null)
                        }
                    )
                }
                values.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(label(option)) },
                        onClick = {
                            expanded = false
                            onChange(option)
                        }
                    )
                }
            }
        }
    }
}

private fun prettyGlowLabel(mode: GlowMode): String = when (mode) {
    GlowMode.OFF -> "Off"
    GlowMode.ON -> "On"
    GlowMode.FOLLOW_DYNAMIC -> "Follow app color"
}

private fun prettyEnumLabel(value: Enum<*>): String =
    value.name.lowercase().replace('_', ' ').replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }

fun formatSeconds(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h"
    }
}

@Composable
fun SettingsToggleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    shape: Shape,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        onClick = { if (enabled) onCheckedChange(!checked) },
        enabled = enabled,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        SettingsRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            enabled = enabled,
            trailing = {
                Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun IslandSettingsControlPreview() {
    HyperBridgeTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            IslandSettingsControl(
                config = IslandConfig(
                    firstFloat = true,
                    timeout = 5,
                    floatTimeout = 5,
                    isShowShade = true,
                    removeOriginalNotification = false,
                    dismissWithOriginal = false
                ),
                onUpdate = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsToggleCardPreview() {
    HyperBridgeTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            SettingsToggleCard(
                title = "Example Title",
                subtitle = "Example subtitle for the toggle card",
                icon = Icons.Default.Layers,
                checked = true,
                shape = RoundedCornerShape(24.dp),
                onCheckedChange = {}
            )
        }
    }
}
