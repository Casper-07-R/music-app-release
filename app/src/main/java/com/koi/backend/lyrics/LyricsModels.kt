package com.koi.backend.lyrics

data class LyricLine(
    val timeMs: Long? = null,
    val text: String
)

data class Lyrics(
    val videoId: String = "",
    val source: String = "unknown",
    val synced: Boolean,
    val lines: List<LyricLine>
)
