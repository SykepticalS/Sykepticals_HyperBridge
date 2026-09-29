# Permanent Island and App-Exit Animation Research

Date: 2026-09-29

## Scope

This document records the research performed before implementing HyperBridge's permanent island. The first implementation scope is deliberately narrow:

- keep a blank, non-floating island available at the camera cutout;
- when an app with a live focus island closes to Home, force Xiaomi's system-island close target;
- update the permanent center island from the app's existing focus data at the animation endpoint;
- restore the blank anchor when the source app becomes foreground or the shown island is expanded;
- leave ordinary received-notification ingestion for a later phase.

## Device and binaries examined

Research was performed against the connected Xiaomi device, not against guessed class names.

| Component | Installed build |
| --- | --- |
| HyperOS | `OS3.0.305.0.WPATRXM` |
| MiuiSystemUI | `16.03.251211.r` (`202501210`) |
| MIUISystemUIPlugin | `17.1.4.71.0` (`171047100`) |
| MiuiHome | `RELEASE-6.01.06.2462-06151546` (`601062462`) |

The installed APKs were pulled from the device and decompiled with JADX 1.5.6. The important paths below refer to those installed binaries.

## What the three animations really are

MiuiHome does not contain three separate island-closing animation engines. It has one `CLOSE_TO_ELEMENT` path. SystemUI supplies a different remote-animation element rectangle, and the launcher springs the closing task into that rectangle.

In `com.miui.home.recents.anim.WindowAnimParamsProvider#getClosingWindowAnimParams`, MiuiHome:

1. obtains the remote animation `elementTarget`;
2. uses `elementTarget.sourceContainerBounds` as the target rectangle;
3. uses `elementTarget.cornerRadius` as the terminal radius;
4. selects `CLOSE_TO_ELEMENT`;
5. calls the SystemUI callbacks for animation start and end.

The gesture path in `NavStubView` reaches the same element-target behavior.

Therefore the visible variants are target-selection variants:

| User-visible case | Xiaomi-selected target |
| --- | --- |
| No competing island | the app island's big/pill rectangle |
| Lower-priority secondary island | the side small-island circle |
| A show-once system island is active | the physical cutout circle |

## Where Xiaomi selects the target

The selection is in MIUISystemUIPlugin's
`miui.systemui.dynamicisland.window.DynamicIslandWindowViewController`.

`sendWindowAnimEvent("request_close_position", ...)` locates the island belonging to the closing package and calls `getAppCloseRealIslandRect(...)`. That method normally returns the big or small island rectangle. On a phone, when the window is temporarily hidden or a show-once island is active, it returns `getCutoutRect()` instead.

The relevant event sequence is:

```text
request_close_position(packageName) -> Bundle[position = Rect]
close_app_start(packageName)         -> Xiaomi starts normal-to-island state
close_app_end(packageName)           -> launcher spring reached its endpoint
app_to_recent(packageName)           -> interruption/recents cleanup path
```

The framework-side glue is `DynamicIslandController$onAppToRecent$2` and `MiuiOverviewProxy`. The plugin receives the event strings above; MiuiHome only consumes the remote element target that SystemUI produces.

## Live reproduction

Spotify was used with an active MediaStyle session.

### Ordinary close

The trace showed:

- source state `AppExpanded`;
- selected next state `BigIsland`;
- returned target `Rect(370, 30 - 830, 132)` (460 x 102);
- MiuiHome entered its close-to-element path.

### Close while Xiaomi's charging island was active

Charging state was temporarily simulated with `cmd battery`, then reset with `cmd battery reset` after the trace.

The trace showed:

- source state `AppExpanded`;
- selected next state `BigIsland`;
- `isShowOnceIsland:true`;
- returned target `Rect(566, 47 - 634, 115)` (68 x 68);
- the rectangle exactly matched the camera-cutout target;
- the charging entry was a short-lived show-once island while Spotify remained the live focus island.

This directly validates that the requested animation is produced by substituting the cutout rectangle at `request_close_position`. Reimplementing the spring or altering MiuiHome is unnecessary.

## HyperBridge architecture relevant to the feature

HyperBridge already has the pieces needed to avoid copying Xiaomi's internal view hierarchy:

- `NotificationProcessingEngine` owns the semantic source-to-island lifecycle.
- Owned proxy notifications carry `hyperbridge.source_pkg`, `hyperbridge.source_key`, owner, and generation metadata.
- `SystemUiDispatcher` posts those notifications as SystemUI and guards stale cancellation by generation.
- `DynamicClassLoaderHooks` observes MIUISystemUIPlugin's runtime class loader.
- existing hooks already resolve the `miui.sbn` stored in an island content view.

The real app focus island remains the source of content and actions. HyperBridge copies its data model onto the stable anchor key; it does not graft or replace Xiaomi views.

