# Island inline Reply — handoff prompt

You are working in `C:\Users\ataha\Desktop\Desktop\HyperBridge-master` (HyperBridge). Device is a rooted Xiaomi/HyperOS phone (`nezha` / `2512BPNDAG`) with wireless ADB already connected and LSPosed loading `HyperBridgeModule` into **SystemUI** and the **MIUISystemUIPlugin** classloader.

## What Reply must do

HyperBridge posts HyperOS Dynamic Island notifications. Messaging islands get a **Reply** text button. Tapping Reply must:

1. **Stay inside the expanded island.** Replace the island’s own text-button row (`ModuleTextButtonViewHolder` / parent of the Reply `TextView`) with an `EditText` + send control. The composer must be a child of `DynamicIslandExpandedView` (or that button row). It must **not** be a new window, overlay, Compose activity, or `Gravity.BOTTOM` `addView` on `DynamicIslandWindowView` / any fullscreen SystemUI root.
2. **Keep the island expanded** while composing. Disable every auto-hide path until the user **sends** or **taps out** of the island. The island currently still collapses on its normal auto-hide / `expandedTime` timer.
3. Send the original notification `RemoteInput` (`PendingIntent` + `resultKey` currently stuffed into `InlineReplyIntents`).
4. Do **not** update the island from the app’s “You: …” self-echo after send (`OutgoingReplyEchoDetector` already exists for this).

## What the user still sees (bugs to fix)

After several patches, **nothing changed from the user’s point of view**:

- Pressing Reply still shows the **inline reply bar at the bottom of the screen** (the `InlineReplyActivity` overlay: full-screen dim + `Alignment.BottomCenter` + `imePadding`). That is the wrong UI.
- The island still **dismisses/shrinks on the auto-hide timer** while the composer is open.

If the in-island embed fails, current code is supposed to swallow the PendingIntent and retry. If the user still sees the bottom bar, **the intercept is not firing, not matching the PI, or Xiaomi is starting the activity without going through the hooked `PendingIntent.send` / plugin click path**. Treat that as the primary bug. Do not keep adding overlay `addView` fallbacks.

## Current architecture (do not blindly extend it)

Reply button wiring:

- `BaseTranslator.kt` (~466–488): if the notification action has `RemoteInput` and inline reply is enabled, the island `textButton` PI is **`PendingIntent.getActivity` → `InlineReplyActivity`**, `actionIntentType = 1`.
- `IslandVisualMetadata.fixTextButtonJson`: copies `actionIntent` → `action` (V3 island reads `action`).
- `InlineReplyActivity.kt`: **this is the bottom-of-screen overlay the user still sees.** It must never launch for island Reply.
- `InlineReplyReceiver.kt`: leftover broadcast trampoline; not the desired path.

Attempted SystemUI intercept (`IslandInlineReplyHook.kt` + `IslandReplyComposer`):

- Hooks `PendingIntent.send` / `sendAndReturnResult`.
- Hooks plugin: `FocusNotifPreHandler$ClickHandler`, `FocusNotifUtils`, `ClickEventCoordinator`, `ModuleTextButton*ViewHolder`, `startPendingIntent` / `clickWithCollapse` / `onClick`.
- On match of extras `hyperbridge.inline_reply` / component `InlineReply*`, swallows the PI and tries to `embed()` an EditText into a guessed button-row `ViewGroup` found by walking `WindowManagerGlobal.mViews` for `DynamicIslandExpandedView` or reflecting `itemView` off the click source.
- `openNow()` **always returns true** even when `embed()` returned false, then retries at 50ms/160ms. If embed never finds a host, **Reply does nothing** — unless Xiaomi still starts the activity on a path we don’t hook, which matches the user’s “bottom bar still appears.”
- Auto-hide hold: `MarqueeHook.holdAutoHide()`, `SystemUiDispatcher.notifyReplyComposer(true)` → engine `replyComposerHold` pauses `timeoutJobs`, skip `onDynamicPluginCallback_expandedToSmall/bigToSmall/expandedToBig`, name-filter hook on `DynamicIslandAnimationDelegate` (`ToSmall` / `resetToSmall` / `getExpandedTime` / `TimeoutMs`). **Xiaomi still collapses**, so the real timer is not those methods, or the plugin ClassLoader is never hooked, or the timeout is a `Handler`/`postDelayed`/`mTimeoutMs` field on another class.

IME:

