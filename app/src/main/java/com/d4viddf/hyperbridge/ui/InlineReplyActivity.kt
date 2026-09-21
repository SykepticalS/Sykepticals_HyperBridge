package com.d4viddf.hyperbridge.ui

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.ui.theme.HyperBridgeTheme

/**
 * Standalone reply surface launched directly from island/notification actions.
 * Targeting this activity (not a broadcast trampoline) lets SystemUI start it
 * over whichever app is in the foreground.
 */
class InlineReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOverlayWindow()
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun configureOverlayWindow() {
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }

    override fun finish() {
        super.finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }

    private fun render(source: Intent) {
        val pendingIntent = source.replyPendingIntent()
        val resultKey = source.getStringExtra(EXTRA_RESULT_KEY)
        if (pendingIntent == null || resultKey.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            HyperBridgeTheme {
                var message by remember(pendingIntent, resultKey) { mutableStateOf("") }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(pendingIntent, resultKey) { focusRequester.requestFocus() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() },
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF101010))
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = message,
                            onValueChange = { message = it },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.reply_hint)) },
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF2C2C2E),
                                unfocusedContainerColor = Color(0xFF2C2C2E),
                                disabledContainerColor = Color(0xFF2C2C2E),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                sendReply(pendingIntent, resultKey, message)
                            }),
                        )
                        IconButton(
                            onClick = { sendReply(pendingIntent, resultKey, message) },
                            modifier = Modifier.size(56.dp).background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(24.dp),
                            ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.reply_hint),
                                tint = if (message.isBlank()) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sendReply(pendingIntent: PendingIntent, resultKey: String, message: String) {
        if (message.isBlank()) return
        val replyIntent = Intent()
        val results = Bundle().apply { putCharSequence(resultKey, message) }
        RemoteInput.addResultsToIntent(arrayOf(RemoteInput.Builder(resultKey).build()), replyIntent, results)
        runCatching { pendingIntent.send(this, 0, replyIntent) }
            .onFailure { Log.e(TAG, "Reply PendingIntent failed", it) }
        finish()
    }

    private fun Intent.replyPendingIntent(): PendingIntent? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(EXTRA_PENDING_INTENT, PendingIntent::class.java)
    } else @Suppress("DEPRECATION") getParcelableExtra(EXTRA_PENDING_INTENT)

    companion object {
        const val EXTRA_PENDING_INTENT = "pending_intent"
        const val EXTRA_RESULT_KEY = "result_key"
        const val EXTRA_PACKAGE_NAME = "package_name"
        private const val TAG = "InlineReplyActivity"
    }
}
