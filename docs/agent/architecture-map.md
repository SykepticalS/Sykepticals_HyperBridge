# HyperPop architecture map

**Baseline commit:** `64f0d35eb4986efcdb5f3a654d302bec4e4639d4` — Stable 9.
Verify changed symbols against the current working tree; this map is a compact
router, not authority over an in-progress refactor.

HyperPop is a libxposed module that hooks Xiaomi SystemUI, XMSF, and (optionally) Screen Recorder. It intercepts source notifications early in SystemUI, runs semantics in the HyperPop app process, and posts owned Focus/island notifications back into SystemUI. Xiaomi remains the native island lifecycle owner; HyperPop alters the smallest necessary transitions.

## End-to-end flow

```
SystemUI (ingress hook) ──AIDL──► NotificationProcessingService (app)
        │                              │
        │ IIslandDispatcher            ▼ NotificationProcessingEngine
        ◄────────────────────────  translators + policies
        │
        ▼ SystemUiDispatcher.post/cancel (broadcast fallback: IslandProtocol.ACTION_*)
```

Remote prefs `IslandProtocol.REMOTE_PREFS` (`HyperPopHookConfig`) mirror app settings into hook processes via `HookConfig` / `HookConfigSync`.

---

## libxposed entry and classloader handling

| Path | Symbols |
|------|---------|
| `app/src/main/resources/META-INF/xposed/java_init.list` | Entry class name |
| `app/src/main/resources/META-INF/xposed/scope.list` | Scoped packages |
| `app/src/main/resources/META-INF/xposed/module.prop` | Module metadata |
| `app/src/main/java/com/sykeptical/hyperpop/xposed/HyperPopModule.kt` | `HyperPopModule` : `XposedModule`, `onPackageLoaded` |
| `app/src/main/java/com/sykeptical/hyperpop/xposed/HookConfig.kt` | `HookConfig.initialize`, remote prefs readers |
| `app/src/main/java/com/sykeptical/hyperpop/xposed/XposedLog.kt` | `log()` extension for modules |

**Scoped packages:** `com.android.systemui`, `com.xiaomi.xmsf`, `com.miui.screenrecorder` (see `scope.list`; routing uses `IslandProtocol.SYSTEM_UI_PACKAGE`, `XMSF_PACKAGE`, `SCREEN_RECORDER_PACKAGE`).

**`HyperPopModule.onPackageLoaded`:** calls `HookConfig.initialize(this)`, then installs package-specific hooks (see SystemUI/XMSF sections).

**Classloaders:** `app/src/main/java/com/sykeptical/hyperpop/xposed/hooks/DynamicClassLoaderHooks.kt` — `observe()`, `hookPluginFactory()` on `PluginInstance$PluginFactory.createPluginContext`, DEX `ClassLoader` constructors; dispatches discovered loaders to callbacks (used by `MediaCardHook` for plugin loaders).

**Reflection helper:** `app/src/main/java/com/sykeptical/hyperpop/xposed/hooks/IslandHookReflection.kt` — `IslandHookReflection` (shared method lookup for hooks).

**App ↔ module service:** `app/src/main/java/com/sykeptical/hyperpop/HyperPopApplication.kt` — `XposedServiceHelper.OnServiceListener`, syncs `HookConfigSync` / `AppPreferences` flows into remote prefs.

**Runtime health:** `app/src/main/java/com/sykeptical/hyperpop/xposed/runtime/EnvironmentRuntime.kt`, `EnvironmentHealth.kt`, `ModuleServiceState.kt`, `HealthLease.kt`, `ApplyRequirement.kt` (`forSetting`, `RestartTarget`).

---

## SystemUI hooks

Installed from `HyperPopModule` when `packageName == IslandProtocol.SYSTEM_UI_PACKAGE`:

