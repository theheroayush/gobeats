package com.example.gobeats.feature.nowplaying

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.VideoAspectMode
import com.example.gobeats.core.model.VideoQualityOption
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary

private enum class VideoSettingsSubMenu {
    MAIN,
    QUALITY,
    SPEED,
    AUDIO,
    SUBTITLES,
    ASPECT_RATIO,
    LOUDNESS_BOOST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSettingsBottomSheet(
    state: PlaybackUiState,
    onDismiss: () -> Unit,
    onSelectQuality: (String) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onSelectAspectMode: (VideoAspectMode) -> Unit,
    onToggleLoop: () -> Unit,
    onToggleStatsForNerds: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSelectLoudnessBoost: (Int) -> Unit = {},
    onPickExternalSubtitle: (Uri) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentSubMenu by remember { mutableStateOf(VideoSettingsSubMenu.MAIN) }

    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onPickExternalSubtitle(uri)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GobeatsObsidian,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentSubMenu != VideoSettingsSubMenu.MAIN) {
                    IconButton(onClick = { currentSubMenu = VideoSettingsSubMenu.MAIN }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GobeatsTextPrimary)
                    }
                } else {
                    Spacer(Modifier.size(40.dp))
                }

                Text(
                    text = when (currentSubMenu) {
                        VideoSettingsSubMenu.MAIN -> "Video Settings"
                        VideoSettingsSubMenu.QUALITY -> "Quality"
                        VideoSettingsSubMenu.SPEED -> "Playback Speed"
                        VideoSettingsSubMenu.AUDIO -> "Audio Track"
                        VideoSettingsSubMenu.SUBTITLES -> "Subtitles / Captions"
                        VideoSettingsSubMenu.ASPECT_RATIO -> "Aspect Ratio"
                        VideoSettingsSubMenu.LOUDNESS_BOOST -> "Volume Booster (Loudness)"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = GobeatsTextMuted)
                }
            }

            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = currentSubMenu,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "video_settings_transition"
            ) { subMenu ->
                when (subMenu) {
                    VideoSettingsSubMenu.MAIN -> {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            // 1. Quality
                            val activeQualityLabel = state.videoQualityOptions.find { it.isSelected }?.label
                                ?: state.videoQualityOptions.find { it.isAuto }?.label
                                ?: "Auto"
                            SettingRow(
                                icon = Icons.Default.HighQuality,
                                title = "Quality",
                                subtitle = activeQualityLabel,
                                onClick = { currentSubMenu = VideoSettingsSubMenu.QUALITY }
                            )

                            // 2. Playback Speed
                            val speedLabel = if (state.playbackSpeed == 1.0f) "Normal" else "${state.playbackSpeed}x"
                            SettingRow(
                                icon = Icons.Default.Speed,
                                title = "Playback speed",
                                subtitle = speedLabel,
                                onClick = { currentSubMenu = VideoSettingsSubMenu.SPEED }
                            )

                            // 3. Audio Track
                            val activeAudio = state.audioTracks.getOrNull(state.selectedAudioTrackIndex) ?: "Default Track"
                            SettingRow(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                title = "Audio track",
                                subtitle = activeAudio,
                                onClick = { currentSubMenu = VideoSettingsSubMenu.AUDIO }
                            )

                            // 4. Volume Booster (Loudness Booster)
                            val boostLabel = if (state.videoLoudnessBoostPercent > 100) "${state.videoLoudnessBoostPercent}% [Boost Active]" else "100% (Normal)"
                            SettingRow(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                title = "Volume booster",
                                subtitle = boostLabel,
                                onClick = { currentSubMenu = VideoSettingsSubMenu.LOUDNESS_BOOST }
                            )

                            // 4. Subtitles
                            val activeSub = if (state.selectedSubtitleTrackIndex == -1) "Off"
                            else state.subtitleTracks.getOrNull(state.selectedSubtitleTrackIndex) ?: "Active"
                            SettingRow(
                                icon = Icons.Default.Subtitles,
                                title = "Subtitles",
                                subtitle = activeSub,
                                onClick = { currentSubMenu = VideoSettingsSubMenu.SUBTITLES }
                            )

                            // 5. Aspect Ratio
                            val aspectLabel = when (state.videoAspectMode) {
                                VideoAspectMode.FIT -> "Fit (Letterbox)"
                                VideoAspectMode.FILL_CROP -> "Fill (Zoom to fill)"
                                VideoAspectMode.STRETCH -> "Stretch to screen"
                                VideoAspectMode.RATIO_16_9 -> "16:9 Standard"
                            }
                            SettingRow(
                                icon = Icons.Default.AspectRatio,
                                title = "Aspect ratio",
                                subtitle = aspectLabel,
                                onClick = { currentSubMenu = VideoSettingsSubMenu.ASPECT_RATIO }
                            )

                            // 6. Loop Video Toggle
                            SettingToggleRow(
                                icon = Icons.Default.Repeat,
                                title = "Loop video",
                                subtitle = "Continuously repeat this video",
                                checked = state.isVideoLoopEnabled,
                                onCheckedChange = { onToggleLoop() }
                            )

                            // 7. Stats for Nerds Toggle
                            SettingToggleRow(
                                icon = Icons.Default.Analytics,
                                title = "Stats for nerds",
                                subtitle = "Real-time technical video performance HUD",
                                checked = state.showStatsForNerds,
                                onCheckedChange = { onToggleStatsForNerds() }
                            )

                            // 8. Sleep Timer
                            SettingRow(
                                icon = Icons.Default.Bedtime,
                                title = "Sleep timer",
                                subtitle = "Pause video after timer expires",
                                onClick = {
                                    onDismiss()
                                    onOpenSleepTimer()
                                }
                            )
                        }
                    }

                    VideoSettingsSubMenu.QUALITY -> {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            if (state.isSingleRenditionLocalFile) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0x26FF9800),
                                    border = BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Single-rendition local offline file — downscaling disabled to prevent black screen",
                                        fontSize = 12.sp,
                                        color = GobeatsAmber,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            if (state.videoQualityOptions.isEmpty()) {
                                Text(
                                    text = "Auto (Original Quality)",
                                    fontSize = 14.sp,
                                    color = GobeatsAmber,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                state.videoQualityOptions.forEach { opt ->
                                    SelectableOptionRow(
                                        title = opt.label,
                                        subtitle = if (opt.resolutionLabel.isNotBlank()) opt.resolutionLabel else null,
                                        badge = if (opt.isAuto) "DEFAULT" else null,
                                        isSelected = opt.id == state.selectedVideoQualityId,
                                        onClick = {
                                            onSelectQuality(opt.id)
                                            currentSubMenu = VideoSettingsSubMenu.MAIN
                                        }
                                    )
                                }
                            }
                        }
                    }

                    VideoSettingsSubMenu.SPEED -> {
                        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            speeds.forEach { sp ->
                                val label = if (sp == 1.0f) "Normal (1.0x)" else "${sp}x"
                                SelectableOptionRow(
                                    title = label,
                                    subtitle = null,
                                    badge = if (sp == 1.0f) "DEFAULT" else null,
                                    isSelected = (state.playbackSpeed - sp) in -0.01f..0.01f,
                                    onClick = {
                                        onSelectSpeed(sp)
                                        currentSubMenu = VideoSettingsSubMenu.MAIN
                                    }
                                )
                            }
                        }
                    }

                    VideoSettingsSubMenu.AUDIO -> {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            if (state.audioTracks.isEmpty()) {
                                Text(
                                    text = "Default Audio Stream",
                                    fontSize = 14.sp,
                                    color = GobeatsTextMuted,
                                    modifier = Modifier.padding(16.dp)
                                )
                            } else {
                                state.audioTracks.forEachIndexed { index, trackName ->
                                    SelectableOptionRow(
                                        title = trackName,
                                        subtitle = null,
                                        isSelected = state.selectedAudioTrackIndex == index,
                                        onClick = {
                                            onSelectAudioTrack(index)
                                            currentSubMenu = VideoSettingsSubMenu.MAIN
                                        }
                                    )
                                }
                            }
                        }
                    }

                    VideoSettingsSubMenu.SUBTITLES -> {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            SelectableOptionRow(
                                title = "Load external subtitle (.srt / .vtt)...",
                                subtitle = "Pick from device storage",
                                badge = "CUSTOM",
                                isSelected = false,
                                onClick = {
                                    subtitleFilePicker.launch(arrayOf("*/*", "application/x-subrip", "text/vtt", "text/plain"))
                                    currentSubMenu = VideoSettingsSubMenu.MAIN
                                }
                            )

                            SelectableOptionRow(
                                title = "Off",
                                subtitle = null,
                                isSelected = state.selectedSubtitleTrackIndex == -1,
                                onClick = {
                                    onSelectSubtitleTrack(-1)
                                    currentSubMenu = VideoSettingsSubMenu.MAIN
                                }
                            )

                            state.subtitleTracks.forEachIndexed { index, subName ->
                                SelectableOptionRow(
                                    title = subName,
                                    subtitle = null,
                                    isSelected = state.selectedSubtitleTrackIndex == index,
                                    onClick = {
                                        onSelectSubtitleTrack(index)
                                        currentSubMenu = VideoSettingsSubMenu.MAIN
                                    }
                                )
                            }
                        }
                    }

                    VideoSettingsSubMenu.LOUDNESS_BOOST -> {
                        val boostPresets = listOf(
                            100 to "100% (Standard Max Volume)",
                            125 to "125% (+3.7 dB Dialogue Boost)",
                            150 to "150% (+7.5 dB Loudness Boost)",
                            175 to "175% (+11.2 dB High Gain)",
                            200 to "200% (+15.0 dB Maximum Booster)"
                        )
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            boostPresets.forEach { (pct, desc) ->
                                SelectableOptionRow(
                                    title = desc,
                                    subtitle = if (pct > 100) "Hardware dynamic compressor & limiter active" else "Standard hardware audio stream",
                                    badge = if (pct == 100) "DEFAULT" else if (pct == 200) "MAX BOOST" else null,
                                    isSelected = state.videoLoudnessBoostPercent == pct,
                                    onClick = {
                                        onSelectLoudnessBoost(pct)
                                        currentSubMenu = VideoSettingsSubMenu.MAIN
                                    }
                                )
                            }
                        }
                    }

                    VideoSettingsSubMenu.ASPECT_RATIO -> {
                        val modes = listOf(
                            VideoAspectMode.FIT to "Fit (Original Aspect Ratio)",
                            VideoAspectMode.FILL_CROP to "Fill (Zoom to Fill Screen)",
                            VideoAspectMode.STRETCH to "Stretch to Fill",
                            VideoAspectMode.RATIO_16_9 to "16:9 Cinema Standard"
                        )
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            modes.forEach { (mode, desc) ->
                                SelectableOptionRow(
                                    title = desc,
                                    subtitle = null,
                                    isSelected = state.videoAspectMode == mode,
                                    onClick = {
                                        onSelectAspectMode(mode)
                                        currentSubMenu = VideoSettingsSubMenu.MAIN
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E1D24),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                Text(text = subtitle, fontSize = 12.sp, color = GobeatsTextMuted)
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E1D24),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                Text(text = subtitle, fontSize = 12.sp, color = GobeatsTextMuted)
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = GobeatsAmber,
                    uncheckedThumbColor = GobeatsTextMuted,
                    uncheckedTrackColor = Color(0xFF27272A)
                )
            )
        }
    }
}

@Composable
private fun SelectableOptionRow(
    title: String,
    subtitle: String?,
    badge: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0x33E5A93C) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, GobeatsAmber.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) GobeatsAmber else GobeatsTextPrimary
                    )
                    if (badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF27272A)
                        ) {
                            Text(
                                text = badge,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsAmber,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (subtitle != null) {
                    Text(text = subtitle, fontSize = 11.sp, color = GobeatsTextMuted)
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = GobeatsAmber,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
