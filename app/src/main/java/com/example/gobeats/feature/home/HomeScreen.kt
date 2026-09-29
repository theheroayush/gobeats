package com.example.gobeats.feature.home

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.HomeHeroBanner
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Track
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

@Composable
fun HomeScreen(
    playbackState: PlaybackUiState,
    recentlyPlayed: List<Track>,
    recentlyAdded: List<Track>,
    albums: List<Album>,
    artists: List<Artist>,
    onTrackClick: (Track, List<Track>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSeeAllContinue: () -> Unit,
    onSeeAllRecentlyPlayed: () -> Unit,
    onSeeAllRecentlyAdded: () -> Unit,
    onPlayNext: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onEditTags: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Top App Bar: Brand + Search & Settings
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

                Row {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = GobeatsTextPrimary
                        )
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = GobeatsTextPrimary
                        )
                    }
                }
            }
        }

        // Hero Banner ("Good Morning / Music makes a brighter day")
        item {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                HomeHeroBanner()
            }
        }

        // Section: Continue listening
        item {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeaderWithSeeAll(
                title = "Continue listening",
                onSeeAllClick = onSeeAllContinue
            )
            Spacer(modifier = Modifier.height(12.dp))

            val continueTracks = (recentlyPlayed.ifEmpty { recentlyAdded }).take(6)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(continueTracks) { track ->
                    ContinueListeningCard(
                        track = track,
                        isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                        onClick = { onTrackClick(track, continueTracks) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onEditTags = { onEditTags(track) }
                    )
                }
            }
        }

        // Section: Recently played (Circular Artist Avatars)
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeaderWithSeeAll(
                title = "Recently played",
                onSeeAllClick = onSeeAllRecentlyPlayed
            )
            Spacer(modifier = Modifier.height(14.dp))

            val displayArtists = artists.ifEmpty {
                listOf(
                    Artist("a1", "Billie Eilish"),
                    Artist("a2", "Frank Ocean"),
                    Artist("a3", "Radiohead"),
                    Artist("a4", "Tyler, The Creator"),
                    Artist("a5", "SZA"),
                    Artist("a6", "Coldplay")
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(displayArtists) { artist ->
                    ArtistAvatarItem(
                        artist = artist,
                        onClick = { onArtistClick(artist) }
                    )
                }
            }
        }

        // Section: Recently added (Album Covers)
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeaderWithSeeAll(
                title = "Recently added",
                onSeeAllClick = onSeeAllRecentlyAdded
            )
            Spacer(modifier = Modifier.height(14.dp))

            val displayAlbums = albums.ifEmpty {
                listOf(
                    Album("alb1", "Charm", null, "Clairo", 2024),
                    Album("alb2", "HIT ME HARD AND SOFT", null, "Billie Eilish", 2024),
                    Album("alb3", "Lost and Found", null, "FKJ", 2024),
                    Album("alb4", "The Slow Rush", null, "Tame Impala", 2020),
                    Album("alb5", "GUTS", null, "Olivia Rodrigo", 2023)
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(displayAlbums) { album ->
                    RecentlyAddedAlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderWithSeeAll(
    title: String,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = GobeatsTextPrimary
        )

        Row(
            modifier = Modifier.clickable { onSeeAllClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "See all",
                fontSize = 13.sp,
                color = GobeatsTextMuted
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = ">",
                fontSize = 13.sp,
                color = GobeatsTextMuted
            )
        }
    }
}

@Composable
private fun ContinueListeningCard(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onEditTags: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GobeatsSurfaceElevated)
        ) {
            // Artwork canvas gradient
            Canvas(modifier = Modifier.fillMaxSize()) {
                val colors = when ((track.title.hashCode() % 3)) {
                    0 -> listOf(Color(0xFF2C3E50), Color(0xFF1E2833))
                    1 -> listOf(Color(0xFF5D2E46), Color(0xFF2E1925))
                    else -> listOf(Color(0xFF384358), Color(0xFF161E2E))
                }
                drawRect(brush = Brush.verticalGradient(colors))
            }

            // Play button circle overlay on card
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .size(32.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Bottom progress bar indicator
            LinearProgressIndicator(
                progress = { 0.45f },
                color = GobeatsAmber,
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = track.title,
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
                        text = { Text("Edit Tags") },
                        onClick = {
                            showMenu = false
                            onEditTags()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = GobeatsAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        Text(
            text = track.artistName,
            fontSize = 11.sp,
            color = GobeatsTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistAvatarItem(
    artist: Artist,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(82.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(GobeatsSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val colors = when ((artist.name.hashCode() % 3)) {
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
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = GobeatsTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun RecentlyAddedAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GobeatsSurfaceElevated)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val colors = when ((album.title.hashCode() % 3)) {
                    0 -> listOf(Color(0xFF5A3B33), Color(0xFF2E1916))
                    1 -> listOf(Color(0xFF2E4554), Color(0xFF15222B))
                    else -> listOf(Color(0xFF4B3B5E), Color(0xFF21162F))
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
    }
}