| File | `install()` |
|------|-------------|
| `xposed/hooks/SystemUiBootstrapHook.kt` | `Application.attach` / `onCreate` → `SystemUiDispatcher.register` |
| `xposed/hooks/SystemUiNotificationIngressHook.kt` | Notification intake, service bind, `IIslandDispatcher` stub |
| `xposed/hooks/IslandClickCleanupHook.kt` | Island click cleanup |
| `xposed/hooks/IslandInlineReplyHook.kt` | Inline reply |
| `xposed/hooks/IslandWindowImeHook.kt` | IME / island window |
| `xposed/hooks/FocusWhitelistHook.kt` | Focus whitelist |
| `xposed/hooks/FocusShadeBackgroundHook.kt` | Focus shade background (gated by `regular_shade_background`) |
| `xposed/hooks/MediaShadeBackgroundHook.kt` | Media card shade background, same toggle |
| `xposed/hooks/HeadsUpSuppressionHook.kt` | Heads-up suppression (uses `IncomingCallBannerPolicy`) |
| `xposed/hooks/CallIslandPresenceHook.kt` | Call island presence |
| `xposed/hooks/OuterGlowHook.kt` | Outer glow |
| `xposed/hooks/ActiveIslandDismissHook.kt` | Active island dismiss |
| `xposed/hooks/ExpandedProgressAnimationHook.kt` | Expanded progress animation |
| `xposed/hooks/MarqueeHook.kt` | Marquee |
| `xposed/hooks/IslandTextUpdateAnimationHook.kt` | Text update animation |
| `xposed/hooks/BetterAnimationsHook.kt` | Better animations |
| `xposed/hooks/ExpandedTakeoverHook.kt` | Portrait expanded island over the status bar. Pill mode seats clippable text in the camera band via `CutoutSafeLayout`; `FocusIslandLayoutApplier` retunes both the real expanded copy and Xiaomi's drag copy (`DynamicIslandData.fakeView`) and leaves titles untruncated. `ExpandedFakeMirrorPolicy` skips that second write when the drag copy is already in sync. Media backgrounds extend to the handle-aware card bottom via `ExpandedMediaSurfaceApplicator`, and the drag copy reuses that resolved extension. Hosts inside Xiaomi's expanded view are pinned to the card from their params (a media refresh resets them while their laid-out bounds are stale). `ExpandedLayoutProbe` logs only when `debug.hyperpop.layoutprobe=1`. A takeover armed before Xiaomi's Focus template is measurable is provisional (`ExpandedDecisionRefreshPolicy`): it re-resolves in place, with no re-arm, when `updateExpandedView` installs the template, on the next size update, and before an expanded target is built after a Focus bind. A yielded circle promoted by an app open keeps Xiaomi's small-to-big animation as its whole compact state; HyperPop does not seed the small-island Folme state |
| `service/animation/expanded/ExpandedReacquirePolicy.kt`, `ExpandedMeasurementBaseline.kt` | `ExpandedTakeoverHook` ends a takeover on Xiaomi's private `reset()` (a position recompute reached from `calculateBigIslandY`, not a collapse) so Xiaomi measures native content; an island still `Expanded` is remembered and re-armed after `calculateBigIslandY` or on its next frame. The body offset a session wrote is carried per island so the next arm does not adopt Xiaomi's margin-inflated `expandedViewHeight`. |
| `xposed/hooks/SecondaryQuarantineHook.kt` | Hides other compact islands while one is expanded. A replaced expanded owner plays Xiaomi's expanded expiry animation (`expandedToDeletedAnimation`) in that same flush and stays in its slot. A primary collapse may reveal the sibling with the status-bar fade. A secondary collapse keeps the main island hidden until the expand-over-status-bar card is compact, so its ear icons stay behind the shrink |
| `xposed/hooks/StatusBarTakeoverHook.kt` | Status-bar fade for that takeover |
| `xposed/hooks/fingerprint/FingerprintSignalBridge.kt` | Read-only lockscreen fingerprint island. Observes keyguard auth and the FOD touch stream, and draws a HyperPop view in `DynamicIslandWindowView`. Native island `transitionAlpha` is cleared only while the scan overlay is showing, not for the idle lock pill or Xiaomi height writes. The real unlock is unchanged. |
| `xposed/mediacard/MediaCardHook.kt` | Media card orchestration + `DynamicClassLoaderHooks` |

**Bootstrap:** `SystemUiBootstrapHook.register` → `SystemUiDispatcher.register(context, module)` and connects ingress (`SystemUiNotificationIngressHook.connect` from bootstrap tail).

**Dispatch surface:** `app/src/main/java/com/sykeptical/hyperpop/xposed/dispatch/SystemUiDispatcher.kt` — `register`, `post`, `postOwned`, `cancel`, `cancelAll`; handles `IslandProtocol.ACTION_POST`, `ACTION_CANCEL`, `ACTION_RELOAD_ENGINE`, etc.

**In-process backend (when engine runs in SystemUI):** `app/src/main/java/com/sykeptical/hyperpop/island/backend/InjectedSystemUiIslandBackend.kt` — `IslandBackend` implementation using `SystemUiDispatcher` directly.

**Visual / live:** `app/src/main/java/com/sykeptical/hyperpop/xposed/hooks/IslandLiveVisual.kt` (island live visual hooks).

