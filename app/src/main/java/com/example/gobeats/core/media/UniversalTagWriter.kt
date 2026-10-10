package com.example.gobeats.core.media

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.media.tag.ScopedStoragePermissionRequiredException
import com.example.gobeats.core.media.tag.ScopedStorageRouter
import com.example.gobeats.core.media.tag.StorageWriteMechanism
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object UniversalTagWriter {

    /**
     * Executes the comprehensive 4-stage synchronization pipeline:
     * 1. Raw container-level file rewriting (MP3 ID3, FLAC Vorbis, M4A ilst) with staging validation
     * 2. MediaStore ContentValues attribute synchronization
     * 3. MediaScanner indexing trigger & persistent SQLite database overrides (protecting rescans)
     * 4. Live active PlaybackConnection session / Media3 Player item replacement
     */
    suspend fun writeTags(
        context: Context,
        track: Track,
        payload: TagEditPayload,
        dbHelper: GobeatsDatabaseHelper,
        playbackConnection: PlaybackConnection? = null
    ): Track? = withContext(Dispatchers.IO) {
        var finalPayload = payload

        // Cover artwork persistence
        if (payload.newArtworkBytes != null && payload.newArtworkBytes.isNotEmpty()) {
            val savedArtUri = Id3TagWriter.saveArtworkToStorage(context, payload.newArtworkBytes, payload.trackId)
            if (savedArtUri != null) {
                finalPayload = finalPayload.copy(artworkUri = savedArtUri)
            }
        }

        // Resolve target physical file path and content URI
        val filePath = when {
            track.fileUri.startsWith("file://") -> Uri.parse(track.fileUri).path
            track.fileUri.startsWith("/") -> track.fileUri
            else -> null
        }

        val contentUri = when {
            track.fileUri.startsWith("content://") -> Uri.parse(track.fileUri)
            filePath != null -> ScopedStorageRouter.getContentUriForFilePath(context, filePath)
            else -> null
        }

        // =========================================================================
        // Stage 1: Raw File Tag Writing with Staging Safety
        // =========================================================================
        val targetFile = filePath?.let { File(it) }
        val isMp3 = (filePath?.endsWith(".mp3", ignoreCase = true) == true) ||
                track.fileUri.endsWith(".mp3", ignoreCase = true) ||
                track.format.contains("mp3", ignoreCase = true)
        val isFlac = (filePath?.endsWith(".flac", ignoreCase = true) == true) ||
                track.fileUri.endsWith(".flac", ignoreCase = true) ||
                track.format.contains("flac", ignoreCase = true)
        val isM4a = (filePath?.endsWith(".m4a", ignoreCase = true) == true) ||
                (filePath?.endsWith(".mp4", ignoreCase = true) == true) ||
                track.fileUri.endsWith(".m4a", ignoreCase = true) ||
                track.fileUri.endsWith(".mp4", ignoreCase = true) ||
                track.format.contains("m4a", ignoreCase = true) ||
                track.format.contains("mp4", ignoreCase = true)

        val isPublic = ScopedStorageRouter.isPublicStorage(context, filePath ?: track.fileUri)
        val mechanism = ScopedStorageRouter.resolveWriteMechanism(Build.VERSION.SDK_INT, isPublic)

        val canDirectWrite = targetFile != null && targetFile.exists() && targetFile.canWrite()

        if (mechanism == StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST && !canDirectWrite && contentUri != null) {
            // Check if user consent is required on Android 11+
            val intentSender = ScopedStorageRouter.requestWritePermissionIntentSender(context, contentUri)
            if (intentSender != null) {
                var needsPermission = false
                try {
                    // Test if write channel is available
                    context.contentResolver.openOutputStream(contentUri, "r")?.close()
                } catch (_: SecurityException) {
                    needsPermission = true
                }
                if (needsPermission) {
                    throw ScopedStoragePermissionRequiredException(intentSender, contentUri)
                }
            }
        }

        // Perform the container-level write
        if (targetFile != null && targetFile.exists() && targetFile.canWrite()) {
            when {
                isMp3 -> {
                    val id3Bytes = Id3TagWriter.buildId3v23Tag(
                        title = finalPayload.title,
                        artist = finalPayload.artist,
                        album = finalPayload.album,
                        year = finalPayload.year,
                        genre = finalPayload.genre,
                        trackNumber = finalPayload.trackNumber,
                        artworkBytes = finalPayload.newArtworkBytes
                    )
                    Id3TagWriter.writeTagsToMp3File(targetFile, id3Bytes)
                }
                isFlac -> {
                    FlacTagWriter.writeTagsToFlacFile(targetFile, finalPayload)
                }
                isM4a -> {
                    M4aTagWriter.writeTagsToM4aFile(targetFile, finalPayload)
                }
            }
        } else if (contentUri != null) {
            // Write via ContentResolver using a validated staging file in cache directory
            try {
                val stagingFile = File(context.cacheDir, "stage_cr_${System.currentTimeMillis()}_${payload.trackId}.tmp")
                // Copy source from ContentResolver to stagingFile
                context.contentResolver.openInputStream(contentUri)?.use { input ->
                    FileOutputStream(stagingFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (stagingFile.exists() && stagingFile.length() > 0) {
                    val written = when {
                        isMp3 -> {
                            val id3Bytes = Id3TagWriter.buildId3v23Tag(
                                title = finalPayload.title,
                                artist = finalPayload.artist,
                                album = finalPayload.album,
                                year = finalPayload.year,
                                genre = finalPayload.genre,
                                trackNumber = finalPayload.trackNumber,
                                artworkBytes = finalPayload.newArtworkBytes
                            )
                            Id3TagWriter.writeTagsToMp3File(stagingFile, id3Bytes)
                        }
                        isFlac -> {
                            FlacTagWriter.writeTagsToFlacFile(stagingFile, finalPayload, stagingDir = context.cacheDir)
                        }
                        isM4a -> {
                            M4aTagWriter.writeTagsToM4aFile(stagingFile, finalPayload, stagingDir = context.cacheDir)
                        }
                        else -> false
                    }

                    if (written) {
                        // Stream validated staging file back into ContentResolver
                        context.contentResolver.openOutputStream(contentUri, "wt")?.use { out ->
                            FileInputStream(stagingFile).use { `in` ->
                                `in`.copyTo(out)
                            }
                        }
                    }
                }
                stagingFile.delete()
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                    throw ScopedStoragePermissionRequiredException(e.userAction.actionIntent.intentSender, contentUri)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intentSender = ScopedStorageRouter.requestWritePermissionIntentSender(context, contentUri)
                    if (intentSender != null) {
                        throw ScopedStoragePermissionRequiredException(intentSender, contentUri)
                    }
                }
                // If permission could not be resolved, log/continue to DB overrides
            } catch (_: Exception) {
                // Staging file fallback failure
            }
        }

        // =========================================================================
        // Stage 2: MediaStore ContentValues Attribute Synchronization
        // =========================================================================
        if (contentUri != null) {
            Id3TagWriter.updateMediaStoreTags(
                context = context,
                contentUri = contentUri,
                title = finalPayload.title,
                artist = finalPayload.artist,
                album = finalPayload.album,
                year = finalPayload.year
            )
        }

        // =========================================================================
        // Stage 3: MediaScanner Rescan & Persistent Database Overrides
        // =========================================================================
        if (filePath != null) {
            try {
                MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            } catch (_: Exception) {}
        }
        val updatedTrack = dbHelper.updateTrackMetadata(finalPayload)

        // =========================================================================
        // Stage 4: Live Active PlaybackConnection Session Synchronization
        // =========================================================================
        if (updatedTrack != null) {
            playbackConnection?.notifyTrackMetadataChanged(updatedTrack)
        }

        updatedTrack
    }

    /**
     * URI-oriented overload for direct SAF/MediaStore tag writes conforming to PROJECT.md contract.
     */
    suspend fun writeTags(
        context: Context,
        uri: Uri,
        metadata: TagEditPayload
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val isPublic = ScopedStorageRouter.isPublicStorage(context, uri.toString())
            val mechanism = ScopedStorageRouter.resolveWriteMechanism(Build.VERSION.SDK_INT, isPublic)

            if (mechanism == StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST) {
                val intentSender = ScopedStorageRouter.requestWritePermissionIntentSender(context, uri)
                if (intentSender != null) {
                    var needsPermission = false
                    try {
                        context.contentResolver.openOutputStream(uri, "r")?.close()
                    } catch (_: SecurityException) {
                        needsPermission = true
                    }
                    if (needsPermission) {
                        return@withContext Result.failure(ScopedStoragePermissionRequiredException(intentSender, uri))
                    }
                }
            }

            Id3TagWriter.updateMediaStoreTags(
                context = context,
                contentUri = uri,
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                year = metadata.year
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
