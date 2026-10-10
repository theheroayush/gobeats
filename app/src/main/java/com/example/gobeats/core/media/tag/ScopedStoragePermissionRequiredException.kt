package com.example.gobeats.core.media.tag

import android.content.IntentSender
import android.net.Uri

/**
 * Thrown when modifying a media item in public/scoped storage on Android 10+ (API 29+)
 * requires user consent via RecoverableSecurityException or MediaStore.createWriteRequest.
 */
class ScopedStoragePermissionRequiredException(
    val intentSender: IntentSender,
    val contentUri: Uri,
    override val message: String = "Scoped storage write permission required for media URI: $contentUri"
) : SecurityException(message)
