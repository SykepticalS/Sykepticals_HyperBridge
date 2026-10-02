package com.sykeptical.hyperpop.ui.system

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HpScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, style = HyperPopType.largeTitle) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        content = content,
    )
}

@Composable
fun HpSectionTitle(text: String, first: Boolean = false) {
    Text(
        text = text,
        style = HyperPopType.section,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (first) 4.dp else HyperPopSpace.sectionTop,
                bottom = HyperPopSpace.sectionBottom,
            )
            .semantics { heading() },
    )
}

@Composable
fun HpGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HyperPopSize.radius))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
fun HpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val background = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        destructive -> HyperPopColor.danger
        else -> MaterialTheme.colorScheme.primary
    }
    val foreground = if (enabled) HyperPopColor.onAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HyperPopSize.buttonHeight)
            .clip(RoundedCornerShape(HyperPopSize.radius))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HyperPopType.button, color = foreground)
    }
}

@Composable
fun HpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactive: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val duration = motionMillis(HyperPopMotion.control, reduced)
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
            checked -> MaterialTheme.colorScheme.primary
            else -> if (hyperPopIsDark()) HyperPopColor.darkTrack else HyperPopColor.lightTrack
        },
        animationSpec = tween(duration),
        label = "switchTrack",
    )
    val travel = HyperPopSize.switchWidth - HyperPopSize.switchThumb - 6.dp
    val offset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = tween(duration),
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .width(HyperPopSize.switchWidth)
            .height(HyperPopSize.switchHeight)
            .clip(CircleShape)
            .background(track)
        .then(
            if (interactive) {
                Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            } else {
                Modifier
            }
        ),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 3.dp, top = 3.dp)
                .padding(start = offset)
                .size(HyperPopSize.switchThumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HpSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interaction,
                colors = SliderDefaults.colors(thumbColor = Color.White),
                thumbSize = DpSize(HyperPopSize.sliderThumb, HyperPopSize.sliderThumb),
                enabled = enabled,
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(HyperPopSize.sliderHeight),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = if (hyperPopIsDark()) HyperPopColor.darkTrack else HyperPopColor.lightTrack,
                ),
                enabled = enabled,
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = HyperPopSize.sliderHeight / 2,
            )
        },
    )
}

@Composable
fun HpNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HyperPopSize.rowMin)
            .clickable(onClick = onClick)
            .padding(horizontal = HyperPopSpace.rowHorizontal, vertical = HyperPopSpace.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(HyperPopSize.icon), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = HyperPopType.settingLabel, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = HyperPopType.settingDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HpSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HyperPopSize.rowMin)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = HyperPopSpace.rowHorizontal, vertical = HyperPopSpace.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HyperPopType.settingLabel, color = MaterialTheme.colorScheme.onSurface)
            if (!enabled && subtitle != null) {
                Text(subtitle, style = HyperPopType.settingDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (subtitle != null) {
                Text(subtitle, style = HyperPopType.settingDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        HpSwitch(checked = checked, onCheckedChange = {}, enabled = enabled, interactive = false)
    }
}

@Composable
fun HpStatusRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    ok: Boolean? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HyperPopSize.rowMin)
            .padding(horizontal = HyperPopSpace.rowHorizontal, vertical = HyperPopSpace.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = HyperPopType.settingLabel, modifier = Modifier.weight(1f))
        Text(
            value,
            style = HyperPopType.numeric,
            color = when (ok) {
                true -> HyperPopColor.success
                false -> HyperPopColor.danger
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
fun HpChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HyperPopSize.rowMin)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = HyperPopSpace.rowHorizontal, vertical = HyperPopSpace.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HyperPopType.settingLabel)
            if (subtitle != null) {
                Text(subtitle, style = HyperPopType.settingDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .padding(if (selected) 0.dp else 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
            } else {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (hyperPopIsDark()) HyperPopColor.darkTrack else HyperPopColor.lightTrack),
                )
            }
        }
    }
}

@Composable
fun HpSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HyperPopSize.radius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = HyperPopType.caption,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun HpApplyBanner(
    text: String,
    action: String,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HyperPopSize.radius))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = HyperPopType.secondary, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Text(
            action,
            style = HyperPopType.button,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onApply).padding(8.dp),
        )
    }
}

@Composable
fun HpBottomBar(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HyperPopSpace.screen, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEachIndexed { index, (label, icon) ->
            val active = index == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(index) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    label,
                    style = HyperPopType.caption,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun rememberHighlight(): String? = SettingsFocus.key

object SettingsFocus {
    var key: String? = null
}

@Composable
fun HpFocusBanner(title: String) {
    val key = SettingsFocus.key ?: return
    Text(
        text = title.ifBlank { key },
        style = HyperPopType.caption,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HyperPopSpace.screen, vertical = 8.dp),
    )
}
