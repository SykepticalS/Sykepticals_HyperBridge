package com.d4viddf.hyperbridge.ui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.d4viddf.hyperbridge.receiver.InlineReplyReceiver

object InlineReplyIntents {
    const val ACTION = "com.sykeptical.hyperbridge.action.INLINE_REPLY"
    const val EXTRA_INLINE_REPLY = "hyperbridge.inline_reply"

    fun launchIntent(
        context: Context,
        replyAction: PendingIntent,
        resultKey: String,
        sourcePackage: String? = null,
    ): Intent = Intent(context, InlineReplyActivity::class.java).apply {
        putExtra(InlineReplyActivity.EXTRA_PENDING_INTENT, replyAction)
        putExtra(InlineReplyActivity.EXTRA_RESULT_KEY, resultKey)
        sourcePackage?.let { putExtra(InlineReplyActivity.EXTRA_PACKAGE_NAME, it) }
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_ANIMATION,
        )
    }

    fun pendingIntent(
        context: Context,
        requestCode: Int,
        replyAction: PendingIntent,
        resultKey: String,
        sourcePackage: String? = null,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, InlineReplyReceiver::class.java).apply {
            action = ACTION
            putExtra(InlineReplyActivity.EXTRA_PENDING_INTENT, replyAction)
            putExtra(InlineReplyActivity.EXTRA_RESULT_KEY, resultKey)
            sourcePackage?.let { putExtra(InlineReplyActivity.EXTRA_PACKAGE_NAME, it) }
            putExtra(EXTRA_INLINE_REPLY, true)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}
