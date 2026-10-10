package com.example.gobeats.core.sync

import java.io.Serializable
import java.util.Locale

data class TransferStats(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val durationSeconds: Double
) : Serializable {
    val progressFraction: Float = if (totalBytes > 0) {
        (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }

    val speedMbPerSec: Double = if (durationSeconds > 0) {
        (bytesTransferred / (1024.0 * 1024.0)) / durationSeconds
    } else {
        0.0
    }

    fun formatSpeed(): String = String.format(Locale.US, "%.1f MB/s", speedMbPerSec)

    fun formatProgress(): String = "${(progressFraction * 100).toInt()}%"
}
