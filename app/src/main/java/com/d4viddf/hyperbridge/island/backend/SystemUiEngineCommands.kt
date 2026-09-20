package com.d4viddf.hyperbridge.island.backend

import android.app.BroadcastOptions
import android.content.Context
import android.content.Intent
import android.os.Build

object SystemUiEngineCommands {
    fun reload(context: Context) {
        val intent = Intent(IslandProtocol.ACTION_RELOAD_ENGINE).setPackage(IslandProtocol.SYSTEM_UI_PACKAGE)
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendBroadcast(intent, null, options)
        } else {
            @Suppress("DEPRECATION") context.sendBroadcast(intent)
        }
    }
}
