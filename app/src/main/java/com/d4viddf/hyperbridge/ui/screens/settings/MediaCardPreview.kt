package com.d4viddf.hyperbridge.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class MediaCardPreviewModel(
    val layoutStyle: Int,
    val cardTheme: Int,
    val ambientEnabled: Boolean,
    val backgroundStyle: Int,
    val backgroundBlur: Int,
    val softCoverDark: Boolean,
    val coverStyle: Int,
    val hideCoverSource: Boolean,
    val hideCoverShadow: Boolean,
    val disableCoverFlip: Boolean,
    val hideDeviceSwitch: Boolean,
    val hideTime: Boolean,
    val hideCustomActions: Boolean,
    val waveProgress: Boolean,
    val progressHeadGlow: Boolean,
    val thumbStyle: Int,
    val actionAlignLeft: Boolean,
    val actionOrder: Int,
)

private data class PreviewTrack(val title: String, val artist: String, val colors: List<Color>)

private val previewTracks = listOf(
    PreviewTrack("Quiet Night", "HyperBridge", listOf(Color(0xFFE36A3A), Color(0xFFF2C14E), Color(0xFF8E3D2F))),
    PreviewTrack("Paper Leaves", "HyperBridge", listOf(Color(0xFF2F6F8F), Color(0xFF7ED0C8), Color(0xFF1C3A4A))),
)

@Composable
internal fun MediaCardPreview(model: MediaCardPreviewModel, modifier: Modifier = Modifier) {
    val systemDark = isSystemInDarkTheme()
    val cardDark = when (model.cardTheme) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val customBackground = model.backgroundStyle > 0
    val content = when {
        customBackground && model.backgroundStyle == 5 && !model.softCoverDark -> Color(0xFF1A1C1E)
        customBackground -> Color.White
        cardDark -> Color(0xFFF2F2F2)
        else -> Color(0xFF161616)
    }
    val surface = when {
        customBackground -> Color.Transparent
        cardDark -> Color(0xFF242424)
        else -> Color.White
    }
    var trackIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val flip = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shownIndex = ((flip.value / 180f).roundToInt() % previewTracks.size + previewTracks.size) % previewTracks.size
    val track = previewTracks[shownIndex]
    val spin = rememberInfiniteTransition(label = "cover-spin")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "cover-rotation",
    )
    val flowShift by spin.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "ambient-shift",
    )

    fun step(delta: Int) {
        trackIndex = (trackIndex + delta + previewTracks.size) % previewTracks.size
        val target = flip.value + if (delta > 0) 180f else -180f
        scope.launch {
            if (model.disableCoverFlip) flip.snapTo(target) else {
                flip.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 158f))
            }
        }
    }

    val cardRadius = when (model.layoutStyle) {
        1 -> 32.dp
        2 -> 20.dp
        5 -> 12.dp
        else -> 28.dp
    }
    val coverSize = when (model.layoutStyle) {
        1 -> 72.dp
        3 -> 48.dp
        4 -> 52.dp
        else -> 60.dp
    }
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cardRadius))
                .background(surface)
                .padding(if (model.layoutStyle == 3) 12.dp else 16.dp),
        ) {
            if (customBackground) {
                PreviewBackground(model, track, Modifier.matchParentSize())
            } else if (model.ambientEnabled) {
                AmbientWash(track.colors, cardDark, flowShift, Modifier.matchParentSize())
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.coverStyle != 3) {
                        CoverArt(
                            track = track,
                            coverStyle = model.coverStyle,
                            hideSource = model.hideCoverSource,
                            showShadow = !model.hideCoverShadow,
                            rotation = if (model.coverStyle == 2 && playing) rotation else 0f,
                            flip = flip.value,
                            coverSize = coverSize,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(start = if (model.coverStyle == 3) 4.dp else 12.dp, end = 8.dp),
                    ) {
                        Text(track.title, color = content, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, color = content.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1)
                    }
                    if (!model.hideDeviceSwitch) {
                        Icon(Icons.Rounded.Speaker, null, tint = content, modifier = Modifier.size(22.dp))
                    }
                }
                ActionRow(
                    content = content,
                    model = model,
                    playing = playing,
                    onPrevious = { step(-1) },
                    onPlay = { playing = !playing },
                    onNext = { step(1) },
                )
                ProgressRow(model, content)
            }
        }
        Text(
            "Tap the controls to preview cover changes. The real card updates after SystemUI restarts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
        )
    }
}

@Composable
private fun CoverArt(
    track: PreviewTrack,
    coverStyle: Int,
    hideSource: Boolean,
    showShadow: Boolean,
    rotation: Float,
    flip: Float,
    coverSize: Dp,
) {
    val shape = if (coverStyle == 1 || coverStyle == 2) CircleShape else RoundedCornerShape(12.dp)
    Box(
        Modifier
            .graphicsLayer {
                rotationY = flip
                cameraDistance = 12 * density
            }
            .shadow(if (showShadow) 8.dp else 0.dp, shape)
            .size(coverSize)
            .clip(shape)
            .background(Brush.linearGradient(track.colors)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
                .background(Brush.radialGradient(listOf(track.colors[1], track.colors[0], track.colors[2]))),
        )
        if (!hideSource) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(track.colors[0]))
            }
        }
    }
}

