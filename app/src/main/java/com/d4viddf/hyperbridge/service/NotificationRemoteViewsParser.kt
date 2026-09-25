package com.d4viddf.hyperbridge.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.view.View
import android.widget.RemoteViews
import java.util.ArrayList
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

data class RemoteViewClick(
    val pendingIntent: PendingIntent,
    val label: String,
)

data class NotificationRemoteViewsExtract(
    val texts: List<String> = emptyList(),
    val bitmaps: List<Bitmap> = emptyList(),
    val progress: Int = 0,
    val progressMax: Int = 0,
    val clicks: List<RemoteViewClick> = emptyList(),
)

/** Reads custom notification layouts when extras only have captions and a media thumbnail. */
object NotificationRemoteViewsParser {
    fun collect(
        notification: Notification,
        context: Context? = null,
        recoverIfMissing: Boolean = false,
        packageName: String? = null,
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
        val viewStates = linkedMapOf<Int, RemoteViewState>()
        views.forEach { remoteViews ->
            collectFromActions(remoteViews, texts, bitmaps)
            readStructuredActions(remoteViews, viewStates)
        }
        if (context != null && !packageName.isNullOrBlank()) {
            viewStates.forEach { (viewId, state) ->
                resourceName(context, packageName, viewId)?.let { state.viewName = it }
                state.imageRes?.let { resId ->
                    resourceName(context, packageName, resId)?.let { name ->
                        state.imageName = name
                        state.descriptions += name
                    }
                }
            }
        }
        val progress = bestProgress(
            viewStates.values.mapNotNull { state ->
                val max = state.max ?: return@mapNotNull null
                if (max <= 0) return@mapNotNull null
                (state.progress ?: 0) to max
            }
        )
        val clicks = playbackClicks(viewStates)
        return NotificationRemoteViewsExtract(
            texts = texts.toList(),
            bitmaps = bitmaps.toList(),
            progress = progress.first,
            progressMax = progress.second,
            clicks = clicks,
        )
    }

    internal class RemoteViewState {
        var progress: Int? = null
        var max: Int? = null
        var click: PendingIntent? = null
        var imageRes: Int? = null
        var imageName: String? = null
        var viewName: String? = null
        var visibility: Int = View.VISIBLE
        val texts = mutableListOf<String>()
        val descriptions = mutableListOf<String>()
    }

    /**
     * Play/pause content descriptions and their click handlers are often different view ids.
     * Pair a playback label with the nearest click when the click itself has no label.
     */
    internal fun playbackClicks(states: Map<Int, RemoteViewState>): List<RemoteViewClick> {
        val labeled = states.mapNotNull { (viewId, state) ->
            if (state.visibility == View.GONE || state.visibility == View.INVISIBLE) return@mapNotNull null
            val intent = state.click ?: return@mapNotNull null
            viewId to RemoteViewClick(
                intent,
                (state.descriptions + state.texts).filter { it.isNotBlank() }.joinToString(" "),
            )
        }
        if (labeled.any { (_, click) -> com.d4viddf.hyperbridge.service.voice.VoicePlaybackDetector.roleFor(click.label) != null }) {
            return labeled.map { it.second }
        }
        val loose = states.flatMap { (viewId, state) ->
            (state.descriptions + state.texts).mapNotNull { text ->
                val role = com.d4viddf.hyperbridge.service.voice.VoicePlaybackDetector.roleFor(text)
                if (role == null) null else viewId to text
            }
        }
        val phrase = loose.firstOrNull() ?: return labeled.map { it.second }
        val target = labeled.minByOrNull { (viewId, _) -> kotlin.math.abs(viewId - phrase.first) }
            ?: return labeled.map { it.second }
        return labeled.map { (viewId, click) ->
            if (viewId == target.first) click.copy(label = phrase.second) else click
        }
    }

    /** Largest max wins so a millisecond seek bar beats a 0–100 decoration. */
    fun bestProgress(samples: List<Pair<Int, Int>>): Pair<Int, Int> {
        val candidate = samples.filter { it.second > 0 }.maxByOrNull { it.second }
        return candidate ?: (0 to 0)
    }

