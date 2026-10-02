# HyperPop Agent Guide

HyperPop is a Kotlin Android project that integrates with Xiaomi HyperOS
SystemUI/XMSF through libxposed hooks and Xiaomi private SystemUI/plugin APIs.

## Core principles

Never guess Xiaomi private API behavior, lifecycle ordering, event meaning,
geometry, classloader behavior, or state transitions when direct evidence can
reasonably be obtained.

Evidence priority:
1. Current repository implementation and tests
2. Current connected-device runtime evidence
3. Current Xiaomi APK/JADX evidence
4. Compatible stored verified knowledge as a navigation aid
5. Inference only when none of the above can answer the question

Current device/APK evidence overrides old assumptions.

## Architecture behavior

Preserve Xiaomi's native island lifecycle and state ownership whenever possible.
HyperPop should intercept or alter only the smallest necessary transition.

Reflection/libxposed hooks must fail open if an expected Xiaomi class, method,
field, state, or classloader surface is unavailable.

Prefer pure policy/state logic outside reflective hooks when practical.
Add regression tests for lifecycle/state behavior.

## Context discipline

Do not begin non-trivial tasks by reading the entire repository.

Prefer:
symbol search -> targeted file ranges -> callers/callees -> tests -> implementation

Avoid:
full-repo scans -> whole-file rereads -> giant logcat dumps -> speculative Xiaomi analysis

Existing unchanged large files should not be repeatedly reread.

## Verification

Never claim that a test, build, device check, or runtime behavior passed unless it
was actually executed or observed.

After non-trivial runtime changes, verify the relevant tests/build and final
diff, obtain independent verifier review, then deploy the verified APK over the
existing wireless ADB connection and perform relevant device checks. Skip phone
deployment only for trivial/non-runtime work or when device verification is not
meaningful, and state the reason. Keep code/build, installation, and directly
observed runtime behavior as separate verification states.

## Knowledge

Verified Xiaomi discoveries belong in
`.cursor/skills/xiaomi-systemui/references/verified-behavior.md`.

Never record speculation as verified knowledge.
Every Xiaomi observation must identify the device/build/APK context that established it.

The primary target is a Xiaomi 17 Ultra on HyperOS 3 TR. Root is temporary via
GhostLock: ordinary verification must not require a normal reboot or persistent
boot-image changes.

## APK installation — wireless ADB + root

Use only an already-connected wireless ADB target. Do not restart `adbd`, change
ADB ports or wireless-debugging settings, alter ADB authentication, fall back to
USB, or reboot unless the user explicitly asks.

Never install HyperPop with plain `adb install` or an Android-MCP app-install
tool. For every APK installation, select an online wireless target from
`adb devices`, then perform this sequence with that target's serial:

```powershell
adb -s <wireless-serial> push "<LOCAL_APK_PATH>" /data/local/tmp/__codex_install.apk
adb -s <wireless-serial> shell su -c "pm install -r -g --user 0 /data/local/tmp/__codex_install.apk"
adb -s <wireless-serial> shell su -c "rm -f /data/local/tmp/__codex_install.apk"
```

Quote local paths, install for user `0` unless another profile is explicitly
requested, and always attempt the temporary-file cleanup even after a failed
install. Treat only Package Manager output containing `Success` as success;
otherwise report its error verbatim. Repeat the complete push, root install, and
cleanup sequence for each APK when installing multiple files.
