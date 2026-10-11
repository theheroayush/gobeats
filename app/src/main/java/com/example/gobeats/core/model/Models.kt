package com.example.gobeats.core.model

import java.io.Serializable

data class Track(
    val id: String,
    val sourceId: String,
    val fileUri: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val durationMs: Long,
    val artistId: String? = null,
    val albumId: String? = null,
    val trackNumber: Int = 1,
    val discNumber: Int = 1,
    val genre: String? = null,
    val year: Int? = null,
    val format: String = "MP3",
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val fileSize: Long = 0,
    val artworkUri: String? = null,
    val isAvailable: Boolean = true,
    val isFavorite: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val isVideo: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
) : Serializable

data class Album(
    val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String,
    val year: Int? = null,
    val artworkUri: String? = null,
    val trackCount: Int = 0
) : Serializable

data class Artist(
    val id: String,
    val name: String,
    val sortName: String? = null,
    val artworkUri: String? = null,
    val trackCount: Int = 0
) : Serializable

data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val artworkUri: String? = null,
    val isSmart: Boolean = false,
    val trackCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

@androidx.compose.runtime.Immutable
data class LibraryDataState(
    val tracks: List<Track> = emptyList(),
    val videos: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val recentlyPlayed: List<Track> = emptyList(),
    val recentlyAdded: List<Track> = emptyList()
) : Serializable

enum class SourceType {
    LOCAL_STORAGE,
    DESKTOP_CONNECT,
    PERSONAL_SERVER,
    STREAMING_SERVICE
}

data class MusicSource(
    val id: String,
    val type: SourceType,
    val name: String,
    val rootUri: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class VideoAspectMode {
    FIT,
    FILL_CROP,
    STRETCH,
    RATIO_16_9
}

data class VideoQualityOption(
    val id: String,
    val label: String,
    val resolutionLabel: String,
    val frameRate: Float = 0f,
    val bitrate: Int = 0,
    val isSelected: Boolean = false,
    val isAuto: Boolean = false,
    val maxWidth: Int = Int.MAX_VALUE,
    val maxHeight: Int = Int.MAX_VALUE
)

data class VideoPlaybackStats(
    val resolution: String = "",
    val videoCodec: String = "",
    val colorStandard: String = "",
    val videoBitrateKbps: Int = 0,
    val decoderName: String = "",
    val audioCodec: String = "",
    val droppedFrames: Long = 0L,
    val totalFrames: Long = 0L,
    val bufferHealthMs: Long = 0L
)

data class PlaybackUiState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleMode: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val isVideoMode: Boolean = false,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoAspectMode: VideoAspectMode = VideoAspectMode.FIT,
    val audioTracks: List<String> = emptyList(),
    val selectedAudioTrackIndex: Int = 0,
    val subtitleTracks: List<String> = emptyList(),
    val selectedSubtitleTrackIndex: Int = -1,
    val videoQualityOptions: List<VideoQualityOption> = emptyList(),
    val selectedVideoQualityId: String = "auto",
    val is2xSpeedPreviewActive: Boolean = false,
    val isTouchLocked: Boolean = false,
    val isFullscreenCinema: Boolean = false,
    val showStatsForNerds: Boolean = false,
    val isVideoLoopEnabled: Boolean = false,
    val videoStats: VideoPlaybackStats? = null,
    val videoLoudnessBoostPercent: Int = 100,
    val isSingleRenditionLocalFile: Boolean = false,
    val resumePrompt: VideoResumePrompt? = null,
    val crossfadeDurationSec: Int = 0,
    val gaplessPlayback: Boolean = true,
    val isCrossfadeActive: Boolean = false,
    val dialogueBoostMode: DialogueBoostMode = DialogueBoostMode.OFF,
    val avDelayMs: Long = 0L,
    val primarySubtitleTrack: DualSubtitleTrack? = null,
    val secondarySubtitleTrack: DualSubtitleTrack? = null,
    val activePrimarySubtitleText: String? = null,
    val activeSecondarySubtitleText: String? = null,
    val activeSkipIntroSegment: SkipIntroSegment? = null,
    val isSkipIntroVisible: Boolean = false,
    val currentAudioDeviceProfile: DeviceAudioProfile? = null
)

data class VideoResumePrompt(
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long
)

enum class DeviceAudioProfileType {
    EARBUDS,
    HEADPHONES,
    CAR_AUDIO,
    SPEAKER
}

data class DeviceAudioProfile(
    val deviceId: String,
    val name: String,
    val type: DeviceAudioProfileType,
    val preferredEqPreset: String = "Studio Reference",
    val avDelayMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

enum class DialogueBoostMode {
    OFF,
    CLEAR,
    MAXIMUM
}

data class SkipIntroSegment(
    val startMs: Long = 0L,
    val endMs: Long = 85_000L,
    val label: String = "Opening"
) : Serializable

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
) : Serializable

data class DualSubtitleTrack(
    val id: String,
    val label: String,
    val languageCode: String,
    val isAiGenerated: Boolean = false,
    val cues: List<SubtitleCue> = emptyList()
) : Serializable


