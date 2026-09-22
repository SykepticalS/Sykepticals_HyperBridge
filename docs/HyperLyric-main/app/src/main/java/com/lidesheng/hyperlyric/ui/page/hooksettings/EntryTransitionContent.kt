package com.lidesheng.hyperlyric.ui.page.hooksettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private const val ENTRY_TRANSITION_DELAY_MS = 500L

@Composable
internal fun rememberEntryTransitionContentReady(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    val initiallyResumed = remember(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }
    var contentReady by remember(lifecycleOwner) {
        mutableStateOf(initiallyResumed)
    }

    LaunchedEffect(lifecycleOwner) {
        if (!initiallyResumed) {
            delay(ENTRY_TRANSITION_DELAY_MS)
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            contentReady = true
        }
    }

    return contentReady
}
