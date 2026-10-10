package com.example.gobeats.feature.nowplaying

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import android.content.pm.ActivityInfo
import android.os.Build
import com.example.gobeats.core.model.VideoAspectMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.audio.EqualizerManager
import com.example.gobeats.core.audio.SleepTimerManager
import com.example.gobeats.core.audio.SleepTimerState
import com.example.gobeats.core.designsystem.components.ArtworkCarousel
import com.example.gobeats.core.designsystem.components.QualityBadgesRow
import com.example.gobeats.core.designsystem.components.TactileScrubber
import com.example.gobeats.core.lyrics.LyricsRepository
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.core.model.Track
import com.example.gobeats.data.repository.PlaybackRepository
import com.example.gobeats.feature.settings.EqualizerDialog
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingBottomSheet(
    state: PlaybackUiState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onSpeedChange: (Float) -> Unit = {},
    equalizerManager: EqualizerManager? = null,
    onRewind10s: () -> Unit = {},
    onFastForward10s: () -> Unit = {},
    sleepTimerManager: SleepTimerManager? = null,
    lyricsRepository: LyricsRepository? = null,
    playbackRepository: PlaybackRepository? = null,
    onEditTags: (Track) -> Unit = {}
) {
    val track = state.currentTrack ?: return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSyncedLyricsView by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showTitleMenu by remember { mutableStateOf(false) }
    var showAudioSubtitlesDialog by remember { mutableStateOf(false) }
    var showVideoSettingsSheet by remember { mutableStateOf(false) }

    val cycleAspectMode: () -> Unit = {
        val nextMode = when (state.videoAspectMode) {
            VideoAspectMode.FIT -> VideoAspectMode.FILL_CROP
            VideoAspectMode.FILL_CROP -> VideoAspectMode.RATIO_16_9
            VideoAspectMode.RATIO_16_9 -> VideoAspectMode.STRETCH
            VideoAspectMode.STRETCH -> VideoAspectMode.FIT
        }
        playbackRepository?.setVideoAspectMode(nextMode)
    }

    val enterPiP: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context is android.app.Activity) {
            try {
                val aspect = if (state.videoWidth > 0 && state.videoHeight > 0) {
                    android.util.Rational(state.videoWidth, state.videoHeight)
                } else {
                    android.util.Rational(16, 9)
                }
                val validAspect = if (aspect.toFloat() in 0.418f..2.39f) aspect else android.util.Rational(16, 9)
                val params = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(validAspect)
                    .build()
                context.enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    val toggleFullscreen: () -> Unit = {
        if (context is android.app.Activity) {
            val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            context.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    val sleepTimerState by (sleepTimerManager?.state ?: MutableStateFlow(SleepTimerState.Inactive)).collectAsState()

    val previousTrack = if (state.queueIndex > 0) state.queue.getOrNull(state.queueIndex - 1) else null
    val nextTrack = if (state.queueIndex < state.queue.lastIndex) state.queue.getOrNull(state.queueIndex + 1) else null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GobeatsObsidian,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = GobeatsTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "gobeats",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        letterSpacing = (-0.3).sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (showSyncedLyricsView) "LYRICS" else "NOW PLAYING",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextMuted,
                            letterSpacing = 1.6.sp
                        )
                        if (sleepTimerState !is SleepTimerState.Inactive) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                onClick = { showSleepTimerDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF2B2118)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bedtime, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(10.dp))
                                    Spacer(Modifier.width(3.dp))
                                    val timeLabel = when (val s = sleepTimerState) {
                                        is SleepTimerState.Running -> "%02d:%02d".format(s.remainingSeconds / 60, s.remainingSeconds % 60)
                                        is SleepTimerState.EndOfTrack -> "End"
                                        else -> ""
                                    }
                                    Text(timeLabel, fontSize = 9.sp, color = GobeatsAmber, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showTopMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = GobeatsTextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Track Details") },
                            onClick = {
                                showTopMenu = false
                                showInfoDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                        if (equalizerManager != null) {
                            DropdownMenuItem(
                                text = { Text("Equalizer") },
                                onClick = {
                                    showTopMenu = false
                                    showEqualizerDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null) }
                            )
                        }
                        if (sleepTimerManager != null && playbackRepository != null) {
                            DropdownMenuItem(
                                text = { Text("Sleep Timer") },
                                onClick = {
                                    showTopMenu = false
                                    showSleepTimerDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Speed (${formatSpeed(state.playbackSpeed)})") },
                            onClick = {
                                showTopMenu = false
                                showSpeedDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Tags") },
                            onClick = {
                                showTopMenu = false
                                onEditTags(track)
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = GobeatsAmber) }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Track") },
                            onClick = {
                                showTopMenu = false
                                shareSong(context, track)
                            },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                        )

                        if (track.isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context is android.app.Activity) {
                            DropdownMenuItem(
                                text = { Text("Picture in Picture") },
                                onClick = {
                                    showTopMenu = false
                                    onDismiss()
                                    try {
                                        context.enterPictureInPictureMode(
                                            android.app.PictureInPictureParams.Builder().build()
                                        )
                                    } catch (_: Exception) {}
                                },
                                leadingIcon = { Icon(Icons.Default.PictureInPicture, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Song / Video Toggle Pill (Available whenever track is a video/MP4)
            if (track.isVideo) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = GobeatsSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { playbackRepository?.toggleVideoMode(false) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (!state.isVideoMode) GobeatsAmber else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = if (!state.isVideoMode) Color.Black else GobeatsTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Song",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!state.isVideoMode) Color.Black else GobeatsTextMuted
                                )
                            }
                        }

                        Surface(
                            onClick = { playbackRepository?.toggleVideoMode(true) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (state.isVideoMode) GobeatsAmber else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = if (state.isVideoMode) Color.Black else GobeatsTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Video",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (state.isVideoMode) Color.Black else GobeatsTextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Center View: Synced Lyrics View OR Video Player Surface OR Artwork Carousel
            if (showSyncedLyricsView && lyricsRepository != null) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF141318),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                ) {
                    SyncedLyricsView(
                        track = track,
                        currentPositionMs = state.currentPositionMs,
                        lyricsRepository = lyricsRepository,
                        onSeek = onSeek,
                        onDismiss = { showSyncedLyricsView = false }
                    )
                }
            } else if (state.isVideoMode && track.isVideo) {
                VideoPlayerSurface(
                    player = playbackRepository?.getPlayer(),
                    videoWidth = state.videoWidth,
                    videoHeight = state.videoHeight,
                    aspectMode = state.videoAspectMode,
                    currentPositionMs = state.currentPositionMs,
                    durationMs = state.durationMs,
                    isTouchLocked = state.isTouchLocked,
                    is2xSpeedActive = state.is2xSpeedPreviewActive,
                    showStatsForNerds = state.showStatsForNerds,
                    videoStats = state.videoStats,
                    onSeek = onSeek,
                    onCycleAspectMode = cycleAspectMode,
                    onToggleFullscreen = {
                        playbackRepository?.setFullscreenCinema(true)
                        toggleFullscreen()
                    },
                    onEnterPiP = enterPiP,
                    onOpenAudioSubtitles = { showAudioSubtitlesDialog = true },
                    onOpenSettings = { showVideoSettingsSheet = true },
                    onDoubleTapLeft = onRewind10s,
                    onDoubleTapRight = onFastForward10s,
                    onStart2xSpeed = { playbackRepository?.start2xSpeedPreview() },
                    onStop2xSpeed = { playbackRepository?.stop2xSpeedPreview() },
                    onPinchZoomIn = { playbackRepository?.setVideoAspectMode(VideoAspectMode.FILL_CROP) },
                    onPinchZoomOut = { playbackRepository?.setVideoAspectMode(VideoAspectMode.FIT) },
                    onSetTouchLocked = { playbackRepository?.setTouchLocked(it) },
                    onCloseStats = { playbackRepository?.toggleStatsForNerds() },
                    videoLoudnessBoostPercent = state.videoLoudnessBoostPercent,
                    resumePrompt = state.resumePrompt,
                    onVideoLoudnessBoostChange = { playbackRepository?.setVideoLoudnessBoost(it) },
                    onResumeSavedPosition = { playbackRepository?.resumeFromSavedPosition() },
                    onDismissResumePrompt = { playbackRepository?.dismissResumePrompt() }
                )
            } else {
                ArtworkCarousel(
                    currentTrack = track,
                    previousTrack = previousTrack,
                    nextTrack = nextTrack,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onRewind10s = onRewind10s,
                    onFastForward10s = onFastForward10s,
                    onArtworkClick = { showSyncedLyricsView = true }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title, Artist, Favorite, and Title Overflow Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = track.artistName,
                        fontSize = 15.sp,
                        color = GobeatsTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) GobeatsAmber else GobeatsTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showTitleMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = GobeatsTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showTitleMenu,
                        onDismissRequest = { showTitleMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Track Details") },
                            onClick = {
                                showTitleMenu = false
                                showInfoDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Track") },
                            onClick = {
                                showTitleMenu = false
                                shareSong(context, track)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quality Badges & Playback Speed Selector Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QualityBadgesRow(
                    format = track.format,
                    isLossless = true,
                    isHiRes = true
                )

                // Tactile Speed Control Pill
                Surface(
                    onClick = { showSpeedDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = GobeatsSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed",
                            tint = GobeatsAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatSpeed(state.playbackSpeed),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsTextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tactile Scrubber
            TactileScrubber(
                positionMs = state.currentPositionMs,
                durationMs = state.durationMs,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleMode) GobeatsAmber else GobeatsTextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Previous (|<)
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = GobeatsTextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Play / Pause Hero Button (White circular with black pause bars)
                Surface(
                    onClick = onTogglePlayPause,
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Next (>|)
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = GobeatsTextPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Repeat
                IconButton(onClick = onToggleRepeat) {
                    val icon = if (state.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = if (state.repeatMode != RepeatMode.OFF) GobeatsAmber else GobeatsTextMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4 Circular Quick Action Pills (Lyrics, Queue, Audio, Share)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                QuickActionButton(
                    icon = Icons.Default.ChatBubbleOutline,
                    label = "Lyrics",
                    onClick = { showSyncedLyricsView = !showSyncedLyricsView }
                )
                QuickActionButton(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = "Queue",
                    onClick = onOpenQueue
                )
                QuickActionButton(
                    icon = Icons.Default.Equalizer,
                    label = "Audio",
                    onClick = {
                        if (equalizerManager != null) showEqualizerDialog = true
                    }
                )
                QuickActionButton(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = { shareSong(context, track) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Up Next Drawer Card at Bottom
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = Color(0xFF16151A),
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenQueue() }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .background(GobeatsBorderSubtle, RoundedCornerShape(2.dp))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(GobeatsSurfaceElevated, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = GobeatsAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Up Next",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GobeatsTextPrimary
                                )
                                Text(
                                    text = "${state.queue.size} tracks in queue",
                                    fontSize = 11.sp,
                                    color = GobeatsTextMuted
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expand",
                            tint = GobeatsTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text(
                    text = "Playback Speed",
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    speeds.forEach { speed ->
                        val isSelected = kotlin.math.abs(state.playbackSpeed - speed) < 0.05f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSpeedChange(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) GobeatsAmber else GobeatsTextPrimary
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = GobeatsAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = GobeatsAmber)
                }
            }
        )
    }

    // Track Details Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text("Song Details", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    InfoRow(label = "Title", value = track.title)
                    InfoRow(label = "Artist", value = track.artistName)
                    InfoRow(label = "Album", value = track.albumTitle)
                    InfoRow(label = "Format", value = track.format)
                    InfoRow(label = "Bitrate", value = "${track.bitrate} kbps")
                    InfoRow(label = "Sample Rate", value = "${track.sampleRate} Hz")
                    if (track.fileSize > 0) {
                        InfoRow(label = "Size", value = String.format("%.2f MB", track.fileSize.toDouble() / (1024 * 1024)))
                    }
                    InfoRow(label = "Location", value = track.fileUri)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Done", color = GobeatsAmber)
                }
            }
        )
    }

    // Equalizer Dialog
    if (showEqualizerDialog && equalizerManager != null) {
        EqualizerDialog(
            equalizerManager = equalizerManager,
            currentTrack = track,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog && sleepTimerManager != null && playbackRepository != null) {
        SleepTimerDialog(
            sleepTimerManager = sleepTimerManager,
            playbackRepository = playbackRepository,
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    // Audio Tracks & Subtitles Dialog
    if (showAudioSubtitlesDialog) {
        AlertDialog(
            onDismissRequest = { showAudioSubtitlesDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text("Audio & Subtitles", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Audio Track", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GobeatsAmber)
                    Spacer(Modifier.height(8.dp))
                    if (state.audioTracks.isEmpty()) {
                        Text("Default Audio Stream", fontSize = 13.sp, color = GobeatsTextMuted)
                    } else {
                        state.audioTracks.forEachIndexed { index, trackName ->
                            val isSelected = state.selectedAudioTrackIndex == index
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playbackRepository?.selectAudioTrack(index)
                                    }
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF27272A) else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = trackName,
                                        fontSize = 13.sp,
                                        color = if (isSelected) GobeatsAmber else GobeatsTextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Text("Subtitles", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GobeatsAmber)
                    Spacer(Modifier.height(8.dp))

                    // Option: Subtitles Off
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playbackRepository?.selectSubtitleTrack(-1)
                            }
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = if (state.selectedSubtitleTrackIndex == -1) Color(0xFF27272A) else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Off",
                                fontSize = 13.sp,
                                color = if (state.selectedSubtitleTrackIndex == -1) GobeatsAmber else GobeatsTextPrimary,
                                fontWeight = if (state.selectedSubtitleTrackIndex == -1) FontWeight.Bold else FontWeight.Normal
                            )
                            if (state.selectedSubtitleTrackIndex == -1) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    state.subtitleTracks.forEachIndexed { index, subName ->
                        val isSelected = state.selectedSubtitleTrackIndex == index
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackRepository?.selectSubtitleTrack(index)
                                }
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF27272A) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = subName,
                                    fontSize = 13.sp,
                                    color = if (isSelected) GobeatsAmber else GobeatsTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioSubtitlesDialog = false }) {
                    Text("Done", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showVideoSettingsSheet) {
        VideoSettingsBottomSheet(
            state = state,
            onDismiss = { showVideoSettingsSheet = false },
            onSelectQuality = { playbackRepository?.selectVideoQuality(it) },
            onSelectSpeed = { playbackRepository?.setPlaybackSpeed(it) },
            onSelectAudioTrack = { playbackRepository?.selectAudioTrack(it) },
            onSelectSubtitleTrack = { playbackRepository?.selectSubtitleTrack(it) },
            onSelectAspectMode = { playbackRepository?.setVideoAspectMode(it) },
            onToggleLoop = { playbackRepository?.toggleVideoLoop() },
            onToggleStatsForNerds = { playbackRepository?.toggleStatsForNerds() },
            onOpenSleepTimer = { showSleepTimerDialog = true },
            onSelectLoudnessBoost = { playbackRepository?.setVideoLoudnessBoost(it) },
            onPickExternalSubtitle = { playbackRepository?.addExternalSubtitle(it) }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = GobeatsTextMuted)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            color = GobeatsTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
}

private fun shareSong(context: Context, track: Track) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "Listening to \"${track.title}\" by ${track.artistName} on gobeats!")
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(Color(0xFF1E1D24), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = GobeatsTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = GobeatsTextMuted
        )
    }
}
