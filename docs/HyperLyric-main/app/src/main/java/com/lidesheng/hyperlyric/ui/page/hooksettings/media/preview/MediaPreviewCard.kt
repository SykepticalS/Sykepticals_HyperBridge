package com.lidesheng.hyperlyric.ui.page.hooksettings.media.preview

import android.content.res.ColorStateList
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import androidx.constraintlayout.compose.Visibility
import androidx.constraintlayout.compose.layoutId
import com.lidesheng.hyperlyric.R
import com.lidesheng.hyperlyric.common.media.MediaCardTonePolicy
import com.lidesheng.hyperlyric.root.mediacard.progress.view.SquigglySeekBar
import com.lidesheng.hyperlyric.root.mediacard.progress.view.ThumbStyle
import com.lidesheng.hyperlyric.ui.anim.albumArtFlip
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun MediaPreviewCard(
    showShadow: Boolean = true,
    coverStyle: Int = 0, // 0: default, 1: circle, 2: rotating circle
    hideCoverSource: Boolean = false,
    disableCoverFlip: Boolean = false,
    hideDeviceSwitch: Boolean = false,
    hideCustomActions: Boolean = false,
    hideTime: Boolean = false,
    actionOrder: Int = 0,
    actionAlignLeft: Boolean = false,
    cardTheme: Int = 0,
    backgroundStyle: Int = 0,
    backgroundBlur: Int = 10,
    softCoverTone: Int = 1,
    ambientFlowMode: Int = 0,
    waveProgress: Boolean = false,
    verticalProgressThumb: Boolean = false,
    hideProgressThumb: Boolean = false
) {
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isSoftCoverDark = MediaCardTonePolicy.isSoftCoverDark(softCoverTone, isSystemDark)

    val defaultLightColor = Color.White
    val defaultLightContentColor = Color.Black
    val defaultDarkColor = Color(0xFF242424)
    val defaultDarkContentColor = Color(0xE6FFFFFF)

    val cardColors = when (cardTheme) {
        1 -> CardDefaults.defaultColors(
            color = defaultLightColor,
            contentColor = defaultLightContentColor
        )

        2 -> CardDefaults.defaultColors(
            color = defaultDarkColor,
            contentColor = defaultDarkContentColor
        )

        else -> if (isSystemDark) {
            CardDefaults.defaultColors(
                color = defaultDarkColor,
                contentColor = defaultDarkContentColor
            )
        } else {
            CardDefaults.defaultColors(
                color = defaultLightColor,
                contentColor = defaultLightContentColor
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = if (backgroundStyle > 0) CardDefaults.defaultColors(color = Color.Transparent) else cardColors,
        cornerRadius = 24.dp
    ) {
        val constraints = ConstraintSet {
            val albumArt = createRefFor("album_art")
            createRefFor("app_icon")
            val title = createRefFor("title")
            val artist = createRefFor("artist")
            val seamlessBtn = createRefFor("seamless_btn")
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

            constrain(seamlessBtn) {
                end.linkTo(parent.end, 17.dp)
                top.linkTo(parent.top, 21.dp)
                visibility = if (hideDeviceSwitch) Visibility.Gone else Visibility.Visible
            }

            constrain(title) {
                width = Dimension.fillToConstraints
                start.linkTo(albumArt.end, margin = 12.dp, goneMargin = 27.dp)
                end.linkTo(seamlessBtn.start, 6.dp)
                top.linkTo(parent.top, 21.dp)
            }

            constrain(artist) {
                width = Dimension.fillToConstraints
                start.linkTo(albumArt.end, margin = 12.dp, goneMargin = 27.dp)
                end.linkTo(seamlessBtn.start, 6.dp)
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

        val albumRotation = remember { Animatable(0f) }
        val endlessRotation = remember { Animatable(0f) }
        val coroutineScope = rememberCoroutineScope()

        var playAtEnd by remember { mutableStateOf(false) }
        var currentTrackIndex by remember { mutableStateOf(0) }
        val tracks = listOf(
            Triple(R.drawable.media_album_cover_1, "一直很安静", "阿桑"),
            Triple(R.drawable.media_album_cover_2, "叶子", "阿桑")
        )
        val trackDurations = listOf("04:10", "04:52")
        val currentTrack = tracks[currentTrackIndex]

        // Derive image track index from rotation so it swaps exactly halfway through the flip
        val imageTrackIndex = ((Math.round(albumRotation.value / 180f)
            % tracks.size) + tracks.size) % tracks.size
        val imageTrack = tracks[imageTrackIndex]

        LaunchedEffect(playAtEnd, coverStyle) {
            if (coverStyle == 2) {
                if (playAtEnd) {
                    endlessRotation.animateTo(
                        targetValue = endlessRotation.value + 360f * 1000,
                        animationSpec = tween(durationMillis = 10000 * 1000, easing = LinearEasing)
                    )
                } else {
                    endlessRotation.stop()
                }
            } else {
                endlessRotation.snapTo(0f)
            }
        }

        val colorConfig = PreviewMediaStyleConfig.getColorConfig(
            currentTrackIndex,
            backgroundStyle,
            if (backgroundStyle == 5) isSoftCoverDark else isSystemDark
        )
        val onSurfaceColor = if (backgroundStyle > 0) {
            colorConfig.textPrimary
        } else {
            when (cardTheme) {
                1 -> Color.Black
                2 -> Color(0xFFF2F2F2)
                else -> if (isSystemDark) Color(0xFFF2F2F2) else Color.Black
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            // Background Layer
            if (backgroundStyle > 0) {
                when (backgroundStyle) {
                    1 -> {
                        val colorMatrix = floatArrayOf(
                            1f, 0f, 0f, 0f, -20f,
                            0f, 1f, 0f, 0f, -20f,
                            0f, 0f, 1f, 0f, -20f,
                            0f, 0f, 0f, 1f, 0f
                        )
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(imageTrack.first),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(40.dp)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(color = colorConfig.backgroundStart.copy(alpha = 0.44f))
                                    drawRect(color = Color.Black.copy(alpha = 0.08f))
                                },
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                androidx.compose.ui.graphics.ColorMatrix(colorMatrix)
                            )
                        )
                    }

                    2 -> {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(imageTrack.first),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .blur(backgroundBlur.dp)
                                .drawWithContent {
                                    drawContent()
                                    val gradient =
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            colors = listOf(
                                                colorConfig.backgroundStart.copy(alpha = 0.19f),
                                                colorConfig.backgroundEnd.copy(alpha = 0.88f)
                                            ),
                                            center = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.42f,
                                                size.height * 0.5f
                                            ),
                                            radius = size.maxDimension * 0.9f
                                        )
                                    drawRect(brush = gradient)
                                }
                        )
                    }

                    3 -> {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(imageTrack.first),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .drawWithContent {
                                    drawContent()
                                    val gradient =
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            colors = listOf(
                                                colorConfig.backgroundStart.copy(alpha = 0.13f),
                                                colorConfig.backgroundEnd.copy(alpha = 0.92f)
                                            ),
                                            center = androidx.compose.ui.geometry.Offset(
                                                size.width * 0.5f,
                                                size.height * 0.5f
                                            ),
                                            radius = size.maxDimension * 0.8f
                                        )
                                    drawRect(brush = gradient)
                                }
                        )
                    }

                    4 -> {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(colorConfig.backgroundStart)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f)
                                    .align(Alignment.CenterEnd)
                            ) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(imageTrack.first),
                                    contentDescription = null,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                0f to colorConfig.backgroundStart,
                                                0.5f to colorConfig.backgroundStart.copy(alpha = 142f / 255f),
                                                1f to colorConfig.backgroundStart.copy(alpha = 23f / 255f)
                                            )
                                        )
                                )
                            }
                        }
                    }

                    5 -> {
                        val softBgRes = if (currentTrackIndex == 0) {
                            if (isSoftCoverDark) R.drawable.preview_bg_soft_dark_1 else R.drawable.preview_bg_soft_light_1
                        } else {
                            if (isSoftCoverDark) R.drawable.preview_bg_soft_dark_2 else R.drawable.preview_bg_soft_light_2
                        }
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(softBgRes),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }

            // Ambient Flow Layer
            if (backgroundStyle == 0 && (ambientFlowMode == 1 || ambientFlowMode == 2 || ambientFlowMode == 3)) {
                val isCardDark = when (cardTheme) {
                    1 -> false
                    2 -> true
                    else -> isSystemDark
                }
                val flowRes = if (ambientFlowMode == 1 || ambientFlowMode == 2) {
                    if (isCardDark) R.drawable.preview_flow_dark else R.drawable.preview_flow_light
                } else { // ambientFlowMode == 3 (封面流光)
                    if (currentTrackIndex == 0) {
                        if (isCardDark) R.drawable.preview_bg_soft_dark_1 else R.drawable.preview_bg_soft_light_1
                    } else {
                        if (isCardDark) R.drawable.preview_bg_soft_dark_2 else R.drawable.preview_bg_soft_light_2
                    }
                }

                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(flowRes),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            ConstraintLayout(constraints, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier
                        .layoutId("cover_space")
                        .size(60.dp)
                )

                val albumCoverWithIconShape = remember {
                    object : androidx.compose.ui.graphics.Shape {
                        override fun createOutline(
                            size: androidx.compose.ui.geometry.Size,
                            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                            density: androidx.compose.ui.unit.Density
                        ): androidx.compose.ui.graphics.Outline {
                            val path = androidx.compose.ui.graphics.Path()
                            path.addOval(
                                androidx.compose.ui.geometry.Rect(
                                    0f,
                                    0f,
                                    size.width,
                                    size.height
                                )
                            )
                            val padding = with(density) { 4.dp.toPx() }
                            val iconSize = with(density) { 14.dp.toPx() }
                            val iconCorner = with(density) { 4.dp.toPx() }
                            val iconRect = androidx.compose.ui.geometry.Rect(
                                left = size.width - padding - iconSize,
                                top = size.height - padding - iconSize,
                                right = size.width - padding,
                                bottom = size.height - padding
                            )
                            path.addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    iconRect,
                                    androidx.compose.ui.geometry.CornerRadius(
                                        iconCorner,
                                        iconCorner
                                    )
                                )
                            )
                            return androidx.compose.ui.graphics.Outline.Generic(path)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .layoutId("album_art")
                        .size(60.dp)
                        .albumArtFlip(
                            rotationYValue = albumRotation.value,
                            shape = if (coverStyle == 1 || coverStyle == 2) {
                                if (hideCoverSource) CircleShape else albumCoverWithIconShape
                            } else RoundedCornerShape(10.dp),
                            shadowElevation = if (showShadow) 8.dp else 0.dp
                        )
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(imageTrack.first),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(rotationZ = endlessRotation.value)
                            .clip(
                                if (coverStyle == 1 || coverStyle == 2) CircleShape else RoundedCornerShape(
                                    10.dp
                                )
                            )
                    )

                    if (!hideCoverSource && coverStyle != 3) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.media_app_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 4.dp, bottom = 4.dp)
                                .size(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }

                Text(
                    modifier = Modifier.layoutId("title"),
                    text = currentTrack.second,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurfaceColor,
                    maxLines = 1
                )

                Text(
                    modifier = Modifier.layoutId("artist"),
                    text = currentTrack.third,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = onSurfaceColor.copy(alpha = 0.5f),
                    maxLines = 1
                )

                Box(
                    modifier = Modifier
                        .layoutId("seamless_btn")
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val seamlessIcon = ImageVector.vectorResource(R.drawable.ic_media_seamless)
                    Icon(
                        imageVector = seamlessIcon,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = onSurfaceColor
                    )
                }

                Row(
                    modifier = Modifier
                        .layoutId("actions")
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = if (actionAlignLeft) Arrangement.Start else Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var prevClickCount by remember { mutableStateOf(0) }
                    var nextClickCount by remember { mutableStateOf(0) }


                    var lastPrevClickTime by remember { mutableStateOf(0L) }
                    var lastPlayClickTime by remember { mutableStateOf(0L) }
                    var lastNextClickTime by remember { mutableStateOf(0L) }

                    val btnCustom1 = @Composable {
                        Box(
                            modifier = Modifier
                                .size(60.dp, 50.dp)
                                .alpha(if (hideCustomActions) 0f else 1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val favIcon = ImageVector.vectorResource(R.drawable.ic_media_fav)
                            Icon(
                                imageVector = favIcon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = onSurfaceColor
                            )
                        }
                    }

                    val btnPrev = @Composable {
                        Box(
                            modifier = Modifier
                                .size(60.dp, 50.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        if (now - lastPrevClickTime > 500) {
                                            lastPrevClickTime = now
                                            prevClickCount++
                                            currentTrackIndex =
                                                if (currentTrackIndex > 0) currentTrackIndex - 1 else tracks.size - 1
                                            if (disableCoverFlip) {
                                                coroutineScope.launch {
                                                    albumRotation.snapTo(albumRotation.value - 180f)
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    albumRotation.animateTo(
                                                        targetValue = albumRotation.targetValue - 180f,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.72f,
                                                            stiffness = 158f
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { context ->
                                    android.widget.ImageView(context).apply {
                                        setImageResource(R.drawable.ic_media_prev)
                                    }
                                },
                                update = { view ->
                                    view.setColorFilter(onSurfaceColor.toArgb())
                                    val lastAnim = view.tag as? Int ?: 0
                                    if (prevClickCount > lastAnim) {
                                        view.tag = prevClickCount
                                        (view.drawable as? android.graphics.drawable.AnimatedVectorDrawable)?.apply {
                                            stop()
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    val btnPlay = @Composable {
                        Box(
                            modifier = Modifier
                                .size(60.dp, 50.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        if (now - lastPlayClickTime > 500) {
                                            lastPlayClickTime = now
                                            playAtEnd = !playAtEnd
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { context ->
                                    android.widget.ImageView(context).apply {
                                        tag = playAtEnd
                                        setImageResource(if (playAtEnd) R.drawable.ic_media_play else R.drawable.ic_media_pause)
                                    }
                                },
                                update = { view ->
                                    view.setColorFilter(onSurfaceColor.toArgb())
                                    val lastAnim = view.tag as? Boolean
                                    if (lastAnim != playAtEnd) {
                                        view.tag = playAtEnd
                                        if (playAtEnd) {
                                            view.setImageResource(R.drawable.ic_media_pause)
                                        } else {
                                            view.setImageResource(R.drawable.ic_media_play)
                                        }
                                        if (lastAnim != null) {
                                            (view.drawable as? android.graphics.drawable.AnimatedVectorDrawable)?.start()
                                        }
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    val btnNext = @Composable {
                        Box(
                            modifier = Modifier
                                .size(60.dp, 50.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        if (now - lastNextClickTime > 500) {
                                            lastNextClickTime = now
                                            nextClickCount++
                                            currentTrackIndex =
                                                (currentTrackIndex + 1) % tracks.size
                                            if (disableCoverFlip) {
                                                coroutineScope.launch {
                                                    albumRotation.snapTo(albumRotation.value + 180f)
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    albumRotation.animateTo(
                                                        targetValue = albumRotation.targetValue + 180f,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.72f,
                                                            stiffness = 158f
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { context ->
                                    android.widget.ImageView(context).apply {
                                        setImageResource(R.drawable.ic_media_next)
                                    }
                                },
                                update = { view ->
                                    view.setColorFilter(onSurfaceColor.toArgb())
                                    val lastAnim = view.tag as? Int ?: 0
                                    if (nextClickCount > lastAnim) {
                                        view.tag = nextClickCount
                                        (view.drawable as? android.graphics.drawable.AnimatedVectorDrawable)?.apply {
                                            stop()
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    val btnCustom2 = @Composable {
                        Box(
                            modifier = Modifier
                                .size(60.dp, 50.dp)
                                .alpha(if (hideCustomActions) 0f else 1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val lyricIcon = ImageVector.vectorResource(R.drawable.ic_media_lyric)
                            Icon(
                                imageVector = lyricIcon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = onSurfaceColor
                            )
                        }
                    }

                    when (actionOrder) {
                        1 -> {
                            btnPrev()
                            btnPlay()
                            btnNext()
                            btnCustom1()
                            btnCustom2()
                        }

                        2 -> {
                            btnPlay()
                            btnPrev()
                            btnNext()
                            btnCustom1()
                            btnCustom2()
                        }

                        else -> {
                            btnCustom1()
                            btnPrev()
                            btnPlay()
                            btnNext()
                            btnCustom2()
                        }
                    }
                }

                Text(
                    text = "00:00",
                    fontSize = 12.sp,
                    color = onSurfaceColor.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .width(52.dp)
                        .layoutId("time_start")
                )

                MediaProgressPreview(
                    wave = waveProgress,
                    thumbStyle = when {
                        hideProgressThumb -> ThumbStyle.Hidden
                        verticalProgressThumb -> ThumbStyle.VerticalBar
                        else -> ThumbStyle.Circle
                    },
                    color = onSurfaceColor,
                    playing = playAtEnd,
                    modifier = Modifier
                        .layoutId("seekbar")
                        .height(38.dp)
                )

                Text(
                    text = trackDurations[currentTrackIndex],
                    fontSize = 12.sp,
                    color = onSurfaceColor.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .width(52.dp)
                        .layoutId("time_end")
                )
            }
        }
    }
}

@Composable
private fun MediaProgressPreview(
    wave: Boolean,
    thumbStyle: ThumbStyle,
    color: Color,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    if (wave) {
        AndroidView(
            factory = { context ->
                val density = context.resources.displayMetrics.density
                SquigglySeekBar(context).apply {
                    max = PREVIEW_DURATION
                    progress = PREVIEW_PROGRESS
                    waveLength = 20f * density
                    lineAmplitude = 1.5f * density
                    phaseSpeed = 8f * density
                    strokeWidth = 2f * density
                    isEnabled = false
                    setOnTouchListener { _, _ -> true }
                }
            },
            update = { seekBar ->
                if (seekBar.progress != PREVIEW_PROGRESS) {
                    seekBar.progress = PREVIEW_PROGRESS
                }
                seekBar.progressTintList = ColorStateList.valueOf(color.toArgb())
                if (seekBar.thumbStyle != thumbStyle) {
                    seekBar.thumbStyle = thumbStyle
                }
                seekBar.animate = playing
            },
            modifier = modifier
        )
        return
    }

    Canvas(modifier = modifier) {
        val trackHeight = 6.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val activeEnd = size.width * PREVIEW_PROGRESS_FRACTION
        val trackRadius = CornerRadius(trackHeight / 2f)

        drawRoundRect(
            color = color.copy(alpha = 0.2f),
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = trackRadius
        )

        drawRoundRect(
            color = color,
            topLeft = Offset(0f, trackTop),
            size = Size(activeEnd, trackHeight),
            cornerRadius = trackRadius
        )
    }
}

private const val PREVIEW_DURATION = 250_000
private const val PREVIEW_PROGRESS = 75_000
private const val PREVIEW_PROGRESS_FRACTION = 0.3f
