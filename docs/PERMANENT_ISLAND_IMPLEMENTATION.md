# Permanent Island Implementation Plan

This is the implementation checklist derived from `PERMANENT_ISLAND_RESEARCH.md`. It is the source of truth for the first permanent-island milestone.

## Deliverable

Add an opt-in permanent island that is blank and non-floating at rest. Keep its notification ID, plugin key, and center position permanent. For app-to-Home closes involving a live focus island, force Xiaomi's cutout-target variant and update that same anchor's `DynamicIslandData` from the source at the first `close_app_start`. Restore blank content when the source app reopens, the presented island expands, the source ends, SystemUI restarts, or the transaction aborts.

## Components

### 1. Pure lifecycle policy

Create a small, Android-free state reducer with:

- states: `Blank`, `Closing(package, generation)`, `Showing(package, sourceKey, generation)`;
- events for eligible target request, start, end, abort, top-package change, expansion, source removal, disable;
- effects: override target, update anchor content, restore blank content, none;
- monotonically increasing generations so late end/abort callbacks cannot affect a newer close.

Unit-test normal completion, interruption, mismatched package, reopen, expand, source removal, replacement close, and disable.

### 2. Permanent anchor notification

Add a controller in the notification-processing process that:

- observes the opt-in preference;
- builds one reserved-ID, reserved-token owned notification;
- encodes an empty property-0 ShowOnce/system island with infinite island timeout, `enableFloat=false`, `firstFloat=false`, no content intent, ongoing transport, and an explicit anchor marker;
- posts at initialization/recovery and removes it when disabled;
- never lets orphan cleanup reap the reserved ID.

The anchor must be identifiable in SystemUI solely from notification extras and constants shared in `IslandProtocol`.

### 3. Plugin app-exit hook

Install through `DynamicClassLoaderHooks` and hook the installed plugin's
`DynamicIslandWindowViewController#sendWindowAnimEvent(String, boolean, boolean, Bundle)`.

For `request_close_position`, after Xiaomi returns:

- read `packageName` from the request bundle;
- use Xiaomi's `requestHasIsland(packageName)` result to capture the live source `DynamicIslandData`, including native media islands that have no Focus extras;
- capture the permanent anchor's blank data by its reserved key;
- reflect `controller.getView().getCutoutRect()`;
- validate and copy the rect;
- put the copy into the returned bundle's `position` field;
- begin/refresh the reducer transaction.

For the first `close_app_start`, mark the matching transaction started and apply the in-place content update. For `close_app_end`, confirm through the reducer and use it only as a fallback if the early update failed. For `app_to_recent` or mismatch, abort and restore blank content.

Do not call a Xiaomi layout/width updater from any callback.

### 4. In-place anchor update runtime

Keep a defensive copy of the blank anchor data when observed. Effects execute inside the installed plugin:

- clone the source data at the first `close_app_start`, retaining the anchor key;
- rewrite the clone to property 0, infinite timeout, and non-dismissible behavior;
- call Xiaomi's normal `updateDynamicIslandView(data, false)` path for the anchor;
- mirror later source-data updates onto the same anchor key;
- source removal while presented: restore the cached blank data immediately;
- missing source or anchor data: preserve Xiaomi's original behavior and otherwise fail open.

Never cancel the anchor during presentation. No polling, alarms, wake locks, or periodic work.

### 5. Foreground and expansion reset

Hook the installed controller's `onTopActivityChange(ComponentName, ...)` after execution. When the new top package equals the adopted package, reduce a reopen event and restore blank.

On an anchor click while it presents source content, allow Xiaomi's normal click path and then restore the blank anchor data. Block clicks and long presses only while the anchor is blank.

Do not force a collapse or relayout. Xiaomi remains responsible for the expanded real island.

### 6. Preference and UI

Add one default-off toggle in Island settings. Sync it into remote hook preferences with the existing `HookConfigSync` path. Disabling must be reversible immediately: remove the anchor and stop overriding targets without requiring an app-data reset.

### 7. Diagnostics

Use concise `HyperBridge` log events for:

- hook capability installed/unavailable;
- target override with package and generation (no notification text);
- committed/aborted in-place update;
- blank restoration reason;
- fail-open reflection errors, rate-limited per class loader/error category.

## Verification gates

### Automated

- reducer unit tests pass;
- preference sync/default tests pass;
- anchor identity/eligibility tests pass;
- full unit test suite passes;
- debug APK builds with serialized Gradle if needed.

### Device

Install only through wireless ADB: push APK to `/data/local/tmp`, run root `pm install -r -g --user 0`, then delete the temporary APK.

After SystemUI reload, verify separately:

1. disabled mode preserves native targets;
2. enabled idle mode shows a blank anchor without float animation;
3. Spotify/media close returns the cutout-sized target, not the pill target;
4. in-place content update happens on the first `close_app_start`, about 929 ms before the measured end callback, with the anchor notification ID and plugin key unchanged;
5. reopening Spotify restores blank;
6. expanding the adopted island restores blank;
7. ending playback restores blank;
8. an interrupted/recents close does not strand the anchor hidden;
9. charging/show-once islands still behave natively;
10. SystemUI has no ANR, crash, or recursive transition trace.

Build/install/hook logs prove deployment and event routing only. Visual behavior must be reported as directly observed on the device or explicitly left pending.
