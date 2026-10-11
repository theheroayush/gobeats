package com.example.gobeats.feature.playlists

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.data.repository.PlaylistRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.launch

private val playlistGradients = listOf(
    listOf(Color(0xFFB45309), Color(0xFF431407)), // Amber warm
    listOf(Color(0xFFBE185D), Color(0xFF4A044E)), // Magenta rose
    listOf(Color(0xFF1E3A8A), Color(0xFF0F172A)), // Midnight navy
    listOf(Color(0xFFC2410C), Color(0xFF451A03)), // Rust sunset
    listOf(Color(0xFF4338CA), Color(0xFF1E1B4B)), // Indigo
    listOf(Color(0xFF0F766E), Color(0xFF042F2E)), // Deep teal
    listOf(Color(0xFFBE123C), Color(0xFF3F0B18)), // Crimson
    listOf(Color(0xFF1E293B), Color(0xFF020617)), // Slate obsidian
    listOf(Color(0xFF065F46), Color(0xFF022C22)), // Emerald forest
    listOf(Color(0xFF6B21A8), Color(0xFF3B0764))  // Royal purple
)

private fun gradientForPlaylist(id: String): List<Color> {
    val index = kotlin.math.abs(id.hashCode()) % playlistGradients.size
    return playlistGradients[index]
}

data class PlaylistCardSpec(
    val id: String,
    val title: String,
    val songCount: Int,
    val gradientColors: List<Color>,
    val isLikedSongs: Boolean = false,
    val isSmart: Boolean = false,
    val isImported: Boolean = false
)

