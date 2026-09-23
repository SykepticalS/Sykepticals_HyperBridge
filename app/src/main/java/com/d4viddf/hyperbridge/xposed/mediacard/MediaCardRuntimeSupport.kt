package com.d4viddf.hyperbridge.xposed.mediacard

import android.util.Log
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.util.Collections
import java.util.WeakHashMap

internal object MediaCardLog {
    @Volatile var module: XposedModule? = null
    private val states = HashMap<String, String>()

    fun d(tag: String, msg: String) = module?.log("MediaCard/$tag: $msg") ?: Unit
    fun i(tag: String, msg: String) = d(tag, msg)
    fun w(tag: String, msg: String, e: Throwable? = null) {
        Log.w("HyperBridge", "MediaCard/$tag: $msg", e)
        module?.log("MediaCard/$tag warning: $msg${e?.message?.let { ": $it" }.orEmpty()}")
    }
    fun e(tag: String, msg: String, e: Throwable? = null) {
        Log.e("HyperBridge", "MediaCard/$tag: $msg", e)
        module?.log("MediaCard/$tag error: $msg${e?.message?.let { ": $it" }.orEmpty()}")
    }
    fun dState(stateId: String, tag: String, state: String, message: () -> String) {
        val changed = synchronized(states) { states.put(stateId, state) != state }
        if (changed) d(tag, message())
    }
}

/** Tracks imported hook handles so a partially installed feature can roll itself back safely. */
internal object HookRuntimeRegistry {
    private val handles = Collections.synchronizedMap(WeakHashMap<XposedModule, MutableSet<HookHandle>>())
    fun install(module: XposedModule, executable: Executable, hooker: Hooker, deoptimize: Boolean): HookHandle {
        executable.isAccessible = true
        if (deoptimize) runCatching { module.deoptimize(executable) }
        return module.hook(executable).intercept(hooker).also { handle ->
            handles.getOrPut(module) { Collections.synchronizedSet(mutableSetOf()) }.add(handle)
        }
    }
    fun forget(module: XposedModule, handle: HookHandle) { handles[module]?.remove(handle) }
}

internal fun XposedModule.managedHook(
    executable: Executable,
    capability: String,
    hooker: Hooker,
    deoptimize: Boolean = true,
): HookHandle = HookRuntimeRegistry.install(this, executable, hooker, deoptimize)
