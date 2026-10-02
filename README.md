<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="150" alt="HyperPop Logo" style="border-radius: 20%;" />
</p>

<h1 align="center">HyperPop</h1>

<p align="center">
  <strong>HyperOS Dynamic Island notifications for rooted Xiaomi devices.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-0.6.1--sykeptical-blue?style=for-the-badge&logo=github" alt="Version 0.6.1-sykeptical" />
  <img src="https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Material%20Design-757575?style=for-the-badge&logo=material-design&logoColor=white" alt="Material Design" />
</p>

<br>

## About

HyperPop is the current application. Its package is `com.sykeptical.hyperpop`. It posts, updates, and cancels islands inside SystemUI through a libxposed module. There is no Shizuku or app-side island fallback.

HyperPop grew out of HyperBridge. HyperBridge was the upstream project created by [D4vidDf](https://github.com/D4vidDf/HyperBridge). This application is developed by sykeptical in Türkiye. A future References / Credits page will collect that history; it is not the current app name.

## Features

* **Native visuals:** Turns selected notifications into HyperOS islands.
* **App avatar badges:** Keeps a sender or content image and adds the source app icon as a compact badge.
* **Smart colors:** Extracts brand colors from app icons and tints dark or monochrome icons so they stay visible.
* **Media, navigation, downloads, calls, VPN, and screen recording:** Dedicated island layouts, including album art, turn-by-turn instructions, download progress, call timers, VPN status, and Xiaomi screen recording.
* **Spoiler protection:** Block terms globally or per app.
* **Login codes:** Surfaces one-time codes on the island with a copy action.
* **Privileged SystemUI backend:** A versioned IPC contract sends each island to the injected SystemUI dispatcher. The dispatcher checks ownership and performs tagged post, update, and cancel operations. Heads-up suppression fails open. Focus hooks and heartbeat checks run only while the backend is healthy. Hook configuration is synchronized with libxposed `RemotePreferences`.

## Languages

HyperPop ships with community translations. The Crowdin project used for those translations is still the historical project at [hyper-bridge](https://crowdin.com/project/hyper-bridge).

## Tech stack

* Kotlin
* Jetpack Compose (Material 3)
* Room
* LSPosed / libxposed hooks in `com.android.systemui` and `com.xiaomi.xmsf`

## Installation

HyperPop is a new application id. It can be installed beside an older HyperBridge package. Do not uninstall the previous package unless you intend to.

1. Install the HyperPop APK on a rooted Xiaomi, POCO, or Redmi device.
2. Enable the module in a libxposed API 102 implementation for both `com.android.systemui` and `com.xiaomi.xmsf`.
3. Use **Restart scopes** in setup or diagnostics.

Source notifications are intercepted inside SystemUI. The app does not need a notification-listener, overlay, or auxiliary service permission for that path.

Most policy changes hot-reload. Hook-installation changes require restarting the affected SystemUI or XMSF scope.

## Acknowledgements

* **[Stardawn](https://www.coolapk1s.com/feed/70418983)** for the XMSF notification research used on Chinese ROMs.
* **[HyperIsland](https://github.com/1812z/HyperIsland)** by 1812z, MIT License: libxposed service, scope, classloader, Xiaomi Focus, and SystemUI hook patterns.
* **[HyperIsland-ToolKit](https://github.com/D4vidDf/HyperIsland-ToolKit)** by D4vidDf, Apache 2.0.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0. See `LICENSE`.

Third-party notices that name their own authors stay with those works. Do not remove them during rebrands.
