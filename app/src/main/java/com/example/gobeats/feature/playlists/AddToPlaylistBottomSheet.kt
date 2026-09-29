package com.example.gobeats.feature.playlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    track: Track,
    playlistRepository: PlaylistRepository,
    onDismiss: () -> Unit,
    onAddedToPlaylist: (Playlist) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var showCreateInline by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var addedPlaylistId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        playlistRepository.seedDefaultPlaylistsIfEmpty()
        playlists = playlistRepository.getAllPlaylists()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GobeatsObsidian,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(GobeatsBorderSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add to Playlist",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = GobeatsTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Target Track Preview Pill
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = GobeatsSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GobeatsSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!track.artworkUri.isNullOrBlank()) {
                            AsyncImage(
                                model = track.artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

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
                            text = "${track.artistName} • ${track.albumTitle}",
                            fontSize = 12.sp,
                            color = GobeatsTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Create New Playlist Button / Inline Creator
            if (!showCreateInline) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateInline = true },
                    shape = RoundedCornerShape(14.dp),
                    color = GobeatsSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GobeatsAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "New Playlist",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsAmber
                            )
                            Text(
                                text = "Create a new playlist with this song",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsAmber)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Create & Add Song",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Playlist name (e.g. Workout Mix)", color = GobeatsTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GobeatsTextPrimary,
                                unfocusedTextColor = GobeatsTextPrimary,
                                focusedBorderColor = GobeatsAmber,
                                unfocusedBorderColor = GobeatsBorderSubtle,
                                cursorColor = GobeatsAmber
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                modifier = Modifier.clickable {
                                    showCreateInline = false
                                    newPlaylistName = ""
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Transparent
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontSize = 13.sp,
                                    color = GobeatsTextMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                modifier = Modifier.clickable {
                                    if (newPlaylistName.isNotBlank()) {
                                        scope.launch {
                                            val created = playlistRepository.createPlaylist(newPlaylistName.trim())
                                            playlistRepository.addTrackToPlaylist(created.id, track.id)
                                            addedPlaylistId = created.id
                                            onAddedToPlaylist(created)
                                            onDismiss()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = GobeatsAmber
                            ) {
                                Text(
                                    text = "Create & Add",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0C0A09),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = GobeatsBorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "EXISTING PLAYLISTS (${playlists.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GobeatsTextMuted,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom playlists yet. Tap 'New Playlist' above!",
                        fontSize = 13.sp,
                        color = GobeatsTextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        val isAdded = addedPlaylistId == playlist.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        playlistRepository.addTrackToPlaylist(playlist.id, track.id)
                                        addedPlaylistId = playlist.id
                                        onAddedToPlaylist(playlist)
                                        onDismiss()
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = GobeatsSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAdded) GobeatsAmber else GobeatsBorderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFF27272A), Color(0xFF18181B))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = playlist.name.take(2).uppercase(),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GobeatsAmber
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GobeatsTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.trackCount} songs",
                                        fontSize = 12.sp,
                                        color = GobeatsTextMuted
                                    )
                                }

                                Icon(
                                    imageVector = if (isAdded) Icons.Default.Check else Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = "Add",
                                    tint = if (isAdded) GobeatsAmber else GobeatsTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
