package com.tsuki.backend.lyrics

data class LyricLine(
    val timeMs: Long?,
    val text: String
)

data class Lyrics(
    val source: String,
    val synced: Boolean,
    val lines: List<LyricLine>
)
