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

## Expanded island geometry and status-bar relationship

### Finding

- Date: 2026-10-03
- Device/build: Xiaomi 2512BPNDAG (`nezha` / `nezha_tr`); Android 16; fingerprint `Xiaomi/nezha_tr/nezha:16/BP2A.250605.031.A3/OS3.0.305.0.WPATRXM:user/release-keys`. Wireless serial `adb-45a36c27-5TjWL3._adb-tls-connect._tcp`.
- SystemUI/plugin version: `com.android.systemui` `16.03.251211.r`; `miui.systemui.plugin` `17.1.4.71.0`.
- APK SHA-256: `MiuiSystemUI.apk` `A423B9805823B93301A0094732274F4E5EA951F86F333C7B94D9730715576E06`; `MIUISystemUIPlugin.apk` `AE6373D764375748F5BBE4BE9D766E22243B124E38DCE006BD086035DF9A2AED`.
- Class/method/event: `DynamicIslandWindowController` constructor; `DynamicIslandBaseContentView.calculateBigIslandY` / `updateExpandedSize` / `getExpandedViewY`; `DynamicIslandAnimationDelegate.getExpandedAnimState` / `getBigIslandAnimState` / `containerScheduleUpdate` / `bigIslandScheduleUpdate` / `smallIslandToTempHiddenAnimation` / `getCutoutAnimState`; `DynamicIslandEventCoordinator.getExpandedIslandRegion` / `updateTouchRegion`; `DynamicIslandWindowView.getCutoutRect`; `IslandStretchAnimation`; `CollapsedStatusBarFragment.onViewCreated`.
- Evidence source: both
  - Device, idle (no island visible): `dumpsys window` showed `DynamicIslandWindow` at `(0,0)-(1200,0)`, type `KEYGUARD_DIALOG` (2009), gravity `TOP`, `layoutInDisplayCutoutMode=always`, layer `191000`, touchable insets mode 3, empty touch region, `isOnScreen=false`. `StatusBar` is `(fill x 144)`, layer `151000`. `NotificationShade` is layer `171000`. Display cutout bounding rect is `Rect(566, 0 - 634, 144)` at 1200x2608, density 480. Expansion frames were not captured because no island was showing; the phone was not rotated.
  - JADX on the pulled APKs above.