---

## XMSF hooks

| File | Behavior |
|------|----------|
| `xposed/hooks/XmsfFocusAuthHook.kt` | Focus auth bypass; fail-open if classes missing |
| `xposed/hooks/XmsfHandshakeHook.kt` | Installed only if focus auth hook succeeds |

`IslandProtocol.ACTION_PING_XMSF` supports handshake (see `IslandProtocol`).

---

## Screen Recorder hook

`app/src/main/java/com/sykeptical/hyperpop/xposed/hooks/screenrecorder/ScreenRecorderHook.kt` — gated by `HookConfig.screenRecorderEnabled()` and `HookConfig.replaceScreenRecorder()` in `HyperPopModule`.

---

## Notification ingestion and parsing

**Ingress (SystemUI):** `SystemUiNotificationIngressHook` hooks Xiaomi listener/util entry points, binds `NotificationProcessingService`, implements `IIslandDispatcher.Stub` for return-path posts, calls `processPosted` / `processRemoved` / `reconcile` on the AIDL stub.

**AIDL:**

- `app/src/main/aidl/com/sykeptical/hyperpop/processing/INotificationProcessingService.aidl`
- `app/src/main/aidl/com/sykeptical/hyperpop/processing/IIslandDispatcher.aidl`

**Service:** `app/src/main/java/com/sykeptical/hyperpop/service/NotificationProcessingService.kt` — owns `NotificationProcessingEngine`, enforces SystemUI caller, tracks `activeSources`, bridges decoration extras back to SystemUI.

**Engine intake:** `NotificationProcessingEngine.onNotificationPosted`, `onNotificationRemoved`, `onIngressConnected` (`app/src/main/java/com/sykeptical/hyperpop/service/NotificationProcessingEngine.kt`).

**Type detection / policy** (`NotificationTypePolicyResolver.kt` — filename; types live in same file):

- `RawNotificationTypeClassifier.classify`
- `EffectiveNotificationTypePolicy`, `EffectiveNotificationTypeResolver`
- `SemanticNotificationResolver.resolve`
- `NotificationTypeEnablementPolicy` (same package)

**Content parsing:** `NotificationContentResolver`, `NotificationRemoteViewsParser`, `RenderedJsonNormalizer` (under `app/src/main/java/com/sykeptical/hyperpop/service/`).

**Channels:** `BridgeNotificationChannels.kt`, `NotificationTypePolicyResolver` consumers in engine.

**Candidate / identity:** `NotificationCandidatePolicy`, `NotificationIdentityResolver` (service package).

---

## Island post, update, cancel

**Wire contract:** `app/src/main/java/com/sykeptical/hyperpop/island/backend/IslandProtocol.kt` — `VERSION`, actions, extras (`EXTRA_GENERATION`, `EXTRA_SOURCE_KEY`, `EXTRA_SOURCE_FOCUS`, glow/marquee keys, `CAP_*`, `HEARTBEAT_LEASE_MS`).

**App-process backend:** `SystemUiIslandBackend.kt` — `get()`, `attachDispatcher`, `post`/`update`/`cancel`; prefers bound `IIslandDispatcher.post` under cleared identity; falls back to `ACTION_POST` broadcast.

**Interface:** `IslandBackend.kt`, `IslandOwnership.kt`, `IslandMetadata` (backend package).

**Engine posting:** `NotificationProcessingEngine.postIsland` (private); active state in engine + `ExpiredIslandRegistry`.

**Update decisions:** `IslandUpdateResolver.kt` — `IslandUpdateDecision`, `IslandUpdateResolver.decide` (NEW / UPDATE / UNCHANGED, `cancelBeforeNotify`).

**Removal / replay:**

- `NotificationLifecyclePolicy` (in `IslandUpdateResolver.kt`)
- `ExpiredIslandRegistry.kt`
- `SourceReplacementReplayPolicy.kt`, `ShadeReplayPolicy.kt`
- `SourceHeadsUpReplacementPolicy`, `SourceFocusShadePolicy` (service / xposed policy types)

**Models:** `app/src/main/java/com/sykeptical/hyperpop/models/ActiveIslands.kt` — `ActiveIsland`; presentation tests in `IslandPresentationTest`.

---

## Translators (notification → island payload)

Base: `service/translators/BaseTranslator.kt`. Per-type:

`CallTranslator`, `MediaTranslator`, `MessageTranslator`, `VoiceMessageTranslator`, `DownloadTranslator`, `ProgressTranslator`, `TimerTranslator`, `NavTranslator`, `VpnTranslator`, `LiveUpdateTranslator`, `ScreenRecordingTranslator`, `ScreenRecordingSavedTranslator`, `StandardTranslator`.

