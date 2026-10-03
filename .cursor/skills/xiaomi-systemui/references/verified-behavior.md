# Verified Xiaomi / HyperOS behavior

This file contains evidence, not assumptions.

If no matching entry exists for the current device/build, investigate instead
of extrapolating from an unrelated build.

## Device / build / APK identity

### Finding

- Date: 2026-10-02
- Device/build: Xiaomi 2512BPNDAG (`nezha` / `nezha_tr`); Android 16 / API 36; fingerprint `Xiaomi/nezha_tr/nezha:16/BP2A.250605.031.A3/OS3.0.305.0.WPATRXM:user/release-keys`; incremental `OS3.0.305.0.WPATRXM`. Wireless ADB serial `adb-45a36c27-5TjWL3._adb-tls-connect._tcp`.
- SystemUI/plugin version:
  - `com.android.systemui` versionName `16.03.251211.r`, versionCode `202501210`
  - `miui.systemui.plugin` versionName `17.1.4.71.0`, versionCode `171047100`
- APK SHA-256:
  - `MiuiSystemUI.apk` `A423B9805823B93301A0094732274F4E5EA951F86F333C7B94D9730715576E06`
  - `MIUISystemUIPlugin.apk` `AE6373D764375748F5BBE4BE9D766E22243B124E38DCE006BD086035DF9A2AED`
- Class/method/event: class presence only. No methods or runtime events were traced.
- Evidence source: both
  - Device: `getprop`, `pm path`, and `dumpsys package` on the serial above.
  - JADX: `load_apk` plus `search_classes_by_keyword` on the pulled copies. `get_all_classes` was not used.
- Observation:
  - Device paths, each a single base APK (`splits=[base]` for SystemUI; plugin `pm path` returned one file):
    - `com.android.systemui` → `/system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk` (63,718,463 bytes), copied to `.agent-local/apks/MiuiSystemUI.apk`
    - `miui.systemui.plugin` → `/product/app/MIUISystemUIPlugin/MIUISystemUIPlugin.apk` (30,166,397 bytes), copied to `.agent-local/apks/MIUISystemUIPlugin.apk`
  - `MIUISystemUIPlugin.apk` declares package `miui.systemui.plugin` and contains the Dynamic Island UI classes HyperPop hooks, including:
    - `miui.systemui.dynamicisland.DynamicFeatureConfig`
    - `miui.systemui.dynamicisland.anim.DynamicIslandAnimationController`
    - `miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate`
    - `miui.systemui.dynamicisland.display.AvoidScreenBurnInHelper`
    - `miui.systemui.dynamicisland.event.ClickEventCoordinator`
    - `miui.systemui.dynamicisland.module.IslandModuleViewHolderAdapter`
    - `miui.systemui.dynamicisland.module.IslandIconViewHolder`
    - `miui.systemui.dynamicisland.view.DynamicGlowEffectView`
    - `miui.systemui.dynamicisland.view.DynamicIslandExpandedView`
    - `miui.systemui.dynamicisland.window.DynamicIslandWindowView`
    - `miui.systemui.dynamicisland.window.DynamicIslandWindowViewController`
    - `miui.systemui.dynamicisland.window.DynamicIslandSafeguardsController`
    - `miui.systemui.dynamicisland.window.content.DynamicIslandContentView`
    - `miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView`
    - `miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView`
    - `miui.systemui.dynamicisland.window.content.helpers.DynamicIslandContentViewPhoneHelper`
    - `miui.systemui.notification.NotificationSettingsManager`
    - `miui.systemui.notification.focus.FocusNotificationController`
    - `miui.systemui.notification.focus.FocusNotifPreHandler` (and its `ClickHandler`)
    - `miui.systemui.notification.focus.FocusNotifUtils`
    - `miui.systemui.notification.focus.SignatureChecker`
    - `miui.systemui.notification.focus.moduleV3.ModuleViewHolder`
    - `com.mi.widget.core.AbsShader`
  - The same plugin APK does not contain `com.android.systemui.statusbar.notification.DynamicIslandController`, any class matching `mediaisland.MiuiIsland`, or `com.mi.widget.view.MusicBgView`.
  - `MiuiSystemUI.apk` declares package `com.android.systemui` and contains:
    - `com.android.systemui.statusbar.notification.DynamicIslandController`
    - `com.android.systemui.statusbar.notification.MiuiNotificationListener`
    - `com.android.systemui.shared.plugins.PluginInstance.PluginFactory`
    - `com.miui.systemui.notification.MiuiBaseNotifUtil`
    - `com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl`
    - `com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaControllerImpl`
    - `com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewBinderImpl`
    - `com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder`
  - The same SystemUI APK does not contain `miui.systemui.dynamicisland.window.DynamicIslandWindowView` or `miui.systemui.notification.focus.FocusNotificationController`. A search for `miui.systemui.dynamicisland.` in that APK returned no classes.
  - `MusicBgView` was not found in the plugin APK. It was not searched in `MiuiSystemUI.apk`.
  - `com.xiaomi.xmsf` and `com.miui.screenrecorder` were not pulled or loaded. Their `pm path` results were not used as class-ownership evidence.