- Observation:
  - Compact, expanded, and secondary islands are children of one plugin window. Expansion does not reparent or use a SurfaceControl transform. The window sits above the status bar, so z-order does not need to change.
  - `expandedViewY` is written only by `calculateBigIslandY`. On a phone it is `statusBarHeight + island_expanded_padding_top` when that is below the compact bottom, otherwise compact bottom plus `island_expanded_padding_top_1`. `getExpandedAnimState` sets `CONTAINER_TRANS_Y` to `expandedViewY - islandViewMarginTop`, which drops the whole content view below the status bar while the outline clip morphs.
  - The black card is the content view's clipped drawing plus `DynamicIslandBackgroundView`, whose drawable bounds track the clip. The clip cannot extend above a translated view, so the drop has to be removed (`CONTAINER_TRANS_Y = 0`) rather than painted over.
  - Every expansion path (`big`/`small`/`init` to expanded, `expandedChanged`, `resetToExpanded`, `resetPress`) builds its target with `getExpandedAnimState`. Collapse uses `getBigIslandAnimState`, whose `CONTAINER_TRANS_Y` is 0. Phone eases stay on the delegate (`CHANGE_EASE`, `SHOW_EASE`, `HIDDEN_EASE`).
  - Expanded content is one Focus view added to `DynamicIslandExpandedView`'s `LightBgView` (`setContentView`). `updateExpandedSize` then assigns that view an explicit width and height. A top margin on that view shifts it inside the expanded view; a fixed expanded-view height equal to the content height would clip the shifted view, so that height has to grow by the same offset when it is an explicit pixel height.
  - `getExpandedIslandRegion` is `(margin, expandedViewY, margin + width, y + height)`. `updateTouchRegion` unions it with the small/big region and publishes touchable insets. `FLAG_WATCH_OUTSIDE_TOUCH` (0x40000) is toggled on the same window while expanded.
  - `DynamicIslandWindowView.getCutoutRect` is the hole Xiaomi uses (screen-centered, `cutoutY ± cutoutHeight/2`). The framework `DisplayCutout` bounding rect is the full status-bar-tall slot and is not the hole.
  - `SmallIslandStateHandler` keeps the secondary in `SmallIsland` while the primary is `Expanded`. `smallIslandToTempHiddenAnimation` does not change that handler, but it is not visual-only: it runs the secondary delegate's Folme to `getCutoutAnimState` (container alpha 0, clip collapsed to the cutout, `CONTAINER_X` 0) and sets scenario 439. Reusing it can leave the secondary stuck at the cutout.
  - `getExpandedAnimState` ends the compact pill at `BIG_ISLAND_ALPHA` 0 and `BIG_ISLAND_BLUR` 1. `bigIslandScheduleUpdate` writes that blur with `updateContentBlur` (`MiSelfBlur` radius `40 * blur`) on `DynamicIslandBigIslandView`, and writes alpha, scale, translationY, and the left/right area `translationX` on that same view. Forcing alpha back to 1 leaves the blurred pill on screen.
  - The secondary pill's screen X is `containerX + getSmallIslandStart()` (`getSmallIslandStart` is screen-center). `getSmallIslandAnimState` sets `CONTAINER_X` from `getSmallIslandX()`, which is the big-island slot (`bigX + bigWidth + space`) and becomes the left edge once `BigIslandStateHandler.current` is null. `CONTAINER_X` 0 is the cutout.
  - `containerScheduleUpdate` runs from Folme `onUpdate` listeners. It stops when the transition settles.
  - Status-bar clock and icons live in `phone_status_bar_left_container`, `system_icon_area`, and `privacy_area`, captured by `IslandStretchAnimation.initMiuiViewsOnViewCreated` on the instance whose `from == 0`. That animator writes `translationX` and, on pad, `alpha`. `setTransitionAlpha` has no callers on those containers (`IslandStretchAnimation`, `MultiSourceMinAlphaController` use `setAlpha` / `translationX`).
  - `calculateBigIslandY` already branches on display rotation and `getHorizontal`. A later rotation re-enters that method through `setCutoutY` / `updateView`.
  - Runtime, same day, after the stabilization build: a Spotify expanded island on the app drawer kept `expanded_view` centered at `(599, 337)` from 0:11 through 0:40 and across a track change, with no bottom gap. Collapse restored `big_island_view` at `(600, 81)` and the clock. No second island was visible, so secondary hide/show was not observed on the phone.
- HyperPop implication: override `getExpandedViewY` / `getExpandedViewHeight` so the existing Folme target starts at `islandViewMarginTop` and grows downward. Leave the compact-pill Folme end state alone so alpha 0 and blur 1 can finish. Fade the three status-bar containers with `transitionAlpha`. While the primary takeover is active, play the secondary's `getHiddenAnimState` at its current `containerX`. Skip the secondary reposition methods, including `smallIslandToBigIslandAnimation` (`getBigIslandAnimState`, which centers the vacated big slot on the cutout) and `smallIslandToTempHiddenAnimation`. On collapse, let Xiaomi's big-to-small transition run; call `smallIslandChangedAnimation` only if that island is no longer in the big state and its handler still has it. Do not abandon a settled expanded session when Folme stops emitting frames. Portrait and unlocked only; any failed self-check returns the native values.

## Expanded island background and corner radius

### Finding

