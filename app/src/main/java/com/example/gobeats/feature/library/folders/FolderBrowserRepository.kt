package com.example.gobeats.feature.library.folders

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FolderItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val itemCount: Int = 0,
    val lastModified: Long = 0L,
    val track: Track? = null
)

data class StorageRoot(
    val name: String,
    val path: String,
    val isAvailable: Boolean = true
)

class FolderBrowserRepository(
    private val context: Context,
    private val dbHelper: GobeatsDatabaseHelper
) {

    fun getDefaultRootPath(): String {
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (musicDir != null && musicDir.exists() && musicDir.canRead()) {
            return musicDir.absolutePath
        }
        val extStorage = Environment.getExternalStorageDirectory()
        if (extStorage != null && extStorage.exists()) {
            return extStorage.absolutePath
        }
        return "/storage/emulated/0/Music"
    }

    fun getStorageRoots(): List<StorageRoot> {
        val roots = mutableListOf<StorageRoot>()
        val musicPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath
            ?: "/storage/emulated/0/Music"
        val internalPath = Environment.getExternalStorageDirectory()?.absolutePath
            ?: "/storage/emulated/0"
        val downloadsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: "/storage/emulated/0/Download"

        roots.add(StorageRoot("Music", musicPath, File(musicPath).exists()))
        roots.add(StorageRoot("Internal Storage", internalPath, File(internalPath).exists()))
        roots.add(StorageRoot("Downloads", downloadsPath, File(downloadsPath).exists()))

        // Discover external SD card roots if available
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            for (dir in externalDirs) {
                if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                    val rootPath = dir.absolutePath.substringBefore("/Android")
                    if (rootPath.isNotBlank() && !roots.any { it.path == rootPath }) {
                        roots.add(StorageRoot("SD Card", rootPath, File(rootPath).canRead()))
                    }
                }
            }
        } catch (_: Exception) {}

        return roots
    }

    suspend fun listDirectory(path: String): List<FolderItem> = withContext(Dispatchers.IO) {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
            return@withContext emptyList()
        }

        val allFiles = dir.listFiles() ?: return@withContext emptyList()
        val allDbTracks = try {
            dbHelper.getAllTracks()
        } catch (_: Exception) {
            emptyList()
        }

        val dbTrackMap = mutableMapOf<String, Track>()
        for (t in allDbTracks) {
            val fileUri = t.fileUri
            val rawPath = if (fileUri.startsWith("file://")) {
                Uri.parse(fileUri).path
            } else if (fileUri.startsWith("/")) {
                fileUri
            } else {
                null
            }
            if (rawPath != null) {
                dbTrackMap[rawPath] = t
            }
            dbTrackMap[t.title.lowercase()] = t
        }

        val directories = mutableListOf<FolderItem>()
        val audioItems = mutableListOf<FolderItem>()

        for (file in allFiles) {
            if (file.name.startsWith(".")) continue

            if (file.isDirectory) {
                val childCount = file.listFiles()?.count { child ->
                    !child.name.startsWith(".") &&
                            (child.isDirectory || FolderBrowserEngine.isSupportedAudioFile(child.name))
                } ?: 0

                directories.add(
                    FolderItem(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = true,
                        itemCount = childCount,
                        lastModified = file.lastModified()
                    )
                )
            } else if (FolderBrowserEngine.isSupportedAudioFile(file.name)) {
                val matchedTrack = dbTrackMap[file.absolutePath]
                    ?: dbTrackMap[file.nameWithoutExtension.lowercase()]
                    ?: Track(
                        id = "file_" + file.absolutePath.hashCode().toString(),
                        sourceId = "folder",
                        fileUri = Uri.fromFile(file).toString(),
                        title = file.nameWithoutExtension,
                        artistName = dir.name.ifBlank { "Unknown Artist" },
                        albumTitle = dir.name.ifBlank { "Unknown Album" },
                        durationMs = 0L,
                        artworkUri = null,
                        format = file.extension.uppercase(),
                        bitrate = 0,
                        sampleRate = 44100,
                        dateAdded = file.lastModified() / 1000L,
                        fileSize = file.length()
                    )

                audioItems.add(
                    FolderItem(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = false,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        track = matchedTrack
                    )
                )
            }
        }

        val sortedDirs = directories.sortedBy { it.name.lowercase() }
        val sortedFiles = audioItems.sortedBy { it.name.lowercase() }

        sortedDirs + sortedFiles
    }

    suspend fun getFolderTracks(folderPath: String): List<Track> = withContext(Dispatchers.IO) {
        val items = listDirectory(folderPath)
        items.filter { !it.isDirectory && it.track != null }.mapNotNull { it.track }
    }

    suspend fun getRecursiveFolderTracks(folderPath: String): List<Track> = withContext(Dispatchers.IO) {
        val dir = File(folderPath)
        if (!dir.exists() || !dir.isDirectory || !dir.canRead()) {
            return@withContext emptyList()
        }

        val allDbTracks = try {
            dbHelper.getAllTracks()
        } catch (_: Exception) {
            emptyList()
        }

        val dbTrackMap = mutableMapOf<String, Track>()
        for (t in allDbTracks) {
            val fileUri = t.fileUri
            val rawPath = if (fileUri.startsWith("file://")) {
                Uri.parse(fileUri).path
            } else if (fileUri.startsWith("/")) {
                fileUri
            } else {
                null
            }
            if (rawPath != null) {
                dbTrackMap[rawPath] = t
            }
            dbTrackMap[t.title.lowercase()] = t
        }

        val tracks = mutableListOf<Track>()
        dir.walkTopDown().forEach { file ->
            if (file.isFile && !file.name.startsWith(".") && FolderBrowserEngine.isSupportedAudioFile(file.name)) {
                val matched = dbTrackMap[file.absolutePath]
                    ?: dbTrackMap[file.nameWithoutExtension.lowercase()]
                    ?: Track(
                        id = "file_" + file.absolutePath.hashCode().toString(),
                        sourceId = "folder",
                        fileUri = Uri.fromFile(file).toString(),
                        title = file.nameWithoutExtension,
                        artistName = file.parentFile?.name ?: "Unknown Artist",
                        albumTitle = file.parentFile?.name ?: "Unknown Album",
                        durationMs = 0L,
                        artworkUri = null,
                        format = file.extension.uppercase(),
                        bitrate = 0,
                        sampleRate = 44100,
                        dateAdded = file.lastModified() / 1000L,
                        fileSize = file.length()
                    )
                tracks.add(matched)
            }
        }

        tracks.sortedBy { it.title.lowercase() }
    }
}
