package com.example.gobeats.feature.nowplaying

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.lyrics.LrcParser
import com.example.gobeats.core.lyrics.LrclibRecord
import com.example.gobeats.core.lyrics.LyricsData
import com.example.gobeats.core.lyrics.LyricsRepository
import com.example.gobeats.core.model.Track
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
fun SyncedLyricsView(
    track: Track,
    currentPositionMs: Long,
    lyricsRepository: LyricsRepository,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lyricsData by remember(track.id) { mutableStateOf<LyricsData?>(null) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedContent by remember { mutableStateOf("") }

    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember {
        val cleanArtist = if (track.artistName.equals("Local Audio", ignoreCase = true)) "" else track.artistName
        mutableStateOf("$cleanArtist ${track.title}".trim())
    }
    var searchResults by remember { mutableStateOf<List<LrclibRecord>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // File picker launcher for .lrc or .txt files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val parsed = lyricsRepository.importLyricsFromUri(track.id, uri)
                lyricsData = parsed
            }
        }
    }

    LaunchedEffect(track.id) {
        lyricsData = lyricsRepository.getLyricsForTrack(track)
    }

    val activeIndex = remember(lyricsData, currentPositionMs) {
        val lines = lyricsData?.lines ?: emptyList()
        if (lyricsData?.isSynced == true) {
            LrcParser.getActiveLineIndex(lines, currentPositionMs)
        } else {
            -1
        }
    }

    // Auto-scroll to active lyric line keeping it centered
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Lyrics Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LYRICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsAmber,
                        letterSpacing = 1.5.sp
                    )
                    if (lyricsData != null && lyricsData?.lines?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (lyricsData?.isSynced == true) Color(0x33E5A93C) else Color(0x22FFFFFF)
                        ) {
                            Text(
                                text = when {
                                    lyricsData?.source == "lrclib" && lyricsData?.isSynced == true -> "LRCLIB • SYNCED"
                                    lyricsData?.source == "companion" && lyricsData?.isSynced == true -> "LOCAL LRC • SYNCED"
                                    lyricsData?.source == "embedded" && lyricsData?.isSynced == true -> "EMBEDDED • SYNCED"
                                    lyricsData?.source == "embedded" -> "EMBEDDED"
                                    lyricsData?.isSynced == true -> "SYNCED"
                                    else -> "PLAIN"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lyricsData?.isSynced == true) GobeatsAmber else GobeatsTextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = track.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GobeatsTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showSearchDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Online Lyrics",
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = "Import LRC File",
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { showPasteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Paste Lyrics",
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lyrics",
                        tint = GobeatsTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val data = lyricsData
        if (data == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = GobeatsAmber,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Searching synced lyrics online...",
                        color = GobeatsTextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (data.lines.isEmpty()) {
            // Empty state with automated search & import actions
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = GobeatsAmber,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Lyrics Found",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No matching lyrics found automatically. You can search online with custom keywords, import a .lrc file, or paste lyrics.",
                        fontSize = 12.sp,
                        color = GobeatsTextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showSearchDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GobeatsAmber),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Online (LRCLIB)", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GobeatsTextPrimary)
                    ) {
                        Text("Import .LRC File")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = { showPasteDialog = true }) {
                        Text("Paste Text Lyrics", color = GobeatsTextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Synced / Unsynced Lyrics List
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 30.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(data.lines) { index, line ->
                    val isActive = index == activeIndex
                    val isPast = data.isSynced && activeIndex >= 0 && index < activeIndex

                    val textColor by animateColorAsState(
                        targetValue = when {
                            isActive -> GobeatsAmber
                            isPast -> GobeatsTextSecondary
                            else -> GobeatsTextMuted
                        },
                        animationSpec = tween(durationMillis = 300),
                        label = "lyricColor"
                    )

                    val textSize = if (isActive) 23.sp else 19.sp
                    val textWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                    val alpha = if (isActive) 1.0f else if (isPast) 0.75f else 0.45f

                    Text(
                        text = line.text,
                        fontSize = textSize,
                        fontWeight = textWeight,
                        color = textColor,
                        lineHeight = 32.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alpha)
                            .clickable(enabled = data.isSynced && line.timeMs >= 0) {
                                onSeek(line.timeMs)
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }

    // Online Search LRCLIB Dialog
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = GobeatsAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search Online Lyrics", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Query open LRCLIB database for synchronized lyrics:",
                        fontSize = 12.sp,
                        color = GobeatsTextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Artist Song Title", color = GobeatsTextMuted, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GobeatsAmber,
                                unfocusedBorderColor = GobeatsBorderSubtle,
                                focusedTextColor = GobeatsTextPrimary,
                                unfocusedTextColor = GobeatsTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    isSearchingOnline = true
                                    scope.launch {
                                        searchResults = lyricsRepository.searchOnlineLyrics(searchQuery.trim())
                                        isSearchingOnline = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GobeatsAmber),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Go", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isSearchingOnline) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GobeatsAmber, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Text(
                            text = "Results (${searchResults.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(searchResults) { item ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            scope.launch {
                                                val applied = lyricsRepository.applyOnlineLyric(track.id, item)
                                                lyricsData = applied
                                                showSearchDialog = false
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = GobeatsSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.trackName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = GobeatsTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.artistName + (if (!item.albumName.isNullOrBlank()) " • ${item.albumName}" else ""),
                                                fontSize = 11.sp,
                                                color = GobeatsTextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (!item.syncedLyrics.isNullOrBlank()) Color(0x3334D399) else Color(0x22FFFFFF)
                                        ) {
                                            Text(
                                                text = if (!item.syncedLyrics.isNullOrBlank()) "SYNCED" else "PLAIN",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (!item.syncedLyrics.isNullOrBlank()) Color(0xFF34D399) else GobeatsTextMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (searchQuery.isNotBlank()) {
                        Text(
                            text = "No results yet. Tap 'Go' to search.",
                            fontSize = 11.sp,
                            color = GobeatsTextMuted,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Close", color = GobeatsTextMuted)
                }
            }
        )
    }

    // Paste Custom Lyrics Dialog
    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text("Paste Lyrics", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
            },
            text = {
                Column {
                    Text(
                        text = "Paste standard [mm:ss.xx] timestamped LRC lines or plain lyrics text below:",
                        fontSize = 12.sp,
                        color = GobeatsTextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pastedContent,
                        onValueChange = { pastedContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        placeholder = {
                            Text("[00:15.00] Line 1\n[00:20.50] Line 2", color = GobeatsTextMuted, fontSize = 12.sp)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GobeatsAmber,
                            unfocusedBorderColor = GobeatsBorderSubtle,
                            focusedTextColor = GobeatsTextPrimary,
                            unfocusedTextColor = GobeatsTextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pastedContent.isNotBlank()) {
                            scope.launch {
                                val parsed = lyricsRepository.saveLyrics(track.id, pastedContent)
                                lyricsData = parsed
                                showPasteDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GobeatsAmber)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text("Cancel", color = GobeatsTextMuted)
                }
            }
        )
    }
}