Layout/helpers: `IslandCompactLayout`, `ExpandedFocusContent`, `IslandFloatingPresentationPolicy`, `FocusActionIntentTypes`.

---

## Call state

| File | Symbols |
|------|---------|
| `service/call/CallNotificationClassifier.kt` | Classification signals |
| `service/call/CallSessionTracker.kt` | `CallSessionTracker`, `CallSession`, `CallState`, `CallSessionInput`, `CallReplacementPolicy`, `CallIslandTimeoutPolicy` |
| `service/call/CallStageVisibilityPolicy.kt` | Stage visibility |
| `service/call/CallActionIconSizingPolicy.kt`, `CallActionSelectionPolicy.kt` | UI actions |
| `service/translators/CallTranslator.kt` | Focus payload build |
| `xposed/IncomingCallBannerPolicy.kt` | Banner / heads-up interaction |
| `xposed/hooks/CallIslandPresenceHook.kt` | SystemUI presence |
| `xposed/HookConfig.kt` | `expectsIncomingCallBanner`, call keyword lists |

Live calls use `CallIslandTimeoutPolicy.PERSISTENT_TIMEOUT_MILLIS` (`Int.MAX_VALUE`).

---

## Media state

**App-process:** `MediaTranslator.translate` — album art, palette, `HyperIslandData`-style payload.

**SystemUI hooks** (`xposed/mediacard/`):

- `MediaCardHook.kt` — install matrix, `MediaCardRuntimeConfig.load` listener
- `MediaCardRuntimeConfig.kt`, `MediaCardConstants.kt`
- Compact: `island/compact/CompactMediaIslandPolicy.kt`, `CompactMediaIslandHooker` (under mediacard tree)
- Expanded island: `IslandExpandedMediaAmbientFlowHooker`, layout packages (`miui`, `pixel`, `coloros`, `oneui`, `ios`)
- Notification shade: `notification/NotificationMediaAmbientFlowHooker`, `NotificationMediaBackgroundController`, progress `MediaProgressStyleHooker`
- Switcher: `notification/switcher/NotificationMediaPlaybackObserver.kt`, `NotificationMediaPlaybackPolicy`, `NotificationMediaSelectionCoordinator`, `CompactMediaSessions`

**Settings UI:** `ui/screens/settings/MediaCardSettingsScreen.kt`, `MediaCardPreview.kt`; prefs flow through `AppPreferences` and mediacard runtime config (separate from `HookConfigSync` keys for most visual options).

---

## Lifecycle and state policy (selected)

| Class | Path |
|-------|------|
| `IslandUpdateResolver`, `NotificationLifecyclePolicy` | `service/IslandUpdateResolver.kt` |
| `ExpiredIslandRegistry` | `service/ExpiredIslandRegistry.kt` |
| `NotificationCandidatePolicy` | `service/NotificationCandidatePolicy.kt` |
| `SourceReplacementReplayPolicy`, `ShadeReplayPolicy` | `service/` |
| `SourceHeadsUpReplacementPolicy` | `xposed/` (policy + test) |
| `VoicePlaybackUpdateGate`, `VoicePlaybackDetector` | `service/voice/` |
| `PopupSuppressionPolicy` | `service/popup/` |
| `VpnIslandController` | `service/vpn/VpnIslandController.kt` |
| `DownloadSessionTracker` | `service/download/` |
| `ScreenRecordingSessionTracker` | `service/recording/` |
| `MessagePresentationFamilyTracker` | `service/message/` |
| `ReplyComposerHoldRegistry` | `service/` |
| `BetterAnimationsPolicy` | `service/animation/` |
| `ExpandedIslandLayoutPolicy`, `CutoutSafeLayout`, `ExpandedPillPolicy`, `ExpandedSurfaceStyle`, `ExpandedTakeoverCoordinator` (including one pending expansion), `TakeoverFadePolicy`, `SecondaryUiQuarantine` | `service/animation/expanded/` |

---

## Settings that affect hooks

**Source of truth:** `app/src/main/java/com/sykeptical/hyperpop/data/AppPreferences.kt` — allowed packages, per-app overrides, global notification types, island/media/glow/marquee/screen-recorder/better-animations settings. Expand-over-status-bar is stored with the hook mirror, not Room.