- Date: 2026-10-03
- Device/build: Xiaomi 2512BPNDAG (`nezha` / `nezha_tr`); Android 16; fingerprint `Xiaomi/nezha_tr/nezha:16/BP2A.250605.031.A3/OS3.0.305.0.WPATRXM:user/release-keys`. Same identity as the expanded-island geometry entry.
- SystemUI/plugin version: `miui.systemui.plugin` `17.1.4.71.0`.
- APK SHA-256: `MIUISystemUIPlugin.apk` `AE6373D764375748F5BBE4BE9D766E22243B124E38DCE006BD086035DF9A2AED`.
- Class/method/event: `DynamicIslandBackgroundView.onDraw` / `setDrawable`; `DynamicIslandBaseContentView.updateDarkLightMode` / `updateBackgroundBg`; `DynamicIslandAnimationDelegate.containerClipRadius`; `DynamicIslandAnimationDelegate` outline `getOutline`; `DynamicIslandContentView.updateExpandedView`.
- Evidence source: JADX on the pulled plugin APK, plus `aapt dump` of its resources. No new phone trace for these drawables.
- Observation:
  - `DynamicIslandBackgroundView.onDraw` paints `drawable` into bounds taken from `actualLeft/Top/Width/Height`, which the content outline provider writes every frame from `containerX/TransY` plus the clip progress. The drawable is behind the content, not an overlay HyperPop adds.
  - `updateDarkLightMode` installs `dynamic_island_background_big_island_dark` for the phone dark path. That shape is a `GradientDrawable`: solid `stroke_color` (`#1FFFFFFF`) and corner radius `island_radius`. Expanded state then calls `setStroke(island_stroke, stroke_color)` on it. `island_radius` is 30dp. `island_stroke` is 1.4dp. `island_height` is 34dp, so the compact clip radius is `min(17dp, 30dp)`.
  - `updateBackgroundBg` runs on `DynamicIslandExpandedView`. When background blur is open it enables MiBlur mode 1 and blend colors and clears the view background. When blur is closed it sets `dynamic_island_background`, a shape whose solid is `#FF000000` and which has no corner radius of its own.
  - `containerClipRadius()` is `min((containerClipBottomProgress - containerClipTopProgress) / 2, island_radius)`. The outline provider passes that value to `outline.setRoundRect`. `LightBgView` is the calling-effect host inside the expanded view, not the card fill.
- HyperPop implication: a black expanded surface has to recolor a mutated copy of this drawable and clear the expanded view's MiBlur, or the blur keeps covering the fill. A pill radius has to change `containerClipRadius` and the same drawable's corner radius together. Neither is an extra view.
- Phone check, 2026-10-03, same device, HyperPop 0.6.1-sykeptical code 36, both expanded-style toggles on: an expanded Spotify island measured about 1094×473px with a corner curve of about 180px (the 56dp cap; native `island_radius` is 90px at this density) and a pure-black center column through the camera, then a smooth fade into the Ambient Flow color. Collapse returned the compact pill and the status-bar clock. `updateBackgroundBg` throws `NullPointerException: null receiver` from `isNotificationPromotedOngoing` when `currentIslandData` is already null, so teardown must not call it.

## Secondary island click while another island is expanded

### Finding

- Date: 2026-10-03
- Device/build: Xiaomi 2512BPNDAG (`nezha` / `nezha_tr`); Android 16; fingerprint `Xiaomi/nezha_tr/nezha:16/BP2A.250605.031.A3/OS3.0.305.0.WPATRXM:user/release-keys`. Same identity as the expanded-island geometry entry.
- SystemUI/plugin version: `miui.systemui.plugin` `17.1.4.71.0`.
- APK SHA-256: `MIUISystemUIPlugin.apk` `AE6373D764375748F5BBE4BE9D766E22243B124E38DCE006BD086035DF9A2AED`.
- Class/method/event: `DynamicIslandTouchInteractor.onInterceptTouchEvent` / `performClick`; `DynamicIslandContentView.onIslandClick`; `ClickEventCoordinator.handleAppEvent`; `DynamicIslandEventCoordinator.getSmallBigIslandRegion` / `updateTouchRegion`; `DynamicIslandAnimationDelegate.getHiddenAnimState` / `smallIslandToExpandedAnimation`.
- Evidence source: JADX on the pulled plugin APK above. No new phone trace.
- Observation:
  - A compact click is not a view `OnClickListener`. `onInterceptTouchEvent` sets `downInBigIsland` from `getBigIslandRect`, and `downInSmallIsland` only when both big and small handler currents exist, using `getSmallIslandRect` at `bigX + bigWidth + space` (RTL mirrored). `performClick` then calls `onIslandClick` on that handler's current view. `onIslandClick` dispatches `ClickDynamicIsland`.
  - `ClickEventCoordinator` expands a big island by clearing the big handler and moving that view to expanded. It expands a small island by leaving the big handler current in place, clearing the small handler, and moving the small view to expanded. `getExpandedAnimState` builds the target for both `bigIslandToExpandedAnimation` and `smallIslandToExpandedAnimation`.
  - `updateTouchRegion` unions `getExpandedIslandRegion` with `getSmallBigIslandRegion`. When the big handler is empty and only a small island remains, that second region is `createDefaultRegion`: a 200dp-wide band around screen center, from 10dp to 42dp. Alpha 0 does not clear either press flag.
  - `getHiddenAnimState` is the small-island hidden Folme state (container alpha 0, clip inset, `CONTAINER_X` from `getSmallIslandX`). It does not remove the press flags.
