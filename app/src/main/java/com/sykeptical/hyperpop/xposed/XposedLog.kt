package com.sykeptical.hyperpop.xposed

import android.util.Log
import io.github.libxposed.api.XposedModule

fun XposedModule.log(message: String) = log(Log.INFO, "HyperPop", message)
