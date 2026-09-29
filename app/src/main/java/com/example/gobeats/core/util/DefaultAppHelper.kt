package com.example.gobeats.core.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

object DefaultAppHelper {

    /**
     * Checks if gobeats is currently designated as the default music/audio player.
     */
    fun isGobeatsDefaultMusicPlayer(context: Context): Boolean {
        return try {
            val sampleAudioIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://media/external/audio/media/1"), "audio/*")
            }
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    sampleAudioIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(sampleAudioIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Launches the system dialog or settings page to allow the user to designate
     * gobeats as their default player for music and videos.
     */
    fun openDefaultAppsSettings(context: Context) {
        // Option 1: Launch system Default Apps settings screen
        try {
            val manageDefaultAppsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(manageDefaultAppsIntent)
            return
        } catch (_: Exception) {}

        // Option 2: Launch "Open by default" app links settings for gobeats
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val openByDefaultIntent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(openByDefaultIntent)
                return
            } catch (_: Exception) {}
        }

        // Option 3: Launch Application Details Settings
        try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetailsIntent)
        } catch (_: Exception) {}
    }

    /**
     * Returns a human-readable list of all supported media file formats.
     */
    fun getSupportedFormatsDescription(): String {
        return "Audio: MP3, FLAC, WAV, AAC, M4A, OGG, OPUS • Video: MP4, MKV, WEBM, 3GP"
    }
}
