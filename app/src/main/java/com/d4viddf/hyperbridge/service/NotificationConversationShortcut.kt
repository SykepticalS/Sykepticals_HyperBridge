package com.d4viddf.hyperbridge.service

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import androidx.core.graphics.createBitmap

/** Conversation shortcuts often hold the person name and avatar that extras omit on media shares. */
object NotificationConversationShortcut {
    fun load(context: Context, sbn: StatusBarNotification): ShortcutInfo? {
        val shortcutId = sbn.notification.shortcutId?.takeIf { it.isNotBlank() } ?: return null
        val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(sbn.packageName)
            .setShortcutIds(listOf(shortcutId))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED,
            )
        return runCatching { launcher.getShortcuts(query, sbn.user)?.firstOrNull() }.getOrNull()
    }

    fun label(context: Context, sbn: StatusBarNotification): String? {
        val shortcut = load(context, sbn) ?: return null
        return shortcut.shortLabel?.toString()?.trim()?.ifBlank {
            shortcut.longLabel?.toString()?.trim()
        }
    }

    fun iconBitmap(context: Context, sbn: StatusBarNotification): Bitmap? {
        val shortcut = load(context, sbn) ?: return null
        val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
        val drawable = runCatching {
            launcher.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
        }.getOrNull() ?: return null
        return drawableToBitmap(drawable)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
