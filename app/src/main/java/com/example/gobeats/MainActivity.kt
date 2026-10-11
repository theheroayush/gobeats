package com.example.gobeats

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import com.example.gobeats.core.playlist.PlaylistImporter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.gobeats.core.designsystem.components.MiniPlayerBar
import com.example.gobeats.core.media.MetadataExtractor
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.LibraryDataState
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.debounce
import com.example.gobeats.feature.devices.DevicesScreen
import com.example.gobeats.feature.home.HomeScreen
import com.example.gobeats.feature.library.AlbumDetailScreen
import com.example.gobeats.feature.library.ArtistDetailScreen
import com.example.gobeats.feature.library.LibraryScreen
import com.example.gobeats.feature.library.TagEditorBottomSheet
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.example.gobeats.feature.nowplaying.FloatingVideoMiniplayer
import com.example.gobeats.feature.nowplaying.FullscreenVideoPlayer
import com.example.gobeats.feature.nowplaying.NowPlayingBottomSheet
import com.example.gobeats.feature.nowplaying.VideoSettingsBottomSheet
import com.example.gobeats.feature.nowplaying.QueueScreen
import com.example.gobeats.feature.onboarding.FolderSetupScreen
import com.example.gobeats.feature.onboarding.SourceSelectionScreen
import com.example.gobeats.feature.onboarding.WelcomeScreen
import com.example.gobeats.feature.playlists.AddToPlaylistBottomSheet
import com.example.gobeats.feature.playlists.PlaylistDetailScreen
import com.example.gobeats.feature.playlists.PlaylistsScreen
import com.example.gobeats.feature.search.SearchScreen
import com.example.gobeats.feature.settings.SettingsScreen
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTheme
import com.example.gobeats.core.util.safePop
import com.example.gobeats.core.util.safePush
import com.example.gobeats.feature.onboarding.NameSetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Destination {
    data object Welcome : Destination()
    data object NameSetup : Destination()
    data object SourceSelection : Destination()
    data object FolderSetup : Destination()
    data object MainApp : Destination()
    data object Search : Destination()
    data object Settings : Destination()
    data object Queue : Destination()
    data class AlbumDetail(val album: Album) : Destination()
    data class ArtistDetail(val artist: Artist) : Destination()
    data class PlaylistDetail(val playlist: Playlist) : Destination()
    data class Folders(val initialPath: String? = null) : Destination()
}

class MainActivity : ComponentActivity() {

    private val incomingTrackFlow = MutableStateFlow<Track?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GobeatsApplication
        handleIncomingIntent(intent, app)

