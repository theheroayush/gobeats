package com.example.gobeats.core.lyrics

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.gobeats.core.media.FlacTagWriter
import com.example.gobeats.core.media.Id3TagReader
import com.example.gobeats.core.model.Track
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Universal embedded lyrics extractor supporting:
 * - MP3: ID3v2 USLT (unsynchronized) & SYLT (synchronized)
 * - FLAC: Vorbis comment blocks (LYRICS, UNSYNCEDLYRICS, SYNCEDLYRICS)
 * - M4A/MP4: ilst '©lyr' metadata atom
 * - Universal fallback: MediaMetadataRetriever
 */
object EmbeddedLyricsExtractor {

    private val LYRICS_VORBIS_KEYS = listOf(
        "LYRICS",
        "UNSYNCEDLYRICS",
        "SYNCEDLYRICS",
        "UNSYNCED LYRICS",
        "SYNCED LYRICS"
    )

    fun extractLyrics(context: Context, track: Track): LyricsData? {
        val uri = Uri.parse(track.fileUri)
        val format = track.format.uppercase()
        val path = uri.path ?: track.fileUri
        val ext = path.substringAfterLast(".", "").uppercase()

        // 1. Try format-specific binary container decoders first
        val extracted = when {
            format == "MP3" || ext == "MP3" -> extractFromMp3(context, uri)
            format == "FLAC" || ext == "FLAC" -> extractFromFlac(context, uri)
            format in listOf("M4A", "MP4", "ALAC", "AAC") || ext in listOf("M4A", "MP4", "AAC") -> extractFromM4a(context, uri)
            else -> null
        }

        if (extracted != null && extracted.lines.isNotEmpty()) {
            return extracted
        }

        // 2. Fallback to MediaMetadataRetriever
        return extractFromMediaMetadataRetriever(context, uri)
    }

    private fun extractFromMp3(context: Context, uri: Uri): LyricsData? {
        return try {
            openStream(context, uri)?.use { stream ->
                Id3TagReader.extractLyricsFromMp3(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFromFlac(context: Context, uri: Uri): LyricsData? {
        return try {
            openStream(context, uri)?.use { stream ->
                val blocks = FlacTagWriter.readMetadataBlocks(stream)
                for (block in blocks) {
                    if (block.type == FlacTagWriter.BLOCK_TYPE_VORBIS_COMMENT) {
                        // FlacTagWriter.parseVorbisComments expects the 4-byte header + payload
                        val blockBytes = ByteArray(4 + block.payload.size)
                        blockBytes[0] = (block.type and 0x7F).toByte()
                        blockBytes[1] = ((block.payload.size shr 16) and 0xFF).toByte()
                        blockBytes[2] = ((block.payload.size shr 8) and 0xFF).toByte()
                        blockBytes[3] = (block.payload.size and 0xFF).toByte()
                        System.arraycopy(block.payload, 0, blockBytes, 4, block.payload.size)

                        val comments = FlacTagWriter.parseVorbisComments(blockBytes)
                        for (key in LYRICS_VORBIS_KEYS) {
                            val text = comments.entries.find { it.key.equals(key, ignoreCase = true) }?.value
                            if (!text.isNullOrBlank()) {
                                return LrcParser.parse(text, source = "embedded")
                            }
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractFromM4a(context: Context, uri: Uri): LyricsData? {
        return try {
            openStream(context, uri)?.use { stream ->
                extractLyricsFromM4aStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Traverses MP4 atoms in stream to locate moov/udta/meta/ilst/©lyr/data text atom.
     */
    private fun extractLyricsFromM4aStream(input: InputStream): LyricsData? {
        // Read file header into memory up to max 12MB (where moov metadata resides)
        val maxHeaderBytes = 12 * 1024 * 1024
        val buffer = ByteArray(maxHeaderBytes)
        var totalRead = 0
        while (totalRead < maxHeaderBytes) {
            val r = input.read(buffer, totalRead, maxHeaderBytes - totalRead)
            if (r == -1) break
            totalRead += r
        }

        if (totalRead < 16) return null

        // Search for '©lyr' atom: 0xA9, 0x6C, 0x79, 0x72
        val lyrMarker = byteArrayOf(0xA9.toByte(), 0x6C.toByte(), 0x79.toByte(), 0x72.toByte())
        val lyrOffset = indexOfSequence(buffer, 0, totalRead, lyrMarker)
        if (lyrOffset != -1) {
            // Find 'data' atom inside ©lyr: 0x64, 0x61, 0x74, 0x61
            val dataMarker = "data".toByteArray(StandardCharsets.US_ASCII)
            val dataOffset = indexOfSequence(buffer, lyrOffset, totalRead.coerceAtMost(lyrOffset + 1024), dataMarker)
            if (dataOffset != -1 && dataOffset >= 4) {
                // data atom: 4 bytes size, 4 bytes "data", 4 bytes type, 4 bytes locale, followed by text payload
                val dataAtomStart = dataOffset - 4
                val dataAtomSize = ByteBuffer.wrap(buffer, dataAtomStart, 4).order(ByteOrder.BIG_ENDIAN).int
                val payloadOffset = dataOffset + 12 // skip 'data' (4), type (4), locale (4)
                val payloadSize = dataAtomSize - 16
                if (payloadOffset + payloadSize <= totalRead && payloadSize > 0) {
                    val lyricsText = String(buffer, payloadOffset, payloadSize, StandardCharsets.UTF_8).trim()
                    if (lyricsText.isNotEmpty()) {
                        return LrcParser.parse(lyricsText, source = "embedded")
                    }
                }
            }
        }

        return null
    }

    private fun indexOfSequence(source: ByteArray, start: Int, end: Int, target: ByteArray): Int {
        if (target.isEmpty() || end - start < target.size) return -1
        for (i in start..(end - target.size)) {
            var matched = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
        }
        return -1
    }

    private fun extractFromMediaMetadataRetriever(context: Context, uri: Uri): LyricsData? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val comment = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
            if (!comment.isNullOrBlank() && (comment.contains("[00:") || comment.contains("\n"))) {
                val parsed = LrcParser.parse(comment, source = "embedded")
                if (parsed.lines.isNotEmpty()) parsed else null
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        return if (uri.scheme == "content") {
            context.contentResolver.openInputStream(uri)
        } else {
            val file = File(uri.path ?: uri.toString())
            if (file.exists() && file.canRead()) file.inputStream() else null
        }
    }
}