- HyperPop implication: status-bar fade has to arm for whichever content view is entering `Expanded`, including one whose previous state is `SmallIsland`, using that circle as the compact rect. While the other island is hidden, clear only its `downInBigIsland` / `downInSmallIsland` flag before `performClick`, and drop only its contribution from `getSmallBigIslandRegion`. Leave expanded-island and shade touches alone. Do not cancel the hidden source.

## Expanded swipe-up, background outset, and a second expanding island

### Finding

- Date: 2026-10-03
- Device/build: Xiaomi 2512BPNDAG (`nezha` / `nezha_tr`); Android 16; fingerprint `Xiaomi/nezha_tr/nezha:16/BP2A.250605.031.A3/OS3.0.305.0.WPATRXM:user/release-keys`. Same identity as the expanded-island geometry entry.
- SystemUI/plugin version: `miui.systemui.plugin` `17.1.4.71.0`.
- APK SHA-256: `MIUISystemUIPlugin.apk` `AE6373D764375748F5BBE4BE9D766E22243B124E38DCE006BD086035DF9A2AED`.
- Class/method/event: `DynamicIslandTouchInteractor.onInterceptTouchEvent` / `onTouchEvent`; `DynamicIslandWindowView.collapse`; `DynamicIslandAnimationDelegate.swipeUpExpandedAnimation`; `DynamicIslandBackgroundView.onDraw`; `AddEventCoordinator.handleAppEvent`; `ExpandedStateHandler.handleReplacedState`; `DynamicIslandEventCoordinator.isTempHidden`.
- Evidence source: JADX on the pulled plugin APK above.
- Observation:
  - Swipe-up collapse is native. `onInterceptTouchEvent` sets `downInExpanded` from `getExpandedIslandRect()` (`margin, expandedViewY, margin + width, y + height`). On `ACTION_UP`, an upward move past `swipeThreshold` calls `DynamicIslandWindowView.collapse("swipe up")`, which dispatches `DynamicIslandEvent.Collapse` unless `openAppFromIsland` is set or nothing is expanded. The drag itself is `swipeUpExpandedAnimation`, which tracks `getExpandedViewHeight()`.
  - `DynamicIslandBackgroundView.onDraw` draws the drawable outset by `stokeWidth` on every side, outside the content clip. The expanded drawable keeps `setStroke(island_stroke, stroke_color)`.
  - `AddEventCoordinator.handleAppEvent(AddDynamicIsland)` with `canExpanded` true calls `ExpandedStateHandler.handleReplacedState`, which makes the new view `Expanded` and passes the previous expanded view down the chain in the same call. `canExpanded` does not look at `userExpanded`. `isTempHidden` on the event coordinator takes the content view; a no-arg call does not exist.
  - Media content is sized with `updateExpandedSize(maxWidth, maxHeight)`. Other templates, including calls, use the focus view's own height.