**Mirror into hook process:** `HookConfigSync.kt` — `initialize`, `updatePolicy`, `updateCallStagePolicy`, `setScreenRecorder*`, `updateBackendHealth`; keys `KEY_ENGINE_ENABLED`, `KEY_TYPE_POLICY`, `KEY_CALL_STAGE_POLICY`, `KEY_FOCUS_ENABLED`, `KEY_ALLOWED_PACKAGES`, glow/marquee/screen-recorder/`KEY_BETTER_ANIMATIONS_ENABLED`/`KEY_EXPAND_OVER_STATUS_BAR_ENABLED`, etc.

**Hook-side read:** `HookConfig` — `focusEnabled`, `expectsReplacement`, `classify`, type policy JSON, screen recorder gates, `betterAnimationsEnabled`, `expandOverStatusBarEnabled`.

**Apply semantics:** `ApplyRequirement.forSetting` — hot reload vs `RestartTarget.SYSTEM_UI` / XMSF.

**UI entry points:** Home (`OverviewPage`, `LibraryPage`, `SettingsRootScreen`), onboarding (`OnboardingScreen`), hubs in `SettingsHubs.kt`, and section screens under `ui/screens/settings/`. Tweaks (better animations, expanded island, regular shade background) is a root settings row, `SettingsPlace.TWEAKS`. Design tokens live in `ui/system/`.

---

## Unit tests (by area)

| Area | `app/src/test/java/...` |
|------|-------------------------|
| Runtime / handshake | `xposed/runtime/PrivilegedRuntimePolicyTest.kt` |
| Island update / lifecycle | `service/IslandUpdateResolverTest.kt`, `ExpiredIslandRegistryTest.kt`, `SourceReplacementReplayPolicyTest.kt`, `ShadeReplayPolicyTest.kt` |
| Type policy | `service/NotificationTypePolicyResolverTest.kt` |
| Ingress / candidates | `service/NotificationCandidatePolicyTest.kt`, `NotificationReconciliationTest.kt`, `FocusShadeUpdateTest.kt` |
| Call | `service/call/CallSessionTrackerTest.kt`, `CallNotificationClassifierTest.kt`, `CallActionIconSizingPolicyTest.kt`, `CallStageVisibilityPolicyTest.kt`, `xposed/IncomingCallBannerPolicyTest.kt` |
| Media | `xposed/mediacard/island/compact/CompactMediaIslandPolicyTest.kt` |
| Content / parsing | `service/NotificationContentResolverTest.kt`, `RenderedJsonNormalizerTest.kt` |
| Translators / layout | `service/translators/IslandCompactLayoutTest.kt`, `ExpandedFocusContentTest.kt`, `IslandFloatingPresentationPolicyTest.kt`, `ScreenRecordingPayloadFactoryTest.kt` |
| Voice | `service/voice/VoicePlaybackUpdateGateTest.kt`, `VoicePlaybackDetectorTest.kt` |
| Protocol / ownership | `integration/xiaomi/HyperIslandProtocolExtensionsTest.kt`, `island/backend/IslandOwnershipTest.kt`, `models/IslandPresentationTest.kt` |
| Heads-up / focus shade | `xposed/SourceHeadsUpReplacementPolicyTest.kt`, `xposed/FocusShadeBackgroundPolicyTest.kt` |
| Channels / prefs | `service/BridgeNotificationChannelsTest.kt`, `data/AppPreferencesCacheTest.kt` (if present) |
| Animation | `service/animation/BetterAnimationsPolicyTest.kt`, `service/animation/expanded/ExpandedIslandLayoutPolicyTest.kt`, `ExpandedPillPolicyTest.kt`, `ExpandedSurfaceStyleTest.kt`, `ExpandedTakeoverCoordinatorTest.kt`, `TakeoverFadePolicyTest.kt`, `SecondaryUiQuarantineTest.kt` |

---

## Invariants (for agents)

1. Source notifications must enter through SystemUI ingress before Xiaomi snapshots extras (`NotificationProcessingService` KDoc).
2. `IslandProtocol.VERSION` and capability bits must match on IPC; stale handshakes gated by `HealthLease` / heartbeat prefs.
3. Hooks fail open when prefs, classes, or methods are unavailable.
4. `IIslandDispatcher` posts run as SystemUI (`Binder.clearCallingIdentity` in backend/dispatcher paths).
5. `CallSessionTracker` treats source keys as aliases; logical call identity is stable across replacements.
6. Media card hooks install per discovered `ClassLoader` (main + SystemUI plugins).

For undocumented Xiaomi private APIs, use
`.cursor/skills/xiaomi-systemui/references/verified-behavior.md` plus current
device/JADX evidence; do not infer from this map alone.
