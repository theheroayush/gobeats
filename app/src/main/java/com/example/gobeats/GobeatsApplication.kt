package com.example.gobeats

import android.app.Application
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.media.MediaScanner
import com.example.gobeats.core.media.PlaybackConnection
import com.example.gobeats.data.repository.LibraryRepository
import com.example.gobeats.data.repository.PlaybackRepository
import com.example.gobeats.data.repository.PlaylistRepository

class GobeatsApplication : Application() {

    lateinit var dbHelper: GobeatsDatabaseHelper
        private set
    lateinit var mediaScanner: MediaScanner
        private set
    lateinit var playbackConnection: PlaybackConnection
        private set
    lateinit var libraryRepository: LibraryRepository
        private set
    lateinit var playlistRepository: PlaylistRepository
        private set
    lateinit var playbackRepository: PlaybackRepository
        private set
    lateinit var settingsRepository: com.example.gobeats.data.repository.SettingsRepository
        private set
    lateinit var equalizerManager: com.example.gobeats.core.audio.EqualizerManager
        private set
    lateinit var syncServer: com.example.gobeats.core.sync.GobeatsSyncServer
        private set
    lateinit var sleepTimerManager: com.example.gobeats.core.audio.SleepTimerManager
        private set
    lateinit var lyricsRepository: com.example.gobeats.core.lyrics.LyricsRepository
        private set
    lateinit var folderBrowserRepository: com.example.gobeats.feature.library.folders.FolderBrowserRepository
        private set
    lateinit var audioDeviceProfileManager: com.example.gobeats.core.audio.AudioDeviceProfileManager
        private set
    lateinit var dialogueBoostProcessor: com.example.gobeats.core.audio.DialogueBoostProcessor
        private set
    lateinit var monetizationManager: com.example.gobeats.core.billing.MonetizationManager
        private set

    override fun onCreate() {
        super.onCreate()

        monetizationManager = com.example.gobeats.core.billing.MonetizationManager(this)
        dbHelper = GobeatsDatabaseHelper.getInstance(this)
        mediaScanner = MediaScanner(this, dbHelper)
        equalizerManager = com.example.gobeats.core.audio.EqualizerManager(this)
        audioDeviceProfileManager = com.example.gobeats.core.audio.AudioDeviceProfileManager(this, dbHelper, equalizerManager)
        dialogueBoostProcessor = com.example.gobeats.core.audio.DialogueBoostProcessor(this, equalizerManager)
        playbackConnection = PlaybackConnection(this, dbHelper)
        libraryRepository = LibraryRepository(dbHelper, mediaScanner, this, playbackConnection)
        playlistRepository = PlaylistRepository(dbHelper)
        playbackRepository = PlaybackRepository(playbackConnection)
        settingsRepository = com.example.gobeats.data.repository.SettingsRepository(this)
        syncServer = com.example.gobeats.core.sync.GobeatsSyncServer(this, dbHelper)
        sleepTimerManager = com.example.gobeats.core.audio.SleepTimerManager()
        lyricsRepository = com.example.gobeats.core.lyrics.LyricsRepository(this)
        folderBrowserRepository = com.example.gobeats.feature.library.folders.FolderBrowserRepository(this, dbHelper)

        playbackConnection.onTrackEndedListener = {
            sleepTimerManager.notifyTrackEnded()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }
}