- HyperPop implication: the visible pill has to stay inside `getExpandedIslandRect` or the swipe never starts. Zero the drawable stroke and `stokeWidth` together while the styler owns the island, and restore both. A second expand-required add has to wait until `collapse` finishes; replaying `handleAppEvent` lets Xiaomi place the new island. Pass the content view to `isTempHidden`.
- Phone check, 2026-10-03, HyperPop 0.6.1-sykeptical code 36 after this pass: an expanded Spotify island on the home screen kept rewind, pause, and forward in one row with the timeline underneath, the status-bar clock hidden, and no separate black ring. An upward swipe logged `direction: UP` and `skip collapse=(false||false||true), reason=swipe up`, then the compact pill and the clock returned. A hook that returned null from `access$onInterceptTouchEvent` had been discarding that intercept.

## App-exit expand still shows the native media card first

### Finding

- Date: 2026-10-03
- Device/build: Xiaomi 2512BPNDAG (`nezha` / `nezha_tr`); Android 16; fingerprint `Xiaomi/nezha_tr/nezha:16/BP2A.250605.031.A3/OS3.0.305.0.WPATRXM:user/release-keys`. Same identity as the expanded-island geometry entry.
- SystemUI/plugin version: `miui.systemui.plugin` `17.1.4.71.0`.
- APK SHA-256: `MIUISystemUIPlugin.apk` `AE6373D764375748F5BBE4BE9D766E22243B124E38DCE006BD086035DF9A2AED`.
- Class/method/event: `DynamicIslandBackgroundView.actualHeight`; `DynamicIslandBaseContentView.updateMedianLuma`; `DynamicIslandExpandedView`; runtime views `LightBgView`, `MusicBgView`, `PlayerIslandConstraintLayout`, `DynamicIslandContentFakeView`.
- Evidence source: logcat `HyperPopPlate` at 21:25:18 and 21:30:55, plus raw screencaps from the on-device Spotify exit burst (`ex_0.raw` through `ex_7.raw`).
- Observation:
  - `updateMedianLuma` loads a fresh `dynamic_island_background_big_island_dark` and calls `DynamicIslandBackgroundView.setDrawable` on every pass. That is the only writer besides `updateDarkLightMode`.
  - At takeover arm the background `actualHeight` was 132 while `getExpandedView()` was already 1087×502. The fake content view was `GONE`. The expanded tree was `DynamicIslandExpandedView` → `LightBgView` → `PlayerIslandConstraintLayout` → `MusicBgView` (1087×502, background null) plus the art, titles, and controls.
  - Timed bursts after replacing the background drawable still showed the native full-bleed media card for the frames about 0.15–0.6s after the island tap. The black pill, with wallpaper at the screen corner and the battery, appeared on the following frame.
