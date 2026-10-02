# HyperPop device deployment

Load this reference only after a non-trivial runtime change has passed relevant
tests/build checks and independent verifier review.

## Deployment decision

Deploy by default for bug fixes, features, behavioral changes, hook changes,
runtime configuration changes, and other non-trivial application changes.

Do not deploy for comments, spelling, docs-only edits, formatting, or
non-runtime metadata. Deployment may also be skipped when the change clearly
has no meaningful installed-device verification path; state the specific reason
in the final report.

If local verification fails, do not deploy. If the wireless device is absent or
offline, leave ADB untouched and report device verification as pending.

## Required order

1. Complete targeted tests and the appropriate APK build.
2. Obtain `verifier` PASS for local code/build verification, or resolve its
   findings and rerun it.
3. Deploy the exact APK produced by the verified build with:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .cursor/skills/hyperpop-change/scripts/deploy-wireless-root.ps1 -ApkPath app/build/outputs/apk/debug/app-debug.apk
   ```

4. Confirm the helper reports Package Manager `Success`, cleanup success, and
   the installed HyperPop package/version.
5. Run only the relevant read-only device checks. For Xiaomi/SystemUI behavior,
   use the `xiaomi-systemui` workflow and its bounded runtime-capture helper.
6. Perform or request the exact user-visible reproduction when logs and state
   cannot establish the result.

Use `-DryRun` to validate APK-path and already-connected wireless-target
selection without pushing or installing anything. If multiple wireless targets
are online, pass the intended existing target with `-Serial`; never choose a USB
device or emulator.

## Safety invariants

- Use only a target already listed online by `adb devices` with a wireless TCP
  or wireless-debugging mDNS serial.
- Never run `adb connect` or `adb disconnect`, restart `adbd`, change TCP/IP
  ports, alter wireless-debugging or authentication settings, fall back to USB,
  or reboot unless explicitly requested.
- GhostLock root is temporary. Ordinary verification must not require a normal
  reboot or persistent boot-image modification.
- Never use plain `adb install` or an Android-MCP install action.
- Push to `/data/local/tmp/<name>.apk`, invoke
  `pm install -r -g --user 0` through `adb shell su -c`, and attempt root cleanup
  even when installation fails.
- Do not silently switch install methods after any failure. Preserve and report
  Package Manager output verbatim.
- Do not install an arbitrary or stale APK merely to test deployment plumbing.

## Evidence labels

Report each independently:

- `LOCAL VERIFICATION`: tests/build/diff result and exact APK path.
- `DEVICE DEPLOYMENT`: wireless serial, Package Manager result, cleanup result,
  installed package, version name, and version code.
- `RUNTIME VERIFICATION`: exact checks/reproduction performed and their result,
  or `PENDING` with the reason.

A successful deployment proves neither hook activation nor the requested
visual, performance, lifecycle, or repeated-interaction behavior.
