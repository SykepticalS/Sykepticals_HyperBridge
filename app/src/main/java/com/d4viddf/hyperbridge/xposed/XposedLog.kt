package com.d4viddf.hyperbridge.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule

fun XposedModule.log(message: String) = log(Log.INFO, "HyperBridge", message)
