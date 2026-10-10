package com.example.gobeats.core.model

import java.io.Serializable

data class SavedPlaybackState(
    val currentTrackId: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val queueTrackIds: List<String>,
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable
