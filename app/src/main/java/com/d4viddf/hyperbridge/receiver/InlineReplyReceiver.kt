package com.d4viddf.hyperbridge.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.d4viddf.hyperbridge.ui.InlineReplyActivity
import com.d4viddf.hyperbridge.ui.InlineReplyIntents

class InlineReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingIntent = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(InlineReplyActivity.EXTRA_PENDING_INTENT, PendingIntent::class.java)
        } else @Suppress("DEPRECATION") intent.getParcelableExtra(InlineReplyActivity.EXTRA_PENDING_INTENT)
        val resultKey = intent.getStringExtra(InlineReplyActivity.EXTRA_RESULT_KEY)
        if (pendingIntent == null || resultKey.isNullOrBlank()) {
            Toast.makeText(context, "Failed to open reply", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(
                InlineReplyIntents.launchIntent(
                    context,
                    pendingIntent,
                    resultKey,
                    intent.getStringExtra(InlineReplyActivity.EXTRA_PACKAGE_NAME),
                ),
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to open reply", Toast.LENGTH_SHORT).show()
        }
    }
}
