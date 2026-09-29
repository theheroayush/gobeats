package com.example.gobeats.feature.nowplaying

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.formatDuration
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Track
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

@Composable
fun QueueScreen(
    state: PlaybackUiState,
    onTrackClick: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRemoveFromQueue: (Int) -> Unit = {},
    onClearQueue: () -> Unit = {},
    onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
    onShuffleQueue: () -> Unit = {},
    onSaveQueueAsPlaylist: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian)
    ) {
        // Queue Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GobeatsTextPrimary
                    )
                }
                Text(
                    text = "Playback Queue",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.queue.isNotEmpty()) {
                    IconButton(onClick = onShuffleQueue) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle Queue",
                            tint = GobeatsAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Save Queue as Playlist",
                            tint = GobeatsTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    TextButton(onClick = onClearQueue) {
                        Text(
                            text = "Clear",
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = "${state.queue.size} songs",
                    fontSize = 12.sp,
                    color = GobeatsTextMuted,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        if (state.queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "The queue is empty",
                    fontSize = 15.sp,
                    color = GobeatsTextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                itemsIndexed(state.queue) { index, track ->
                    val isPlaying = state.currentTrack?.id == track.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track, state.queue) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(GobeatsSurfaceElevated, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isPlaying) GobeatsAmber else GobeatsTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontSize = 14.sp,
                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPlaying) GobeatsAmber else GobeatsTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artistName} • ${formatDuration(track.durationMs)}",
                                fontSize = 11.sp,
                                color = GobeatsTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Reorder Up Button
                        IconButton(
                            onClick = { onMoveQueueItem(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move Up",
                                tint = if (index > 0) GobeatsTextMuted else Color.Transparent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Reorder Down Button
                        IconButton(
                            onClick = { onMoveQueueItem(index, index + 1) },
                            enabled = index < state.queue.lastIndex,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move Down",
                                tint = if (index < state.queue.lastIndex) GobeatsTextMuted else Color.Transparent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onToggleFavorite(track) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (track.isFavorite) GobeatsAmber else GobeatsTextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        IconButton(
                            onClick = { onRemoveFromQueue(index) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove from Queue",
                                tint = GobeatsTextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Save as Playlist Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text("Save Queue as Playlist", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
            },
            text = {
                Column {
                    Text(
                        "Enter a name for the new playlist containing ${state.queue.size} songs:",
                        fontSize = 13.sp,
                        color = GobeatsTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        placeholder = { Text("My Queue Playlist", color = GobeatsTextMuted) },
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
                Button(
                    onClick = {
                        val name = playlistName.ifBlank { "Queue Playlist" }
                        onSaveQueueAsPlaylist(name)
                        showSaveDialog = false
                        playlistName = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GobeatsAmber)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = GobeatsTextMuted)
                }
            }
        )
    }
}
