package com.d4viddf.hyperbridge.xposed

import com.d4viddf.hyperbridge.island.backend.IslandProtocol
import com.d4viddf.hyperbridge.xposed.hooks.FocusWhitelistHook
import com.d4viddf.hyperbridge.xposed.hooks.HeadsUpSuppressionHook
import com.d4viddf.hyperbridge.xposed.hooks.IslandClickCleanupHook
import com.d4viddf.hyperbridge.xposed.hooks.IslandInlineReplyHook
import com.d4viddf.hyperbridge.xposed.hooks.OuterGlowHook
import com.d4viddf.hyperbridge.xposed.hooks.MarqueeHook
import com.d4viddf.hyperbridge.xposed.hooks.ActiveIslandDismissHook
import com.d4viddf.hyperbridge.xposed.hooks.SystemUiBootstrapHook
import com.d4viddf.hyperbridge.xposed.hooks.SystemUiNotificationIngressHook
import com.d4viddf.hyperbridge.xposed.hooks.XmsfFocusAuthHook
import com.d4viddf.hyperbridge.xposed.hooks.XmsfHandshakeHook
import com.d4viddf.hyperbridge.xposed.hooks.screenrecorder.ScreenRecorderHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class HyperBridgeModule : XposedModule() {
    override fun onPackageLoaded(param: PackageLoadedParam) {
        HookConfig.initialize(this)
        when (param.packageName) {
            IslandProtocol.SYSTEM_UI_PACKAGE -> {
                SystemUiBootstrapHook.install(this, param)
                SystemUiNotificationIngressHook.install(this, param)
                IslandClickCleanupHook.install(this, param)
                IslandInlineReplyHook.install(this, param)
                FocusWhitelistHook.install(this, param)
                HeadsUpSuppressionHook.install(this, param)
                OuterGlowHook.install(this, param)
                ActiveIslandDismissHook.install(this, param)
                MarqueeHook.install(this, param)
            }
            IslandProtocol.XMSF_PACKAGE -> {
                if (XmsfFocusAuthHook.install(this, param)) {
                    XmsfHandshakeHook.install(this, param)
                }
            }
            IslandProtocol.SCREEN_RECORDER_PACKAGE -> {
                if (HookConfig.replaceScreenRecorder()) {
                    ScreenRecorderHook.install(this, param)
                }
            }
        }
    }
}
