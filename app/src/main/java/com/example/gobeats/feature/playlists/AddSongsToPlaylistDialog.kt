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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.gobeats.core.designsystem.components.formatDuration
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistDialog(
    playlist: Playlist,
    allLibraryTracks: List<Track>,
    existingPlaylistTrackIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirmAddTracks: (List<Track>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    val filteredTracks = remember(allLibraryTracks, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allLibraryTracks
        } else {
            allLibraryTracks.filter {
                it.title.lowercase().contains(q) ||
                        it.artistName.lowercase().contains(q) ||
                        it.albumTitle.lowercase().contains(q)
            }
        }
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
                .padding(bottom = 28.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add Songs",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Text(
                        text = "Adding to ${playlist.name}",
                        fontSize = 12.sp,
                        color = GobeatsAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = GobeatsTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search songs, artists, albums...", color = GobeatsTextMuted, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = GobeatsTextMuted)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = GobeatsTextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GobeatsTextPrimary,
                    unfocusedTextColor = GobeatsTextPrimary,
                    focusedContainerColor = GobeatsSurface,
                    unfocusedContainerColor = GobeatsSurface,
                    focusedBorderColor = GobeatsAmber,
                    unfocusedBorderColor = GobeatsBorderSubtle,
                    cursorColor = GobeatsAmber
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Selection Info & Select All Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedTrackIds.size} selected • ${filteredTracks.size} available",
                    fontSize = 12.sp,
                    color = GobeatsTextMuted
                )

                val availableToSelect = filteredTracks.filter { it.id !in existingPlaylistTrackIds }
                if (availableToSelect.isNotEmpty()) {
                    val allSelected = availableToSelect.all { it.id in selectedTrackIds }
                    Text(
                        text = if (allSelected) "Clear Selection" else "Select All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsAmber,
                        modifier = Modifier
                            .clickable {
                                if (allSelected) {
                                    selectedTrackIds.clear()
                                } else {
                                    availableToSelect.forEach { t ->
                                        if (t.id !in selectedTrackIds) {
                                            selectedTrackIds.add(t.id)
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 4.dp, horizontal = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = GobeatsBorderSubtle)

            if (filteredTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (allLibraryTracks.isEmpty())
                            "No tracks in library yet. Scan your device first."
                        else
                            "No matching tracks found.",
                        fontSize = 13.sp,
                        color = GobeatsTextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 380.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredTracks, key = { it.id }) { track ->
                        val alreadyInPlaylist = track.id in existingPlaylistTrackIds
                        val isSelected = track.id in selectedTrackIds

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !alreadyInPlaylist) {
                                    if (isSelected) {
                                        selectedTrackIds.remove(track.id)
                                    } else {
                                        selectedTrackIds.add(track.id)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) GobeatsSurfaceElevated else GobeatsSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) GobeatsAmber.copy(alpha = 0.6f) else GobeatsBorderSubtle
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
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
                                            tint = if (isSelected) GobeatsAmber else GobeatsTextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (alreadyInPlaylist) GobeatsTextMuted else GobeatsTextPrimary,
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

                                if (alreadyInPlaylist) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = GobeatsSurfaceElevated
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = GobeatsAmber,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Added",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = GobeatsTextMuted
                                            )
                                        }
                                    }
                                } else {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                if (track.id !in selectedTrackIds) selectedTrackIds.add(track.id)
                                            } else {
                                                selectedTrackIds.remove(track.id)
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = GobeatsAmber,
                                            checkmarkColor = Color(0xFF0C0A09),
                                            uncheckedColor = GobeatsTextMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Confirm CTA
            val selectedTracks = remember(selectedTrackIds.toList(), allLibraryTracks) {
                val idSet = selectedTrackIds.toSet()
                allLibraryTracks.filter { it.id in idSet }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable(enabled = selectedTracks.isNotEmpty()) {
                        onConfirmAddTracks(selectedTracks)
                    },
                shape = RoundedCornerShape(25.dp),
                color = if (selectedTracks.isNotEmpty()) GobeatsAmber else GobeatsSurfaceElevated
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = if (selectedTracks.isEmpty()) "Select Songs to Add" else "Add ${selectedTracks.size} Songs to Playlist",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTracks.isNotEmpty()) Color(0xFF0C0A09) else GobeatsTextMuted
                    )
                }
            }
        }
    }
}
