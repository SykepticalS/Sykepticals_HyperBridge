package com.d4viddf.hyperbridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class InlineReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Island reply is consumed inside SystemUI by IslandInlineReplyHook. This
        // receiver is deliberately non-visual: if a vendor click path ever evades
        // the hook, it must never fall back to a full-screen/bottom activity.
        Log.w("HyperBridge", "island reply carrier reached app; no activity fallback")
    }
}
