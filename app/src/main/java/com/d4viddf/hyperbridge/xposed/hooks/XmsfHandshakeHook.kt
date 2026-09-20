package com.d4viddf.hyperbridge.xposed.hooks

import android.app.Application
import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.xposed.log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

object XmsfHandshakeHook {
    @Volatile private var installed = false

    fun install(module: XposedModule, param: PackageLoadedParam) {
        if (installed) return
        installed = true
        runCatching {
            val onCreate = param.defaultClassLoader.loadClass("android.app.Application").getDeclaredMethod("onCreate")
            module.hook(onCreate).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Application)?.let { app ->
                    app.registerReceiver(
                        object : BroadcastReceiver() {
                            override fun onReceive(context: Context, intent: Intent) {
                                if (!IslandProtocol.compatible(intent.getIntExtra(IslandProtocol.EXTRA_PROTOCOL, -1))) return
                                val pong = Intent(IslandProtocol.ACTION_PONG).apply {
                                    setPackage(IslandProtocol.APP_PACKAGE)
                                    putExtra(IslandProtocol.EXTRA_PROTOCOL, IslandProtocol.VERSION)
                                    putExtra(IslandProtocol.EXTRA_NONCE, intent.getLongExtra(IslandProtocol.EXTRA_NONCE, 0L))
                                    putExtra(IslandProtocol.EXTRA_HOOK_PACKAGE, IslandProtocol.XMSF_PACKAGE)
                                    putExtra(IslandProtocol.EXTRA_CAPABILITIES, IslandProtocol.CAP_FOCUS_BYPASS)
                                }
                                context.sendBroadcast(
                                    pong,
                                    null,
                                    BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle(),
                                )
                            }
                        },
                        IntentFilter(IslandProtocol.ACTION_PING_XMSF),
                        IslandProtocol.PERMISSION,
                        null,
                        Context.RECEIVER_EXPORTED,
                    )
                }
                result
            }
        }.onFailure {
            installed = false
            module.log("XMSF handshake hook failed: ${it.message}")
        }
    }
}
