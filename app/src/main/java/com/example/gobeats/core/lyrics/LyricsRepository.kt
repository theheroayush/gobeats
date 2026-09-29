package com.example.gobeats.core.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LyricsRepository(private val context: Context) {

    private val cache = mutableMapOf<String, LyricsData>()
    private val lyricsDir = File(context.filesDir, "lyrics").apply { mkdirs() }

    suspend fun getLyricsForTrack(track: Track): LyricsData = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        // 1. Check user-imported or previously cached lyrics in app internal storage
        val localCustomFile = File(lyricsDir, "${track.id}.lrc")
        if (localCustomFile.exists()) {
            val content = localCustomFile.readText()
            val parsed = LrcParser.parse(content, source = "local")
            cache[track.id] = parsed
            return@withContext parsed
        }

        // 2. Check companion .lrc file in the same directory as the audio file
        try {
            val uri = Uri.parse(track.fileUri)
            if (uri.scheme == "file" || track.fileUri.startsWith("/")) {
                val audioPath = uri.path ?: track.fileUri
                val audioFile = File(audioPath)
                val baseName = audioFile.nameWithoutExtension
                val companionLrc = File(audioFile.parentFile, "$baseName.lrc")
                val companionTxt = File(audioFile.parentFile, "$baseName.txt")

                val targetFile = if (companionLrc.exists()) companionLrc else if (companionTxt.exists()) companionTxt else null
                if (targetFile != null && targetFile.canRead()) {
                    val content = targetFile.readText()
                    val parsed = LrcParser.parse(content, source = "companion")
                    cache[track.id] = parsed
                    return@withContext parsed
                }
            }
        } catch (_: Exception) {}

        // 3. Attempt to read embedded lyrics from MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            try {
                val uri = Uri.parse(track.fileUri)
                retriever.setDataSource(context, uri)
                val comment = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
                if (!comment.isNullOrBlank() && (comment.contains("[00:") || comment.contains("\n"))) {
                    val parsed = LrcParser.parse(comment, source = "embedded")
                    if (parsed.lines.isNotEmpty()) {
                        cache[track.id] = parsed
                        return@withContext parsed
                    }
                }
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 4. Automated Online Synced Lyrics via LRCLIB
        try {
            val durationSec = if (track.durationMs > 0) track.durationMs / 1000L else null
            val onlineContent = LrclibClient.fetchLyrics(
                trackName = track.title,
                artistName = track.artistName,
                albumName = track.albumTitle,
                durationSeconds = durationSec
            )

            if (!onlineContent.isNullOrBlank()) {
                // Permanently save to disk for 100% offline access next time!
                localCustomFile.writeText(onlineContent)
                val parsed = LrcParser.parse(onlineContent, source = "lrclib")
                cache[track.id] = parsed
                return@withContext parsed
            }
        } catch (_: Exception) {
            // Graceful fallback on network failure or offline mode
        }

        // 5. Default empty lyrics
        return@withContext LyricsData.EMPTY
    }

    suspend fun searchOnlineLyrics(query: String): List<LrclibRecord> = withContext(Dispatchers.IO) {
        try {
            LrclibClient.searchLyrics(query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun applyOnlineLyric(trackId: String, record: LrclibRecord): LyricsData = withContext(Dispatchers.IO) {
        val content = record.syncedLyrics ?: record.plainLyrics ?: ""
        if (content.isNotBlank()) {
            val targetFile = File(lyricsDir, "$trackId.lrc")
            targetFile.writeText(content)
            val parsed = LrcParser.parse(content, source = "lrclib")
            cache[trackId] = parsed
            parsed
        } else {
            LyricsData.EMPTY
        }
    }

    suspend fun saveLyrics(trackId: String, content: String): LyricsData = withContext(Dispatchers.IO) {
        val targetFile = File(lyricsDir, "$trackId.lrc")
        targetFile.writeText(content)
        val parsed = LrcParser.parse(content, source = "local")
        cache[trackId] = parsed
        parsed
    }

    suspend fun importLyricsFromUri(trackId: String, uri: Uri): LyricsData = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: ""

        if (content.isNotBlank()) {
            saveLyrics(trackId, content)
        } else {
            LyricsData.EMPTY
        }
    }
}
