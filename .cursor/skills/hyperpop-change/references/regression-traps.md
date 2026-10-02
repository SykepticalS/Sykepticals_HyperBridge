# HyperPop regression traps

HyperPop is the current application. Older Git commits used the HyperBridge name;
the commit hashes below still refer to that history.

Load this reference for behavioral/lifecycle work, not trivial edits. Current
code and tests outrank this summary. Xiaomi-private conclusions are valid only
for a matching device/APK identity.

## Source identity, replacement, and cancellation

- A source notification key is an alias, not always the logical session. Calls,
  conversations, downloads, and other long-lived work can replace their source
  notification while remaining one logical island.
- Preserve `source key -> logical identity -> owned island` mappings across a
  proven replacement. Do not merge ambiguous simultaneous sources merely
  because package/type match.
- Suppression markers may be restored only for an identical replay boundary.
  `SourceReplacementReplayPolicy` requires both `visibleHash` and
  `callLifecycleHash`; changed content or call lifecycle must be reprocessed.
- Cancels are generation-scoped. `IslandOwnership.acceptsCancel` rejects an
  older generation so a late removal cannot kill a newer replacement.
- Never cancel a source notification simply to remove a HyperPop proxy when
  the source itself carries a Focus/shade replacement.
- Interactive state is source/window scoped too. Reply composer, IME ownership,
  marquee hold, and timeout pause must target the exact tapped island; skip an
  ambiguous target instead of injecting across islands, and resume with the
  unused timeout rather than restarting it.

Check: `SourceReplacementReplayPolicyTest`, `ShadeReplayPolicyTest`,
`IslandOwnershipTest`, notification reconciliation tests.

## Xiaomi priority and app-exit handoff

- PRIMARY and SECONDARY are Xiaomi-owned presentation slots, not insertion
  order. Classify the native target geometry before overriding anything.
- Better Animations may retarget the first island or a higher-priority PRIMARY
  handoff to the cutout. A SECONDARY/lower-priority target stays fully native.
- The accepted multi-island policy supports two or more existing islands when
  the current big island exists and Xiaomi targets PRIMARY. Do not regress this
  to `activeCount == 0` or `== 1`.
- If any required getter, geometry, state, or current-big ownership check is
  missing, invalid, or throws, return Xiaomi's original result.

Check: `BetterAnimationsPolicyTest`; history `8c9c3d8`, `12cf5d8`.

## Late callbacks and animation generations

- App-exit events are a transaction:
  `request_close_position -> close_app_start -> close_app_end`, with
  `app_to_recent`, interruption, or a newer request able to invalidate it.
- Arm only for the matching package/request generation. Before a delayed reveal,
  confirm the captured content is still the current `BigIsland`; weak references
  alone do not prove ownership.
- Clear pending state on interruption, abort, disable, terminal completion, and
  replacement. A stale delayed callback must become a no-op.
- Never call `updateBigIslandViewWidth()` from collapse/animation callbacks.
  Historical MIUIX re-entry caused `DynamicIslandWindow is not responding` ANRs.

## Permanent Island history (currently not in Stable 9)

- Reintroduction requires a fresh design review; it was previously removed
  after bootloop risk (`e0842ab`) and later experimental fixes are historical,
  not current architecture.
- A blank property-0/`ShowOnce` anchor and an informative adopted source are
  different states. Block blank-anchor interaction only; informative content
  must expand and act like a regular island.
- When any real island is visible, the permanent anchor must yield before the
  native add/exit arbitration. Restore fallback only after Xiaomi's real visible
  handlers are empty.
- When the primary disappears while secondaries remain, do not resurrect the
  anchor over them. Preserve Xiaomi's secondary lifecycle and return only after
  the last real island is gone.
- Capture pristine blank data before adopting source content. Never relearn the
  blank snapshot from an adopted update. Anchor-originated callbacks must be
  idempotent to prevent repost loops.
- Media adoption ends on playback stop/session destruction, not merely on a
  notification callback. Unregister old `MediaController` callbacks during
  source/session handoff.

Historical evidence: `3672938`, `3d49e9b`. Re-verify against the current ROM
and current source before porting any of it.

## Call state

- A first outgoing payload with a chronometer is not proof of `ACTIVE`; some
  dialers start it while still calling. On the same source, chronometer start,
  material base reset, or connected-control transition can prove answer.
- Across a source replacement, one changed signal is insufficient; require the
  established compound chronometer-plus-controls boundary. Keep `ACTIVE` sticky
  across compatible updates and preserve the original connected time.
- Recovery after listener/SystemUI restart is a distinct path: resident
  chronometer/control/age evidence may recover an already-active call.

Check: `CallSessionTrackerTest`; history `9d6f9b0`.

## Plugin classloaders and reflection

- Xiaomi Dynamic Island/focus UI classes live in the SystemUI plugin APK on the
  verified target, while controller/media-island classes also exist in base
  SystemUI. Never assume one loader owns both surfaces.
- Install hooks per discovered loader. `DynamicClassLoaderHooks.observe`
  replays known loaders and observes plugin contexts/DEX loaders; each consumer
  must deduplicate by loader identity.
- Class discovery is asynchronous. A missing class on the base loader is not
  proof that the feature is absent. Conversely, finding a class does not prove
  the hooked overload or live instance is correct.
- Optional private reflection must fail open: log narrowly, skip the enhancement,
  and preserve Xiaomi behavior. Never turn a missing private API into a
  SystemUI crash or broad notification cancellation.

## Device and deployment

- Treat build/test/install/hook logs and direct visual UX as separate evidence.
  A successful APK install does not prove animation, stutter, or repeated-use
  behavior.
- Use only an already-connected wireless ADB target. Do not connect/disconnect,
  restart `adbd`, alter ports/authentication, fall back to USB, or reboot for
  ordinary verification. Root is temporary via GhostLock.
- APK install is always push to `/data/local/tmp/__codex_install.apk`, root
  `pm install -r -g --user 0`, then cleanup even after failure. Never use plain
  `adb install` or an Android-MCP install action.
- Prefer the Xiaomi runtime capture helper and targeted JADX queries. Escalate to
  raw logcat/full-class reads only for a stated evidence gap.
