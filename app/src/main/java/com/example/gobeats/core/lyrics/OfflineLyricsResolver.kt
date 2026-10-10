package com.example.gobeats.core.lyrics

import java.nio.charset.StandardCharsets

/**
 * Deterministic offline lyrics resolution engine implementing priority ordering:
 * Cache -> Companion .lrc -> Embedded ID3/Vorbis/M4A -> Remote LRCLIB -> None
 */
object OfflineLyricsResolver {

    /**
     * Resolves lyrics following strict priority:
     * 1. In-app / storage cache (source: "cache")
     * 2. Local companion .lrc file in audio directory (source: "companion")
     * 3. Embedded tags (source: "embedded_id3")
     * 4. Online LRCLIB if network available (source: "lrclib")
     * 5. Empty (source: "none")
     */
    fun resolveLyrics(
        cacheLyrics: String?,
        companionLyrics: String?,
        embeddedId3Lyrics: String?,
        networkAvailable: Boolean,
        onlineLyricsFetcher: () -> String?
    ): Pair<String, String> {
        if (!cacheLyrics.isNullOrBlank()) return cacheLyrics to "cache"
        if (!companionLyrics.isNullOrBlank()) return companionLyrics to "companion"
        if (!embeddedId3Lyrics.isNullOrBlank()) return embeddedId3Lyrics to "embedded_id3"
        if (networkAvailable) {
            val net = onlineLyricsFetcher()
            if (!net.isNullOrBlank()) return net to "lrclib"
        }
        return "" to "none"
    }

    /**
     * Parses an ID3v2 USLT frame payload into UTF-8 text string.
     * Skips 1-byte encoding, 3-byte language, and null-terminated descriptor.
     */
    fun parseUsltFrame(framePayload: ByteArray): String {
        if (framePayload.size < 5) return ""
        var offset = 4 // skip encoding (1) and 3-byte lang
        while (offset < framePayload.size && framePayload[offset] != 0.toByte()) {
            offset++
        }
        offset++ // skip null terminator
        if (offset >= framePayload.size) return ""
        return String(framePayload, offset, framePayload.size - offset, StandardCharsets.UTF_8)
    }
}
