## APK Installation Rule — Wireless ADB + Root

Whenever installing an APK onto the connected Android device, ALWAYS install it through wireless ADB using the root Package Manager method below.

Do NOT use:

```bash
adb install ...
```

Do NOT switch to USB debugging just because a normal ADB install fails.

Assume the device is rooted and wireless ADB is already connected unless the connection itself is unavailable.

For every APK installation:

1. Push the APK to `/data/local/tmp/`.
2. Install it through `pm install` executed as root with `su -c`.
3. Delete the temporary APK afterward.

Use this pattern:

```bash
adb push "<LOCAL_APK_PATH>" /data/local/tmp/__codex_install.apk
adb shell su -c "pm install -r -g --user 0 /data/local/tmp/__codex_install.apk"
adb shell su -c "rm -f /data/local/tmp/__codex_install.apk"
```

### Requirements

- Always use wireless ADB for APK installation.
- Always invoke Package Manager through root using `su -c`.
- Never use plain `adb install` as a fallback.
- Quote local paths so paths containing spaces work correctly.
- Use `-r` so an existing installation can be updated/replaced while preserving its app data where Android permits.
- Use `-g` to grant runtime permissions normally granted during installation.
- Install for Android user `0` unless the task explicitly requires another user/profile.
- Always remove the temporary APK from `/data/local/tmp/` after installation, including after a failed installation when possible.
- Check the installation command's output. Treat `Success` as a successful install and report any PackageManager error verbatim if installation fails.
- Do not modify, disable, or bypass ADB authentication.
- Do not restart `adbd`, change ADB ports, or alter the wireless ADB configuration unless explicitly requested.

If installing multiple APK files individually, repeat the same push → root install → cleanup sequence for each APK.

When an APK is built as part of the current task and needs to be installed on the device, automatically use this installation method rather than asking how it should be installed.