- HyperPop implication: recoloring `DynamicIslandBackgroundView` does not cover this window. The drawn card is the expanded view, and `MusicBgView` / `LightBgView` paint without a background drawable.
- Correction, same day, same device/plugin, HyperPop 0.6.1-sykeptical code 36 with temporary `HyperPopSnap` tracing (draw listener on the island window root, hooks on the methods below), raw screencap bursts, and a `dumpsys SurfaceFlinger` taken inside the bad frames. The implication above was wrong:
  - The native card in those frames is `DynamicIslandContentFakeView`, Xiaomi's app-close stand-in. The real island was fully styled the whole time: background and expanded view `VISIBLE`, the owned black plate, `MusicBgView` `INVISIBLE` at alpha 0, and the pill radius override active.
  - On the tap, `setState(Expanded)` ran while `getIslandWindowAnimRunning()` was still true from the Spotify close. HyperPop set the fake view `GONE`. About 20ms later Xiaomi called `onWindowAnimExtendLifetimeEnd` and set the fake back to `VISIBLE`. It stayed up until Xiaomi's own close end about 0.9s later: `updateIslandWindowAnimRunning(false, view, false)`, `onWindowAnimExtendLifetimeEnd`, `updateViewStateWhenCloseEnd`, fake `setVisibility(4)`, then `alreadyCloseAppEnd`. The black pill appeared on the next frame.
  - `DynamicIslandContentFakeView.setVisibility` (JADX) runs a handoff only for `VISIBLE` → `INVISIBLE` (4), when the real state is not `AppExpanded` / `MiniWindowExpanded` and `isIslandWindowAnimating(real)` is true. It shows the real view and background, posts `alreadyCloseAppEnd` after 50ms, clears `openAppFromIsland`, calls `updateIslandWindowAnimRunning(false, real, false)`, then `onWindowAnimExtendLifetimeEnd`. `GONE` (8) skips all of it. `alreadyCloseAppEnd` forwards `onDynamicPluginCallback_alreadyCloseAppEnd` to the host. `hideAllElementSurface` sends the same callback.
  - During the bad frames, SurfaceFlinger composited `SurfaceControlViewHost-com.spotify.music` (owned by SystemUI, 1200×498 buffer, scale animating, `roundedCorner` 124) below `DynamicIslandWindow`. It was still listed about 0.12s after `alreadyCloseAppEnd`; the host removes it asynchronously.
  - With the fake set `INVISIBLE` instead, the handoff ran at the tap. The fake never came back, and all eight frames from about 0.03s to 1.4s after the tap were the black pill: clock and card `#000000`, corner and battery wallpaper, top-left corner about 180px.
- HyperPop implication, corrected: when the takeover uncovers the real island, hide a visible fake content view with `INVISIBLE`, never `GONE`, so Xiaomi's own handoff ends the app-close window animation.

## Expanded Focus template and media ConstraintSet

### Finding

- Date: 2026-10-03
- Device/build: Xiaomi 2512BPNDAG (`nezha` / `nezha_tr`); Android 16; fingerprint `Xiaomi/nezha_tr/nezha:16/BP2A.250605.031.A3/OS3.0.305.0.WPATRXM:user/release-keys`.
- SystemUI/plugin version: `com.android.systemui` `16.03.251211.r`; `miui.systemui.plugin` `17.1.4.71.0`.
- APK SHA-256: `MiuiSystemUI.apk` `A423B9805823B93301A0094732274F4E5EA951F86F333C7B94D9730715576E06`; `MIUISystemUIPlugin.apk` `AE6373D764375748F5BBE4BE9D766E22243B124E38DCE006BD086035DF9A2AED`.
- Class/method/event: `DynamicIslandWindowView.getCutoutRect`; `TemplateFactoryV3.createStandardTemplateView`; `TemplateBuilderV3.updateModuleView`; `MiuiIslandMediaControllerImpl` ConstraintSet `xml/miui_media_session_island_normal`; `PlayerIslandConstraintLayout.onAttachedToWindow`.
- Evidence source: JADX on the pulled APKs above. No new phone trace for these methods.
- Observation:
  - `getCutoutRect` is a square: width is `DisplayCutout.getBoundingRectTop().width()` (fallback 20dp), height is `min(width, island_height * 0.9)` with `island_height` 34dp, and Y is `cutoutY` which starts at 0 until the host pushes it.
  - The expanded Focus view is one `focus_notification_template_standard` copy. Module A is icon/text (`focus_title` 18dp, `focus_content` 14dp, profile 48dp). Module C action icons are 52dp with a 10dp gap. Module D text buttons use 13dp labels. Titles use `ellipsize=end`, not marquee. `updatePartial` calls `bind` again. The chronometer ticks without a rebind.
  - The expanded media layout is 364×168dp. `normalLayoutIsland.applyTo` runs on every attach, so margins set only on the live views are overwritten. Title and artist are single-line `ellipsize=end`. Playback polls the seek bar every 500ms and does not change layout.
- HyperPop implication: pill-mode content can share the camera band only when the leaf does not cross the hole, or when it is a title that scrolls inside the safe span. Media retuning has to go through the ConstraintSet load path. Focus retuning has to run again after `bind`. A `cutoutY` of 0 is not a usable hole; rebuild from the compact island and the framework cutout width.

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
