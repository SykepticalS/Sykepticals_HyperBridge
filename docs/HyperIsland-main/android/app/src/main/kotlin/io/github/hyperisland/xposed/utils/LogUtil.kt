package io.github.hyperisland.xposed

import android.util.Log
import io.github.hyperisland.xposed.ConfigManager
import io.github.libxposed.api.XposedModule

fun XposedModule.log(message: String) {
    if (ConfigManager.isDebugLogEnabled())
        log(Log.DEBUG, "HyperIsland", message)
}

fun XposedModule.logWarn(message: String) =
    log(Log.WARN, "HyperIsland", message)

fun XposedModule.logError(message: String) =
    log(Log.ERROR, "HyperIsland", message)

fun log(message: String) {
    if (ConfigManager.isDebugLogEnabled())
        ConfigManager.module()?.log(Log.DEBUG, "HyperIsland", message)
}

fun logWarn(message: String) =
    ConfigManager.module()?.log(Log.WARN, "HyperIsland", message)

fun logError(message: String) =
    ConfigManager.module()?.log(Log.ERROR, "HyperIsland", message)
