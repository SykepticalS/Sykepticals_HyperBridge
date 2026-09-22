# Setup Guide

HyperLyric has two usage modes: Xposed module mode and standalone app mode. Choose one mode first, then follow its setup steps.

## Xposed module mode

This mode integrates lyrics into Xiaomi HyperIsland on devices running HyperOS 3 and requires the LSPosed v2.0 framework.

### 1. Install and enable HyperLyric

1. Install HyperLyric. It is available from GitHub Releases or the LSPosed repository.
2. Enable the HyperLyric module in LSPosed.
3. After enabling the module, turn on **Xiaomi HyperIsland Lyrics** in the HyperLyric app.

### 2. Choose a lyric source

HyperLyric supports three lyric sources. Their working methods and supported music apps differ. Choose a source for your music app and check its repository for the detailed support list.

| Source | How it works | Dependency entry |
| :--- | :--- | :--- |
| **Lyricon** | Provides lyrics through LyricProvider. HyperLyric 6.0 and later requires Lyricon Central and the matching LyricProvider. | [Lyricon Central](https://github.com/tomakino/lyricon/releases/tag/core) · [LyricProvider](https://github.com/proify/LyricProvider/releases) |
| **SuperLyric** | The SuperLyric module handles both lyric scheduling and lyric providing. | [SuperLyric](https://github.com/HChenX/SuperLyric) |
| **LyricInfo** | Reads MediaSession metadata written by the music app; mainstream music apps usually need the LyricInfo module to write lyrics into that metadata. | [LyricInfo](https://github.com/limczhh/LyricInfo) (recommended) |

### 3. Install and enable the selected source

Install the dependencies listed above, then enable HyperLyric and the installed source modules in LSPosed.

- Lyricon: HyperLyric 6.0 and later requires both Lyricon Central and the matching LyricProvider.
- SuperLyric: install SuperLyric only.
- LyricInfo: LyricInfo is recommended because mainstream music apps usually do not write lyrics into MediaSession metadata. Skip it only when the music app provides that metadata itself.

Select the music app that should receive lyrics in LSPosed.

### 4. Restart as required by the source

- Lyricon: restart SystemUI and the music app.
- SuperLyric: restart the phone after installation or first injection.
- LyricInfo: metadata changes normally require only a music-app restart; restart as required by the module for the first injection.

Play music when setup is complete. Lyrics should appear in Xiaomi HyperIsland.

## Standalone app mode

This mode does not use LSPosed. HyperLyric reads MediaSession metadata and displays lyrics through notifications:

1. Select **Notification Dynamic Island Lyrics** in HyperLyric.
2. Grant notification listener and post notification permissions, then add the music app to the lyric whitelist.
3. Make the music app output lyric metadata. **Car Bluetooth lyrics** or a similar feature is only one possible method and is not required by every app; some apps also require a connected Bluetooth headset or another Bluetooth device.
4. Play music. If the system restricts HyperLyric in the background, grant autostart or background-running permission.
