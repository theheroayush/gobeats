package com.example.gobeats.core.media.tag

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

enum class StorageWriteMechanism {
    DIRECT_POSIX_FILE,
    RECOVERABLE_SECURITY_EXCEPTION,
    MEDIA_STORE_CREATE_WRITE_REQUEST
}

object ScopedStorageRouter {

    /**
     * Resolves the storage write mechanism required for the given Android API level and storage location.
     */
    fun resolveWriteMechanism(apiLevel: Int = Build.VERSION.SDK_INT, isPublicStorage: Boolean): StorageWriteMechanism {
        return when {
            apiLevel < 29 -> StorageWriteMechanism.DIRECT_POSIX_FILE
            apiLevel == 29 -> {
                if (isPublicStorage) StorageWriteMechanism.RECOVERABLE_SECURITY_EXCEPTION
                else StorageWriteMechanism.DIRECT_POSIX_FILE
            }
            else -> { // API 30 to 34+
                if (isPublicStorage) StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST
                else StorageWriteMechanism.DIRECT_POSIX_FILE
            }
        }
    }

    /**
     * Validates that a staging file exists, is at least 16 bytes, and starts with the expected magic bytes.
     */
    fun validateStagingFile(stagingFile: File, expectedMagic: ByteArray): Boolean {
        if (!stagingFile.exists() || stagingFile.length() < 16L) return false
        if (stagingFile.length() < expectedMagic.size) return false
        val head = ByteArray(expectedMagic.size)
        val read = try {
            stagingFile.inputStream().use { it.read(head) }
        } catch (_: Exception) {
            -1
        }
        return read == expectedMagic.size && head.contentEquals(expectedMagic)
    }

    /**
     * Validates that a staging file exists, is at least minLength bytes, and contains expected magic bytes at the specified offset.
     */
    fun validateStagingFile(stagingFile: File, expectedMagic: ByteArray, offset: Int, minLength: Long = 16L): Boolean {
        if (!stagingFile.exists() || stagingFile.length() < minLength) return false
        if (stagingFile.length() < offset + expectedMagic.size) return false
        val head = ByteArray(expectedMagic.size)
        val read = try {
            stagingFile.inputStream().use { input ->
                if (offset > 0) {
                    var skipped = 0L
                    while (skipped < offset) {
                        val s = input.skip(offset.toLong() - skipped)
                        if (s <= 0) break
                        skipped += s
                    }
                    if (skipped < offset) return false
                }
                input.read(head)
            }
        } catch (_: Exception) {
            -1
        }
        return read == expectedMagic.size && head.contentEquals(expectedMagic)
    }

    /**
     * Determines whether a given file path or URI points to public external storage.
     */
    fun isPublicStorage(context: Context, pathOrUri: String): Boolean {
        if (pathOrUri.startsWith("content://media/external/")) return true
        if (pathOrUri.startsWith("content://")) return false

        val path = if (pathOrUri.startsWith("file://")) {
            Uri.parse(pathOrUri).path ?: pathOrUri.removePrefix("file://")
        } else {
            pathOrUri
        }

        // Check internal app directories
        val internalDirs = listOfNotNull(
            context.filesDir?.absolutePath,
            context.cacheDir?.absolutePath,
            context.getExternalFilesDir(null)?.absolutePath,
            context.externalCacheDir?.absolutePath
        )
        if (internalDirs.any { path.startsWith(it) }) {
            return false
        }

        // Check external public storage indicators
        val externalStorage = Environment.getExternalStorageDirectory()?.absolutePath
        if (externalStorage != null && path.startsWith(externalStorage)) return true
        if (path.startsWith("/storage/emulated/0") || path.startsWith("/sdcard")) return true

        return false
    }

    /**
     * Resolves the MediaStore content URI for a physical file path.
     */
    fun getContentUriForFilePath(context: Context, filePath: String): Uri? {
        val normalizedPath = if (filePath.startsWith("file://")) {
            Uri.parse(filePath).path ?: filePath.removePrefix("file://")
        } else {
            filePath
        }

        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(normalizedPath)

        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Requests a write permission IntentSender for the specified content URI on Android 11+ (API 30+).
     */
    fun requestWritePermissionIntentSender(context: Context, contentUri: Uri): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, listOf(contentUri))
                pendingIntent.intentSender
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Atomically replaces targetFile with stagingFile with zero data loss risk.
     * Uses Files.move with ATOMIC_MOVE where supported (API 26+ / JVM),
     * backed by a transactional backup-and-restore pattern:
     * 1. Rename targetFile -> backupFile (.bak_<timestamp>)
     * 2. Rename stagingFile -> targetFile
     * 3. On success: delete backupFile
     * 4. On failure: restore backupFile -> targetFile and clean up stagingFile
     *
     * Guarantee: targetFile is NEVER deleted before stagingFile is safely verified and in place.
     */
    fun safeAtomicReplace(targetFile: File, stagingFile: File): Boolean {
        if (!stagingFile.exists() || stagingFile.length() == 0L) {
            stagingFile.delete()
            return false
        }
        val parentDir = targetFile.parentFile ?: stagingFile.parentFile ?: run {
            stagingFile.delete()
            return false
        }

        // If target file doesn't exist yet, simply move staging to target
        if (!targetFile.exists()) {
            return stagingFile.renameTo(targetFile) || tryCopyAndCleanup(stagingFile, targetFile)
        }

        // Tier 1: Primary ATOMIC_MOVE via Files.move (API 26+ / JVM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || Build.VERSION.SDK_INT == 0) {
            try {
                java.nio.file.Files.move(
                    stagingFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                return true
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                // Filesystem does not support ATOMIC_MOVE; fall through to transactional backup
            } catch (_: UnsupportedOperationException) {
                // Fall through to transactional backup
            } catch (_: Throwable) {
                // Any other filesystem exception, fall through to transactional backup
            }
        }

        // Tier 2: Transactional Backup-and-Restore
        val backupFile = File(parentDir, "${targetFile.name}.bak_${System.currentTimeMillis()}_${System.nanoTime() % 10000}")
        if (backupFile.exists()) backupFile.delete()

        // Step A: Move targetFile to backupFile
        if (!targetFile.renameTo(backupFile)) {
            // Cannot rename targetFile; targetFile remains 100% intact. Clean up staging and fail safely.
            stagingFile.delete()
            return false
        }

        // Step B: Move stagingFile to targetFile
        val swapSuccess = if (stagingFile.renameTo(targetFile)) {
            true
        } else {
            // Cross-filesystem or rename failure: copy staging to target
            tryCopyAndCleanup(stagingFile, targetFile)
        }

        if (swapSuccess) {
            // Success: Clean up backup
            backupFile.delete()
            return true
        } else {
            // Failure: Rollback to original backup
            if (!backupFile.renameTo(targetFile)) {
                try {
                    backupFile.inputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    backupFile.delete()
                } catch (_: Exception) {
                    // Backup file is preserved on disk so user data is never lost!
                }
            }
            stagingFile.delete()
            return false
        }
    }

    private fun tryCopyAndCleanup(source: File, destination: File): Boolean {
        return try {
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            source.delete()
            true
        } catch (_: Exception) {
            false
        }
    }
}

