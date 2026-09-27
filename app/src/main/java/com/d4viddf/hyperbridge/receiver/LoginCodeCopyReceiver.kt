package com.d4viddf.hyperbridge.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import com.d4viddf.hyperbridge.R
import com.d4viddf.hyperbridge.island.backend.SystemUiIslandBackend

class LoginCodeCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COPY) return
        val code = intent.getStringExtra(EXTRA_CODE)?.takeIf { it.isNotBlank() } ?: return
        val label = context.getString(R.string.login_code_clip_label)
        // HyperOS rejects clipboard writes from background apps without an error, and island
        // taps always arrive while HyperBridge is in the background.
        SystemUiIslandBackend.get(context)
            .copyToClipboard(label, code, context.getString(R.string.login_code_copied))
            .onFailure { copyLocally(context, label, code) }
        intent.getStringExtra(EXTRA_SOURCE_KEY)?.let { sourceKey ->
            // The engine owns the island state and decides whether "dismiss after copy" applies.
            context.sendBroadcast(
                Intent(ACTION_COPIED)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_SOURCE_KEY, sourceKey)
            )
        }
    }

    private fun copyLocally(context: Context, label: String, code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, code).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        runCatching { clipboard.setPrimaryClip(clip) }
    }

    companion object {
        const val ACTION_COPY = "com.d4viddf.hyperbridge.action.COPY_LOGIN_CODE"
        const val ACTION_COPIED = "com.d4viddf.hyperbridge.action.LOGIN_CODE_COPIED"
        const val EXTRA_CODE = "login_code"
        const val EXTRA_SOURCE_KEY = "source_key"

        fun pendingIntent(context: Context, sourceKey: String, code: String): PendingIntent {
            val intent = Intent(context, LoginCodeCopyReceiver::class.java)
                .setAction(ACTION_COPY)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_SOURCE_KEY, sourceKey)
            return PendingIntent.getBroadcast(
                context,
                31 * sourceKey.hashCode() + code.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
