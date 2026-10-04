package com.sykeptical.hyperpop.xposed

import com.sykeptical.hyperpop.island.backend.IslandProtocol
import com.sykeptical.hyperpop.xposed.hooks.FocusWhitelistHook
import com.sykeptical.hyperpop.xposed.hooks.FocusShadeBackgroundHook
import com.sykeptical.hyperpop.xposed.hooks.CallIslandPresenceHook
import com.sykeptical.hyperpop.xposed.hooks.HeadsUpSuppressionHook
import com.sykeptical.hyperpop.xposed.hooks.IslandClickCleanupHook
import com.sykeptical.hyperpop.xposed.hooks.IslandInlineReplyHook
import com.sykeptical.hyperpop.xposed.hooks.IslandWindowImeHook
import com.sykeptical.hyperpop.xposed.hooks.OuterGlowHook
import com.sykeptical.hyperpop.xposed.hooks.BetterAnimationsHook
import com.sykeptical.hyperpop.xposed.hooks.ExpandedTakeoverHook
import com.sykeptical.hyperpop.xposed.hooks.SecondaryQuarantineHook
import com.sykeptical.hyperpop.xposed.hooks.StatusBarTakeoverHook
import com.sykeptical.hyperpop.xposed.hooks.ExpandedProgressAnimationHook
import com.sykeptical.hyperpop.xposed.hooks.MarqueeHook
import com.sykeptical.hyperpop.xposed.hooks.ActiveIslandDismissHook
import com.sykeptical.hyperpop.xposed.hooks.IslandTextUpdateAnimationHook
import com.sykeptical.hyperpop.xposed.hooks.SystemUiBootstrapHook
import com.sykeptical.hyperpop.xposed.hooks.SystemUiNotificationIngressHook
import com.sykeptical.hyperpop.xposed.hooks.XmsfFocusAuthHook
import com.sykeptical.hyperpop.xposed.hooks.XmsfHandshakeHook
import com.sykeptical.hyperpop.xposed.hooks.screenrecorder.ScreenRecorderHook
import com.sykeptical.hyperpop.xposed.mediacard.MediaCardHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class HyperPopModule : XposedModule() {
    override fun onPackageLoaded(param: PackageLoadedParam) {
        HookConfig.initialize(this)
        when (param.packageName) {
            IslandProtocol.SYSTEM_UI_PACKAGE -> {
                SystemUiBootstrapHook.install(this, param)
                SystemUiNotificationIngressHook.install(this, param)
                IslandClickCleanupHook.install(this, param)
                IslandInlineReplyHook.install(this, param)
                IslandWindowImeHook.install(this, param)
                FocusWhitelistHook.install(this, param)
                FocusShadeBackgroundHook.install(this, param)
                HeadsUpSuppressionHook.install(this, param)
                CallIslandPresenceHook.install(this, param)
                OuterGlowHook.install(this, param)
                ActiveIslandDismissHook.install(this, param)
                ExpandedProgressAnimationHook.install(this, param)
                MarqueeHook.install(this, param)
                IslandTextUpdateAnimationHook.install(this, param)
                BetterAnimationsHook.install(this, param)
                ExpandedTakeoverHook.install(this, param)
                SecondaryQuarantineHook.install(this, param)
                StatusBarTakeoverHook.install(this, param)
                MediaCardHook.install(this, param)
            }
            IslandProtocol.XMSF_PACKAGE -> {
                if (XmsfFocusAuthHook.install(this, param)) {
                    XmsfHandshakeHook.install(this, param)
                }
            }
            IslandProtocol.SCREEN_RECORDER_PACKAGE -> {
                if (HookConfig.screenRecorderEnabled() && HookConfig.replaceScreenRecorder()) {
                    ScreenRecorderHook.install(this, param)
                }
            }
        }
    }
}
