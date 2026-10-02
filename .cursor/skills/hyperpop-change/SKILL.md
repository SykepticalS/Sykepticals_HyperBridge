---
name: hyperpop-change
description: Use for non-trivial HyperPop bug fixes, behavioral changes, features, refactors, or repository investigations.
---

# HyperPop change workflow

## Discovery

Delegate initial repository reconnaissance to `repo-scout`.

The scout should identify only:
- relevant files and symbols;
- callers/callees;
- related tests;
- existing invariants;
- likely state ownership.

Do not repeat broad exploration in the parent context unless the scout's result
is demonstrably insufficient.

For behavioral or lifecycle changes, read
`references/regression-traps.md` before editing. Do not load it for spelling,
formatting, comments, or another clearly trivial edit.

## Root cause

Before editing, establish a concrete root-cause hypothesis supported by repository
or runtime evidence.

If the hypothesis depends on undocumented Xiaomi behavior, use the
`xiaomi-systemui` skill rather than inferring it.

## Implementation

Make the smallest change that solves the actual lifecycle/state problem.

Preserve Xiaomi native behavior outside the affected transition.

Prefer testable pure state/policy logic over expanding reflective hook complexity.

## Verification

After implementation:
1. inspect the diff;
2. run the narrowest relevant unit tests;
3. compile/build the affected module where appropriate;
4. delegate independent regression analysis to `verifier`;
5. after the verifier passes local verification, deploy the verified APK to the
   already-connected wireless device for every non-trivial runtime change;
6. perform the behavior-specific read-only runtime checks or exact manual
   reproduction that can establish the requested behavior.

Before step 5, read `references/device-deployment.md` and follow it exactly.
Deployment is the default final verification stage for bug fixes, features,
behavioral changes, and other non-trivial changes that affect the installed app
or hooks. It is not optional merely because unit tests and a build passed.

Do not deploy comments, spelling, docs-only changes, formatting, or non-runtime
metadata. A change that clearly cannot be meaningfully checked on the phone may
skip deployment, but the final report must state the concrete reason. If no
wireless target is online, do not change ADB configuration; report device
verification as pending.

Keep these states separate in every report:
- code/test/build verified;
- APK deployed and installed-package identity verified;
- runtime behavior directly verified.

Installation and hook logs do not prove visual correctness, performance, or
the result of repeated interactions.

If the change invalidates docs/agent/architecture-map.md, update that map briefly.

If new Xiaomi behavior was directly verified, record it in
`.cursor/skills/xiaomi-systemui/references/verified-behavior.md` with its current
device/build/APK identity.
