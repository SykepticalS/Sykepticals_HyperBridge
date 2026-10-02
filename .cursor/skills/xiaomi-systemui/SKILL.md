---
name: xiaomi-systemui
description: Use whenever HyperPop work depends on undocumented Xiaomi/HyperOS SystemUI, XMSF, Dynamic Island, Focus, plugin classloaders, island lifecycle/state/geometry, or runtime phone behavior.
---

# Xiaomi / HyperOS investigation

Read `references/verified-behavior.md` first.

Treat a stored observation as current only when the session identity status and
`.agent-local/device-state.json` show a compatible build/APK identity.

## Evidence order

1. Current repository implementation and regression tests
2. Current device/runtime evidence
3. Current Xiaomi APK implementation through jadx-analyzer
4. Compatible stored verified knowledge as a navigation aid
5. Inference only if direct evidence cannot reasonably answer the question

## Android MCP discipline

Use device-state and shell queries narrowly.

For runtime debugging, prefer
`scripts/capture-runtime.ps1 -DurationSeconds 20` over unrestricted logcat. It
captures bounded, filtered evidence under `.agent-local/evidence/` without
clearing logs or changing ADB state. Use raw logcat only when the helper omitted
evidence that a specific hypothesis requires, and filter that query narrowly.

Prefer targeted dumpsys/getprop queries.

Do not request screenshots when textual state is sufficient.

Never make destructive device/system changes merely to investigate a bug.

## JADX discipline

Prefer:
- search_classes_by_keyword
- search_method_by_name
- get_methods_of_class
- get_method_by_name
- callers/callees
- subtype/override queries

Avoid `get_all_classes` when a keyword can narrow the search.

Keep default source-size limits unless relationships across a whole class genuinely
require uncapped source.

Do not repeatedly decompile the same unchanged class.

## APK acquisition

APKs used for reverse engineering belong under `.agent-local/apks/`.

If the required Xiaomi APK is not available locally:
- use host ADB commands to determine the package/APK path;
- pull only the required APK;
- compute its SHA-256;
- load it into jadx-analyzer.

Do not assume that a Dynamic Island class necessarily lives in SystemUI.apk;
identify the actual APK/plugin that owns the class.

## Recording discoveries

Only verified observations go into the reference files.

Record:
- Android/HyperOS build
- SystemUI/plugin version where available
- APK SHA-256
- exact class/method/event
- evidence source
- observed behavior
- consequence for HyperPop