- `IslandWindowImeHook` + `IslandWindowImePolicy`: while composing, clear `FLAG_NOT_FOCUSABLE`, use `SOFT_INPUT_ADJUST_NOTHING` so the island window does not pan above the keyboard. Idle island must stay `FLAG_NOT_FOCUSABLE` **without** `FLAG_ALT_FOCUSABLE_IM`.

Module install: `HyperBridgeModule` → SystemUI only, plus `DynamicClassLoaderHooks.observe` for the plugin loader. Plugin classes live under `miui.systemui.*` in **MIUISystemUIPlugin**, not SystemUI’s default loader.

## Why previous approaches failed (do not repeat)

| Approach | Why it failed |
|---|---|
| Launch `InlineReplyActivity` | Always a **screen-bottom overlay**, often above the IME. User rejected this. |
| `host.addView(composer, Gravity.BOTTOM)` on Expanded/Content/Big/Window view | Either missed the host and fell through to the activity, or attached to a **fullscreen** window so the bar sat at the bottom of the **screen**. |
| Swallow PI before attach / skip collapse on first tap | Reply did nothing. |
| `forceHostOpaque` / fighting HyperOS alpha | Composer vanished. |
| Making the island window focusable without `ADJUST_NOTHING` | Island jumped above the keyboard. |
| Guessing `ModuleTextButtonViewHolder` + `AnimationDelegate` method **name** filters | User still sees activity + timer dismiss → hooks likely never bind on the live plugin loader, or click/timeout use different classes/methods than assumed. |
| Engine `replyComposerHold` gated on `sentFromUid` being SystemUI | Hold never set; later relaxed, but Xiaomi `expandedTime` still wins. |

## Required fix strategy

**Prove the click and timeout paths on device before writing more guess-hooks.**

1. Dump the live plugin from the phone (`/system_ext` or overlay APK for `miui.systemui.plugin` / `MIUISystemUIPlugin`). Find:
   - Exact class/method that runs island **textButton** clicks (`startPendingIntent`, `clickWithCollapse`, ViewHolder bind/click, `PendingIntent.send`, ActivityOptions trampoline, etc.).
   - Exact class/method that schedules expanded → small (`expandedTime`, `getExpandedTime`, `mTimeoutMs`, `resetToSmall`, `expandedToSmallIslandAnimation`, `Handler` callbacks on `DynamicIslandAnimationDelegate` or window controller).
2. Hook **those** methods. On HyperBridge Reply PI:
   - Do not `proceed()` and do not start `InlineReplyActivity`.
   - Keep a strong reference to the clicked button’s `View` / ViewHolder `itemView`.
   - Hide siblings in that **small** button container and add the EditText there (`MATCH_PARENT` in that row, not the window).
3. Hold **Xiaomi’s** expand timeout (cancel/replace the runnable or force `expandedTime` to stay huge **and** skip only timer-driven ToSmall). Do **not** block user tap-out (`dropDownExpandedIsland`). On tap-out or send, release holds and restore buttons.
4. IME: composer flags + `ADJUST_NOTHING` only while the field is focused.
5. **Delete the fallback to `InlineReplyActivity` for island Reply.** If embed fails, log the view tree (`DynamicIslandExpandedView` dump) and retry on the next layout pass — never start the activity.

## Constraints

- APK install **must** be wireless ADB + root `pm install`, never `adb install`:

```bash
adb push "<LOCAL_APK>" /data/local/tmp/__codex_install.apk
adb shell su -c "pm install -r -g --user 0 /data/local/tmp/__codex_install.apk"
adb shell su -c "rm -f /data/local/tmp/__codex_install.apk"
```

Then `adb shell su -c "killall com.android.systemui"`. User must test on a **new** island post after SystemUI restart.

- Do not commit unless asked.
- Log with tag `HyperBridge` so `adb logcat -s HyperBridge` shows: plugin class hooked, click intercepted, embed host class name, timeout method blocked.

## Success criteria

- Reply tap: field appears **where the Reply/View buttons were**, inside the island.
- No bar at the bottom of the screen, no HyperBridge activity on screen.
- Island stays expanded until send or tap-out, past the normal auto-hide time.
- Send delivers RemoteInput to WhatsApp/etc.; “You:” does not rewrite the island.

Start by proving on-device which plugin method actually fires on Reply and which method actually collapses the island. Then hook those. The current `IslandInlineReplyHook` guess-list is not hitting the real paths.
