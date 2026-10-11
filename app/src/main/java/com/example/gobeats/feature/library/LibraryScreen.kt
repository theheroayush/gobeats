package com.example.gobeats.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.gobeats.core.designsystem.components.LibraryCategoryTile
import com.example.gobeats.core.designsystem.components.TrackItemRow
import com.example.gobeats.core.ui.debouncedClickable
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.Track
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

enum class LibrarySortOrder {
    TITLE,
    ARTIST,
    DATE_ADDED
}

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    videos: List<Track> = emptyList(),
    albums: List<Album>,
    artists: List<Artist>,
    playlists: List<Playlist>,
    onSongsCategoryClick: () -> Unit = {},
    onAlbumsCategoryClick: () -> Unit = {},
    onArtistsCategoryClick: () -> Unit = {},
    onGenresCategoryClick: () -> Unit = {},
    onFoldersCategoryClick: () -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSeeAllRecentlyAdded: () -> Unit = {},
    onSeeAllPlaylists: () -> Unit = {},
    onTrackClick: (Track, List<Track>) -> Unit = { _, _ -> },
    onToggleFavorite: (Track) -> Unit = {},
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onOpenVideoUri: (android.net.Uri) -> Unit = {},
    onRescanClick: () -> Unit = {},
    onEditTags: (Track) -> Unit = {},
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    var sortOrder by remember { mutableStateOf(LibrarySortOrder.DATE_ADDED) }
    var showTopMenu by remember { mutableStateOf(false) }
    var librarySearchQuery by remember { mutableStateOf("") }
    var showUrlStreamDialog by remember { mutableStateOf(false) }
    var streamUrlInput by remember { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            onOpenVideoUri(uri)
        }
    }

    val videoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            onRescanClick()
        }
    }

    fun requestMediaAccessAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!hasVideo || !hasAudio) {
                videoPermissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
                )
                return
            }
        } else {
            val hasStorage = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!hasStorage) {
                videoPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                return
            }
        }
        onRescanClick()
    }

    val sortedTracks = remember(tracks, sortOrder, librarySearchQuery) {
        val base = when (sortOrder) {
            LibrarySortOrder.TITLE -> tracks.sortedBy { it.title.lowercase() }
            LibrarySortOrder.ARTIST -> tracks.sortedBy { it.artistName.lowercase() }
            LibrarySortOrder.DATE_ADDED -> tracks.sortedByDescending { it.dateAdded }
        }
        if (librarySearchQuery.isBlank()) {
            base
        } else {
            val q = librarySearchQuery.trim().lowercase()
            base.filter {
                it.title.lowercase().contains(q) ||
                    it.artistName.lowercase().contains(q) ||
                    it.albumTitle.lowercase().contains(q) ||
                    it.genre?.lowercase()?.contains(q) == true
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Top App Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "gobeats",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        letterSpacing = (-0.4).sp
                    )
                    Text(
                        text = "YOUR MUSIC. YOUR RULES.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsTextMuted,
                        letterSpacing = 1.6.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = GobeatsTextPrimary
                        )
                    }

                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = GobeatsTextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Video File (.mp4 / .mkv)") },
                                onClick = {
                                    showTopMenu = false
                                    videoPickerLauncher.launch("video/*")
                                },
                                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null, tint = GobeatsAmber) }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Online Stream or Video URL") },
                                onClick = {
                                    showTopMenu = false
                                    showUrlStreamDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = GobeatsAmber) }
                            )
                            DropdownMenuItem(
                                text = { Text("Rescan Device Media") },
                                onClick = {
                                    showTopMenu = false
                                    requestMediaAccessAndScan()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Title (A-Z)") },
                                onClick = {
                                    sortOrder = LibrarySortOrder.TITLE
                                    showTopMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Artist") },
                                onClick = {
                                    sortOrder = LibrarySortOrder.ARTIST
                                    showTopMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort by Recently Added") },
                                onClick = {
                                    sortOrder = LibrarySortOrder.DATE_ADDED
                                    showTopMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Title and Subtitle
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "Library",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selectedCategory == "All") "Your collection. Beautifully organized." else "Viewing: $selectedCategory",
                    fontSize = 13.sp,
                    color = GobeatsTextSecondary
                )
            }
        }

        // Inline Search Filter Input
        item {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color(0xFF16151A),
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = librarySearchQuery,
                        onValueChange = { librarySearchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = GobeatsTextPrimary,
                            fontSize = 13.sp
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(GobeatsAmber),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (librarySearchQuery.isEmpty()) {
                                Text(
                                    text = "Filter songs, artists, albums in library...",
                                    fontSize = 13.sp,
                                    color = GobeatsTextMuted
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (librarySearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { librarySearchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = GobeatsTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5 Quick Category Tiles (Songs, Albums, Artists, Genres, Folders)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LibraryCategoryTile(
                        title = "Songs",
                        countText = "${tracks.size}",
                        icon = Icons.Default.MusicNote,
                        iconTint = Color(0xFFF472B6),
                        onClick = {
                            selectedCategory = if (selectedCategory == "Songs") "All" else "Songs"
                            onSongsCategoryClick()
                        }
                    )
                }
                item {
                    LibraryCategoryTile(
                        title = "Albums",
                        countText = "${albums.size}",
                        icon = Icons.Default.Album,
                        iconTint = GobeatsAmber,
                        onClick = {
                            selectedCategory = if (selectedCategory == "Albums") "All" else "Albums"
                            onAlbumsCategoryClick()
                        }
                    )
                }
                item {
                    LibraryCategoryTile(
                        title = "Artists",
                        countText = "${artists.size}",
                        icon = Icons.Default.Person,
                        iconTint = Color(0xFF60A5FA),
                        isCircleIcon = true,
                        onClick = {
                            selectedCategory = if (selectedCategory == "Artists") "All" else "Artists"
                            onArtistsCategoryClick()
                        }
                    )
                }
                item {
                    val genreCount = tracks.mapNotNull { it.genre }.distinct().size
                    LibraryCategoryTile(
                        title = "Genres",
                        countText = "$genreCount",
                        icon = Icons.Default.GraphicEq,
                        iconTint = Color(0xFFFB7185),
                        onClick = {
                            selectedCategory = if (selectedCategory == "Genres") "All" else "Genres"
                            onGenresCategoryClick()
                        }
                    )
                }
                item {
                    LibraryCategoryTile(
                        title = "Videos",
                        countText = "${videos.size}",
                        icon = Icons.Default.PlayCircle,
                        iconTint = Color(0xFF38BDF8),
                        onClick = {
                            val nextCat = if (selectedCategory == "Videos") "All" else "Videos"
                            selectedCategory = nextCat
                            if (nextCat == "Videos" && videos.isEmpty()) {
                                requestMediaAccessAndScan()
                            }
                        }
                    )
                }
                item {
                    LibraryCategoryTile(
                        title = "Playlists",
                        countText = "${playlists.size}",
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        iconTint = Color(0xFFA78BFA),
                        onClick = {
                            onSeeAllPlaylists()
                        }
                    )
                }
                item {
                    LibraryCategoryTile(
                        title = "Folders",
                        countText = "Browse",
                        icon = Icons.Default.Folder,
                        iconTint = Color(0xFF94A3B8),
                        onClick = onFoldersCategoryClick
                    )
                }
            }
        }

        // Active Filter Dismiss Bar (if a category is filtered)
        if (selectedCategory != "All") {
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Filtered by $selectedCategory",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsAmber
                    )
                    Surface(
                        modifier = Modifier.clickable { selectedCategory = "All" },
                        shape = RoundedCornerShape(12.dp),
                        color = GobeatsSurfaceElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Clear filter", fontSize = 11.sp, color = GobeatsTextMuted)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Close, contentDescription = null, tint = GobeatsTextMuted, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        // Empty State Banner if 0 tracks
        if (tracks.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(GobeatsSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No music indexed yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan your device or transfer songs wirelessly from your computer.",
                            fontSize = 12.sp,
                            color = GobeatsTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier.clickable { onRescanClick() },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFF4EFEA)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF0C0A09), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Device Storage", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0C0A09))
                            }
                        }
                    }
                }
            }
        }

        // Section: Recently added (Albums) - Only when "All" selected
        if (selectedCategory == "All" && albums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recently added",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Row(
                        modifier = Modifier.clickable {
                            selectedCategory = "Albums"
                            onSeeAllRecentlyAdded()
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "See all", fontSize = 13.sp, color = GobeatsTextMuted)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = ">", fontSize = 13.sp, color = GobeatsTextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        LibraryAlbumCard(album = album, onClick = { onAlbumClick(album) })
                    }
                }
            }
        }

        // Section: All Albums (2-Column Grid) - Rendered when "Albums" category is selected
        if (selectedCategory == "Albums" && albums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "All Albums (${albums.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            val albumRows = albums.chunked(2)
            items(albumRows, key = { it.firstOrNull()?.id ?: it.hashCode().toString() }) { rowAlbums ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (alb in rowAlbums) {
                        Box(modifier = Modifier.weight(1f)) {
                            LibraryAlbumCard(album = alb, onClick = { onAlbumClick(alb) })
                        }
                    }
                    if (rowAlbums.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Section: Artists List - Rendered when "Artists" category is selected
        if (selectedCategory == "Artists") {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "All Artists (${artists.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            if (artists.isEmpty()) {
                item {
                    Text(
                        text = "No artists found in your library.",
                        fontSize = 13.sp,
                        color = GobeatsTextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(artists, key = { it.id }) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .debouncedClickable { onArtistClick(artist) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(GobeatsSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                            Text(
                                text = "${artist.trackCount} songs",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GobeatsTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Section: Genres List - Rendered when "Genres" category is selected
        if (selectedCategory == "Genres") {
            val genreGroups = tracks.groupBy { it.genre?.trim() ?: "Unknown Genre" }
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "All Genres (${genreGroups.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            items(genreGroups.keys.toList(), key = { it }) { genreName ->
                val gTracks = genreGroups[genreName] ?: emptyList()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (gTracks.isNotEmpty()) {
                                onTrackClick(gTracks.first(), gTracks)
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = genreName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                            Text(
                                text = "${gTracks.size} songs",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Genre",
                            tint = GobeatsAmber,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Section: Videos List - Rendered when "Videos" category is selected
        if (selectedCategory == "Videos") {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Videos (${videos.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )

                    Surface(
                        modifier = Modifier.clickable { videoPickerLauncher.launch("video/*") },
                        shape = RoundedCornerShape(12.dp),
                        color = GobeatsSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Open File",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                        }
                    }
                }
            }

            if (videos.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = GobeatsSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(GobeatsSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = GobeatsAmber,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No video files found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsTextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Play videos stored on your device or pick any .mp4/.mkv file directly.",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    modifier = Modifier.clickable { videoPickerLauncher.launch("video/*") },
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFFF4EFEA)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color(0xFF0C0A09), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Video File", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0C0A09))
                                    }
                                }

                                Surface(
                                    modifier = Modifier.clickable { requestMediaAccessAndScan() },
                                    shape = RoundedCornerShape(20.dp),
                                    color = GobeatsSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = GobeatsTextPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan Media", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(videos, key = { it.id }) { videoTrack ->
                    TrackItemRow(
                        track = videoTrack,
                        isPlaying = (videoTrack.id == currentTrackId && isPlaying),
                        onClick = { onTrackClick(videoTrack, videos) },
                        onToggleFavorite = { onToggleFavorite(videoTrack) },
                        onPlayNext = { onPlayNext(videoTrack) },
                        onAddToQueue = { onAddToQueue(videoTrack) },
                        onAddToPlaylist = { onAddToPlaylist(videoTrack) },
                        onEditTags = { onEditTags(videoTrack) }
                    )
                }
            }
        }

        // Section: Playlists - Only when "All" selected
        if (selectedCategory == "All" && playlists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playlists",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Row(
                        modifier = Modifier.clickable { onSeeAllPlaylists() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "See all", fontSize = 13.sp, color = GobeatsTextMuted)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = ">", fontSize = 13.sp, color = GobeatsTextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        LibraryPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                    }
                }
            }
        }

        // Section: Songs List (Rendered when "All" or "Songs" is selected and tracks exist)
        if ((selectedCategory == "All" || selectedCategory == "Songs") && tracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategory == "Songs") "All Songs (${tracks.size})" else "Songs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    if (selectedCategory == "All") {
                        Row(
                            modifier = Modifier.clickable { selectedCategory = "Songs" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "See all", fontSize = 13.sp, color = GobeatsTextMuted)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = ">", fontSize = 13.sp, color = GobeatsTextMuted)
                        }
                    }
                }
            }

            val displayList = if (selectedCategory == "Songs") sortedTracks else sortedTracks.take(15)
            items(displayList, key = { it.id }) { track ->
                TrackItemRow(
                    track = track,
                    isPlaying = (track.id == currentTrackId && isPlaying),
                    onClick = { onTrackClick(track, sortedTracks) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onEditTags = { onEditTags(track) }
                )
            }
        }
    }

    if (showUrlStreamDialog) {
        AlertDialog(
            onDismissRequest = { showUrlStreamDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text(
                    text = "Open Online Stream / Video",
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter direct HTTP/HTTPS audio, video (.mp4, .mkv), or HLS (.m3u8) stream URL.",
                        fontSize = 12.sp,
                        color = GobeatsTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = streamUrlInput,
                        onValueChange = { streamUrlInput = it },
                        label = { Text("Stream URL (http:// or https://)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GobeatsAmber,
                            unfocusedBorderColor = GobeatsBorderSubtle,
                            focusedTextColor = GobeatsTextPrimary,
                            unfocusedTextColor = GobeatsTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = streamUrlInput.trim()
                        if (clean.startsWith("http://", true) || clean.startsWith("https://", true)) {
                            val streamName = clean.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').ifBlank { "Online Stream" }
                            val isVideo = clean.endsWith(".mp4", true) || clean.endsWith(".mkv", true) || clean.endsWith(".webm", true) || clean.endsWith(".m3u8", true)
                            val streamTrack = Track(
                                id = "stream_" + clean.hashCode().toUInt().toString(16),
                                title = streamName,
                                artistName = "Internet Stream",
                                albumTitle = "Online Streams",
                                durationMs = 0L,
                                format = if (isVideo) "MP4" else "STREAM",
                                bitrate = 320,
                                sampleRate = 44100,
                                fileUri = clean,
                                sourceId = "source_internet",
                                isVideo = isVideo
                            )
                            showUrlStreamDialog = false
                            streamUrlInput = ""
                            onTrackClick(streamTrack, listOf(streamTrack))
                        }
                    }
                ) {
                    Text("Play", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlStreamDialog = false }) {
                    Text("Cancel", color = GobeatsTextMuted)
                }
            }
        )
    }
}

@Composable
private fun LibraryAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(135.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GobeatsSurfaceElevated)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val colors = when ((album.title.hashCode() % 3)) {
                    0 -> listOf(Color(0xFF5D4037), Color(0xFF1B1412))
                    1 -> listOf(Color(0xFF263238), Color(0xFF0F1417))
                    else -> listOf(Color(0xFF4A148C), Color(0xFF1E0B33))
                }
                drawRect(brush = Brush.verticalGradient(colors))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = album.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Album") },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                }
            }
        }

        Text(
            text = album.artistName,
            fontSize = 11.sp,
            color = GobeatsTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${album.trackCount} songs",
            fontSize = 10.sp,
            color = GobeatsTextMuted
        )
    }
}

@Composable
private fun LibraryPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val isLikedSongs = playlist.name.contains("Liked", ignoreCase = true)

    Column(
        modifier = Modifier
            .width(135.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(135.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GobeatsSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (isLikedSongs) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF4A3B69), Color(0xFF2C1929))
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val colors = when ((playlist.name.hashCode() % 3)) {
                        0 -> listOf(Color(0xFF334E68), Color(0xFF102A43))
                        1 -> listOf(Color(0xFF486581), Color(0xFF243B53))
                        else -> listOf(Color(0xFF627D98), Color(0xFF334E68))
                    }
                    drawRect(brush = Brush.verticalGradient(colors))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playlist.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "${playlist.trackCount} songs",
            fontSize = 11.sp,
            color = GobeatsTextMuted
        )
    }
}
