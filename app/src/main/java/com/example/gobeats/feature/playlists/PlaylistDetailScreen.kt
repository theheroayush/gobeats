package com.example.gobeats.feature.playlists

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.formatDuration
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.Track
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

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    playlistRepository: PlaylistRepository,
    allLibraryTracks: List<Track> = emptyList(),
    onTrackClick: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddSongsDialog by remember { mutableStateOf(false) }

    fun refreshTracks() {
        scope.launch {
            tracks = playlistRepository.getPlaylistTracks(playlist.id)
        }
    }

    LaunchedEffect(playlist.id) {
        refreshTracks()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Top App Bar with back, add songs, and overflow
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(GobeatsSurfaceElevated, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GobeatsTextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playlist.id != "pl_liked" && playlist.id != "pl_favorites") {
                        IconButton(
                            onClick = { showAddSongsDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(GobeatsSurfaceElevated, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Songs",
                                tint = GobeatsAmber
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(GobeatsSurfaceElevated, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = GobeatsTextPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Add Songs") },
                                    leadingIcon = {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = GobeatsAmber)
                                    },
                                    onClick = {
                                        showMenu = false
                                        showAddSongsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Playlist", color = Color(0xFFEF4444)) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                    },
                                    onClick = {
                                        showMenu = false
                                        scope.launch {
                                            playlistRepository.deletePlaylist(playlist.id)
                                            onBack()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hero Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3B1E54), Color(0xFF1B1A55), Color(0xFF0F0E17))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (playlist.id == "pl_liked" || playlist.name.contains("Liked", ignoreCase = true)) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    } else {
                        Text(
                            text = playlist.name.take(2).uppercase(),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = playlist.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary
                )

                Text(
                    text = "${tracks.size} songs • Curated with gobeats",
                    fontSize = 13.sp,
                    color = GobeatsTextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons: Play All & Shuffle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play All Button (bone white pill)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable {
                                if (tracks.isNotEmpty()) {
                                    onTrackClick(tracks.first(), tracks)
                                }
                            },
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFF4EFEA)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFF0C0A09),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play All",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0C0A09)
                            )
                        }
                    }

                    // Shuffle Button (dark pill)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable {
                                if (tracks.isNotEmpty()) {
                                    val shuffled = tracks.shuffled()
                                    onTrackClick(shuffled.first(), shuffled)
                                }
                            },
                        shape = RoundedCornerShape(24.dp),
                        color = GobeatsSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = GobeatsTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Shuffle",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Empty State or Track List
        if (tracks.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(GobeatsSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "This playlist is empty",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add tracks from your library to start listening.",
                            fontSize = 13.sp,
                            color = GobeatsTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Surface(
                            modifier = Modifier.clickable { showAddSongsDialog = true },
                            shape = RoundedCornerShape(22.dp),
                            color = GobeatsAmber
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color(0xFF0C0A09),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Add Songs to Playlist",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0C0A09)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(tracks) { index, track ->
                var trackMenuOpen by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClick(track, tracks) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = GobeatsTextMuted,
                        modifier = Modifier.width(28.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track.artistName} • ${formatDuration(track.durationMs)}",
                            fontSize = 12.sp,
                            color = GobeatsTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { trackMenuOpen = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Track options",
                                tint = GobeatsTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = trackMenuOpen,
                            onDismissRequest = { trackMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play Next") },
                                onClick = {
                                    trackMenuOpen = false
                                    onPlayNext(track)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Queue") },
                                onClick = {
                                    trackMenuOpen = false
                                    onAddToQueue(track)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from Playlist") },
                                onClick = {
                                    trackMenuOpen = false
                                    scope.launch {
                                        playlistRepository.removeTrackFromPlaylist(playlist.id, track.id)
                                        refreshTracks()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSongsDialog) {
        val existingIds = remember(tracks) { tracks.map { it.id }.toSet() }
        AddSongsToPlaylistDialog(
            playlist = playlist,
            allLibraryTracks = allLibraryTracks,
            existingPlaylistTrackIds = existingIds,
            onDismiss = { showAddSongsDialog = false },
            onConfirmAddTracks = { selectedList ->
                scope.launch {
                    selectedList.forEach { t ->
                        playlistRepository.addTrackToPlaylist(playlist.id, t.id)
                    }
                    refreshTracks()
                    showAddSongsDialog = false
                }
            }
        )
    }
}