@Composable
fun PlaylistsScreen(
    playlistRepository: PlaylistRepository,
    onPlaylistClick: (Playlist) -> Unit,
    onFavoritesClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSeeAllRecentlyPlayed: () -> Unit,
    modifier: Modifier = Modifier,
    libraryTracks: List<com.example.gobeats.core.model.Track> = emptyList()
) {
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var smartPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }
    var playlistSearchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    val filters = listOf("All", "Yours", "Smart", "Imported")

    fun loadPlaylists() {
        scope.launch {
            playlistRepository.seedDefaultPlaylistsIfEmpty()
            val loaded = playlistRepository.getAllPlaylists()
            val smart = playlistRepository.getSmartPlaylists()
            playlists = loaded
            smartPlaylists = smart
        }
    }

    LaunchedEffect(Unit) {
        loadPlaylists()
    }

    val cardSpecs = remember(playlists, smartPlaylists) {
        val likedCount = smartPlaylists.find { it.id == "pl_liked" }?.trackCount
            ?: playlists.firstOrNull { it.id == "pl_favorites" || it.id == "pl_liked" }?.trackCount ?: 0

        val smartSpecs = listOf(
            PlaylistCardSpec(
                id = "pl_liked",
                title = "Liked Songs",
                songCount = likedCount,
                gradientColors = listOf(Color(0xFF4A1E5C), Color(0xFF1E1430)),
                isLikedSongs = true,
                isSmart = true,
                isImported = false
            ),
            PlaylistCardSpec(
                id = "pl_most_played",
                title = "Most Played",
                songCount = smartPlaylists.find { it.id == "pl_most_played" }?.trackCount ?: 0,
                gradientColors = listOf(Color(0xFFE11D48), Color(0xFF4C0519)),
                isLikedSongs = false,
                isSmart = true,
                isImported = false
            ),
            PlaylistCardSpec(
                id = "pl_heavy_rotation",
                title = "Heavy Rotation",
                songCount = smartPlaylists.find { it.id == "pl_heavy_rotation" }?.trackCount ?: 0,
                gradientColors = listOf(Color(0xFFD97706), Color(0xFF451A03)),
                isLikedSongs = false,
                isSmart = true,
                isImported = false
            ),
            PlaylistCardSpec(
                id = "pl_recently_played",
                title = "Recently Played",
                songCount = smartPlaylists.find { it.id == "pl_recently_played" }?.trackCount ?: 0,
                gradientColors = listOf(Color(0xFF2563EB), Color(0xFF1E1B4B)),
                isLikedSongs = false,
                isSmart = true,
                isImported = false
            ),
            PlaylistCardSpec(
                id = "pl_forgotten_favorites",
                title = "Forgotten Favorites",
                songCount = smartPlaylists.find { it.id == "pl_forgotten_favorites" }?.trackCount ?: 0,
                gradientColors = listOf(Color(0xFF7C3AED), Color(0xFF2E1065)),
                isLikedSongs = false,
                isSmart = true,
                isImported = false
            )
        )

        val customSpecs = playlists.filter { !it.id.startsWith("pl_") }.map { pl ->
            val isImp = pl.description?.contains("[Imported]", ignoreCase = true) == true
            PlaylistCardSpec(
                id = pl.id,
                title = pl.name,
                songCount = pl.trackCount,
                gradientColors = gradientForPlaylist(pl.id),
                isLikedSongs = false,
                isSmart = false,
                isImported = isImp
            )
        }

        smartSpecs + customSpecs
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Top App Bar: Brand + Search & Overflow (1:1 with mockup)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                        color = GobeatsAmber,
                        letterSpacing = 1.2.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = GobeatsTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    var showTopMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = GobeatsTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Playlist") },
                                onClick = {
                                    showTopMenu = false
                                    showCreateDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = GobeatsAmber) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Playlist") },
                                onClick = {
                                    showTopMenu = false
                                    showImportSheet = true
                                },
                                leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = GobeatsAmber) }
                            )
                            DropdownMenuItem(
                                text = { Text("Rescan Playlists") },
                                onClick = {
                                    showTopMenu = false
                                    loadPlaylists()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Header Section: Playlists Title & + Create Playlist Button (1:1 with mockup_playlists_v2.png)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Playlists",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsTextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your moods. Your moments. Your playlists.",
                            fontSize = 13.sp,
                            color = GobeatsTextMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // White pill Create Playlist CTA (1:1 with mockup_playlists_v2.png)
                    Surface(
                        modifier = Modifier
                            .clickable { showCreateDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFF4EFEA)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color(0xFF0C0A09),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Create Playlist",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0C0A09),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Search Bar & Filter Chips Row
        item {
            Spacer(modifier = Modifier.height(10.dp))
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
                        value = playlistSearchQuery,
                        onValueChange = { playlistSearchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = GobeatsTextPrimary,
                            fontSize = 13.sp
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(GobeatsAmber),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (playlistSearchQuery.isEmpty()) {
                                Text(
                                    text = "Filter playlists...",
                                    fontSize = 13.sp,
                                    color = GobeatsTextMuted
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (playlistSearchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { playlistSearchQuery = "" },
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

            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters.indices.toList(), key = { filters[it] }) { index ->
                    val isSelected = selectedFilterIndex == index
                    Surface(
                        modifier = Modifier.clickable { selectedFilterIndex = index },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFFF4EFEA) else GobeatsSurface,
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                    ) {
                        Text(
                            text = filters[index],
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF0C0A09) else GobeatsTextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // 3-Column Grid of Cards (filtered according to active filter and search query)
        item {
            val filteredByChip = when (filters[selectedFilterIndex]) {
                "Yours" -> cardSpecs.filter { !it.isSmart && !it.isImported }
                "Smart" -> cardSpecs.filter { it.isSmart }
                "Imported" -> cardSpecs.filter { it.isImported }
                else -> cardSpecs
            }
            val displayedSpecs = if (playlistSearchQuery.isBlank()) {
                filteredByChip
            } else {
                val q = playlistSearchQuery.trim().lowercase()
                filteredByChip.filter { it.title.lowercase().contains(q) }
            }
            if (displayedSpecs.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No ${filters[selectedFilterIndex].lowercase()} playlists found",
                            fontSize = 14.sp,
                            color = GobeatsTextMuted
                        )
                        if (filters[selectedFilterIndex] == "Imported") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                modifier = Modifier.clickable { showImportSheet = true },
                                shape = RoundedCornerShape(14.dp),
                                color = GobeatsAmber
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        tint = GobeatsObsidian,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Import from Spotify / CSV / M3U",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GobeatsObsidian
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val rows = displayedSpecs.chunked(3)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (spec in row) {
                                Box(modifier = Modifier.weight(1f)) {
                                    PlaylistGridCard(
                                        spec = spec,
                                        onClick = {
                                            if (spec.isLikedSongs) {
                                                onFavoritesClick()
                                            } else {
                                                val matched = smartPlaylists.find { it.id == spec.id }
                                                    ?: playlists.find { it.id == spec.id }
                                                    ?: Playlist(id = spec.id, name = spec.title, trackCount = spec.songCount, isSmart = spec.isSmart)
                                                onPlaylistClick(matched)
                                            }
                                        },
                                        onDelete = {
                                            if (!spec.isSmart) {
                                                scope.launch {
                                                    playlistRepository.deletePlaylist(spec.id)
                                                    loadPlaylists()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            for (i in 0 until (3 - row.size)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Section: Recently played playlists (matching mockup)
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recently played playlists",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GobeatsTextPrimary
                )

                val userPlaylists = playlists.filter { it.id != "pl_favorites" && it.id != "pl_liked" }
                if (userPlaylists.isNotEmpty()) {
                    Row(
                        modifier = Modifier.clickable { onSeeAllRecentlyPlayed() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "See all",
                            fontSize = 13.sp,
                            color = GobeatsTextMuted
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = GobeatsTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Real Playlists or Clean Empty State
        item {
            val userPlaylists = playlists.filter { it.id != "pl_favorites" && it.id != "pl_liked" }
            if (userPlaylists.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = GobeatsAmber,
                            modifier = Modifier
                                .size(36.dp)
                                .background(GobeatsAmber.copy(alpha = 0.12f), CircleShape)
                                .padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No custom playlists yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Create Playlist' above to craft your first collection.",
                            fontSize = 12.sp,
                            color = GobeatsTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    for (playlist in userPlaylists.take(3)) {
                        RecentPlaylistItem(
                            title = playlist.name,
                            trackCount = playlist.trackCount,
                            gradientColors = gradientForPlaylist(playlist.id),
                            onClick = { onPlaylistClick(playlist) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text(
                    text = "New Playlist",
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary
                )
            },
            text = {
                Column {
                    var isCreating by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        placeholder = { Text("e.g. Late Night Vibes", color = GobeatsTextMuted) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = {
                                if (newPlaylistName.isNotBlank() && !isCreating) {
                                    isCreating = true
                                    scope.launch {
                                        val created = playlistRepository.createPlaylist(newPlaylistName.trim(), newPlaylistDesc.trim().ifEmpty { null })
                                        newPlaylistName = ""
                                        newPlaylistDesc = ""
                                        selectedFilterIndex = 0
                                        showCreateDialog = false
                                        loadPlaylists()
                                        onPlaylistClick(created)
                                    }
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GobeatsAmber,
                            unfocusedBorderColor = GobeatsBorderSubtle,
                            focusedTextColor = GobeatsTextPrimary,
                            unfocusedTextColor = GobeatsTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPlaylistDesc,
                        onValueChange = { newPlaylistDesc = it },
                        label = { Text("Description (Optional)") },
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
                var isCreating by remember { mutableStateOf(false) }
                TextButton(
                    enabled = newPlaylistName.isNotBlank() && !isCreating,
                    onClick = {
                        if (newPlaylistName.isNotBlank() && !isCreating) {
                            isCreating = true
                            scope.launch {
                                val created = playlistRepository.createPlaylist(newPlaylistName.trim(), newPlaylistDesc.trim().ifEmpty { null })
                                newPlaylistName = ""
                                newPlaylistDesc = ""
                                selectedFilterIndex = 0
                                showCreateDialog = false
                                loadPlaylists()
                                onPlaylistClick(created)
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Create",
                        color = if (newPlaylistName.isNotBlank()) GobeatsAmber else GobeatsTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = GobeatsTextMuted)
                }
            }
        )
    }

    if (showImportSheet) {
        ImportPlaylistBottomSheet(
            allTracks = libraryTracks,
            onDismiss = { showImportSheet = false },
            onImportConfirm = { name, matchedTracks ->
                scope.launch {
                    val created = playlistRepository.createPlaylist(
                        name = name,
                        description = "[Imported] ${matchedTracks.size} tracks"
                    )
                    for (track in matchedTracks) {
                        playlistRepository.addTrack(created.id, track)
                    }
                    selectedFilterIndex = 0
                    loadPlaylists()
                    showImportSheet = false
                    onPlaylistClick(created)
                }
            }
        )
    }
}

@Composable
private fun PlaylistGridCard(
    spec: PlaylistCardSpec,
    onClick: () -> Unit,
    onDelete: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(spec.gradientColors))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (spec.isLikedSongs) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                // Procedural ambient glow lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.4f),
                            radius = size.width * 0.6f
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onClick() }
            ) {
                Text(
                    text = spec.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GobeatsTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${spec.songCount} songs",
                    fontSize = 11.sp,
                    color = GobeatsTextMuted,
                    maxLines = 1
                )
            }

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
                        text = { Text("Open Playlist") },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    if (!spec.isLikedSongs && !spec.isSmart) {
                        DropdownMenuItem(
                            text = { Text("Delete Playlist", color = Color(0xFFEF4444)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentPlaylistItem(
    title: String,
    trackCount: Int,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = GobeatsSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(gradientColors))
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsTextPrimary
                    )
                    Text(
                        text = "Playlist • $trackCount songs",
                        fontSize = 12.sp,
                        color = GobeatsTextMuted
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = GobeatsTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
