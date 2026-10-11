package com.example.gobeats.feature.library

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.GobeatsSecondaryButton
import com.example.gobeats.core.designsystem.components.TrackItemRow
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Track
import com.example.gobeats.data.repository.LibraryRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    libraryRepository: LibraryRepository,
    currentTrackId: String? = null,
    playbackState: PlaybackUiState? = null,
    onTrackClick: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit = {},
    onBack: () -> Unit,
    onEditTags: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var artistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    LaunchedEffect(artist.id, artist.name) {
        val tracks = libraryRepository.getTracksByArtist(artist.id)
        artistTracks = if (tracks.isNotEmpty()) {
            tracks
        } else {
            // Fallback match by artist name in library
            libraryRepository.getAllTracks().filter {
                it.artistName.equals(artist.name, ignoreCase = true) ||
                it.artistName.contains(artist.name, ignoreCase = true)
            }
        }
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
                .statusBarsPadding()
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
            // Artist Hero Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(GobeatsSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val colors = when (artist.name.hashCode() % 3) {
                                0 -> listOf(Color(0xFF4A3E4E), Color(0xFF221C26))
                                1 -> listOf(Color(0xFF334A52), Color(0xFF14242B))
                                else -> listOf(Color(0xFF4A4533), Color(0xFF221E14))
                            }
                            drawCircle(brush = Brush.verticalGradient(colors))
                        }
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = GobeatsAmber,
                            modifier = Modifier.size(68.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = artist.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${artistTracks.size.coerceAtLeast(artist.trackCount)} songs in your collection",
                        fontSize = 13.sp,
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
                                    if (artistTracks.isNotEmpty()) {
                                        onTrackClick(artistTracks.first(), artistTracks)
                                    }
                                }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            GobeatsSecondaryButton(
                                text = "Shuffle",
                                onClick = {
                                    if (artistTracks.isNotEmpty()) {
                                        val shuffled = artistTracks.shuffled()
                                        onTrackClick(shuffled.first(), shuffled)
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Track list
            if (artistTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No songs found for ${artist.name}",
                            fontSize = 14.sp,
                            color = GobeatsTextMuted
                        )
                    }
                }
            } else {
                val activeTrackId = currentTrackId ?: playbackState?.currentTrack?.id
                items(artistTracks, key = { it.id }) { track ->
                    TrackItemRow(
                        track = track,
                        isPlaying = activeTrackId == track.id,
                        onClick = { onTrackClick(track, artistTracks) },
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
}
