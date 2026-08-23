package com.sykeptical.hyperbridge.data.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.widget.RemoteViews
import java.util.Collections
import java.util.LinkedHashMap

class HyperAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return HyperAppWidgetHostView(context)
    }
}

class HyperAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    companion object {
        val cachedRemoteViews: MutableMap<Int, RemoteViews> = Collections.synchronizedMap(
            object : LinkedHashMap<Int, RemoteViews>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, RemoteViews>?): Boolean = size > 32
            }
        )
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)

        if (remoteViews != null) {
            cachedRemoteViews[appWidgetId] = remoteViews
        }

        // [FIX] Calls the public method in WidgetManager
        WidgetManager.notifyWidgetUpdated(appWidgetId)
    }
}
