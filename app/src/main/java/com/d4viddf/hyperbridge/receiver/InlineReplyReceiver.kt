package com.d4viddf.hyperbridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.d4viddf.hyperbridge.ui.InlineReplyActivity

class InlineReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activityIntent = Intent(context, InlineReplyActivity::class.java).apply {
            putExtras(intent)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(activityIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to open reply", Toast.LENGTH_SHORT).show()
        }
    }
}
