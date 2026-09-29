package com.example.gobeats.core.media

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.gobeats.core.model.Track
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object MetadataExtractor {

    data class SmartTags(
        val title: String,
        val artist: String,
        val album: String
    )

    fun extractMetadata(
        context: Context,
        uri: Uri,
        sourceId: String = "source_local",
        fileSize: Long = 0,
        displayName: String? = null,
        hintTitle: String? = null,
        hintArtist: String? = null,
        hintAlbum: String? = null,
        hintDurationMs: Long? = null,
        hintAlbumId: Long? = null,
        hintYear: Int? = null,
        hintIsVideo: Boolean = false
    ): Track {
        val retriever = MediaMetadataRetriever()
        return try {
            try {
                retriever.setDataSource(context, uri)
            } catch (_: Exception) {}

            val mmrTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val mmrArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val mmrAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val mmrDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val trackNumStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val discNumStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            val genreRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val yearStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val hasVideoStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val videoWidthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val videoHeightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)

            val rawTitle = mmrTitle?.ifBlank { null } ?: hintTitle?.ifBlank { null }
            val rawArtist = mmrArtist?.ifBlank { null } ?: hintArtist?.ifBlank { null }
            val rawAlbum = mmrAlbum?.ifBlank { null } ?: hintAlbum?.ifBlank { null }
            val fileNameOrUri = displayName ?: uri.lastPathSegment ?: "Track"

            val isVideo = hintIsVideo ||
                    (hasVideoStr != null && hasVideoStr.equals("yes", ignoreCase = true)) ||
                    (mimeType != null && mimeType.startsWith("video/")) ||
                    fileNameOrUri.endsWith(".mp4", ignoreCase = true) ||
                    fileNameOrUri.endsWith(".mkv", ignoreCase = true) ||
                    fileNameOrUri.endsWith(".webm", ignoreCase = true)

            val videoWidth = videoWidthStr?.toIntOrNull() ?: 0
            val videoHeight = videoHeightStr?.toIntOrNull() ?: 0

            val smartTags = parseSmartTags(
                rawTitle = rawTitle,
                rawArtist = if (rawArtist.isNullOrBlank() && isVideo) "Music Video" else rawArtist,
                rawAlbum = if (rawAlbum.isNullOrBlank() && isVideo) "Videos" else rawAlbum,
                fileNameOrUri = fileNameOrUri
            )

            val durationMs = mmrDuration ?: hintDurationMs ?: 0L
            val trackNum = parseFirstInt(trackNumStr) ?: 1
            val discNum = parseFirstInt(discNumStr) ?: 1
            val year = yearStr?.take(4)?.toIntOrNull() ?: hintYear
            val bitrate = (bitrateStr?.toIntOrNull() ?: 0) / 1000

            // Extract embedded album art, system album art, or video frame thumbnail
            var artworkUri = extractArtworkToCache(context, retriever, "${smartTags.artist} - ${smartTags.album}")
            if (artworkUri == null && hintAlbumId != null && hintAlbumId > 0) {
                try {
                    val systemArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        hintAlbumId
                    )
                    artworkUri = systemArtUri.toString()
                } catch (_: Exception) {}
            }

            if (artworkUri == null && isVideo) {
                try {
                    val frame = retriever.getFrameAtTime(1_500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                    if (frame != null) {
                        val cacheFile = File(context.cacheDir, "vid_${generateStableId(uri.toString())}.jpg")
                        FileOutputStream(cacheFile).use { out ->
                            frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        artworkUri = Uri.fromFile(cacheFile).toString()
                    }
                } catch (_: Exception) {}
            }

            val format = detectFormat(mimeType, uri.toString(), fileNameOrUri)

            Track(
                id = generateStableId(uri.toString()),
                sourceId = sourceId,
                fileUri = uri.toString(),
                title = smartTags.title,
                artistName = smartTags.artist,
                albumTitle = smartTags.album,
                durationMs = durationMs,
                trackNumber = trackNum,
                discNumber = discNum,
                genre = genreRaw?.trim() ?: if (isVideo) "Video" else null,
                year = year,
                format = format,
                bitrate = bitrate,
                sampleRate = 44100,
                fileSize = fileSize,
                artworkUri = artworkUri,
                isAvailable = true,
                dateAdded = System.currentTimeMillis(),
                isVideo = isVideo,
                videoWidth = videoWidth,
                videoHeight = videoHeight
            )
        } catch (e: Exception) {
            val fileNameOrUri = displayName ?: uri.lastPathSegment ?: "Unknown Track"
            val isVideoFallback = hintIsVideo ||
                    fileNameOrUri.endsWith(".mp4", ignoreCase = true) ||
                    fileNameOrUri.endsWith(".mkv", ignoreCase = true) ||
                    fileNameOrUri.endsWith(".webm", ignoreCase = true)
            val fallbackTags = parseSmartTags(
                rawTitle = hintTitle,
                rawArtist = if (hintArtist.isNullOrBlank() && isVideoFallback) "Music Video" else hintArtist,
                rawAlbum = if (hintAlbum.isNullOrBlank() && isVideoFallback) "Videos" else hintAlbum,
                fileNameOrUri = fileNameOrUri
            )
            Track(
                id = generateStableId(uri.toString()),
                sourceId = sourceId,
                fileUri = uri.toString(),
                title = fallbackTags.title,
                artistName = fallbackTags.artist,
                albumTitle = fallbackTags.album,
                durationMs = hintDurationMs ?: 0L,
                genre = if (isVideoFallback) "Video" else null,
                format = detectFormat(null, uri.toString(), fileNameOrUri),
                fileSize = fileSize,
                isAvailable = true,
                dateAdded = System.currentTimeMillis(),
                isVideo = isVideoFallback
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Intelligently parses filenames and metadata tags to eliminate "Unknown Artist",
     * remove web download clutter (e.g. "[320kbps]", "(Official Video)"),
     * and split "Artist - Title" patterns.
     */
    fun parseSmartTags(
        rawTitle: String?,
        rawArtist: String?,
        rawAlbum: String?,
        fileNameOrUri: String
    ): SmartTags {
        val isArtistGeneric = rawArtist.isNullOrBlank() ||
                rawArtist.equals("Unknown Artist", ignoreCase = true) ||
                rawArtist.equals("<unknown>", ignoreCase = true) ||
                rawArtist.equals("Unknown", ignoreCase = true)

        var cleanBaseName = fileNameOrUri.substringBeforeLast(".")
            .replace("_", " ")

        // Remove web clutter patterns
        val clutterRegex = Regex("""(\[.*?\]|\(.*?(video|audio|lyrics|kbps|official|download|remix|hd).*?\))""", RegexOption.IGNORE_CASE)
        cleanBaseName = cleanBaseName.replace(clutterRegex, "").trim()
        cleanBaseName = cleanBaseName.replace(Regex("""^\d+[\s\.\-_]+"""), "").trim()

        var candidateArtist = rawArtist?.trim()
        var candidateTitle = rawTitle?.trim()

        if (!candidateTitle.isNullOrBlank()) {
            candidateTitle = candidateTitle.replace(clutterRegex, "").trim()
            candidateTitle = candidateTitle.replace(Regex("""^\d+[\s\.\-_]+"""), "").trim()
        }

        if (isArtistGeneric || candidateTitle.isNullOrBlank()) {
            val dashMatch = Regex("""^(.+?)\s*[-–—]\s*(.+)$""").find(cleanBaseName)
            if (dashMatch != null) {
                val part1 = dashMatch.groupValues[1].trim()
                val part2 = dashMatch.groupValues[2].trim()

                if (isArtistGeneric) {
                    if (part2.equals("Podcast", ignoreCase = true) ||
                        part2.equals("Audio", ignoreCase = true) ||
                        part2.equals("Episode", ignoreCase = true)) {
                        candidateTitle = part1
                        candidateArtist = "Podcast"
                    } else {
                        candidateArtist = part1
                        candidateTitle = part2
                    }
                } else if (candidateTitle.isNullOrBlank()) {
                    candidateTitle = part2
                }
            } else {
                val byMatch = Regex("""^(.+?)\s+by\s+(.+)$""", RegexOption.IGNORE_CASE).find(cleanBaseName)
                if (byMatch != null) {
                    candidateTitle = byMatch.groupValues[1].trim()
                    if (isArtistGeneric) {
                        candidateArtist = byMatch.groupValues[2].trim()
                    }
                } else {
                    if (candidateTitle.isNullOrBlank()) {
                        candidateTitle = cleanBaseName.ifEmpty { "Audio Track" }
                    }
                }
            }
        }

        val finalTitle = candidateTitle.ifBlank { cleanBaseName.ifEmpty { "Audio Track" } }
        val finalArtist = if (!candidateArtist.isNullOrBlank() &&
            !candidateArtist.equals("Unknown Artist", ignoreCase = true) &&
            !candidateArtist.equals("<unknown>", ignoreCase = true)) {
            candidateArtist
        } else {
            "Local Audio"
        }

        val finalAlbum = if (!rawAlbum.isNullOrBlank() &&
            !rawAlbum.equals("Unknown Album", ignoreCase = true) &&
            !rawAlbum.equals("<unknown>", ignoreCase = true)) {
            rawAlbum.trim()
        } else {
            "Personal Library"
        }

        return SmartTags(finalTitle, finalArtist, finalAlbum)
    }

    private fun extractArtworkToCache(
        context: Context,
        retriever: MediaMetadataRetriever,
        albumKey: String
    ): String? {
        val picture = retriever.embeddedPicture ?: return null
        return try {
            val cacheDir = File(context.cacheDir, "artworks")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val hash = md5(albumKey)
            val artFile = File(cacheDir, "art_$hash.jpg")
            if (!artFile.exists() || artFile.length() == 0L) {
                FileOutputStream(artFile).use { out ->
                    out.write(picture)
                }
            }
            Uri.fromFile(artFile).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseFirstInt(str: String?): Int? {
        if (str == null) return null
        val clean = str.split("/")[0].trim()
        return clean.toIntOrNull()
    }

    private fun detectFormat(mimeType: String?, uriString: String, displayName: String? = null): String {
        val lowerUri = uriString.lowercase()
        val lowerName = displayName?.lowercase() ?: ""
        return when {
            lowerUri.endsWith(".flac") || lowerName.endsWith(".flac") || mimeType?.contains("flac") == true -> "FLAC"
            lowerUri.endsWith(".wav") || lowerName.endsWith(".wav") || mimeType?.contains("wav") == true -> "WAV"
            lowerUri.endsWith(".mp4") || lowerName.endsWith(".mp4") || mimeType?.contains("video/mp4") == true || mimeType?.contains("mp4") == true -> "MP4"
            lowerUri.endsWith(".mkv") || lowerName.endsWith(".mkv") || mimeType?.contains("matroska") == true -> "MKV"
            lowerUri.endsWith(".webm") || lowerName.endsWith(".webm") || mimeType?.contains("webm") == true -> "WEBM"
            lowerUri.endsWith(".m4a") || lowerName.endsWith(".m4a") || lowerUri.endsWith(".aac") || lowerName.endsWith(".aac") || mimeType?.contains("mp4a") == true -> "AAC"
            lowerUri.endsWith(".ogg") || lowerName.endsWith(".ogg") || lowerUri.endsWith(".opus") || lowerName.endsWith(".opus") || mimeType?.contains("ogg") == true -> "OGG"
            else -> "MP3"
        }
    }

    private fun generateStableId(input: String): String {
        return md5(input)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
