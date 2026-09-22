# 配置教程

HyperLyric 有两种使用方式：Xposed 模块模式和独立应用模式。先选择一种方式，再按下面的步骤配置。

## Xposed 模块模式

这种方式把歌词接入小米超级岛，适用于运行 HyperOS 3 的设备，需要 LSPosed v2.0 框架支持。

### 1. 安装并启用 HyperLyric

1. 安装 HyperLyric。可以从 GitHub Releases 或 LSPosed 仓库获取。
2. 在 LSPosed 中启用 HyperLyric 模块。
3. 模块启用后，在 HyperLyric 应用内启用 **小米超级岛歌词** 开关。

### 2. 选择歌词源

HyperLyric 支持三种歌词源。它们的工作方式和支持的音乐软件不同，请根据目标音乐软件选择。各歌词源仓库会列出具体的支持范围。

| 歌词源 | 工作方式 | 依赖入口 |
| :--- | :--- | :--- |
| **Lyricon** | 通过 LyricProvider 提供歌词。HyperLyric 6.0 及之后的版本需要 Lyricon Central 和对应的 LyricProvider。 | [Lyricon Central](https://github.com/tomakino/lyricon/releases/tag/core) · [LyricProvider](https://github.com/proify/LyricProvider/releases) |
| **SuperLyric** | SuperLyric 模块同时负责歌词调度和歌词提供。 | [SuperLyric](https://github.com/HChenX/SuperLyric) |
| **LyricInfo** | 读取音乐软件写入的 MediaSession 元数据；主流音乐软件通常需要 LyricInfo 模块把歌词写入元数据。 | [LyricInfo](https://github.com/limczhh/LyricInfo)（推荐安装） |

### 3. 安装并启用所选歌词源

根据上表安装所选歌词源的依赖，然后在 LSPosed 中启用 HyperLyric 和已经安装的歌词源模块。

- Lyricon：HyperLyric 6.0 及之后的版本必须同时安装 Lyricon Central 和对应的 LyricProvider。
- SuperLyric：只需要安装 SuperLyric。
- LyricInfo：建议安装 LyricInfo，因为主流音乐软件通常不会把歌词写入 MediaSession 元数据。只有音乐软件自身支持输出这类元数据时，才可以不安装。

在 LSPosed 中勾选需要使用歌词的音乐软件。

### 4. 按歌词源要求重启

- Lyricon：重启 SystemUI，并重启音乐软件。
- SuperLyric：安装或首次注入后重启手机。
- LyricInfo：元数据发生变化时通常只需要重启音乐软件；首次注入按模块要求重启。

完成后播放音乐，歌词就会显示在小米超级岛中。

## 独立应用模式

这种方式不使用 LSPosed，HyperLyric 通过 MediaSession 元数据和通知显示歌词：

1. 在 HyperLyric 中选择 **通知型灵动岛歌词**。
2. 授予通知监听权限和发送通知权限，并把需要显示歌词的音乐软件加入歌词白名单。
3. 让音乐软件输出歌词元数据。“车载蓝牙歌词”或类似功能只是其中一种方式，并不是所有软件都要求打开；有些软件还需要连接蓝牙耳机或其他蓝牙设备。
4. 播放音乐。如果系统限制 HyperLyric 后台运行，请同时授予自启动或后台运行权限。