        val prefs = getSharedPreferences("gobeats_prefs", Context.MODE_PRIVATE)
        val isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        setContent {
            GobeatsTheme {
                GobeatsApp(
                    app = app,
                    initialDestination = if (isOnboardingCompleted) Destination.MainApp else Destination.Welcome,
                    incomingTrackFlow = incomingTrackFlow,
                    onOnboardingCompleted = {
                        prefs.edit().putBoolean("onboarding_completed", true).apply()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as GobeatsApplication
        handleIncomingIntent(intent, app)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val app = application as? GobeatsApplication
        val state = app?.playbackRepository?.playbackState?.value
        if (state?.isPlaying == true && state.isVideoMode && state.currentTrack?.isVideo == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val aspect = if (state.videoWidth > 0 && state.videoHeight > 0) {
                        android.util.Rational(state.videoWidth, state.videoHeight)
                    } else {
                        android.util.Rational(16, 9)
                    }
                    val validAspect = if (aspect.toFloat() in 0.418f..2.39f) aspect else android.util.Rational(16, 9)
                    val params = android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(validAspect)
                        .build()
                    enterPictureInPictureMode(params)
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?, app: GobeatsApplication) {
        if (intent == null) return
        val action = intent.action
        if (action != Intent.ACTION_VIEW && action != Intent.ACTION_SEND) return

        val uri: Uri? = if (action == Intent.ACTION_SEND) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } else {
            intent.data
        }

        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    try {
                        val takeFlags = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                        if (takeFlags != 0) {
                            contentResolver.takePersistableUriPermission(uri, takeFlags)
                        }
                    } catch (_: Exception) {}

                    var displayName = "Shared Audio"
                    var size = 0L
                    try {
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (cursor.moveToFirst()) {
                                if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: "Shared Audio"
                                if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                            }
                        }
                    } catch (_: Exception) {}

                    if (displayName == "Shared Audio") {
                        uri.lastPathSegment?.let { seg ->
                            val clean = seg.substringAfterLast('/')
                            if (clean.isNotBlank()) displayName = clean
                        }
                    }

                    val mimeType = intent.type ?: contentResolver.getType(uri)
                    val isVid = (action == Intent.ACTION_VIEW && mimeType?.startsWith("video/") == true) ||
                        MetadataExtractor.isVideoExtensionOrMime(displayName, mimeType)

                    if (displayName.endsWith(".m3u", true) || displayName.endsWith(".m3u8", true) || displayName.endsWith(".csv", true)) {
                        val baseName = displayName.substringBeforeLast('.').ifBlank { "External Playlist" }
                        val (parsedName, entries) = try {
                            contentResolver.openInputStream(uri)?.use { stream ->
                                PlaylistImporter.parseStream(stream, baseName)
                            } ?: Pair(baseName, emptyList())
                        } catch (_: Exception) {
                            Pair(baseName, emptyList())
                        }

                        if (entries.isNotEmpty()) {
                            val libraryTracks = app.libraryRepository.getAllTracks()
                            val matchRes = PlaylistImporter.matchTracks(entries, libraryTracks, parsedName)
                            matchRes.streamTracks.forEach { st ->
                                app.libraryRepository.insertOrUpdateTrack(st)
                            }
                            val created = app.playlistRepository.createPlaylist(
                                matchRes.playlistName,
                                "Opened from File Explorer (${matchRes.totalPlayableTracks.size} tracks)"
                            )
                            matchRes.totalPlayableTracks.forEach { t ->
                                app.playlistRepository.addTrack(created.id, t)
                            }

                            withContext(Dispatchers.Main) {
                                if (matchRes.totalPlayableTracks.isNotEmpty()) {
                                    val firstTrack = matchRes.totalPlayableTracks.first()
                                    app.playbackRepository.playTrack(firstTrack, matchRes.totalPlayableTracks)
                                    incomingTrackFlow.value = firstTrack
                                }
                            }
                            return@launch
                        }
                    }

                    val track = MetadataExtractor.extractMetadata(
                        context = this@MainActivity,
                        uri = uri,
                        sourceId = "source_external",
                        fileSize = size,
                        displayName = displayName,
                        hintIsVideo = isVid
                    )

                    app.libraryRepository.insertOrUpdateTrack(track)

                    withContext(Dispatchers.Main) {
                        app.playbackRepository.playTrack(track, listOf(track))
                        incomingTrackFlow.value = track
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

@Composable
fun GobeatsApp(
    app: GobeatsApplication,
    initialDestination: Destination,
    incomingTrackFlow: StateFlow<Track?>? = null,
    onOnboardingCompleted: () -> Unit
) {
    val navStack = remember { mutableStateListOf<Destination>(initialDestination) }
    val currentDestination = navStack.lastOrNull() ?: Destination.MainApp

    var selectedTab by remember { mutableIntStateOf(0) }
    var showNowPlayingSheet by remember { mutableStateOf(false) }
    var trackToEdit by remember { mutableStateOf<Track?>(null) }
    var trackToAddToPlaylist by remember { mutableStateOf<Track?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val playbackState by app.playbackRepository.playbackState.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCinemaActive = playbackState.isVideoMode && playbackState.currentTrack?.isVideo == true && (playbackState.isFullscreenCinema || isLandscape)

    var isFloatingMiniplayerDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState.currentTrack?.id) {
        isFloatingMiniplayerDismissed = false
    }

    val showFloatingMiniplayer = playbackState.isVideoMode &&
        playbackState.currentTrack?.isVideo == true &&
        !showNowPlayingSheet &&
        !isCinemaActive &&
        !isFloatingMiniplayerDismissed

    var libraryData by remember { mutableStateOf(LibraryDataState()) }
    val allTracks = libraryData.tracks
    val allVideos = libraryData.videos
    val allAlbums = libraryData.albums
    val allArtists = libraryData.artists
    val allPlaylists = libraryData.playlists
    val recentlyPlayed = libraryData.recentlyPlayed
    val recentlyAdded = libraryData.recentlyAdded

    val scope = rememberCoroutineScope()

    fun refreshLibrary() {
        scope.launch(Dispatchers.IO) {
            val tracksDef = async { app.libraryRepository.getAllTracks() }
            val videosDef = async { app.libraryRepository.getVideoTracks() }
            val albumsDef = async { app.libraryRepository.getAllAlbums() }
            val artistsDef = async { app.libraryRepository.getAllArtists() }
            val playlistsDef = async { app.playlistRepository.getAllPlaylists() }
            val recPlayedDef = async { app.libraryRepository.getRecentlyPlayedTracks() }
            val recAddedDef = async { app.libraryRepository.getRecentlyAddedTracks() }

            val newState = LibraryDataState(
                tracks = tracksDef.await(),
                videos = videosDef.await(),
                albums = albumsDef.await(),
                artists = artistsDef.await(),
                playlists = playlistsDef.await(),
                recentlyPlayed = recPlayedDef.await(),
                recentlyAdded = recAddedDef.await()
            )
            withContext(Dispatchers.Main) {
                libraryData = newState
            }
        }
    }

    val startupPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            scope.launch {
                app.libraryRepository.scanMediaStore().collect { progress ->
                    if (progress.isComplete) refreshLibrary()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasVideo = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_VIDEO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasAudio && !hasVideo) {
                startupPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_MEDIA_AUDIO,
                        android.Manifest.permission.READ_MEDIA_VIDEO
                    )
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            app.libraryRepository.cleanLegacyMockData()
            refreshLibrary()
            val hasAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_MEDIA_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (hasAudio) {
                app.libraryRepository.scanMediaStore().collect { progress ->
                    if (progress.isComplete) refreshLibrary()
                }
            }
        }
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        app.libraryRepository.dbChangeTrigger
            .debounce(300L)
            .collectLatest {
                refreshLibrary()
            }
    }

    val isScanning by app.libraryRepository.isScanning.collectAsState()
    val scanProgress by app.libraryRepository.scanProgress.collectAsState()
    val userProfile by app.settingsRepository.userProfile.collectAsState()

    LaunchedEffect(incomingTrackFlow) {
        incomingTrackFlow?.collectLatest { track ->
            if (track != null) {
                showNowPlayingSheet = true
                if (navStack.any { it is Destination.Welcome || it is Destination.NameSetup || it is Destination.SourceSelection || it is Destination.FolderSetup }) {
                    navStack.clear()
                    navStack.add(Destination.MainApp)
                }
                refreshLibrary()
            }
        }
    }

    BackHandler(enabled = navStack.size > 1) {
        navStack.safePop()
    }

    Box(modifier = Modifier.fillMaxSize().background(GobeatsObsidian)) {
        when (val dest = currentDestination) {
            is Destination.Welcome -> {
                WelcomeScreen(
                    onGetStarted = { navStack.safePush(Destination.NameSetup) },
                    onExploreFirst = {
                        onOnboardingCompleted()
                        navStack.clear()
                        navStack.add(Destination.MainApp)
                    },
                    onSkip = {
                        onOnboardingCompleted()
                        navStack.clear()
                        navStack.add(Destination.MainApp)
                    }
                )
            }
            is Destination.NameSetup -> {
                NameSetupScreen(
                    settingsRepository = app.settingsRepository,
                    onContinue = { navStack.safePush(Destination.SourceSelection) },
                    onBack = { navStack.safePop() },
                    onSkip = {
                        onOnboardingCompleted()
                        navStack.clear()
                        navStack.add(Destination.MainApp)
                    }
                )
            }
            is Destination.SourceSelection -> {
                SourceSelectionScreen(
                    syncServer = app.syncServer,
                    onContinue = { chosenSource ->
                        if (chosenSource == "computer") {
                            com.example.gobeats.core.sync.GobeatsSyncService.start(context)
                            onOnboardingCompleted()
                            navStack.clear()
                            navStack.add(Destination.MainApp)
                        } else {
                            navStack.safePush(Destination.FolderSetup)
                        }
                    },
                    onBack = { navStack.safePop() },
                    onSkip = {
                        onOnboardingCompleted()
                        navStack.clear()
                        navStack.add(Destination.MainApp)
                    }
                )
            }
            is Destination.FolderSetup -> {
                FolderSetupScreen(
                    libraryRepository = app.libraryRepository,
                    onComplete = {
                        onOnboardingCompleted()
                        navStack.clear()
                        navStack.add(Destination.MainApp)
                    },
                    onBack = { navStack.safePop() }
                )
            }
            is Destination.Folders -> {
                com.example.gobeats.feature.library.folders.FoldersScreen(
                    initialPath = dest.initialPath,
                    folderBrowserRepository = app.folderBrowserRepository,
                    playbackRepository = app.playbackRepository,
                    libraryRepository = app.libraryRepository,
                    onBack = { navStack.safePop() },
                    onTrackClick = { track, queue ->
                        app.playbackRepository.playTrack(track, queue)
                        if (track.isVideo) showNowPlayingSheet = true
                    },
                    onEditTags = { track -> trackToEdit = track },
                    onSearchClick = { navStack.add(Destination.Search) }
                )
            }
            is Destination.Search -> {
                SearchScreen(
                    libraryRepository = app.libraryRepository,
                    onTrackClick = { track, queue ->
                        app.playbackRepository.playTrack(track, queue)
                        if (track.isVideo) showNowPlayingSheet = true
                    },
                    onAlbumClick = { album ->
                        navStack.add(Destination.AlbumDetail(album))
                    },
                    onArtistClick = { artist ->
                        navStack.add(Destination.ArtistDetail(artist))
                    },
                    onPlaylistClick = { playlist ->
                        navStack.add(Destination.PlaylistDetail(playlist))
                    },
                    onPlayNext = { track ->
                        app.playbackRepository.playNextTrack(track)
                    },
                    onAddToQueue = { track ->
                        app.playbackRepository.addToQueue(track)
                    },
                    onBack = { navStack.safePop() }
                )
            }
            is Destination.Settings -> {
                SettingsScreen(
                    settingsRepository = app.settingsRepository,
                    equalizerManager = app.equalizerManager,
                    onDevicesClick = {
                        navStack.safePop()
                        selectedTab = 3
                    },
                    onSearchClick = { navStack.add(Destination.Search) },
                    sleepTimerManager = app.sleepTimerManager,
                    playbackRepository = app.playbackRepository,
                    dbHelper = app.dbHelper,
                    onLibraryRefresh = { refreshLibrary() }
                )
            }
            is Destination.Queue -> {
                QueueScreen(
                    state = playbackState,
                    onTrackClick = { track, queue ->
                        app.playbackRepository.playTrack(track, queue)
                    },
                    onToggleFavorite = { track ->
                        scope.launch { app.libraryRepository.toggleFavorite(track.id) }
                    },
                    onRemoveFromQueue = { index ->
                        app.playbackRepository.removeFromQueue(index)
                    },
                    onClearQueue = {
                        app.playbackRepository.clearQueue()
                    },
                    onMoveQueueItem = { from, to ->
                        app.playbackRepository.moveQueueItem(from, to)
                    },
                    onShuffleQueue = {
                        app.playbackRepository.shuffleQueue()
                    },
                    onSaveQueueAsPlaylist = { name ->
                        scope.launch {
                            val pl = app.playlistRepository.createPlaylist(name)
                            playbackState.queue.forEach { t ->
                                app.playlistRepository.addTrackToPlaylist(pl.id, t.id)
                            }
                            refreshLibrary()
                        }
                    },
                    onBack = { navStack.safePop() }
                )
            }
            is Destination.AlbumDetail -> {
                AlbumDetailScreen(
                    album = dest.album,
                    libraryRepository = app.libraryRepository,
                    currentTrackId = playbackState.currentTrack?.id,
                    onTrackClick = { track, queue ->
                        app.playbackRepository.playTrack(track, queue)
                        if (track.isVideo) showNowPlayingSheet = true
                    },
                    onToggleFavorite = { track ->
                        scope.launch { app.libraryRepository.toggleFavorite(track.id) }
                    },
                    onPlayNext = { track ->
                        app.playbackRepository.playNextTrack(track)
                    },
                    onAddToQueue = { track ->
                        app.playbackRepository.addToQueue(track)
                    },
                    onAddToPlaylist = { track ->
                        trackToAddToPlaylist = track
                    },
                    onEditTags = { track ->
                        trackToEdit = track
                    },
                    onBack = { navStack.safePop() }
                )
            }
            is Destination.ArtistDetail -> {
                ArtistDetailScreen(
                    artist = dest.artist,
                    libraryRepository = app.libraryRepository,
                    currentTrackId = playbackState.currentTrack?.id,
                    onTrackClick = { track, queue ->
                        app.playbackRepository.playTrack(track, queue)
                        if (track.isVideo) showNowPlayingSheet = true
                    },
                    onToggleFavorite = { track ->
                        scope.launch { app.libraryRepository.toggleFavorite(track.id) }
                    },
                    onPlayNext = { track ->
                        app.playbackRepository.playNextTrack(track)
                    },
                    onAddToQueue = { track ->
                        app.playbackRepository.addToQueue(track)
                    },
                    onAddToPlaylist = { track ->
                        trackToAddToPlaylist = track
                    },
                    onEditTags = { track ->
                        trackToEdit = track
                    },
                    onBack = { navStack.safePop() }
                )
            }
            is Destination.PlaylistDetail -> {
                PlaylistDetailScreen(
                    playlist = dest.playlist,
                    playlistRepository = app.playlistRepository,
                    allLibraryTracks = allTracks,
                    onTrackClick = { track, queue ->
                        app.playbackRepository.playTrack(track, queue)
                        if (track.isVideo) showNowPlayingSheet = true
                    },
                    onPlayNext = { track ->
                        app.playbackRepository.playNextTrack(track)
                    },
                    onAddToQueue = { track ->
                        app.playbackRepository.addToQueue(track)
                    },
                    onBack = { navStack.safePop() }
                )
            }
            is Destination.MainApp -> {
                Scaffold(
                    containerColor = GobeatsObsidian,
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GobeatsObsidian)
                                .navigationBarsPadding()
                        ) {
                            AnimatedVisibility(
                                visible = playbackState.currentTrack != null && !showFloatingMiniplayer,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(200))
                            ) {
                                MiniPlayerBar(
                                    state = playbackState,
                                    onTogglePlayPause = { app.playbackRepository.togglePlayPause() },
                                    onToggleFavorite = {
                                        playbackState.currentTrack?.let { track ->
                                            scope.launch { app.libraryRepository.toggleFavorite(track.id) }
                                        }
                                    },
                                    onClick = { showNowPlayingSheet = true }
                                )
                            }

                            NavigationBar(
                                containerColor = GobeatsSurface,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = GobeatsAmber,
                                        selectedTextColor = GobeatsAmber,
                                        unselectedIconColor = GobeatsTextMuted,
                                        unselectedTextColor = GobeatsTextMuted,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                                    label = { Text("Library", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = GobeatsAmber,
                                        selectedTextColor = GobeatsAmber,
                                        unselectedIconColor = GobeatsTextMuted,
                                        unselectedTextColor = GobeatsTextMuted,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlists") },
                                    label = { Text("Playlists", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = GobeatsAmber,
                                        selectedTextColor = GobeatsAmber,
                                        unselectedIconColor = GobeatsTextMuted,
                                        unselectedTextColor = GobeatsTextMuted,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 3,
                                    onClick = { selectedTab = 3 },
                                    icon = { Icon(Icons.Default.Devices, contentDescription = "Connect") },
                                    label = { Text("Connect", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = GobeatsAmber,
                                        selectedTextColor = GobeatsAmber,
                                        unselectedIconColor = GobeatsTextMuted,
                                        unselectedTextColor = GobeatsTextMuted,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 4,
                                    onClick = { selectedTab = 4 },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings", fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = GobeatsAmber,
                                        selectedTextColor = GobeatsAmber,
                                        unselectedIconColor = GobeatsTextMuted,
                                        unselectedTextColor = GobeatsTextMuted,
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
                            },
                            label = "MainAppTabContent"
                        ) { tab ->
                            when (tab) {
                                0 -> HomeScreen(
                                    recentlyPlayed = recentlyPlayed,
                                    recentlyAdded = recentlyAdded,
                                    albums = allAlbums,
                                    artists = allArtists,
                                    playlists = allPlaylists,
                                    currentTrackId = playbackState.currentTrack?.id,
                                    isPlaying = playbackState.isPlaying,
                                    userName = userProfile.name,
                                    isScanning = isScanning,
                                    scannedCount = scanProgress.scannedCount,
                                    onTrackClick = { track, queue ->
                                        app.playbackRepository.playTrack(track, queue)
                                        if (track.isVideo) showNowPlayingSheet = true
                                    },
                                    onAlbumClick = { album ->
                                        navStack.safePush(Destination.AlbumDetail(album))
                                    },
                                    onArtistClick = { artist ->
                                        navStack.safePush(Destination.ArtistDetail(artist))
                                    },
                                    onPlaylistClick = { playlist ->
                                        navStack.safePush(Destination.PlaylistDetail(playlist))
                                    },
                                    onCreatePlaylistClick = { selectedTab = 2 },
                                    onSeeAllPlaylists = { selectedTab = 2 },
                                    onSearchClick = { navStack.safePush(Destination.Search) },
                                    onSettingsClick = { selectedTab = 4 },
                                    onSeeAllContinue = { selectedTab = 1 },
                                    onSeeAllRecentlyPlayed = { selectedTab = 1 },
                                    onSeeAllRecentlyAdded = { selectedTab = 1 },
                                    onPlayNext = { track ->
                                        app.playbackRepository.playNextTrack(track)
                                    },
                                    onAddToQueue = { track ->
                                        app.playbackRepository.addToQueue(track)
                                    },
                                    onEditTags = { track ->
                                        trackToEdit = track
                                    }
                                )
                            1 -> LibraryScreen(
                                tracks = allTracks,
                                videos = allVideos,
                                albums = allAlbums,
                                artists = allArtists,
                                playlists = allPlaylists,
                                currentTrackId = playbackState.currentTrack?.id,
                                isPlaying = playbackState.isPlaying,
                                onFoldersCategoryClick = { navStack.safePush(Destination.Folders()) },
                                onAlbumClick = { album ->
                                    navStack.safePush(Destination.AlbumDetail(album))
                                },
                                onArtistClick = { artist ->
                                    navStack.safePush(Destination.ArtistDetail(artist))
                                },
                                onPlaylistClick = { playlist ->
                                    navStack.safePush(Destination.PlaylistDetail(playlist))
                                },
                                onSearchClick = { navStack.safePush(Destination.Search) },
                                onSeeAllRecentlyAdded = {},
                                onSeeAllPlaylists = { selectedTab = 2 },
                                onTrackClick = { track, queue ->
                                    app.playbackRepository.playTrack(track, queue)
                                    if (track.isVideo) showNowPlayingSheet = true
                                },
                                onToggleFavorite = { track ->
                                    scope.launch { app.libraryRepository.toggleFavorite(track.id) }
                                },
                                onPlayNext = { track ->
                                    app.playbackRepository.playNextTrack(track)
                                },
                                onAddToQueue = { track ->
                                    app.playbackRepository.addToQueue(track)
                                },
                                onAddToPlaylist = { track ->
                                    trackToAddToPlaylist = track
                                },
                                onOpenVideoUri = { uri ->
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            var displayName = "Video"
                                            var size = 0L
                                            try {
                                                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                context.contentResolver.takePersistableUriPermission(uri, flags)
                                            } catch (_: Exception) {}

                                            try {
                                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                                    if (cursor.moveToFirst()) {
                                                        if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: "Video"
                                                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                                                    }
                                                }
                                            } catch (_: Exception) {}

                                            val videoTrack = MetadataExtractor.extractMetadata(
                                                context = context,
                                                uri = uri,
                                                sourceId = "source_local",
                                                fileSize = size,
                                                displayName = displayName,
                                                hintIsVideo = true
                                            )

                                            app.libraryRepository.insertOrUpdateTrack(videoTrack)
                                            withContext(Dispatchers.Main) {
                                                app.playbackRepository.playTrack(videoTrack, listOf(videoTrack))
                                                showNowPlayingSheet = true
                                                refreshLibrary()
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                },
                                onEditTags = { track ->
                                    trackToEdit = track
                                },
                                onRescanClick = {
                                    scope.launch {
                                        app.libraryRepository.scanMediaStore().collect { progress ->
                                            if (progress.isComplete) {
                                                refreshLibrary()
                                            }
                                        }
                                    }
                                }
                            )
                            2 -> PlaylistsScreen(
                                playlistRepository = app.playlistRepository,
                                libraryTracks = allTracks,
                                onPlaylistClick = { playlist ->
                                    navStack.add(Destination.PlaylistDetail(playlist))
                                },
                                onFavoritesClick = {
                                    scope.launch {
                                        val favs = app.libraryRepository.getFavoriteTracks()
                                        if (favs.isNotEmpty()) {
                                            app.playbackRepository.playTrack(favs.first(), favs)
                                        }
                                    }
                                },
                                onSearchClick = { navStack.add(Destination.Search) },
                                onSeeAllRecentlyPlayed = { selectedTab = 1 }
                            )
                            3 -> DevicesScreen(
                                syncServer = app.syncServer,
                                onSettingsClick = { selectedTab = 4 }
                            )
                            4 -> SettingsScreen(
                                settingsRepository = app.settingsRepository,
                                equalizerManager = app.equalizerManager,
                                onDevicesClick = { selectedTab = 3 },
                                onSearchClick = { navStack.add(Destination.Search) },
                                sleepTimerManager = app.sleepTimerManager,
                                playbackRepository = app.playbackRepository,
                                dbHelper = app.dbHelper,
                                onLibraryRefresh = { refreshLibrary() }
                            )
                        }
                    }
                }
            }
            }
        }

        // Now Playing Fullscreen Bottom Sheet
        if (showNowPlayingSheet && playbackState.currentTrack != null) {
            NowPlayingBottomSheet(
                state = playbackState,
                onDismiss = { showNowPlayingSheet = false },
                onTogglePlayPause = { app.playbackRepository.togglePlayPause() },
                onSeek = { app.playbackRepository.seekTo(it) },
                onNext = { app.playbackRepository.playNext() },
                onPrevious = { app.playbackRepository.playPrevious() },
                onToggleShuffle = { app.playbackRepository.setShuffle(!playbackState.shuffleMode) },
                onToggleRepeat = { app.playbackRepository.toggleRepeatMode() },
                onToggleFavorite = {
                    playbackState.currentTrack?.let { track ->
                        scope.launch { app.libraryRepository.toggleFavorite(track.id) }
                    }
                },
                onOpenQueue = {
                    showNowPlayingSheet = false
                    navStack.add(Destination.Queue)
                },
                onSpeedChange = { speed ->
                    app.playbackRepository.setPlaybackSpeed(speed)
                },
                equalizerManager = app.equalizerManager,
                onRewind10s = { app.playbackRepository.seekBy(-10_000L) },
                onFastForward10s = { app.playbackRepository.seekBy(10_000L) },
                sleepTimerManager = app.sleepTimerManager,
                lyricsRepository = app.lyricsRepository,
                playbackRepository = app.playbackRepository,
                onEditTags = { trackToEdit = it }
            )
        }

        // Tag Editor Modal
        trackToEdit?.let { track ->
            TagEditorBottomSheet(
                track = track,
                onDismiss = { trackToEdit = null },
                onSave = { payload ->
                    scope.launch {
                        app.libraryRepository.updateTrackMetadata(payload)
                        refreshLibrary()
                        trackToEdit = null
                    }
                }
            )
        }

        // Add To Playlist Modal
        trackToAddToPlaylist?.let { track ->
            AddToPlaylistBottomSheet(
                track = track,
                playlistRepository = app.playlistRepository,
                onDismiss = { trackToAddToPlaylist = null },
                onAddedToPlaylist = {
                    refreshLibrary()
                }
            )
        }

        // In-App Floating Video Miniplayer Overlay
        if (showFloatingMiniplayer) {
            FloatingVideoMiniplayer(
                player = app.playbackRepository.getPlayer(),
                state = playbackState,
                onExpand = { showNowPlayingSheet = true },
                onTogglePlayPause = { app.playbackRepository.togglePlayPause() },
                onClose = {
                    isFloatingMiniplayerDismissed = true
                    app.playbackRepository.toggleVideoMode(false)
                }
            )
        }

        // Fullscreen Cinema Video Player Overlay
        if (isCinemaActive) {
            BackHandler {
                app.playbackRepository.setFullscreenCinema(false)
                if (context is android.app.Activity) {
                    context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }

            var showFullscreenSettings by remember { mutableStateOf(false) }

            FullscreenVideoPlayer(
                player = app.playbackRepository.getPlayer(),
                state = playbackState,
                onSeek = { app.playbackRepository.seekTo(it) },
                onTogglePlayPause = { app.playbackRepository.togglePlayPause() },
                onRewind10s = { app.playbackRepository.seekBy(-10_000L) },
                onFastForward10s = { app.playbackRepository.seekBy(10_000L) },
                onCycleAspectMode = {
                    val next = when (playbackState.videoAspectMode) {
                        com.example.gobeats.core.model.VideoAspectMode.FIT -> com.example.gobeats.core.model.VideoAspectMode.FILL_CROP
                        com.example.gobeats.core.model.VideoAspectMode.FILL_CROP -> com.example.gobeats.core.model.VideoAspectMode.RATIO_16_9
                        com.example.gobeats.core.model.VideoAspectMode.RATIO_16_9 -> com.example.gobeats.core.model.VideoAspectMode.STRETCH
                        com.example.gobeats.core.model.VideoAspectMode.STRETCH -> com.example.gobeats.core.model.VideoAspectMode.FIT
                    }
                    app.playbackRepository.setVideoAspectMode(next)
                },
                onExitFullscreen = {
                    app.playbackRepository.setFullscreenCinema(false)
                    if (context is android.app.Activity) {
                        context.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                },
                onOpenSettings = { showFullscreenSettings = true },
                onStart2xSpeed = { app.playbackRepository.start2xSpeedPreview() },
                onStop2xSpeed = { app.playbackRepository.stop2xSpeedPreview() },
                onPinchZoomIn = { app.playbackRepository.setVideoAspectMode(com.example.gobeats.core.model.VideoAspectMode.FILL_CROP) },
                onPinchZoomOut = { app.playbackRepository.setVideoAspectMode(com.example.gobeats.core.model.VideoAspectMode.FIT) },
                onSetTouchLocked = { app.playbackRepository.setTouchLocked(it) },
                onToggleSubtitles = {
                    val nextSub = if (playbackState.selectedSubtitleTrackIndex == -1 && playbackState.subtitleTracks.isNotEmpty()) 0 else -1
                    app.playbackRepository.selectSubtitleTrack(nextSub)
                },
                onCloseStats = { app.playbackRepository.toggleStatsForNerds() },
                onVideoLoudnessBoostChange = { app.playbackRepository.setVideoLoudnessBoost(it) },
                onResumeSavedPosition = { app.playbackRepository.resumeFromSavedPosition() },
                onDismissResumePrompt = { app.playbackRepository.dismissResumePrompt() }
            )

            if (showFullscreenSettings) {
                VideoSettingsBottomSheet(
                    state = playbackState,
                    onDismiss = { showFullscreenSettings = false },
                    onSelectQuality = { app.playbackRepository.selectVideoQuality(it) },
                    onSelectSpeed = { app.playbackRepository.setPlaybackSpeed(it) },
                    onSelectAudioTrack = { app.playbackRepository.selectAudioTrack(it) },
                    onSelectSubtitleTrack = { app.playbackRepository.selectSubtitleTrack(it) },
                    onSelectAspectMode = { app.playbackRepository.setVideoAspectMode(it) },
                    onToggleLoop = { app.playbackRepository.toggleVideoLoop() },
                    onToggleStatsForNerds = { app.playbackRepository.toggleStatsForNerds() },
                    onOpenSleepTimer = {},
                    onSelectLoudnessBoost = { app.playbackRepository.setVideoLoudnessBoost(it) },
                    onPickExternalSubtitle = { app.playbackRepository.addExternalSubtitle(it) }
                )
            }
        }
    }
}
