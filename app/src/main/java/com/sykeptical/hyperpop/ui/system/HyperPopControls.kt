package com.sykeptical.hyperpop.ui.system

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sykeptical.hyperpop.R

@Composable
fun HpScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding(),
            ) {
                if (onBack != null) {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .padding(start = 4.dp)
                                .size(48.dp)
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        actions()
                    }
                }
                Text(
                    text = title,
                    style = HyperPopType.largeTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = HyperPopSpace.titleInset,
                        end = HyperPopSpace.titleInset,
                        top = 4.dp,
                        bottom = 10.dp,
                    ),
                )
            }
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
                start = 14.dp,
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
    val travel = HyperPopSize.switchWidth - HyperPopSize.switchThumb - (HyperPopSize.switchThumbInset * 2)
    val offset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = tween(duration),
        label = "switchThumb",
    )
    Box(
        modifier = modifier
            .width(HyperPopSize.switchWidth)
            .height(HyperPopSize.switchHeight)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(track)
            .then(
                if (interactive) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = HyperPopSize.switchThumbInset)
                .offset(x = offset)
                .size(HyperPopSize.switchThumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun HpSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val active = hyperPopAccent()
    val inactive = if (hyperPopIsDark()) HyperPopColor.darkTrack else HyperPopColor.lightTrack
    fun emit(raw: Float) {
        onValueChange(valueRange.start + raw.coerceIn(0f, 1f) * span)
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { position ->
                    emit(position.x / size.width)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { onValueChangeFinished?.invoke() },
                    onHorizontalDrag = { change, _ ->
                        emit(change.position.x / size.width)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = maxWidth - HyperPopSize.sliderThumb
        Box(
            Modifier
                .fillMaxWidth()
                .height(HyperPopSize.sliderHeight)
                .clip(CircleShape)
                .background(inactive),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceAtLeast(0.001f))
                .height(HyperPopSize.sliderHeight)
                .clip(CircleShape)
                .background(active),
        )
        Box(
            Modifier
                .offset(x = travel * fraction)
                .size(HyperPopSize.sliderThumb)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
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
