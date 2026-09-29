package com.example.gobeats.feature.playlists

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.designsystem.components.GobeatsPrimaryButton
import com.example.gobeats.core.designsystem.components.GobeatsSecondaryButton
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.playlist.PlaylistImportResult
import com.example.gobeats.core.playlist.PlaylistImporter
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderFocused
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSuccess
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistBottomSheet(
    allTracks: List<Track>,
    onDismiss: () -> Unit,
    onImportConfirm: (playlistName: String, matchedTracks: List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = File (.csv/.m3u), 1 = Paste Text
    var playlistName by remember { mutableStateOf("Imported Playlist") }
    var rawTextInput by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val importResult: PlaylistImportResult? by remember(rawTextInput, playlistName, allTracks) {
        derivedStateOf {
            if (rawTextInput.isBlank()) {
                null
            } else {
                val (parsedName, entries) = PlaylistImporter.parseText(rawTextInput, playlistName)
                PlaylistImporter.matchTracks(
                    imported = entries,
                    library = allTracks,
                    playlistName = playlistName.ifBlank { parsedName }
                )
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val (name, textContent) = withContext(Dispatchers.IO) {
                    var displayName = "Spotify Import"
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx != -1 && cursor.moveToFirst()) {
                                displayName = cursor.getString(nameIdx) ?: displayName
                            }
                        }
                    } catch (_: Exception) {}

                    val content = try {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    Pair(displayName, content)
                }

                if (textContent.isNotBlank()) {
                    selectedFileName = name
                    playlistName = name.substringBeforeLast('.').replace('_', ' ').ifBlank { "Imported Playlist" }
                    rawTextInput = textContent
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GobeatsSurfaceElevated,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GobeatsBorderFocused) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import Playlist",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Text(
                        text = "Spotify CSV • Apple Music TSV • M3U / M3U8 • Text",
                        fontSize = 11.sp,
                        color = GobeatsAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = GobeatsTextMuted)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GobeatsObsidian)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf("Select File (.CSV / .M3U)", "Paste Tracklist")
                tabs.forEachIndexed { idx, label ->
                    val isSelected = selectedTab == idx
                    Surface(
                        onClick = { selectedTab = idx },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) GobeatsAmber else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) GobeatsObsidian else GobeatsTextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playlist Name Field
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name", fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GobeatsTextPrimary,
                    unfocusedTextColor = GobeatsTextPrimary,
                    focusedContainerColor = GobeatsSurface,
                    unfocusedContainerColor = GobeatsSurface,
                    focusedBorderColor = GobeatsAmber,
                    unfocusedBorderColor = GobeatsBorderSubtle,
                    focusedLabelColor = GobeatsAmber,
                    unfocusedLabelColor = GobeatsTextMuted,
                    cursorColor = GobeatsAmber
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0) {
                // File Picker Dropzone Card
                Surface(
                    onClick = { filePickerLauncher.launch("*/*") },
                    shape = RoundedCornerShape(16.dp),
                    color = GobeatsSurface,
                    border = BorderStroke(1.dp, if (selectedFileName != null) GobeatsAmber else GobeatsBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = GobeatsAmber,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = selectedFileName ?: "Tap to choose .CSV, .M3U, .M3U8, or .TXT file",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Supports Exportify Spotify CSVs, Apple Music exports, and M3U playlists",
                            fontSize = 11.sp,
                            color = GobeatsTextMuted
                        )
                    }
                }
            } else {
                // Paste Tracklist Input
                OutlinedTextField(
                    value = rawTextInput,
                    onValueChange = { rawTextInput = it },
                    label = { Text("Paste songs (e.g. 'The Weeknd - Starboy' or CSV rows)", fontSize = 12.sp) },
                    minLines = 5,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GobeatsTextPrimary,
                        unfocusedTextColor = GobeatsTextPrimary,
                        focusedContainerColor = GobeatsSurface,
                        unfocusedContainerColor = GobeatsSurface,
                        focusedBorderColor = GobeatsAmber,
                        unfocusedBorderColor = GobeatsBorderSubtle,
                        focusedLabelColor = GobeatsAmber,
                        unfocusedLabelColor = GobeatsTextMuted,
                        cursorColor = GobeatsAmber
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Live Match Diagnostics Card
            importResult?.let { result ->
                if (result.totalCount > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = GobeatsObsidian,
                        border = BorderStroke(1.dp, GobeatsBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (result.matchedTracks.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (result.matchedTracks.isNotEmpty()) GobeatsSuccess else GobeatsAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Matched ${result.matchedTracks.size} of ${result.totalCount} songs",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GobeatsTextPrimary
                                    )
                                }
                                Text(
                                    text = "${result.matchRatePercent}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GobeatsAmber
                                )
                            }

                            if (result.matchedTracks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                result.matchedTracks.take(4).forEach { track ->
                                    Text(
                                        text = "✓ ${track.artistName} — ${track.title}",
                                        fontSize = 11.sp,
                                        color = GobeatsTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    )
                                }
                                if (result.matchedTracks.size > 4) {
                                    Text(
                                        text = "+ ${result.matchedTracks.size - 4} more matched tracks in your library",
                                        fontSize = 11.sp,
                                        color = GobeatsAmber,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            if (result.unmatchedEntries.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${result.unmatchedEntries.size} songs not found on device storage",
                                    fontSize = 11.sp,
                                    color = GobeatsTextMuted
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val matchedCount = importResult?.matchedTracks?.size ?: 0
            GobeatsPrimaryButton(
                text = if (matchedCount > 0) "Import $matchedCount Matched Songs" else "Create Empty Imported Playlist",
                enabled = rawTextInput.isNotBlank() || selectedFileName != null,
                onClick = {
                    val finalName = playlistName.trim().ifBlank { "Imported Playlist" }
                    val tracksToImport = importResult?.matchedTracks ?: emptyList()
                    onImportConfirm(finalName, tracksToImport)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            GobeatsSecondaryButton(
                text = "Cancel",
                onClick = onDismiss
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
