package com.example.gobeats.feature.library.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.TrackItemRow
import com.example.gobeats.core.model.Track
import com.example.gobeats.data.repository.LibraryRepository
import com.example.gobeats.data.repository.PlaybackRepository
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FoldersScreen(
    initialPath: String? = null,
    folderBrowserRepository: FolderBrowserRepository,
    playbackRepository: PlaybackRepository,
    libraryRepository: LibraryRepository,
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onEditTags: (Track) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPath by remember {
        mutableStateOf(initialPath ?: folderBrowserRepository.getDefaultRootPath())
    }
    var items by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val storageRoots = remember { folderBrowserRepository.getStorageRoots() }
    val playbackState by playbackRepository.playbackState.collectAsState()

    LaunchedEffect(currentPath) {
        isLoading = true
        items = folderBrowserRepository.listDirectory(currentPath)
        isLoading = false
    }

    val currentFolderFile = remember(currentPath) { File(currentPath) }
    val pathSegments = remember(currentPath) { FolderBrowserEngine.getPathSegments(currentPath) }
    val folderTracks = remember(items) { items.filter { !it.isDirectory && it.track != null }.mapNotNull { it.track } }
    val subfolders = remember(items) { items.filter { it.isDirectory } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GobeatsTextPrimary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Folder Browser",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Text(
                        text = currentFolderFile.name.ifBlank { "Root" },
                        fontSize = 12.sp,
                        color = GobeatsAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = GobeatsTextSecondary
                    )
                }
            }

            // Storage Root Chips Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                storageRoots.forEach { root ->
                    val isSelected = currentPath.startsWith(root.path)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            currentPath = root.path
                        },
                        label = {
                            Text(
                                text = root.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GobeatsAmber.copy(alpha = 0.2f),
                            selectedLabelColor = GobeatsAmber,
                            containerColor = GobeatsSurfaceElevated,
                            labelColor = GobeatsTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) GobeatsAmber else GobeatsBorderSubtle
                        )
                    )
                }
            }

            // Breadcrumbs Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GobeatsSurfaceElevated.copy(alpha = 0.5f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Root",
                    fontSize = 12.sp,
                    color = GobeatsTextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { currentPath = "/" }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )

                pathSegments.forEachIndexed { index, segment ->
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    val isLast = index == pathSegments.lastIndex
                    Text(
                        text = segment,
                        fontSize = 12.sp,
                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                        color = if (isLast) GobeatsAmber else GobeatsTextSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                currentPath = FolderBrowserEngine.buildPathForSegmentIndex(currentPath, index)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action Header (Play All / Shuffle) when folder contains tracks
            if (folderTracks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${folderTracks.size} ${if (folderTracks.size == 1) "track" else "tracks"}" +
                                    if (subfolders.isNotEmpty()) " • ${subfolders.size} folders" else "",
                            fontSize = 13.sp,
                            color = GobeatsTextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = {
                                if (folderTracks.isNotEmpty()) {
                                    playbackRepository.playTrack(folderTracks.first(), folderTracks)
                                }
                            },
                            shape = RoundedCornerShape(percent = 50),
                            color = GobeatsAmber
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = GobeatsObsidian,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Play All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GobeatsObsidian
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                if (folderTracks.isNotEmpty()) {
                                    val shuffled = folderTracks.shuffled()
                                    playbackRepository.playTrack(shuffled.first(), shuffled)
                                }
                            },
                            shape = RoundedCornerShape(percent = 50),
                            color = GobeatsSurfaceElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    tint = GobeatsTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Shuffle",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GobeatsTextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Main Listing
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GobeatsAmber)
                }
            } else if (items.isEmpty()) {
                // Empty Directory View
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = GobeatsTextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No audio files or folders here",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Supported formats: MP3, FLAC, M4A, WAV, OGG, OPUS, AAC",
                            fontSize = 12.sp,
                            color = GobeatsTextMuted
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            onClick = { currentPath = folderBrowserRepository.getDefaultRootPath() },
                            shape = RoundedCornerShape(percent = 50),
                            color = GobeatsSurfaceElevated
                        ) {
                            Text(
                                text = "Go to Music",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = GobeatsTextPrimary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Parent folder row if current directory is not root
                    val parentFile = currentFolderFile.parentFile
                    if (parentFile != null && parentFile.canRead() && currentPath != "/") {
                        item(key = "parent_folder") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentPath = parentFile.absolutePath }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = GobeatsSurfaceElevated
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.DriveFolderUpload,
                                            contentDescription = "Up",
                                            tint = GobeatsAmber,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = ".. Parent Folder",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GobeatsTextPrimary
                                    )
                                    Text(
                                        text = parentFile.name.ifBlank { "Root" },
                                        fontSize = 12.sp,
                                        color = GobeatsTextMuted
                                    )
                                }
                            }
                        }
                    }

                    // Directory rows
                    items(subfolders, key = { it.path }) { folderItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentPath = folderItem.path }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = GobeatsSurfaceElevated
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folder",
                                        tint = GobeatsAmber,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folderItem.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GobeatsTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${folderItem.itemCount} items",
                                    fontSize = 12.sp,
                                    color = GobeatsTextMuted
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val tracks = folderBrowserRepository.getFolderTracks(folderItem.path)
                                        if (tracks.isNotEmpty()) {
                                            playbackRepository.playTrack(tracks.first(), tracks)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Folder",
                                    tint = GobeatsAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = GobeatsTextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Track rows
                    items(folderTracks, key = { it.id }) { track ->
                        val isCurrentTrack = playbackState.currentTrack?.id == track.id
                        TrackItemRow(
                            track = track,
                            isPlaying = isCurrentTrack && playbackState.isPlaying,
                            onClick = { onTrackClick(track, folderTracks) },
                            onToggleFavorite = {
                                scope.launch { libraryRepository.toggleFavorite(track.id) }
                            },
                            onPlayNext = { playbackRepository.playNextTrack(track) },
                            onAddToQueue = { playbackRepository.addToQueue(track) },
                            onEditTags = { onEditTags(track) }
                        )
                    }
                }
            }
        }
    }
}