- HyperPop implication: the Dynamic Island window, animation, glow, and focus classes HyperPop loads under `miui.systemui.dynamicisland` and `miui.systemui.notification.focus` are in `MIUISystemUIPlugin.apk`. `DynamicIslandController` and the `mediaisland` classes HyperPop loads are in `MiuiSystemUI.apk`.

## Settings visual metrics in MiuiSystemUI

### Finding

- Date: 2026-10-02
- Device/build: Xiaomi 2512BPNDAG (`nezha` / `nezha_tr`); Android 16; fingerprint `Xiaomi/nezha_tr/nezha:16/BP2A.250605.031.A3/OS3.0.305.0.WPATRXM:user/release-keys`. Same wireless serial as the identity entry above.
- SystemUI/plugin version: `com.android.systemui` versionName `16.03.251211.r` (see identity entry).
- APK SHA-256: `MiuiSystemUI.apk` `A423B9805823B93301A0094732274F4E5EA951F86F333C7B94D9730715576E06`
- Class/method/event: `miuix.slidingwidget.widget.SlidingButtonHelper.initResource`, `miuix.androidbasewidget.widget.SeekBar.onDraw`, and the `miuix_appcompat_sliding_button_*`, `miuix_appcompat_seekbar_height`, `miuix_preference_item_*`, `miuix_appcompat_action_bar_title_horizontal_padding`, `miuix_theme_radius_common`, `miuix_font_size_title1` resources.
- Evidence source: JADX on the pulled `MiuiSystemUI.apk`, plus `aapt dump --values resources` for the dimen/color payloads. Display Settings on the phone was also measured: card corner matches 16dp, seek track height matches 28dp, switch width matches 49dp.
- Observation:
  - Sliding button computed size is width 49dp and height 32dp (28dp bar plus 2dp frame padding on each side). Thumb is 20dp, inset 4dp. Frame corner radius is 36dp. Default on-color is `#3482FF` light / `#277AF7` dark (`miuix_color_blue_*_primary_default`). Off track is `miuix_color_white_level7` (`#33FFFFFF`) in dark and `miuix_color_black_level7` (`#1A000000`) in light.
  - Seek bar progress height is `miuix_appcompat_seekbar_height` = 28dp. Custom track corner is 14dp.
  - Preference row minimum height is 56dp. Horizontal content padding is 16dp. Vertical item padding is 14dp. Page horizontal padding token `miuix_theme_padding_horizontal_common` is 12dp. Expanded title inset `miuix_appcompat_action_bar_title_horizontal_padding` is 26dp. Expanded title size `miuix_font_size_title1` is 32sp. Group corner `miuix_theme_radius_common` is 16dp.
  - The connected Settings app’s brightness slider was themed blue `#5786F7`, not the miuix default `#3482FF`. Geometry still matched the dimens above.
- HyperPop implication: settings controls should use these miuix dimens. Accent may follow the live theme; the code default remains `#3482FF` / `#277AF7`.

## Entry template

### Finding

- Date:
- Device/build:
- SystemUI/plugin version:
- APK SHA-256:
- Class/method/event:
- Evidence source: device trace / JADX / both
- Observation:
- HyperPop implication:
