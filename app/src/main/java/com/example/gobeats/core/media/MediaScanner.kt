package com.example.gobeats.core.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ScanProgress(
    val scannedCount: Int = 0,
    val currentFile: String = "",
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)

class MediaScanner(
    private val context: Context,
    private val dbHelper: GobeatsDatabaseHelper
) {

    fun scanMediaStore(): Flow<ScanProgress> = flow {
        emit(ScanProgress(scannedCount = 0, currentFile = "Querying MediaStore...", isComplete = false))

        var count = 0
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_RINGTONE,
            MediaStore.Audio.Media.IS_NOTIFICATION,
            MediaStore.Audio.Media.IS_ALARM
        )

        // Include both standard music and internet-downloaded audio files, excluding only system ringtones/alarms/notifications (NULL-safe)
        val selection = "(${MediaStore.Audio.Media.IS_RINGTONE} = 0 OR ${MediaStore.Audio.Media.IS_RINGTONE} IS NULL) AND " +
                "(${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 OR ${MediaStore.Audio.Media.IS_NOTIFICATION} IS NULL) AND " +
                "(${MediaStore.Audio.Media.IS_ALARM} = 0 OR ${MediaStore.Audio.Media.IS_ALARM} IS NULL)"

        val batch = mutableListOf<Track>()

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Audio"
                    val size = cursor.getLong(sizeCol)
                    val msTitle = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val msArtist = if (artistCol >= 0) cursor.getString(artistCol) else null
                    val msAlbum = if (albumCol >= 0) cursor.getString(albumCol) else null
                    val msDuration = if (durationCol >= 0) cursor.getLong(durationCol) else null
                    val msAlbumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else null
                    val msYear = if (yearCol >= 0) cursor.getInt(yearCol) else null

                    // Noise Filter: Skip tiny UI pings (1ms to 5s), but allow 0L
                    if (msDuration != null && msDuration in 1L until 5_000L) {
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val cleanTitle = msTitle?.ifBlank { null } ?: name.substringBeforeLast('.')
                    val cleanArtist = msArtist?.ifBlank { null } ?: "Unknown Artist"
                    val cleanAlbum = msAlbum?.ifBlank { null } ?: "Unknown Album"
                    val artUri = if (msAlbumId != null && msAlbumId > 0) {
                        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), msAlbumId).toString()
                    } else null

                    val track = Track(
                        id = MetadataExtractor.generateStableId(contentUri.toString()),
                        sourceId = "source_local",
                        fileUri = contentUri.toString(),
                        title = cleanTitle,
                        artistName = cleanArtist,
                        albumTitle = cleanAlbum,
                        durationMs = msDuration ?: 0L,
                        trackNumber = 1,
                        discNumber = 1,
                        genre = null,
                        year = msYear,
                        format = MetadataExtractor.detectFormat(null, contentUri.toString(), name),
                        bitrate = 0,
                        sampleRate = 44100,
                        fileSize = size,
                        artworkUri = artUri,
                        isAvailable = true,
                        dateAdded = System.currentTimeMillis(),
                        isVideo = false
                    )

                    batch.add(track)
                    count++

                    if (batch.size >= 50) {
                        emit(ScanProgress(scannedCount = count, currentFile = name, isComplete = false))
                        dbHelper.insertTracksBatch(batch, notify = true)
                        batch.clear()
                    }
                }
            }

            // Also query Video MediaStore to index music videos, concert clips, and MP4/MKV/WEBM/MOV/AVI files
            val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.ARTIST,
                MediaStore.Video.Media.ALBUM,
                MediaStore.Video.Media.DURATION
            )

            try {
                context.contentResolver.query(
                    videoCollection,
                    videoProjection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { vCursor ->
                    val vIdCol = vCursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val vNameCol = vCursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val vSizeCol = vCursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val vTitleCol = vCursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val vArtistCol = vCursor.getColumnIndex(MediaStore.Video.Media.ARTIST)
                    val vAlbumCol = vCursor.getColumnIndex(MediaStore.Video.Media.ALBUM)
                    val vDurationCol = vCursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                    while (vCursor.moveToNext()) {
                        val vDuration = if (vDurationCol >= 0) vCursor.getLong(vDurationCol) else 0L
                        if (vDuration in 1L until 2_000L) continue

                        val id = vCursor.getLong(vIdCol)
                        val name = vCursor.getString(vNameCol) ?: "Video"
                        val size = vCursor.getLong(vSizeCol)
                        val msTitle = if (vTitleCol >= 0) vCursor.getString(vTitleCol) else null
                        val msArtist = if (vArtistCol >= 0) vCursor.getString(vArtistCol) else null
                        val msAlbum = if (vAlbumCol >= 0) vCursor.getString(vAlbumCol) else null

                        val contentUri = ContentUris.withAppendedId(videoCollection, id)
                        val cleanTitle = msTitle?.ifBlank { null } ?: name.substringBeforeLast('.')
                        val cleanArtist = msArtist?.ifBlank { null } ?: "Music Video"
                        val cleanAlbum = msAlbum?.ifBlank { null } ?: "Videos"

                        val track = Track(
                            id = MetadataExtractor.generateStableId(contentUri.toString()),
                            sourceId = "source_local",
                            fileUri = contentUri.toString(),
                            title = cleanTitle,
                            artistName = cleanArtist,
                            albumTitle = cleanAlbum,
                            durationMs = vDuration,
                            trackNumber = 1,
                            discNumber = 1,
                            genre = "Video",
                            year = null,
                            format = MetadataExtractor.detectFormat("video/mp4", contentUri.toString(), name),
                            bitrate = 0,
                            sampleRate = 48000,
                            fileSize = size,
                            artworkUri = null,
                            isAvailable = true,
                            dateAdded = System.currentTimeMillis(),
                            isVideo = true,
                            videoWidth = 1920,
                            videoHeight = 1080
                        )

                        batch.add(track)
                        count++

                        if (batch.size >= 50) {
                            emit(ScanProgress(scannedCount = count, currentFile = name, isComplete = false))
                            dbHelper.insertTracksBatch(batch, notify = true)
                            batch.clear()
                        }
                    }
                }
            } catch (_: Exception) {}

            // Scan Downloads, Music, and App Sync folders for unindexed internet downloads (.opus, .webm, .m4a, .mp4) & .m3u/.m3u8 playlists
            val extraDirs = listOfNotNull(
                context.getExternalFilesDir(null),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            )
            val playlistFiles = mutableListOf<File>()
            for (dir in extraDirs) {
                if (dir.exists() && dir.canRead()) {
                    val files = dir.listFiles().orEmpty()
                    for (file in files) {
                        if (file.isFile && file.canRead()) {
                            val lower = file.name.lowercase()
                            if (lower.endsWith(".m3u") || lower.endsWith(".m3u8")) {
                                playlistFiles.add(file)
                            } else if (isSupportedMediaFile(file.name, null) && file.length() > 8_192L) {
                                val fileUri = Uri.fromFile(file)
                                val isVid = MetadataExtractor.isVideoExtensionOrMime(file.name, null, fileUri.toString())
                                val cleanName = file.nameWithoutExtension
                                val track = Track(
                                    id = MetadataExtractor.generateStableId(fileUri.toString()),
                                    sourceId = "source_local",
                                    fileUri = fileUri.toString(),
                                    title = cleanName,
                                    artistName = if (isVid) "Video Download" else "Audio Download",
                                    albumTitle = "Downloads",
                                    durationMs = 0L,
                                    genre = if (isVid) "Video" else null,
                                    format = MetadataExtractor.detectFormat(null, fileUri.toString(), file.name),
                                    fileSize = file.length(),
                                    isAvailable = true,
                                    dateAdded = System.currentTimeMillis(),
                                    isVideo = isVid
                                )
                                batch.add(track)
                                count++
                                if (batch.size >= 50) {
                                    dbHelper.insertTracksBatch(batch, notify = true)
                                    batch.clear()
                                }
                            }
                        }
                    }
                }
            }

            // Flush any remaining batch items
            if (batch.isNotEmpty()) {
                dbHelper.insertTracksBatch(batch, notify = true)
                batch.clear()
            }

            // Single notification to update UI once scan completes
            dbHelper.insertTracksBatch(emptyList(), notify = true)

            // Auto-sync any .m3u / .m3u8 playlists discovered in Downloads or Music
            if (playlistFiles.isNotEmpty()) {
                val allLibrary = dbHelper.getAllTracks()
                val existingPlaylists = dbHelper.getAllPlaylists()
                for (plFile in playlistFiles) {
                    try {
                        val content = plFile.readText()
                        val defaultName = plFile.nameWithoutExtension.replace('_', ' ').ifBlank { "Synced Playlist" }
                        val (parsedName, entries) = com.example.gobeats.core.playlist.PlaylistImporter.parseText(content, defaultName)
                        val matchRes = com.example.gobeats.core.playlist.PlaylistImporter.matchTracks(entries, allLibrary, parsedName)
                        val tracksToSync = matchRes.matchedTracks + matchRes.streamTracks
                        if (tracksToSync.isNotEmpty()) {
                            for (st in matchRes.streamTracks) {
                                dbHelper.insertOrUpdateTrack(st)
                            }
                            val targetPl = existingPlaylists.find { it.name.equals(parsedName, ignoreCase = true) }
                                ?: dbHelper.createPlaylist(parsedName, "[Imported] Synced from ${plFile.name}")
                            for (t in tracksToSync) {
                                dbHelper.addTrackToPlaylist(targetPl.id, t.id)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            emit(ScanProgress(scannedCount = count, currentFile = "Completed", isComplete = true))
        } catch (e: Exception) {
            emit(ScanProgress(scannedCount = count, currentFile = "", isComplete = true, errorMessage = e.localizedMessage))
        }
    }.flowOn(Dispatchers.IO)

    fun scanDocumentTree(treeUri: Uri): Flow<ScanProgress> = flow {
        emit(ScanProgress(scannedCount = 0, currentFile = "Scanning folder...", isComplete = false))

        var count = 0
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
        if (rootDoc != null && rootDoc.isDirectory) {
            val mediaFiles = mutableListOf<DocumentFile>()
            collectMediaFiles(rootDoc, mediaFiles)

            for (doc in mediaFiles) {
                val name = doc.name ?: "Track"
                val isVid = MetadataExtractor.isVideoExtensionOrMime(name, doc.type, doc.uri.toString())
                emit(ScanProgress(scannedCount = count, currentFile = name, isComplete = false))

                val track = MetadataExtractor.extractMetadata(
                    context = context,
                    uri = doc.uri,
                    sourceId = "source_local",
                    fileSize = doc.length(),
                    displayName = name,
                    hintIsVideo = isVid
                )

                dbHelper.insertOrUpdateTrack(track)
                count++
            }
        }
        emit(ScanProgress(scannedCount = count, currentFile = "Folder scan complete", isComplete = true))
    }.flowOn(Dispatchers.IO)

    private fun collectMediaFiles(dir: DocumentFile, result: MutableList<DocumentFile>) {
        val files = dir.listFiles()
        for (f in files) {
            if (f.isDirectory) {
                collectMediaFiles(f, result)
            } else if (isSupportedMediaFile(f.name, f.type)) {
                result.add(f)
            }
        }
    }

    private fun isSupportedMediaFile(name: String?, mime: String?): Boolean {
        if (mime != null && (mime.startsWith("audio/") || mime.startsWith("video/"))) return true
        if (name == null) return false
        val lower = name.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".flac") ||
                lower.endsWith(".wav") || lower.endsWith(".m4a") ||
                lower.endsWith(".aac") || lower.endsWith(".ogg") ||
                lower.endsWith(".opus") || lower.endsWith(".mp4") ||
                lower.endsWith(".mkv") || lower.endsWith(".webm") ||
                lower.endsWith(".mov") || lower.endsWith(".avi") ||
                lower.endsWith(".3gp") || lower.endsWith(".m4v")
    }

    /**
     * Purges legacy starter sample tracks to ensure 100% genuine user data.
     */
    suspend fun cleanLegacyMockData() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("tracks", "id = 'starter_sample_01' OR file_uri LIKE 'android.resource://%'", null)
    }
}
