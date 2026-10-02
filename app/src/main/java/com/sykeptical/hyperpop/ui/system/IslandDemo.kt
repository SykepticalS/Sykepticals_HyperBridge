package com.sykeptical.hyperpop.ui.system

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class IslandDemoKind { Message, Music, Call, Progress }

@Composable
fun IslandDemo(
    kind: IslandDemoKind,
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    animated: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val duration = motionMillis(if (animated) HyperPopMotion.page else HyperPopMotion.control, reduced)
    val shape = RoundedCornerShape(28.dp)
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        if (glow) {
            Box(
                Modifier
                    .widthIn(min = 180.dp)
                    .height(52.dp)
                    .shadow(18.dp, shape, ambientColor = HyperPopColor.accent, spotColor = HyperPopColor.accent)
            )
        }
        AnimatedContent(
            targetState = kind,
            transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
            label = "islandDemo",
        ) { state ->
            val (icon, title, trailing) = demoContent(state)
            Row(
                modifier = Modifier
                    .widthIn(min = 168.dp, max = 280.dp)
                    .height(44.dp)
                    .clip(shape)
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(title, style = HyperPopType.caption, color = Color.White, modifier = Modifier.weight(1f))
                Text(trailing, style = HyperPopType.numeric, color = Color.White)
            }
        }
    }
}

@Composable
fun CyclingIslandDemo(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    animated: Boolean = true,
) {
    var index by remember { mutableIntStateOf(0) }
    val reduced = LocalReducedMotion.current
    LaunchedEffect(reduced, animated) {
        if (reduced) return@LaunchedEffect
        while (true) {
            delay(if (animated) 1600 else 2200)
            index = (index + 1) % IslandDemoKind.entries.size
        }
    }
    IslandDemo(
        kind = IslandDemoKind.entries[index],
        modifier = modifier,
        glow = glow,
        animated = animated,
    )
}

private fun demoContent(kind: IslandDemoKind): Triple<ImageVector, String, String> = when (kind) {
    IslandDemoKind.Message -> Triple(Icons.Default.Sms, "Alex", "Hi")
    IslandDemoKind.Music -> Triple(Icons.Default.MusicNote, "Now playing", "♪")
    IslandDemoKind.Call -> Triple(Icons.Default.Call, "Incoming", "•••")
    IslandDemoKind.Progress -> Triple(Icons.Default.Download, "Download", "64%")
}

@Composable
fun IslandDemoColumn() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CyclingIslandDemo()
    }
}
