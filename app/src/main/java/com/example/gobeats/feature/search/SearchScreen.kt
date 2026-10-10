package com.example.gobeats.feature.search

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.formatDuration
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.Track
import com.example.gobeats.data.repository.LibraryRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    libraryRepository: LibraryRepository,
    playbackState: PlaybackUiState,
    onTrackClick: (Track, List<Track>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var allTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var allAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var allArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var allPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Songs", "Videos", "Albums", "Artists", "Playlists", "Genres")

    val prefs = remember { context.getSharedPreferences("gobeats_search_history", Context.MODE_PRIVATE) }
    val recentSearches = remember {
        mutableStateListOf<String>().apply {
            val saved = prefs.getString("history", null)?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
            addAll(saved)
        }
    }

    fun saveRecentSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.length < 2) return
        recentSearches.remove(trimmed)
        recentSearches.add(0, trimmed)
        while (recentSearches.size > 8) {
            recentSearches.removeAt(recentSearches.lastIndex)
        }
        prefs.edit().putString("history", recentSearches.joinToString("||")).apply()
    }

    fun clearRecentSearches() {
        recentSearches.clear()
        prefs.edit().remove("history").apply()
    }

    LaunchedEffect(Unit) {
        allTracks = libraryRepository.getAllTracks()
        allAlbums = libraryRepository.getAllAlbums()
        allArtists = libraryRepository.getAllArtists()
        allPlaylists = libraryRepository.getAllPlaylists()
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
        } else {
            delay(200)
            searchResults = libraryRepository.searchTracks(searchQuery.trim())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GobeatsTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "gobeats",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "YOUR MUSIC. YOUR RULES.",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsTextMuted,
                        letterSpacing = 1.4.sp
                    )
                }
            }

            Text(
                text = "Find what moves you.",
                fontSize = 12.sp,
                color = GobeatsTextSecondary
            )
        }

        // Search Input Bar
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = Color(0xFF16151A),
            border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = GobeatsTextMuted,
                    modifier = Modifier.size(20.dp)
                )

                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(
                        color = GobeatsTextPrimary,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(GobeatsAmber),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search songs, videos, artists, albums, playlists...",
                                fontSize = 13.sp,
                                color = GobeatsTextMuted
                            )
                        }
                        innerTextField()
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = GobeatsTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Filter Chips (All, Songs, Videos, Albums, Artists, Playlists, Genres)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters.size) { index ->
                val isSelected = selectedFilterIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (isSelected) Color.White else Color(0xFF1A191F))
                        .clickable { selectedFilterIndex = index }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filters[index],
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else GobeatsTextSecondary
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Section: Recent searches
            if (searchQuery.isEmpty() && recentSearches.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent searches",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsTextPrimary
                        )
                        Text(
                            text = "Clear all",
                            fontSize = 12.sp,
                            color = GobeatsTextMuted,
                            modifier = Modifier.clickable { clearRecentSearches() }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recentSearches) { tag ->
                            Surface(
                                shape = RoundedCornerShape(percent = 50),
                                color = Color(0xFF18171E),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                                modifier = Modifier.clickable { searchQuery = tag }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = GobeatsTextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tag,
                                        fontSize = 12.sp,
                                        color = GobeatsTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top results header
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (searchQuery.isEmpty()) "Top results in your library" else "Results",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            val activeFilter = filters[selectedFilterIndex]

            if (searchQuery.isNotEmpty()) {
                val q = searchQuery.trim().lowercase()

                // Direct HTTP/HTTPS Stream URL result
                if (q.startsWith("http://") || q.startsWith("https://")) {
                    item {
                        val cleanUrl = searchQuery.trim()
                        val streamName = cleanUrl.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifBlank { "Online Stream" }
                        val isVideo = cleanUrl.endsWith(".mp4", true) || cleanUrl.endsWith(".mkv", true) || cleanUrl.endsWith(".webm", true) || cleanUrl.endsWith(".m3u8", true)
                        SearchResultRow(
                            title = "Play Stream: $streamName",
                            subtitle = cleanUrl,
                            badge = if (isVideo) "Online Video" else "Online Stream",
                            isCircular = false,
                            onClick = {
                                saveRecentSearch(cleanUrl)
                                val streamTrack = Track(
                                    id = "stream_" + cleanUrl.hashCode().toUInt().toString(16),
                                    sourceId = "source_internet",
                                    fileUri = cleanUrl,
                                    title = streamName,
                                    artistName = "Internet Stream",
                                    albumTitle = "Online Streams",
                                    durationMs = 0L,
                                    format = if (isVideo) "MP4" else "STREAM",
                                    bitrate = 320,
                                    sampleRate = 44100,
                                    isVideo = isVideo
                                )
                                onTrackClick(streamTrack, listOf(streamTrack))
                            }
                        )
                    }
                }

                // Filter matching items based on chip
                val songResults = if (activeFilter in listOf("All", "Songs")) {
                    searchResults.filter { !it.isVideo }
                } else emptyList()

                val videoResults = if (activeFilter in listOf("All", "Videos")) {
                    (searchResults.filter { it.isVideo } +
                        allTracks.filter { it.isVideo && (it.title.lowercase().contains(q) || it.artistName.lowercase().contains(q)) }
                    ).distinctBy { it.id }
                } else emptyList()

                val albumResults = if (activeFilter in listOf("All", "Albums")) {
                    allAlbums.filter { it.title.lowercase().contains(q) || it.artistName.lowercase().contains(q) }
                } else emptyList()

                val artistResults = if (activeFilter in listOf("All", "Artists")) {
                    allArtists.filter { it.name.lowercase().contains(q) }
                } else emptyList()

                val playlistResults = if (activeFilter in listOf("All", "Playlists")) {
                    allPlaylists.filter { it.name.lowercase().contains(q) || it.description?.lowercase()?.contains(q) == true }
                } else emptyList()

                val allGenres = allTracks.mapNotNull { it.genre?.takeIf { g -> g.isNotBlank() && g != "Unknown" } }.distinct()
                val genreResults = if (activeFilter in listOf("All", "Genres")) {
                    allGenres.filter { it.lowercase().contains(q) }
                } else emptyList()

                val hasAnyResults = songResults.isNotEmpty() || videoResults.isNotEmpty() ||
                    albumResults.isNotEmpty() || artistResults.isNotEmpty() ||
                    playlistResults.isNotEmpty() || genreResults.isNotEmpty() ||
                    q.startsWith("http://") || q.startsWith("https://")

                if (!hasAnyResults) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results found for \"$searchQuery\"",
                                fontSize = 14.sp,
                                color = GobeatsTextMuted
                            )
                        }
                    }
                } else {
                    // Display Artists
                    items(artistResults) { artist ->
                        SearchResultRow(
                            title = artist.name,
                            subtitle = "Artist • ${artist.trackCount} songs",
                            badge = "Artist",
                            isCircular = true,
                            onClick = {
                                saveRecentSearch(artist.name)
                                onArtistClick(artist)
                            }
                        )
                    }

                    // Display Albums
                    items(albumResults) { album ->
                        SearchResultRow(
                            title = album.title,
                            subtitle = "${album.artistName} • ${album.trackCount} songs",
                            badge = "Album",
                            isCircular = false,
                            onClick = {
                                saveRecentSearch(album.title)
                                onAlbumClick(album)
                            }
                        )
                    }

                    // Display Playlists
                    items(playlistResults) { playlist ->
                        SearchResultRow(
                            title = playlist.name,
                            subtitle = "Playlist • ${playlist.trackCount} tracks",
                            badge = "Playlist",
                            isCircular = false,
                            onClick = {
                                saveRecentSearch(playlist.name)
                                onPlaylistClick(playlist)
                            }
                        )
                    }

                    // Display Genres
                    items(genreResults) { genre ->
                        val count = allTracks.count { it.genre.equals(genre, true) }
                        SearchResultRow(
                            title = genre,
                            subtitle = "Genre • $count tracks",
                            badge = "Genre",
                            isCircular = false,
                            onClick = {
                                searchQuery = genre
                                saveRecentSearch(genre)
                            }
                        )
                    }

                    // Display Videos
                    items(videoResults) { video ->
                        SearchResultRow(
                            title = video.title,
                            subtitle = "${video.artistName} • ${formatDuration(video.durationMs)}",
                            badge = "Video",
                            isCircular = false,
                            onClick = {
                                saveRecentSearch(video.title)
                                onTrackClick(video, videoResults)
                            },
                            onPlayNext = { onPlayNext(video) },
                            onAddToQueue = { onAddToQueue(video) },
                            onShare = { shareSong(context, video) }
                        )
                    }

                    // Display Songs
                    items(songResults) { track ->
                        SearchResultRow(
                            title = track.title,
                            subtitle = "${track.artistName} • ${formatDuration(track.durationMs)}",
                            badge = if (track.isVideo) "Video" else "Song",
                            isCircular = false,
                            onClick = {
                                saveRecentSearch(track.title)
                                onTrackClick(track, songResults)
                            },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onShare = { shareSong(context, track) }
                        )
                    }
                }
            } else {
                // When search query is empty, show user's real library tracks, albums, and artists
                val allVideos = allTracks.filter { it.isVideo }
                val displayVideos = when (activeFilter) {
                    "Videos" -> allVideos
                    "All" -> allVideos.take(3)
                    else -> emptyList()
                }

                val displayTracks = when (activeFilter) {
                    "Songs" -> allTracks.filter { !it.isVideo }
                    "All" -> allTracks.filter { !it.isVideo }.take(8)
                    else -> emptyList()
                }

                val displayAlbums = when (activeFilter) {
                    "Albums" -> allAlbums
                    "All" -> allAlbums.take(4)
                    else -> emptyList()
                }

                val displayArtists = when (activeFilter) {
                    "Artists" -> allArtists
                    "All" -> allArtists.take(4)
                    else -> emptyList()
                }

                val displayPlaylists = when (activeFilter) {
                    "Playlists" -> allPlaylists
                    "All" -> allPlaylists.take(3)
                    else -> emptyList()
                }

                val allGenres = allTracks.mapNotNull { it.genre?.takeIf { g -> g.isNotBlank() && g != "Unknown" } }.distinct()
                val displayGenres = when (activeFilter) {
                    "Genres" -> allGenres
                    "All" -> allGenres.take(3)
                    else -> emptyList()
                }

                // Render artists
                items(displayArtists) { artist ->
                    SearchResultRow(
                        title = artist.name,
                        subtitle = "Artist • ${artist.trackCount} songs",
                        badge = "Artist",
                        isCircular = true,
                        onClick = { onArtistClick(artist) }
                    )
                }

                // Render albums
                items(displayAlbums) { album ->
                    SearchResultRow(
                        title = album.title,
                        subtitle = "${album.artistName} • ${album.trackCount} songs",
                        badge = "Album",
                        isCircular = false,
                        onClick = { onAlbumClick(album) }
                    )
                }

                // Render Playlists
                items(displayPlaylists) { pl ->
                    SearchResultRow(
                        title = pl.name,
                        subtitle = "Playlist • ${pl.trackCount} tracks",
                        badge = "Playlist",
                        isCircular = false,
                        onClick = { onPlaylistClick(pl) }
                    )
                }

                // Render Genres
                items(displayGenres) { genre ->
                    val count = allTracks.count { it.genre.equals(genre, true) }
                    SearchResultRow(
                        title = genre,
                        subtitle = "Genre • $count tracks",
                        badge = "Genre",
                        isCircular = false,
                        onClick = { searchQuery = genre }
                    )
                }

                // Render Videos
                items(displayVideos) { video ->
                    SearchResultRow(
                        title = video.title,
                        subtitle = "${video.artistName} • ${formatDuration(video.durationMs)}",
                        badge = "Video",
                        isCircular = false,
                        onClick = { onTrackClick(video, allVideos) },
                        onPlayNext = { onPlayNext(video) },
                        onAddToQueue = { onAddToQueue(video) },
                        onShare = { shareSong(context, video) }
                    )
                }

                // Render songs
                items(displayTracks) { track ->
                    SearchResultRow(
                        title = track.title,
                        subtitle = "${track.artistName} • ${formatDuration(track.durationMs)}",
                        badge = "Song",
                        isCircular = false,
                        onClick = { onTrackClick(track, allTracks) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onShare = { shareSong(context, track) }
                    )
                }

                if (displayTracks.isEmpty() && displayVideos.isEmpty() && displayAlbums.isEmpty() && displayArtists.isEmpty() && displayPlaylists.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start typing above or paste a stream link to search",
                                fontSize = 13.sp,
                                color = GobeatsTextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    badge: String,
    isCircular: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(if (isCircular) CircleShape else RoundedCornerShape(10.dp))
                .background(Color(0xFF222028)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCircular) Icons.Default.Person else Icons.Default.MusicNote,
                contentDescription = null,
                tint = GobeatsAmber,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = GobeatsTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Badge
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = Color(0xFF1E1D24),
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text = badge,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GobeatsTextMuted,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }

        if (badge == "Song") {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next") },
                        onClick = {
                            showMenu = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue") },
                        onClick = {
                            showMenu = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GobeatsTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun shareSong(context: Context, track: Track) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "Listening to \"${track.title}\" by ${track.artistName} on gobeats!")
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
}
