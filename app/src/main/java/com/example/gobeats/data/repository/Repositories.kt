package com.example.gobeats.data.repository

import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.media.MediaScanner
import com.example.gobeats.core.media.PlaybackConnection
import com.example.gobeats.core.media.ScanProgress
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

import android.content.Context
import android.net.Uri
import com.example.gobeats.core.media.Id3TagWriter
import com.example.gobeats.core.media.TagEditPayload
import com.example.gobeats.core.media.UniversalTagWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LibraryRepository(
    private val dbHelper: GobeatsDatabaseHelper,
    private val mediaScanner: MediaScanner,
    private val context: Context? = null,
    private val playbackConnection: PlaybackConnection? = null
) {
    val dbChangeTrigger: Flow<Unit> = dbHelper.dbChangeTrigger

    suspend fun getAllTracks(): List<Track> = dbHelper.getAllTracks()

    suspend fun getRecentlyAddedTracks(limit: Int = 20): List<Track> =
        dbHelper.getRecentlyAddedTracks(limit)

    suspend fun getFavoriteTracks(): List<Track> = dbHelper.getFavoriteTracks()

    suspend fun getTracksByAlbum(albumId: String): List<Track> = dbHelper.getTracksByAlbum(albumId)

    suspend fun getTracksByArtist(artistId: String): List<Track> = dbHelper.getTracksByArtist(artistId)

    suspend fun searchTracks(query: String): List<Track> = dbHelper.searchTracks(query)

    suspend fun toggleFavorite(trackId: String): Boolean = dbHelper.toggleFavorite(trackId)

    suspend fun getAllAlbums(): List<Album> = dbHelper.getAllAlbums()

    suspend fun getAllArtists(): List<Artist> = dbHelper.getAllArtists()

    suspend fun getAllPlaylists(): List<Playlist> = dbHelper.getAllPlaylists()

    suspend fun getRecentlyPlayedTracks(limit: Int = 10): List<Track> =
        dbHelper.getRecentlyPlayedTracks(limit)

    suspend fun getVideoTracks(): List<Track> = dbHelper.getVideoTracks()

    private val _isScanning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanProgress = kotlinx.coroutines.flow.MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress

    fun scanMediaStore(): Flow<ScanProgress> = kotlinx.coroutines.flow.flow {
        _isScanning.value = true
        try {
            mediaScanner.scanMediaStore().collect { progress ->
                _scanProgress.value = progress
                emit(progress)
                if (progress.isComplete) {
                    _isScanning.value = false
                }
            }
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun cleanLegacyMockData() = mediaScanner.cleanLegacyMockData()

    suspend fun insertOrUpdateTrack(track: Track) = dbHelper.insertOrUpdateTrack(track)

    suspend fun updateTrackMetadata(payload: TagEditPayload): Track? = withContext(Dispatchers.IO) {
        val ctx = context
        val track = if (ctx != null) dbHelper.getTrackById(payload.trackId) else null

        if (ctx != null && track != null) {
            UniversalTagWriter.writeTags(
                context = ctx,
                track = track,
                payload = payload,
                dbHelper = dbHelper,
                playbackConnection = playbackConnection
            )
        } else {
            var finalPayload = payload
            if (ctx != null && payload.newArtworkBytes != null) {
                val savedUri = Id3TagWriter.saveArtworkToStorage(ctx, payload.newArtworkBytes, payload.trackId)
                if (savedUri != null) {
                    finalPayload = finalPayload.copy(artworkUri = savedUri)
                }
            }
            val updated = dbHelper.updateTrackMetadata(finalPayload)
            if (updated != null) {
                playbackConnection?.notifyTrackMetadataChanged(updated)
            }
            updated
        }
    }
}

class PlaylistRepository(
    private val dbHelper: GobeatsDatabaseHelper
) {
    suspend fun getAllPlaylists(): List<Playlist> = dbHelper.getAllPlaylists()

    suspend fun createPlaylist(name: String, description: String? = null): Playlist =
        dbHelper.createPlaylist(name, description)

    suspend fun deletePlaylist(playlistId: String) =
        dbHelper.deletePlaylist(playlistId)

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) =
        dbHelper.removeTrackFromPlaylist(playlistId, trackId)

    suspend fun seedDefaultPlaylistsIfEmpty() =
        dbHelper.seedDefaultPlaylistsIfEmpty()

    suspend fun insertOrUpdateTrack(track: Track) =
        dbHelper.insertOrUpdateTrack(track)

    suspend fun addTrack(playlistId: String, track: Track) {
        dbHelper.insertOrUpdateTrack(track)
        dbHelper.addTrackToPlaylist(playlistId, track.id)
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) =
        dbHelper.addTrackToPlaylist(playlistId, trackId)

    suspend fun getPlaylistTracks(playlistId: String): List<Track> =
        dbHelper.getPlaylistTracks(playlistId)

    suspend fun getSmartPlaylists(): List<Playlist> =
        dbHelper.getSmartPlaylists()
}

class PlaybackRepository(
    private val playbackConnection: PlaybackConnection
) {
    val playbackState: StateFlow<PlaybackUiState> = playbackConnection.playbackState
    val audioSessionId: StateFlow<Int> = playbackConnection.audioSessionId

    fun getPlayer() = playbackConnection.getPlayer()

    fun toggleVideoMode(enabled: Boolean) {
        playbackConnection.toggleVideoMode(enabled)
    }

    fun setVideoAspectMode(mode: com.example.gobeats.core.model.VideoAspectMode) {
        playbackConnection.setVideoAspectMode(mode)
    }

    fun selectAudioTrack(index: Int) {
        playbackConnection.selectAudioTrack(index)
    }

    fun selectSubtitleTrack(index: Int) {
        playbackConnection.selectSubtitleTrack(index)
    }

    fun selectVideoQuality(optionId: String) {
        playbackConnection.selectVideoQuality(optionId)
    }

    fun setVideoLoudnessBoost(percent: Int) {
        playbackConnection.setVideoLoudnessBoost(percent)
    }

    fun addExternalSubtitle(uri: Uri, customLabel: String? = null) {
        playbackConnection.addExternalSubtitle(uri, customLabel)
    }

    fun setPrimarySubtitleTrack(track: com.example.gobeats.core.model.DualSubtitleTrack?) {
        playbackConnection.setPrimarySubtitleTrack(track)
    }

    fun setSecondarySubtitleTrack(track: com.example.gobeats.core.model.DualSubtitleTrack?) {
        playbackConnection.setSecondarySubtitleTrack(track)
    }

    fun loadAiSubtitleTrack(track: Track, languageCode: String, isPrimary: Boolean = true) {
        playbackConnection.loadAiSubtitleTrack(track, languageCode, isPrimary)
    }

    fun setDialogueBoostMode(mode: com.example.gobeats.core.model.DialogueBoostMode) {
        playbackConnection.setDialogueBoostMode(mode)
    }

    fun setAvDelay(delayMs: Long) {
        playbackConnection.setAvDelay(delayMs)
    }

    fun skipIntro() {
        playbackConnection.skipIntro()
    }

    fun resumeFromSavedPosition() {
        playbackConnection.resumeFromSavedPosition()
    }

    fun dismissResumePrompt() {
        playbackConnection.dismissResumePrompt()
    }

    fun start2xSpeedPreview() {
        playbackConnection.start2xSpeedPreview()
    }

    fun stop2xSpeedPreview() {
        playbackConnection.stop2xSpeedPreview()
    }

    fun setTouchLocked(locked: Boolean) {
        playbackConnection.setTouchLocked(locked)
    }

    fun setFullscreenCinema(fullscreen: Boolean) {
        playbackConnection.setFullscreenCinema(fullscreen)
    }

    fun toggleVideoLoop() {
        playbackConnection.toggleVideoLoop()
    }

    fun toggleStatsForNerds() {
        playbackConnection.toggleStatsForNerds()
    }

    fun playTrack(track: Track, queue: List<Track>) {
        playbackConnection.playTrack(track, queue)
    }

    fun togglePlayPause() {
        playbackConnection.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playbackConnection.seekTo(positionMs)
    }

    fun playNext() {
        playbackConnection.playNext()
    }

    fun playPrevious() {
        playbackConnection.playPrevious()
    }

    fun setShuffle(enabled: Boolean) {
        playbackConnection.setShuffle(enabled)
    }

    fun toggleRepeatMode() {
        playbackConnection.toggleRepeatMode()
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackConnection.setPlaybackSpeed(speed)
    }

    fun playNextTrack(track: Track) {
        playbackConnection.playNext(track)
    }

    fun addToQueue(track: Track) {
        playbackConnection.addToQueue(track)
    }

    fun removeFromQueue(index: Int) {
        playbackConnection.removeFromQueue(index)
    }

    fun clearQueue() {
        playbackConnection.clearQueue()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playbackConnection.moveQueueItem(fromIndex, toIndex)
    }

    fun shuffleQueue() {
        playbackConnection.shuffleQueue()
    }

    fun seekBy(offsetMs: Long) {
        playbackConnection.seekBy(offsetMs)
    }

    fun setVolume(volume: Float) {
        playbackConnection.setVolume(volume)
    }

    fun setCrossfadeDuration(seconds: Int) {
        playbackConnection.setCrossfadeDuration(seconds)
    }

    fun setGaplessPlayback(enabled: Boolean) {
        playbackConnection.setGaplessPlayback(enabled)
    }

    fun persistPlaybackState() {
        playbackConnection.persistCurrentPlaybackState()
    }
}

data class UserProfile(
    val name: String = "Music Lover",
    val email: String = "gobeats@local.audio",
    val avatarUri: String? = null
)

data class AppSettings(
    val audioQuality: String = "High (320 kbps)",
    val theme: String = "Dark",
    val appIconStyle: String = "neon_purple",
    val syncLibraryEnabled: Boolean = true,
    val crossfadeDurationSec: Int = 0,
    val gaplessPlayback: Boolean = true
)

class SettingsRepository(private val context: android.content.Context) {
    private val prefs = context.getSharedPreferences("gobeats_app_settings", android.content.Context.MODE_PRIVATE)

    private val _userProfile = kotlinx.coroutines.flow.MutableStateFlow(
        UserProfile(
            name = prefs.getString("profile_name", "Ayush") ?: "Ayush",
            email = prefs.getString("profile_email", "ayush@example.com") ?: "ayush@example.com"
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile

    private val _settings = kotlinx.coroutines.flow.MutableStateFlow(
        AppSettings(
            audioQuality = prefs.getString("audio_quality", "High (320 kbps)") ?: "High (320 kbps)",
            theme = prefs.getString("app_theme", "Dark") ?: "Dark",
            appIconStyle = prefs.getString("app_icon_style", "neon_purple") ?: "neon_purple",
            syncLibraryEnabled = prefs.getBoolean("sync_library", true),
            crossfadeDurationSec = prefs.getInt("crossfade_sec", 0),
            gaplessPlayback = prefs.getBoolean("gapless", true)
        )
    )
    val settings: StateFlow<AppSettings> = _settings

    fun setUserName(name: String) {
        val cleanName = name.trim().ifBlank { "Music Lover" }
        prefs.edit().putString("profile_name", cleanName).apply()
        _userProfile.value = _userProfile.value.copy(name = cleanName)
    }

    fun updateProfile(name: String, email: String) {
        prefs.edit().putString("profile_name", name).putString("profile_email", email).apply()
        _userProfile.value = _userProfile.value.copy(name = name, email = email)
    }

    fun setAudioQuality(quality: String) {
        prefs.edit().putString("audio_quality", quality).apply()
        _settings.value = _settings.value.copy(audioQuality = quality)
    }

    fun setTheme(theme: String) {
        prefs.edit().putString("app_theme", theme).apply()
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun setAppIconStyle(style: String) {
        prefs.edit().putString("app_icon_style", style).apply()
        _settings.value = _settings.value.copy(appIconStyle = style)
        com.example.gobeats.core.util.AppIconManager.setAppIcon(context, style)
    }

    fun toggleSyncLibrary(enabled: Boolean) {
        prefs.edit().putBoolean("sync_library", enabled).apply()
        _settings.value = _settings.value.copy(syncLibraryEnabled = enabled)
    }

    fun setPlaybackSettings(crossfadeSec: Int, gapless: Boolean) {
        prefs.edit().putInt("crossfade_sec", crossfadeSec).putBoolean("gapless", gapless).apply()
        _settings.value = _settings.value.copy(crossfadeDurationSec = crossfadeSec, gaplessPlayback = gapless)
    }
}