## Corrected model: fixed ShowOnce anchor with in-place data updates

The permanent island is an owned, persistent blank anchor notification. It has no float-on-post behavior and is marked so HyperBridge can distinguish it from real focus islands.

When an eligible app exits:

1. The plugin hook lets Xiaomi calculate its normal result.
2. If permanent island is enabled, the package has a live non-anchor focus entry, and the returned cutout rectangle is valid, HyperBridge replaces only `Bundle["position"]` with a defensive copy of Xiaomi's own cutout rectangle.
3. `close_app_start` records a generation-bound closing transaction.
4. The first `close_app_start` clones the source's current `DynamicIslandData` onto the existing anchor key through Xiaomi's normal update method. `close_app_end` confirms the transaction and is only a fallback if that early update failed.

The anchor notification is never canceled or replaced. Its property is `0`, Xiaomi's ShowOnce/system-island lane, with an infinite timeout. This makes Xiaomi keep it over the cutout and naturally send the closing app circle behind it. Only the anchor's plugin data changes; later updates from the source key are mirrored onto the anchor key.

When the source package becomes foreground, the source ends, or the presented anchor is expanded, the cached blank data is written back to the same anchor key. The real source notification remains untouched throughout.

## Eligibility and invariants

The override is allowed only when all of these are true:

- the feature preference is enabled;
- Xiaomi produced a successful `request_close_position` result;
- `packageName` is nonblank and is not HyperBridge/SystemUI/MiuiHome;
- Xiaomi's `requestHasIsland(packageName)` returns a non-anchor content view with current data;
- the reserved anchor content view and blank snapshot are available;
- the target returned from `getCutoutRect()` is non-empty and plausible;
- the event belongs to the current close generation.

The system fails open: if any class, method, event argument, notification metadata, or rectangle cannot be resolved, Xiaomi's original result and behavior are preserved.

## Lifecycle cases

| Event | Required behavior |
| --- | --- |
| Feature enabled / SystemUI starts | post or recover the blank anchor |
| Eligible `request_close_position` | return Xiaomi's cutout rect |
| first `close_app_start` | update the existing anchor key with source data |
| matching `close_app_end` | confirm the transaction; fallback-update only if needed |
| `app_to_recent`, mismatch, interruption | abort transaction and keep/restore anchor |
| adopted source app becomes top activity | restore blank anchor |
| adopted island expands | restore blank anchor and clear adoption |
| adopted source notification ends | restore blank anchor |
| preference disabled | restore native behavior and remove anchor |
| SystemUI/plugin restart | recover from owned notifications; start blank |

## Safety constraints

- Never call `updateBigIslandViewWidth()` (or another Xiaomi relayout method) from expansion/collapse or state callbacks. A previous experiment confirmed recursive `onStateChanged -> expandedToBigIslandAnimation` re-entry and a SystemUI ANR.
- Do not use a timer to guess when the app reaches the cutout. `close_app_end` is the authoritative animation endpoint.
- Do not poll notification state, top activity, or view state. Use notification callbacks and Xiaomi's existing event callbacks.
- Keep only weak references to plugin objects and bounded transaction state.
- Never mutate the `Rect` returned by Xiaomi in place; put a copied rectangle in the returned bundle.
- Never cancel the source notification when clearing the permanent presentation.
- Never cancel or replace the permanent anchor while presenting source content.

## Implementation validation update

The first deployed prototype canceled the anchor at `close_app_end`. Live use showed exactly why that model was incorrect: Xiaomi promoted the source island and moved the permanent island to the side. That prototype was discarded.

The corrected build posts the anchor with `islandProperty:0`. Device logs confirm Xiaomi classifies the unchanged reserved key as `ShowOnceBigIsland`, reports `isShowOnceIsland:true`, and keeps notification ID `1212305481` active. Endpoint content is now applied through `updateDynamicIslandView` under that same key; cancellation is not part of the presentation path.

The successful live trace measured 929 ms between the first `close_app_start` and `close_app_end`. Visual testing found the end callback about one second too late, so content application was moved to the first start callback, with duplicate starts suppressed by the reducer.

## Compatibility boundaries

The installed plugin exposes the exact semantic event method and `getCutoutRect()`, but Xiaomi may rename or reshape them in later plugin builds. Installation must therefore discover methods by name plus signature, log a single capability result per class loader, and leave native behavior untouched when discovery fails.

This first phase targets the installed OS3 plugin. Additional version adapters should be evidence-driven from future installed APKs rather than speculative aliases.

## Deferred work

- Feeding ordinary received notifications into the permanent slot.
- Arbitration among several simultaneous eligible real islands beyond Xiaomi's existing priority.
- User-selectable permanent-island content or visual themes.
- Cross-device geometry adapters where the camera cutout is not centered.
