package com.lidesheng.hyperlyric.lyric.source

import com.lidesheng.hyperlyric.lyric.model.LyricMediaMetadata
import com.lidesheng.hyperlyric.lyric.model.Song
import com.lidesheng.hyperlyric.lyric.model.interfaces.IRichLyricLine

interface LyricSink {
    fun onSongChanged(song: Song?)
    fun onLyricLine(line: IRichLyricLine)
    fun onPlainText(text: String?)
    fun onStop()
    fun onMetadata(metadata: LyricMediaMetadata?)
    fun onPlaybackStateChanged(isPlaying: Boolean, playbackSpeed: Float = Float.NaN)
    fun onPositionChanged(position: Long, playbackSpeed: Float = Float.NaN)
}
