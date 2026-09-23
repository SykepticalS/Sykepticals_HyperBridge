package com.d4viddf.hyperbridge.ui.screens.settings

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.Visibility
import androidx.constraintlayout.compose.layoutId
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.ui.anim.albumArtFlip
import com.d4viddf.hyperbridge.xposed.mediacard.compat.MediaCardTonePolicy
import com.d4viddf.hyperbridge.xposed.mediacard.progress.view.SquigglySeekBar
import com.d4viddf.hyperbridge.xposed.mediacard.progress.view.ThumbStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text

internal data class MediaCardPreviewModel(
    val showShadow: Boolean,
    val coverStyle: Int,
    val hideCoverSource: Boolean,
    val disableCoverFlip: Boolean,
    val hideDeviceSwitch: Boolean,
    val hideCustomActions: Boolean,
    val hideTime: Boolean,
    val actionOrder: Int,
    val actionAlignLeft: Boolean,
    val cardTheme: Int,
    val backgroundStyle: Int,
    val backgroundBlur: Int,
    val softCoverTone: Int,
    val ambientFlowMode: Int,
    val waveProgress: Boolean,
    val verticalProgressThumb: Boolean,
    val hideProgressThumb: Boolean,
)

@Composable
internal fun MediaCardPreview(model: MediaCardPreviewModel, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        HyperOsMediaPreview(model, modifier)
    }
}