    private fun readStructuredActions(
        remoteViews: RemoteViews,
        states: MutableMap<Int, RemoteViewState>,
    ) {
        val actions = remoteViewsActions(remoteViews) ?: return
        actions.forEach { action -> readAction(action, states) }
    }

    private fun readAction(action: Any, states: MutableMap<Int, RemoteViewState>) {
        val viewId = intField(action, "viewId") ?: intField(action, "mViewId") ?: 0
        val state = states.getOrPut(viewId) { RemoteViewState() }
        deepPendingIntent(action)?.let { state.click = it }
        val method = stringField(action, "methodName") ?: stringField(action, "mMethodName").orEmpty()
        val value = anyField(action, "value") ?: anyField(action, "mValue")
        when (method) {
            "setProgress" -> if (value is Int) state.progress = value
            "setMax" -> if (value is Int) state.max = value
            "setText" -> if (value is CharSequence) state.texts += value.toString()
            "setContentDescription" -> if (value is CharSequence) state.descriptions += value.toString()
            "setImageResource" -> if (value is Int) state.imageRes = value
            "setVisibility" -> if (value is Int) state.visibility = value
        }
        if (value is Icon) {
            runCatching { value.resId }.getOrNull()?.takeIf { it != 0 }?.let { state.imageRes = it }
        }
        intField(action, "progress")?.let { state.progress = it }
        intField(action, "max")?.let { if (it > 0) state.max = it }
        harvestText(action, state)
        if (value is RemoteViews) readStructuredActions(value, states)
    }

    private fun harvestText(action: Any, state: RemoteViewState) {
        var type: Class<*>? = action.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val value = runCatching {
                    field.isAccessible = true
                    field.get(action)
                }.getOrNull() ?: continue
                when (value) {
                    is CharSequence -> {
                        val text = value.toString().trim()
                        if (text.isBlank() || text.startsWith("set")) continue
                        if (field.name.contains("description", ignoreCase = true) ||
                            text.contains("pause", ignoreCase = true) ||
                            text.contains("play", ignoreCase = true)
                        ) {
                            state.descriptions += text
                        } else {
                            state.texts += text
                        }
                    }
                }
            }
            type = type.superclass
        }
    }

    private fun resourceName(context: Context, packageName: String, id: Int): String? {
        if (id == 0) return null
        return runCatching {
            context.packageManager.getResourcesForApplication(packageName).getResourceEntryName(id)
        }.getOrNull()
    }

    private fun intField(target: Any, name: String): Int? {
        val value = anyField(target, name)
        return value as? Int
    }

    private fun stringField(target: Any, name: String): String? = anyField(target, name) as? String

    private fun deepPendingIntent(root: Any): PendingIntent? {
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        return findPendingIntent(root, seen, 0)
    }

    private fun findPendingIntent(value: Any?, seen: MutableSet<Any>, depth: Int): PendingIntent? {
        if (value == null || depth > 4 || value is String || value is Number || value is Boolean) return null
        if (value is PendingIntent) return value
        if (!seen.add(value)) return null
        if (value is Array<*>) {
            return value.firstNotNullOfOrNull { findPendingIntent(it, seen, depth + 1) }
        }
        if (value is Iterable<*>) {
            return value.firstNotNullOfOrNull { findPendingIntent(it, seen, depth + 1) }
        }
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive) continue
                val child = runCatching {
                    field.isAccessible = true
                    field.get(value)
                }.getOrNull()
                findPendingIntent(child, seen, depth + 1)?.let { return it }
            }
            type = type.superclass
        }
        return null
    }

    private fun pendingIntentField(target: Any): PendingIntent? {
        var type: Class<*>? = target.javaClass
        while (type != null && type != Any::class.java) {
            for (field in type.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                if (!PendingIntent::class.java.isAssignableFrom(field.type)) continue
                val value = runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
                if (value is PendingIntent) return value
            }
            type = type.superclass
        }
        return null
    }

    private fun anyField(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null && type != Any::class.java) {
            val field = runCatching { type.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
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
