package com.lidesheng.hyperlyric.root.island.renderer

/**
 * 超级岛渲染器接口。
 * 仅接收歌词源驱动的内容、播放状态和进度事件。
 * 结构注入和宿主恢复统一交给 IslandPresentationCoordinator 对账。
 */
interface IslandRenderer {
    fun refreshActiveIsland()
    fun updateMetadata()
    fun updateLyricLine()
    fun updateTextColors()
    fun updatePosition(position: Long, playbackSpeed: Float = 1f)
    fun onPlaybackStateChanged(isPlaying: Boolean)
    fun clearAllViews()
}