@Composable
private fun HyperOsMediaPreview(model: MediaCardPreviewModel, modifier: Modifier) {
    val isSystemDark = isSystemInDarkTheme()
    val isSoftCoverDark = MediaCardTonePolicy.isSoftCoverDark(model.softCoverTone, isSystemDark)
    val cardDark = when (model.cardTheme) {
        1 -> false
        2 -> true
        else -> isSystemDark
    }
    val surface = if (cardDark) Color(0xFF242424) else Color.White
    val onSurface = if (cardDark) Color(0xE6FFFFFF) else Color.Black
    val constraints = remember(model.coverStyle, model.hideDeviceSwitch, model.hideTime) {
        mediaCardConstraints(model.coverStyle, model.hideDeviceSwitch, model.hideTime)
    }
    val albumRotation = remember { Animatable(0f) }
    val endlessRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(true) }
    var trackIndex by remember { mutableIntStateOf(0) }
    val tracks = listOf(
        Triple(R.drawable.media_album_cover_1, "Quiet Night", "HyperBridge"),
        Triple(R.drawable.media_album_cover_2, "Paper Leaves", "HyperBridge"),
    )
    val durations = listOf("04:10", "04:52")
    val imageIndex = ((albumRotation.value.roundToInt() / 180) % tracks.size + tracks.size) % tracks.size
    val track = tracks[trackIndex]
    val imageTrack = tracks[imageIndex]

    LaunchedEffect(playing, model.coverStyle) {
        if (model.coverStyle == 2 && playing) {
            endlessRotation.animateTo(
                endlessRotation.value + 360f * 1000,
                tween(durationMillis = 10_000 * 1000, easing = LinearEasing),
            )
        } else {
            endlessRotation.stop()
        }
    }

    val colorConfig = PreviewMediaStyleConfig.getColorConfig(
        trackIndex,
        model.backgroundStyle,
        if (model.backgroundStyle == 5) isSoftCoverDark else isSystemDark,
    )
    val content = if (model.backgroundStyle > 0) colorConfig.textPrimary else onSurface
    val coverShape = remember(model.coverStyle, model.hideCoverSource) {
        if (model.coverStyle == 1 || model.coverStyle == 2) {
            if (model.hideCoverSource) CircleShape else albumCoverWithSourceShape()
        } else {
            RoundedCornerShape(10.dp)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (model.backgroundStyle > 0) Color.Transparent else surface),
    ) {
        if (model.backgroundStyle > 0) {
            PreviewBackground(model, imageTrack.first, colorConfig, isSoftCoverDark, trackIndex, Modifier.matchParentSize())
        }
        if (model.backgroundStyle == 0 && model.ambientFlowMode in 1..3) {
            val flowRes = if (model.ambientFlowMode == 3) {
                when {
                    trackIndex == 0 && cardDark -> R.drawable.preview_bg_soft_dark_1
                    trackIndex == 0 -> R.drawable.preview_bg_soft_light_1
                    cardDark -> R.drawable.preview_bg_soft_dark_2
                    else -> R.drawable.preview_bg_soft_light_2
                }
            } else if (cardDark) {
                R.drawable.preview_flow_dark
            } else {
                R.drawable.preview_flow_light
            }
            Image(
                painterResource(flowRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        ConstraintLayout(constraints, Modifier.fillMaxWidth()) {
            Box(Modifier.layoutId("cover_space").size(60.dp))
            Box(
                Modifier
                    .layoutId("album_art")
                    .size(60.dp)
                    .albumArtFlip(
                        rotationYValue = albumRotation.value,
                        shape = coverShape,
                        shadowElevation = if (model.showShadow) 8.dp else 0.dp,
                    ),
            ) {
                Image(
                    painterResource(imageTrack.first),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = endlessRotation.value }
                        .clip(if (model.coverStyle == 1 || model.coverStyle == 2) CircleShape else RoundedCornerShape(10.dp)),
                )
                if (!model.hideCoverSource && model.coverStyle != 3) {
                    Image(
                        painterResource(R.drawable.media_app_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 4.dp)
                            .size(14.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
            Text(track.second, Modifier.layoutId("title"), color = content, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(track.third, Modifier.layoutId("artist"), color = content.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1)
            Box(Modifier.layoutId("seamless_btn").size(32.dp), contentAlignment = Alignment.Center) {
                Icon(ImageVector.vectorResource(R.drawable.ic_media_seamless), null, Modifier.size(32.dp), tint = content)
            }
            ActionRow(
                model = model,
                content = content,
                playing = playing,
                onPrevious = {
                    trackIndex = if (trackIndex > 0) trackIndex - 1 else tracks.lastIndex
                    scope.launch { flipCover(albumRotation, model.disableCoverFlip, -180f) }
                },
                onPlay = { playing = !playing },
                onNext = {
                    trackIndex = (trackIndex + 1) % tracks.size
                    scope.launch { flipCover(albumRotation, model.disableCoverFlip, 180f) }
                },
            )
            Text("00:00", Modifier.layoutId("time_start").width(52.dp), color = content.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
            MediaProgressPreview(
                wave = model.waveProgress,
                thumbStyle = when {
                    model.hideProgressThumb -> ThumbStyle.Hidden
                    model.verticalProgressThumb -> ThumbStyle.VerticalBar
                    else -> ThumbStyle.Circle
                },
                color = content,
                playing = playing,
                modifier = Modifier.layoutId("seekbar").height(38.dp),
            )
            Text(durations[trackIndex], Modifier.layoutId("time_end").width(52.dp), color = content.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

private suspend fun flipCover(rotation: Animatable<Float, *>, instant: Boolean, delta: Float) {
    val target = rotation.value + delta
    if (instant) rotation.snapTo(target) else {
        rotation.animateTo(target, spring(dampingRatio = 0.72f, stiffness = 158f))
    }
}

private fun mediaCardConstraints(coverStyle: Int, hideDevice: Boolean, hideTime: Boolean) = ConstraintSet {
    val albumArt = createRefFor("album_art")
    val title = createRefFor("title")
    val artist = createRefFor("artist")
    val seamless = createRefFor("seamless_btn")
    val actions = createRefFor("actions")
    val seekbar = createRefFor("seekbar")
    val timeStart = createRefFor("time_start")
    val timeEnd = createRefFor("time_end")
    val coverSpace = createRefFor("cover_space")
    val parent = createRefFor("parent")
    constrain(coverSpace) {
        start.linkTo(parent.start, 15.dp)
        top.linkTo(parent.top, 15.dp)
    }
    constrain(albumArt) {
        start.linkTo(parent.start, 15.dp)
        top.linkTo(parent.top, 15.dp)
        visibility = if (coverStyle == 3) Visibility.Gone else Visibility.Visible
    }
    constrain(seamless) {
        end.linkTo(parent.end, 17.dp)
        top.linkTo(parent.top, 21.dp)
        visibility = if (hideDevice) Visibility.Gone else Visibility.Visible
    }
    constrain(title) {
        width = Dimension.fillToConstraints
        start.linkTo(albumArt.end, margin = 12.dp, goneMargin = 27.dp)
        end.linkTo(seamless.start, 6.dp)
        top.linkTo(parent.top, 21.dp)
    }
    constrain(artist) {
        width = Dimension.fillToConstraints
        start.linkTo(albumArt.end, margin = 12.dp, goneMargin = 27.dp)
        end.linkTo(seamless.start, 6.dp)
        top.linkTo(title.bottom, 4.dp)
    }
    constrain(actions) {
        width = Dimension.matchParent
        top.linkTo(coverSpace.bottom, 11.dp)
        start.linkTo(parent.start)
        end.linkTo(parent.end)
    }
    constrain(seekbar) {
        width = Dimension.fillToConstraints
        top.linkTo(actions.bottom, 2.dp)
        start.linkTo(timeStart.end, margin = 6.dp, goneMargin = 21.dp)
        end.linkTo(timeEnd.start, margin = 6.dp, goneMargin = 21.dp)
        bottom.linkTo(parent.bottom, 12.dp)
    }
    constrain(timeStart) {
        start.linkTo(parent.start, 15.dp)
        top.linkTo(seekbar.top)
        bottom.linkTo(seekbar.bottom)
        visibility = if (hideTime) Visibility.Gone else Visibility.Visible
    }
    constrain(timeEnd) {
        end.linkTo(parent.end, 15.dp)
        top.linkTo(seekbar.top)
        bottom.linkTo(seekbar.bottom)
        visibility = if (hideTime) Visibility.Gone else Visibility.Visible
    }
}

@Composable
private fun PreviewBackground(
    model: MediaCardPreviewModel,
    artwork: Int,
    colors: PreviewMediaColorConfig,
    softDark: Boolean,
    trackIndex: Int,
    modifier: Modifier,
) {
    when (model.backgroundStyle) {
        1 -> Image(
            painterResource(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, -20f,
                0f, 1f, 0f, 0f, -20f,
                0f, 0f, 1f, 0f, -20f,
                0f, 0f, 0f, 1f, 0f,
            ))),
            modifier = modifier.blur(40.dp).drawWithContent {
                drawContent()
                drawRect(colors.backgroundStart.copy(alpha = 0.44f))
                drawRect(Color.Black.copy(alpha = 0.08f))
            },
        )
        2 -> Image(
            painterResource(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.blur(model.backgroundBlur.coerceIn(1, 20).dp).drawWithContent {
                drawContent()
                drawRect(Brush.radialGradient(
                    listOf(colors.backgroundStart.copy(alpha = 0.19f), colors.backgroundEnd.copy(alpha = 0.88f)),
                    center = Offset(size.width * 0.42f, size.height * 0.5f),
                    radius = size.maxDimension * 0.9f,
                ))
            },
        )
        3 -> Image(
            painterResource(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.drawWithContent {
                drawContent()
                drawRect(Brush.radialGradient(
                    listOf(colors.backgroundStart.copy(alpha = 0.13f), colors.backgroundEnd.copy(alpha = 0.92f)),
                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                    radius = size.maxDimension * 0.8f,
                ))
            },
        )
        4 -> Box(modifier.background(colors.backgroundStart)) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f).align(Alignment.CenterEnd)) {
                Image(painterResource(artwork), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                    0f to colors.backgroundStart,
                    0.5f to colors.backgroundStart.copy(alpha = 142f / 255f),
                    1f to colors.backgroundStart.copy(alpha = 23f / 255f),
                )))
            }
        }
        else -> {
            val soft = when {
                trackIndex == 0 && softDark -> R.drawable.preview_bg_soft_dark_1
                trackIndex == 0 -> R.drawable.preview_bg_soft_light_1
                softDark -> R.drawable.preview_bg_soft_dark_2
                else -> R.drawable.preview_bg_soft_light_2
            }
            Image(painterResource(soft), null, modifier, contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun ActionRow(
    model: MediaCardPreviewModel,
    content: Color,
    playing: Boolean,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
) {
    var previousClicks by remember { mutableIntStateOf(0) }
    var nextClicks by remember { mutableIntStateOf(0) }
    var lastPrevious by remember { mutableLongStateOf(0L) }
    var lastPlay by remember { mutableLongStateOf(0L) }
    var lastNext by remember { mutableLongStateOf(0L) }
    Row(
        Modifier.layoutId("actions").fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = if (model.actionAlignLeft) Arrangement.Start else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val customFavorite = @Composable {
            Glyph(R.drawable.ic_media_fav, content, Modifier.alpha(if (model.hideCustomActions) 0f else 1f)) {}
        }
        val customLyric = @Composable {
            Glyph(R.drawable.ic_media_lyric, content, Modifier.alpha(if (model.hideCustomActions) 0f else 1f)) {}
        }
        val previous = @Composable {
            AnimatedGlyph(R.drawable.ic_media_prev, content, previousClicks) {
                val now = System.currentTimeMillis()
                if (now - lastPrevious > 500) {
                    lastPrevious = now
                    previousClicks++
                    onPrevious()
                }
            }
        }
        val play = @Composable {
            AnimatedGlyph(if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play, content, if (playing) 0 else 1) {
                val now = System.currentTimeMillis()
                if (now - lastPlay > 500) {
                    lastPlay = now
                    onPlay()
                }
            }
        }
        val next = @Composable {
            AnimatedGlyph(R.drawable.ic_media_next, content, nextClicks) {
                val now = System.currentTimeMillis()
                if (now - lastNext > 500) {
                    lastNext = now
                    nextClicks++
                    onNext()
                }
            }
        }
        when (model.actionOrder) {
            1 -> { previous(); play(); next(); customFavorite(); customLyric() }
            2 -> { play(); previous(); next(); customFavorite(); customLyric() }
            else -> { customFavorite(); previous(); play(); next(); customLyric() }
        }
    }
}

@Composable
private fun Glyph(icon: Int, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(60.dp, 50.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(ImageVector.vectorResource(icon), null, Modifier.size(40.dp), tint = tint)
    }
}

@Composable
private fun AnimatedGlyph(icon: Int, tint: Color, pulse: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(60.dp, 50.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context -> ImageView(context).apply { setImageResource(icon) } },
            update = { view ->
                view.setColorFilter(tint.toArgb())
                if (view.tag != icon) {
                    view.tag = icon
                    view.setImageResource(icon)
                }
                val seen = view.getTag(R.id.media_preview_pulse) as? Int
                view.setTag(R.id.media_preview_pulse, pulse)
                if (seen != null && pulse != seen) {
                    (view.drawable as? android.graphics.drawable.AnimatedVectorDrawable)?.apply {
                        stop()
                        start()
                    }
                }
            },
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun MediaProgressPreview(
    wave: Boolean,
    thumbStyle: ThumbStyle,
    color: Color,
    playing: Boolean,
    modifier: Modifier,
) {
    if (wave) {
        AndroidView(
            factory = { context ->
                val density = context.resources.displayMetrics.density
                SquigglySeekBar(context).apply {
                    max = 250_000
                    progress = 75_000
                    waveLength = 20f * density
                    lineAmplitude = 1.5f * density
                    phaseSpeed = 8f * density
                    strokeWidth = 2f * density
                    isEnabled = false
                    setOnTouchListener { _, _ -> true }
                }
            },
            update = { seekBar ->
                if (seekBar.progress != 75_000) seekBar.progress = 75_000
                seekBar.progressTintList = ColorStateList.valueOf(color.toArgb())
                if (seekBar.thumbStyle != thumbStyle) seekBar.thumbStyle = thumbStyle
                seekBar.animate = playing
            },
            modifier = modifier,
        )
        return
    }
    Canvas(modifier) {
        val trackHeight = 6.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val activeEnd = size.width * 0.3f
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(color.copy(alpha = 0.2f), Offset(0f, trackTop), Size(size.width, trackHeight), cornerRadius = radius)
        drawRoundRect(color, Offset(0f, trackTop), Size(activeEnd, trackHeight), cornerRadius = radius)
    }
}

private fun albumCoverWithSourceShape(): Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        path.addOval(Rect(0f, 0f, size.width, size.height))
        val padding = with(density) { 4.dp.toPx() }
        val iconSize = with(density) { 14.dp.toPx() }
        val corner = with(density) { 4.dp.toPx() }
        path.addRoundRect(RoundRect(
            Rect(size.width - padding - iconSize, size.height - padding - iconSize, size.width - padding, size.height - padding),
            CornerRadius(corner, corner),
        ))
        return Outline.Generic(path)
    }
}
