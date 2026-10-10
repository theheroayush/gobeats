package com.example.gobeats.core.lyrics

import android.content.Context
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-performance background lyrics fetcher.
 * Executes on Dispatchers.IO, queries LRCLIB, parses synced LRC timestamps,
 * and persists directly into local SQLite lyrics_cache + app internal storage for 100% offline playback.
 */
class LrclibLyricsFetcher(private val context: Context) {

    private val dbHelper = GobeatsDatabaseHelper.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lyricsDir = File(context.filesDir, "lyrics").apply { mkdirs() }

    /**
     * Non-blocking background prefetch for a track.
     * Fires asynchronously on IO thread without blocking the calling thread or UI.
     */
    fun prefetchLyricsAsync(track: Track, onComplete: ((LyricsData?) -> Unit)? = null) {
        scope.launch {
            val lyrics = fetchAndCache(track)
            onComplete?.invoke(lyrics)
        }
    }

    /**
     * Synchronous suspend fetcher for background coroutines.
     * Checks SQLite cache first -> falls back to LRCLIB -> caches in SQLite.
     */
    suspend fun fetchAndCache(track: Track): LyricsData? = withContext(Dispatchers.IO) {
        // 1. Check local SQLite lyrics cache first (0 network roundtrips)
        val cached = dbHelper.getCachedLyrics(track.id)
        if (cached != null) {
            val content = cached.syncedLyrics ?: cached.plainLyrics
            if (!content.isNullOrBlank()) {
                return@withContext LrcParser.parse(content, source = cached.source)
            }
        }

        // 2. Query LRCLIB via clean title/artist heuristics
        try {
            val durationSec = if (track.durationMs > 0) track.durationMs / 1000L else null
            val lyricsContent = LrclibClient.fetchLyrics(
                trackName = track.title,
                artistName = track.artistName,
                albumName = track.albumTitle,
                durationSeconds = durationSec
            )

            if (!lyricsContent.isNullOrBlank()) {
                val parsed = LrcParser.parse(lyricsContent, source = "lrclib")
                // Persist to local SQLite lyrics_cache
                dbHelper.saveCachedLyrics(
                    trackId = track.id,
                    plainLyrics = if (!parsed.isSynced) lyricsContent else null,
                    syncedLyrics = if (parsed.isSynced) lyricsContent else null,
                    source = "LRCLIB"
                )

                // Also persist companion .lrc in internal storage
                try {
                    File(lyricsDir, "${track.id}.lrc").writeText(lyricsContent)
                } catch (_: Exception) {}

                return@withContext parsed
            }
        } catch (_: Exception) {
            // Graceful offline fallback
        }

        null
    }
}
