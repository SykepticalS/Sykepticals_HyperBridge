# Automatic popup control: clean-room research record

This document records behavioral observations only. No Jawomo source, resources, UI, or other proprietary material is included in HyperBridge.

## Local reference APKs

The APKs were inspected in place under `reference-apps/popupControl/` and remain excluded from version control.

| File | SHA-256 |
| --- | --- |
| `base.apk` | `4F0BE5B4FCACB5E7B136769DDD9F55702AE7EB88E5A2CC3357CAE7098167C973` |
| `split_config.arm64_v8a.apk` | `306D116F3DA5D2C2C19F06653769D5FDBB8914F390529F95F9A81C39124DD84F` |
| `split_config.xxhdpi.apk` | `7B7B1E055E044531A613A9AD3A57975B31C6EF67284DD7A4E1CB6819C9758268` |

The base manifest declares package `com.jamworks.disablenotificationpopups`, listener `com.jamworks.disablenotificationpopups.NotificationObserverControl`, and required feature `android.software.companion_device_setup`. It does not request Bluetooth scan/connect or location permissions for association.

## Verified behavior

- Companion setup constructs `AssociationRequest.Builder().build()` directly. No device filter, device profile, or self-managed mode is configured.
- The notification listener calls `getNotificationChannels(packageName, user)` and `updateNotificationChannel(packageName, user, channel)` for other packages.
- The ordinary disable-popup mode demotes importance above `IMPORTANCE_DEFAULT` to `IMPORTANCE_DEFAULT`.
- A separate aggressive mode can use `IMPORTANCE_MIN`; another mode contains sound playback and vibration behavior. HyperBridge intentionally implements neither.
- Original importance is persisted locally. The reference also appends `originalImportance + 1` U+200F RIGHT-TO-LEFT MARK characters to a channel description as recovery metadata.
- Its channel callback reacts to added and deleted channels. HyperBridge independently handles added, updated, and deleted channels with type-aware ownership rules.

HyperBridge uses a distinct, namespaced invisible marker format and its own Room ownership records. It does not copy the reference application's storage format, code, UI, strings, or assets.

## Notification Assistant conclusion

`NotificationAssistantService` can return a pre-alert importance `Adjustment`, but AOSP marks the class `@SystemApi`; it is absent from the Android 36 public SDK used by this project. There is therefore no supported normal third-party implementation route in HyperBridge. No hidden-API reflection, assistant-role manipulation, or SystemUI hook is used.

- AOSP: <https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/service/notification/NotificationAssistantService.java>
- Privileged listener channel API contract: <https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/service/notification/NotificationListenerService.java>

The remaining HyperOS-specific question—whether a Xiaomi 17 Ultra exposes any supported third-party assistant assignment UI—must be recorded during device acceptance. Even if present, it is not enabled by this implementation without a separate design decision.
