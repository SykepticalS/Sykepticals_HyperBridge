package com.d4viddf.hyperbridge.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import java.util.ArrayList
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

data class NotificationRemoteViewsExtract(
    val texts: List<String> = emptyList(),
    val bitmaps: List<Bitmap> = emptyList(),
)

/** Reads custom notification layouts when extras only have captions and a media thumbnail. */
object NotificationRemoteViewsParser {
    fun collect(
        notification: Notification,
        context: Context? = null,
        recoverIfMissing: Boolean = false,
    ): NotificationRemoteViewsExtract {
        val views = linkedSetOf<RemoteViews>()
        listOfNotNull(
            notification.contentView,
            notification.bigContentView,
            notification.headsUpContentView,
        ).forEach(views::add)
        if (views.isEmpty() && recoverIfMissing && context != null) {
            runCatching {
                val builder = Notification.Builder.recoverBuilder(context, notification)
                listOfNotNull(
                    builder.createContentView(),
                    builder.createBigContentView(),
                    builder.createHeadsUpContentView(),
                )
            }.getOrNull()?.forEach(views::add)
        }
        val texts = linkedSetOf<String>()
        val bitmaps = linkedSetOf<Bitmap>()
        views.forEach { remoteViews ->
            collectFromActions(remoteViews, texts, bitmaps)
        }
        return NotificationRemoteViewsExtract(texts.toList(), bitmaps.toList())
    }

    private fun collectFromActions(
        remoteViews: RemoteViews,
        texts: MutableSet<String>,
        bitmaps: MutableSet<Bitmap>,
    ) {
        val actions = remoteViewsActions(remoteViews) ?: return
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        actions.forEach { action ->
            walk(action, texts, bitmaps, 0, visited)
        }
    }

    private fun remoteViewsActions(remoteViews: RemoteViews): List<Any>? {
        val field = generateSequence(remoteViews.javaClass as Class<*>?) { it.superclass }
            .mapNotNull { type ->
                runCatching {
                    type.getDeclaredField("mActions").apply { isAccessible = true }
                }.getOrNull()
            }
            .firstOrNull() ?: return null
        return when (val value = runCatching { field.get(remoteViews) }.getOrNull()) {
            is ArrayList<*> -> value.filterNotNull()
            is Array<*> -> value.filterNotNull()
            is List<*> -> value.filterNotNull()
            else -> null
        }
    }

    private fun walk(
        value: Any?,
        texts: MutableSet<String>,
        bitmaps: MutableSet<Bitmap>,
        depth: Int,
        visited: MutableSet<Any>,
    ) {
        if (value == null || depth > 4) return
        if (!visited.add(value)) return
        when (value) {
            // Never descend into boxed numbers, enum constants or their static caches.
            is Number, is Boolean, is Char, is Enum<*>, is Class<*> -> Unit
            is CharSequence -> value.toString().trim().takeIf { it.isNotBlank() }?.let(texts::add)
            is Bitmap -> if (!value.isRecycled && value.width > 1 && value.height > 1) bitmaps.add(value)
            is Array<*> -> value.forEach { walk(it, texts, bitmaps, depth + 1, visited) }
            is Iterable<*> -> value.forEach { walk(it, texts, bitmaps, depth + 1, visited) }
            else -> {
                value.javaClass.declaredFields.forEach { field ->
                    if (field.name == "this$0" || field.type.isPrimitive || Modifier.isStatic(field.modifiers)) return@forEach
                    runCatching {
                        field.isAccessible = true
                        walk(field.get(value), texts, bitmaps, depth + 1, visited)
                    }
                }
            }
        }
    }
}
