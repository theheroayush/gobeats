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

        // Include both standard music and internet-downloaded audio files (where IS_MUSIC may be 0), excluding only system ringtones/alarms/notifications
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"

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

                    // Noise Filter: Skip tiny UI pings (1ms to 5s), but allow 0L (internet-downloaded files with unindexed duration headers)
                    if (msDuration != null && msDuration in 1L until 5_000L) {
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    emit(ScanProgress(scannedCount = count, currentFile = name, isComplete = false))

                    val track = MetadataExtractor.extractMetadata(
                        context = context,
                        uri = contentUri,
                        sourceId = "source_local",
                        fileSize = size,
                        displayName = name,
                        hintTitle = msTitle,
                        hintArtist = msArtist,
                        hintAlbum = msAlbum,
                        hintDurationMs = msDuration,
                        hintAlbumId = msAlbumId,
                        hintYear = msYear
                    )

                    dbHelper.insertOrUpdateTrack(track)
                    count++
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
                    // Skip tiny micro clips (1ms to 2s), but allow 0L (unindexed videos)
                    if (vDuration in 1L until 2_000L) continue

                    val id = vCursor.getLong(vIdCol)
                    val name = vCursor.getString(vNameCol) ?: "Video"
                    val size = vCursor.getLong(vSizeCol)
                    val msTitle = if (vTitleCol >= 0) vCursor.getString(vTitleCol) else null
                    val msArtist = if (vArtistCol >= 0) vCursor.getString(vArtistCol) else null
                    val msAlbum = if (vAlbumCol >= 0) vCursor.getString(vAlbumCol) else null

                    val contentUri = ContentUris.withAppendedId(videoCollection, id)
                    emit(ScanProgress(scannedCount = count, currentFile = name, isComplete = false))

                    val track = MetadataExtractor.extractMetadata(
                        context = context,
                        uri = contentUri,
                        sourceId = "source_local",
                        fileSize = size,
                        displayName = name,
                        hintTitle = msTitle,
                        hintArtist = msArtist ?: "Music Video",
                        hintAlbum = msAlbum ?: "Videos",
                        hintDurationMs = vDuration,
                        hintIsVideo = true
                    )

                    dbHelper.insertOrUpdateTrack(track)
                    count++
                }
            }

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
                                val track = MetadataExtractor.extractMetadata(
                                    context = context,
                                    uri = fileUri,
                                    sourceId = "source_local",
                                    fileSize = file.length(),
                                    displayName = file.name,
                                    hintIsVideo = isVid
                                )
                                dbHelper.insertOrUpdateTrack(track)
                                count++
                            }
                        }
                    }
                }
            }

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
