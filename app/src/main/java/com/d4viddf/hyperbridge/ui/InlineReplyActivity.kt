package com.d4viddf.hyperbridge.ui

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

/** User-initiated reply surface. An Activity avoids the obsolete overlay permission. */
class InlineReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pendingIntent = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PENDING_INTENT, PendingIntent::class.java)
        } else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_PENDING_INTENT)
        val resultKey = intent.getStringExtra(EXTRA_RESULT_KEY)
        if (pendingIntent == null || resultKey == null) {
            finish()
            return
        }
        setContent {
            HyperBridgeTheme {
                var message by remember { mutableStateOf("") }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp),
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
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
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

    companion object {
        const val EXTRA_PENDING_INTENT = "pending_intent"
        const val EXTRA_RESULT_KEY = "result_key"
        private const val TAG = "InlineReplyActivity"
    }
}
