package com.example.gobeats.core.video

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.example.gobeats.core.model.Track
import java.io.File
import java.util.Locale

data class DetectedSubtitle(
    val uri: Uri,
    val label: String,
    val language: String,
    val mimeType: String,
    val isDefault: Boolean = false
) {
    fun toSubtitleConfiguration(): MediaItem.SubtitleConfiguration {
        return MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage(language)
            .setLabel(label)
            .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
            .build()
    }
}

object SubtitleDetector {

    fun detectSubtitles(context: Context, track: Track): List<DetectedSubtitle> {
        val detected = mutableListOf<DetectedSubtitle>()
        val parsedUri = Uri.parse(track.fileUri)

        val filePath = when {
            parsedUri.scheme == ContentResolver.SCHEME_FILE -> parsedUri.path
            parsedUri.scheme == null || parsedUri.scheme == "" -> track.fileUri
            parsedUri.scheme == ContentResolver.SCHEME_CONTENT -> resolveFilePathFromContentUri(context, parsedUri)
            else -> null
        }

        if (filePath != null) {
            val videoFile = File(filePath)
            val baseName = videoFile.nameWithoutExtension
            val companions = findCompanionFiles(videoFile)
            for (file in companions) {
                val name = file.name
                val isVtt = name.endsWith(".vtt", ignoreCase = true)
                val mimeType = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                val subUri = Uri.fromFile(file)
                val (langCode, langLabel) = extractLanguageInfo(baseName, name)
                val isExactMatch = name.equals("$baseName.srt", ignoreCase = true) ||
                        name.equals("$baseName.vtt", ignoreCase = true)

                detected.add(
                    DetectedSubtitle(
                        uri = subUri,
                        label = langLabel,
                        language = langCode,
                        mimeType = mimeType,
                        isDefault = isExactMatch && detected.none { it.isDefault }
                    )
                )
            }
        }

        return detected
    }

    fun findCompanionFiles(videoFile: File): List<File> {
        val parentDir = videoFile.parentFile ?: return emptyList()
        if (!parentDir.exists() || !parentDir.isDirectory) return emptyList()
        val baseName = videoFile.nameWithoutExtension
        val files = parentDir.listFiles() ?: return emptyList()

        val results = mutableListOf<File>()
        for (file in files) {
            val name = file.name
            if (name.equals(videoFile.name, ignoreCase = true)) continue

            val isSrt = name.endsWith(".srt", ignoreCase = true)
            val isVtt = name.endsWith(".vtt", ignoreCase = true)
            if (!isSrt && !isVtt) continue

            if (name.startsWith(baseName, ignoreCase = true) ||
                name.equals("subtitles.srt", ignoreCase = true) ||
                name.equals("subtitles.vtt", ignoreCase = true) ||
                name.equals("default.srt", ignoreCase = true) ||
                name.equals("default.vtt", ignoreCase = true)
            ) {
                results.add(file)
            }
        }
        return results
    }

    fun createSubtitleConfigurationFromUri(context: Context, uri: Uri, customLabel: String? = null): DetectedSubtitle {
        val displayName = customLabel ?: getDisplayName(context, uri) ?: "External Subtitle"
        val isVtt = displayName.endsWith(".vtt", ignoreCase = true) ||
                (context.contentResolver.getType(uri)?.contains("vtt", ignoreCase = true) == true)
        val mimeType = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
        val (langCode, label) = extractLanguageInfo("", displayName)
        val finalLabel = customLabel ?: "$label (External)"

        return DetectedSubtitle(
            uri = uri,
            label = finalLabel,
            language = langCode,
            mimeType = mimeType,
            isDefault = true
        )
    }

    internal fun extractLanguageInfo(baseName: String, fileName: String): Pair<String, String> {
        val nameWithoutExt = fileName.substringBeforeLast('.', fileName)
        val remainder = if (baseName.isNotBlank() && nameWithoutExt.startsWith(baseName, ignoreCase = true)) {
            nameWithoutExt.substring(baseName.length).trimStart('.', '-', '_')
        } else {
            nameWithoutExt
        }

        val langCode = remainder.lowercase(Locale.ROOT)
        return when (langCode) {
            "en", "eng", "english" -> "en" to "English"
            "es", "spa", "spanish" -> "es" to "Spanish"
            "fr", "fra", "fre", "french" -> "fr" to "French"
            "de", "deu", "ger", "german" -> "de" to "German"
            "hi", "hin", "hindi" -> "hi" to "Hindi"
            "ja", "jpn", "japanese" -> "ja" to "Japanese"
            "zh", "zho", "chi", "chinese" -> "zh" to "Chinese"
            "ko", "kor", "korean" -> "ko" to "Korean"
            "it", "ita", "italian" -> "it" to "Italian"
            "pt", "por", "portuguese" -> "pt" to "Portuguese"
            "ru", "rus", "russian" -> "ru" to "Russian"
            "ar", "ara", "arabic" -> "ar" to "Arabic"
            else -> {
                if (remainder.isNotBlank()) {
                    remainder.take(3) to remainder.replaceFirstChar { it.uppercase() }
                } else {
                    "und" to "Subtitles"
                }
            }
        }
    }

    private fun resolveFilePathFromContentUri(context: Context, uri: Uri): String? {
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx != -1) {
                        return cursor.getString(idx)
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun getDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.lastPathSegment
        }
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return cursor.getString(idx)
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment
    }
}