@Composable
private fun PreviewBackground(model: MediaCardPreviewModel, track: PreviewTrack, modifier: Modifier) {
    val start = track.colors[2]
    val end = track.colors[0]
    when (model.backgroundStyle) {
        1, 2 -> Box(
            modifier
                .background(Brush.linearGradient(track.colors))
                .blur(if (model.backgroundStyle == 1) 28.dp else model.backgroundBlur.coerceIn(1, 20).dp)
                .background(Brush.radialGradient(listOf(start.copy(alpha = 0.2f), end.copy(alpha = 0.82f)))),
        )
        3 -> Box(modifier.background(Brush.radialGradient(listOf(track.colors[1], end))))
        4 -> Box(modifier.background(Brush.horizontalGradient(listOf(end, track.colors[1].copy(alpha = 0.35f)))))
        else -> {
            val base = if (model.softCoverDark) Color(0xFF121316) else Color(0xFFF4F1EC)
            val glow = if (model.softCoverDark) track.colors[1].copy(alpha = 0.55f) else track.colors[0].copy(alpha = 0.35f)
            Box(modifier.background(Brush.radialGradient(listOf(glow, base))))
        }
    }
}

@Composable
private fun AmbientWash(colors: List<Color>, dark: Boolean, shift: Float, modifier: Modifier) {
    val base = if (dark) Color(0xFF242424) else Color.White
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(base, colors[0].copy(alpha = 0.55f), colors[1].copy(alpha = 0.35f), base),
                start = Offset(shift * 400f, 0f),
                end = Offset(220f + shift * 400f, 280f),
            ),
        ),
    )
}

@Composable
private fun ActionRow(
    content: Color,
    model: MediaCardPreviewModel,
    playing: Boolean,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
) {
    val custom = listOf(
        ActionIcon(Icons.Rounded.Favorite, "Favorite", onClick = {}),
        ActionIcon(Icons.Rounded.GraphicEq, "Visualizer", onClick = {}),
    )
    val transport = listOf(
        ActionIcon(Icons.Rounded.SkipPrevious, "Previous", onPrevious),
        ActionIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play", onPlay),
        ActionIcon(Icons.Rounded.SkipNext, "Next", onNext),
    )
    val icons = when (model.actionOrder) {
        1 -> transport + custom
        2 -> listOf(transport[1], transport[0], transport[2]) + custom
        else -> listOf(custom[0]) + transport + listOf(custom[1])
    }.filter { model.hideCustomActions.not() || it.label != "Favorite" && it.label != "Visualizer" }
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = if (model.actionAlignLeft) Arrangement.spacedBy(4.dp, Alignment.Start) else Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEach { action ->
            Icon(
                action.icon,
                action.label,
                tint = content,
                modifier = Modifier
                    .size(36.dp)
                    .clickable(onClick = action.onClick)
                    .padding(6.dp),
            )
        }
    }
}

private data class ActionIcon(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
private fun ProgressRow(model: MediaCardPreviewModel, content: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!model.hideTime) TimeLabel("1:12", content)
        Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(18.dp), contentAlignment = Alignment.Center) {
            if (model.waveProgress) {
                WaveBar(content, model.thumbStyle)
            } else {
                FlatBar(content, model.progressHeadGlow)
            }
        }
        if (!model.hideTime) TimeLabel("3:41", content)
    }
}

@Composable
private fun TimeLabel(text: String, color: Color) {
    Text(text, color = color.copy(alpha = 0.6f), fontSize = 11.sp)
}

@Composable
private fun FlatBar(color: Color, glow: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val y = size.height / 2f
        val head = size.width * 0.38f
        drawRoundRect(color.copy(alpha = 0.2f), cornerRadius = CornerRadius(8f, 8f), size = Size(size.width, 6f), topLeft = Offset(0f, y - 3f))
        drawRoundRect(color, cornerRadius = CornerRadius(8f, 8f), size = Size(head, 6f), topLeft = Offset(0f, y - 3f))
        if (glow) {
            drawCircle(color.copy(alpha = 0.35f), radius = 10f, center = Offset(head, y))
            drawCircle(color, radius = 4f, center = Offset(head, y))
        }
    }
}

@Composable
private fun WaveBar(color: Color, thumbStyle: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val path = Path()
        val mid = size.height / 2f
        val progress = 0.38f
        path.moveTo(0f, mid)
        val steps = 28
        for (index in 0..steps) {
            val x = size.width * index / steps
            val wave = sin(index / steps.toFloat() * PI * 4).toFloat() * 5f
            path.lineTo(x, mid + wave)
        }
        drawPath(path, color.copy(alpha = 0.28f), style = Stroke(width = 3f))
        val head = size.width * progress
        val played = Path()
        played.moveTo(0f, mid)
        val playedSteps = (steps * progress).roundToInt().coerceAtLeast(1)
        for (index in 0..playedSteps) {
            val x = size.width * index / steps
            val wave = sin(index / steps.toFloat() * PI * 4).toFloat() * 5f
            played.lineTo(x, mid + wave)
        }
        drawPath(played, color, style = Stroke(width = 3f))
        when (thumbStyle) {
            1 -> drawRoundRect(color, topLeft = Offset(head - 2f, mid - 8f), size = Size(4f, 16f), cornerRadius = CornerRadius(4f, 4f))
            2 -> Unit
            else -> drawCircle(color, radius = 4.5f, center = Offset(head, mid))
        }
    }
}
