package com.example.gobeats.feature.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.GobeatsSecondaryButton
import com.example.gobeats.core.designsystem.components.TrackItemRow
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Track
import com.example.gobeats.data.repository.LibraryRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

@Composable
fun AlbumDetailScreen(
    album: Album,
    libraryRepository: LibraryRepository,
    currentTrackId: String? = null,
    playbackState: PlaybackUiState? = null,
    onTrackClick: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onAddToPlaylist: (Track) -> Unit = {},
    onEditTags: (Track) -> Unit = {}
) {
    var albumTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    LaunchedEffect(album.id) {
        albumTracks = libraryRepository.getTracksByAlbum(album.id)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GobeatsTextPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Album Hero Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = GobeatsSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                        modifier = Modifier.size(190.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Album,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = album.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = album.artistName,
                        fontSize = 15.sp,
                        color = GobeatsAmber,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "${albumTracks.size} tracks • ${album.year ?: 2026}",
                        fontSize = 12.sp,
                        color = GobeatsTextMuted
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            GobeatsPrimaryButton(
                                text = "Play",
                                icon = Icons.Default.PlayArrow,
                                onClick = {
                                    if (albumTracks.isNotEmpty()) {
                                        onTrackClick(albumTracks.first(), albumTracks)
                                    }
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            GobeatsSecondaryButton(
                                text = "Shuffle",
                                onClick = {
                                    if (albumTracks.isNotEmpty()) {
                                        val shuffled = albumTracks.shuffled()
                                        onTrackClick(shuffled.first(), shuffled)
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            val activeTrackId = currentTrackId ?: playbackState?.currentTrack?.id
            // Track list
            items(albumTracks, key = { it.id }) { track ->
                TrackItemRow(
                    track = track,
                    isPlaying = activeTrackId == track.id,
                    onClick = { onTrackClick(track, albumTracks) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onEditTags = { onEditTags(track) }
                )
            }
        }
    }
}
