package com.example.gobeats.feature.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.audio.EqualizerManager
import com.example.gobeats.core.util.DefaultAppHelper
import com.example.gobeats.data.repository.SettingsRepository
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
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    equalizerManager: EqualizerManager,
    onDevicesClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    sleepTimerManager: com.example.gobeats.core.audio.SleepTimerManager? = null,
    playbackRepository: com.example.gobeats.data.repository.PlaybackRepository? = null,
    dbHelper: com.example.gobeats.core.database.GobeatsDatabaseHelper? = null,
    onLibraryRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val profile by settingsRepository.userProfile.collectAsState()
    val settings by settingsRepository.settings.collectAsState()
    val sleepTimerState by (sleepTimerManager?.state ?: kotlinx.coroutines.flow.MutableStateFlow(com.example.gobeats.core.audio.SleepTimerState.Inactive)).collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(profile.name) }
    var editEmail by remember { mutableStateOf(profile.email) }

    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showPlaybackSettingsDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    val exportBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && dbHelper != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        com.example.gobeats.core.backup.BackupRestoreEngine.exportToStream(dbHelper, stream)
                    }
                    Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importBackupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && dbHelper != null) {
            scope.launch {
                try {
                    val result = context.contentResolver.openInputStream(uri)?.use { stream ->
                        com.example.gobeats.core.backup.BackupRestoreEngine.restoreFromStream(dbHelper, stream)
                    }
                    if (result?.success == true) {
                        onLibraryRefresh()
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, result?.message ?: "Failed to restore backup", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Top App Bar: Brand + Search & Overflow
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                        color = GobeatsAmber,
                        letterSpacing = 1.2.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = GobeatsTextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { showTopMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = GobeatsTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("About gobeats") },
                                onClick = {
                                    showTopMenu = false
                                    showAboutDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Temporary Cache") },
                                onClick = {
                                    showTopMenu = false
                                    try {
                                        context.cacheDir.deleteRecursively()
                                        Toast.makeText(context, "Temporary cache cleared", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }
            }
        }

        // Header: Settings + Subtitle
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "Settings",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = GobeatsTextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Make gobeats yours.",
                    fontSize = 13.sp,
                    color = GobeatsTextMuted
                )
            }
        }

        // User Profile Card (1:1 with mockup_settings_v2.png)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = GobeatsSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // User Avatar with Camera Badge
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFE5A93C), Color(0xFFC2410C), Color(0xFF4A044E))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.name.take(1).uppercase(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Camera Mini Badge
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(GobeatsObsidian)
                                    .padding(1.dp)
                                    .clip(CircleShape)
                                    .background(GobeatsSurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = GobeatsTextPrimary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = profile.name,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsTextPrimary
                            )
                            Text(
                                text = profile.email,
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                        }
                    }

                    // Edit Profile pill button
                    Surface(
                        modifier = Modifier.clickable {
                            editName = profile.name
                            editEmail = profile.email
                            showEditProfileDialog = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = GobeatsSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Edit Profile",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = GobeatsTextPrimary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = GobeatsTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // gobeats Premium Card (matching mockup_settings_v2.png)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF16131F),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C243B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF241833), Color(0xFF15121B))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "gobeats Premium",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GobeatsTextPrimary
                                )
                                Text(
                                    text = "Ad-free music. Higher sound quality. More control.",
                                    fontSize = 11.sp,
                                    color = GobeatsTextMuted
                                )
                            }
                        }

                        // Gradient Upgrade Button
                        Surface(
                            modifier = Modifier.clickable { showPremiumDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFEC4899)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Upgrade",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Music & Audio
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Music & Audio",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            SettingsItemRow(
                icon = Icons.Default.GraphicEq,
                iconTint = Color(0xFFEC4899),
                title = "Audio Quality",
                subtitle = "Choose your preferred sound quality",
                trailingValue = settings.audioQuality,
                onClick = { showAudioQualityDialog = true }
            )

            SettingsItemRow(
                icon = Icons.Default.FileDownload,
                iconTint = Color(0xFF38BDF8),
                title = "Downloads",
                subtitle = "Manage offline music and cache",
                onClick = { showDownloadsDialog = true }
            )

            SettingsItemRow(
                icon = Icons.Default.PlayCircleOutline,
                iconTint = Color(0xFF34D399),
                title = "Playback Settings",
                subtitle = "Crossfade, gapless playback, and more",
                onClick = { showPlaybackSettingsDialog = true }
            )

            SettingsItemRow(
                icon = Icons.Default.Tune,
                iconTint = Color(0xFFA855F7),
                title = "Equalizer",
                subtitle = "Fine-tune your sound",
                trailingValue = "Custom",
                onClick = { showEqualizerDialog = true }
            )

            SettingsItemRow(
                icon = Icons.Default.Bedtime,
                iconTint = GobeatsAmber,
                title = "Sleep Timer",
                subtitle = "Set audio stop timer and gentle fade out",
                trailingValue = when (val s = sleepTimerState) {
                    is com.example.gobeats.core.audio.SleepTimerState.Running -> "%02d:%02d remaining".format(s.remainingSeconds / 60, s.remainingSeconds % 60)
                    is com.example.gobeats.core.audio.SleepTimerState.EndOfTrack -> "End of song"
                    else -> "Off"
                },
                onClick = { showSleepTimerDialog = true }
            )
        }

        // Section: Default Media Player
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Default Media Player",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            val isDefault = DefaultAppHelper.isGobeatsDefaultMusicPlayer(context)
            SettingsItemRow(
                icon = Icons.Default.MusicNote,
                iconTint = GobeatsAmber,
                title = "Set as Default Player",
                subtitle = "Open music and videos automatically from WhatsApp, Files, and browser",
                trailingValue = if (isDefault) "Active" else "Configure",
                onClick = {
                    DefaultAppHelper.openDefaultAppsSettings(context)
                    Toast.makeText(
                        context,
                        "Select gobeats under Default apps or 'Open by default' for audio and video",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }

        // Section: Data & Backup
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Data & Backup",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            SettingsItemRow(
                icon = Icons.Default.FileDownload,
                iconTint = Color(0xFF10B981),
                title = "Backup Playlists & Favorites",
                subtitle = "Export your curated library to a JSON backup file",
                onClick = {
                    val defaultName = "gobeats_backup_${System.currentTimeMillis()}.json"
                    exportBackupLauncher.launch(defaultName)
                }
            )

            SettingsItemRow(
                icon = Icons.Default.Sync,
                iconTint = Color(0xFF6366F1),
                title = "Restore from Backup",
                subtitle = "Import playlists and favorites from a previous backup",
                onClick = {
                    importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                }
            )
        }

        // Section: Devices & Sync
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Devices & Sync",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            SettingsItemRow(
                icon = Icons.Default.Devices,
                iconTint = Color(0xFF818CF8),
                title = "Connected Devices",
                subtitle = "Manage your devices",
                trailingValue = "3 devices",
                onClick = onDevicesClick
            )

            // Sync Library toggle item
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 3.dp),
                shape = RoundedCornerShape(14.dp),
                color = GobeatsSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF06B6D4).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "Sync Library",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                            Text(
                                text = "Keep your music everywhere",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                        }
                    }

                    Switch(
                        checked = settings.syncLibraryEnabled,
                        onCheckedChange = { settingsRepository.toggleSyncLibrary(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF8B5CF6),
                            uncheckedThumbColor = GobeatsTextMuted,
                            uncheckedTrackColor = GobeatsSurfaceElevated
                        )
                    )
                }
            }
        }

        // Section: Appearance
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Appearance",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            SettingsItemRow(
                icon = Icons.Default.NightlightRound,
                iconTint = Color(0xFF38BDF8),
                title = "Theme",
                subtitle = "Choose how gobeats looks",
                trailingValue = settings.theme,
                onClick = { showThemeDialog = true }
            )

            // App Icon Picker Row (1:1 with mockup_settings_v2.png)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = GobeatsSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFEC4899).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ColorLens,
                                contentDescription = null,
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "App Icon",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "Pick your style",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted,
                                maxLines = 1
                            )
                        }
                    }

                    // 4 Style Icons Selector horizontally on the right (1:1 with mockup_settings_v2.png)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Neon Purple Note (Official Mockup style)
                        AppIconOption(
                            isSelected = settings.appIconStyle == "neon_purple",
                            gradient = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
                            iconTint = Color.White,
                            onClick = {
                                settingsRepository.setAppIconStyle("neon_purple")
                                com.example.gobeats.core.util.AppIconManager.setAppIcon(
                                    context,
                                    com.example.gobeats.core.util.AppIconManager.STYLE_NEON_PURPLE
                                )
                                Toast.makeText(context, "App icon updated to Neon Purple", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // 2. White Note
                        AppIconOption(
                            isSelected = settings.appIconStyle == "white_minimal",
                            gradient = listOf(Color(0xFFF4EFEA), Color(0xFFE5E5E5)),
                            iconTint = Color(0xFF0C0A09),
                            onClick = {
                                settingsRepository.setAppIconStyle("white_minimal")
                                com.example.gobeats.core.util.AppIconManager.setAppIcon(
                                    context,
                                    com.example.gobeats.core.util.AppIconManager.STYLE_WHITE_MINIMAL
                                )
                                Toast.makeText(context, "App icon updated to White Minimal", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // 3. Sunset Glow
                        AppIconOption(
                            isSelected = settings.appIconStyle == "sunset",
                            gradient = listOf(Color(0xFFEA580C), Color(0xFF9333EA)),
                            iconTint = Color.White,
                            onClick = {
                                settingsRepository.setAppIconStyle("sunset")
                                com.example.gobeats.core.util.AppIconManager.setAppIcon(
                                    context,
                                    com.example.gobeats.core.util.AppIconManager.STYLE_SUNSET
                                )
                                Toast.makeText(context, "App icon updated to Sunset Glow", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // 4. Mountain Peak
                        AppIconOption(
                            isSelected = settings.appIconStyle == "mountain",
                            gradient = listOf(Color(0xFF0284C7), Color(0xFF1E1B4B)),
                            iconTint = Color.White,
                            onClick = {
                                settingsRepository.setAppIconStyle("mountain")
                                com.example.gobeats.core.util.AppIconManager.setAppIcon(
                                    context,
                                    com.example.gobeats.core.util.AppIconManager.STYLE_MOUNTAIN
                                )
                                Toast.makeText(context, "App icon updated to Mountain Peak", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text("Edit Profile", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = GobeatsBorderSubtle,
                            focusedTextColor = GobeatsTextPrimary,
                            unfocusedTextColor = GobeatsTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = GobeatsBorderSubtle,
                            focusedTextColor = GobeatsTextPrimary,
                            unfocusedTextColor = GobeatsTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsRepository.updateProfile(editName.trim(), editEmail.trim())
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save", color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = GobeatsTextMuted)
                }
            }
        )
    }

    // Audio Quality Dialog
    if (showAudioQualityDialog) {
        val qualities = listOf(
            "FLAC Lossless (Original)" to "Uncompressed studio bit-perfect fidelity",
            "High (320 kbps)" to "Crisp audiophile MP3/AAC quality",
            "Medium (192 kbps)" to "Balanced playback and battery efficiency",
            "Data Saver (128 kbps)" to "Optimized storage and fast loading"
        )

        AlertDialog(
            onDismissRequest = { showAudioQualityDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text("Audio Quality", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
            },
            text = {
                Column {
                    qualities.forEach { (name, desc) ->
                        val isSelected = settings.audioQuality == name
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsRepository.setAudioQuality(name)
                                    showAudioQualityDialog = false
                                }
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.15f) else Color.Transparent
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF8B5CF6) else GobeatsTextPrimary
                                )
                                Text(text = desc, fontSize = 11.sp, color = GobeatsTextMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioQualityDialog = false }) {
                    Text("Close", color = GobeatsTextMuted)
                }
            }
        )
    }

    // Playback Settings Dialog
    if (showPlaybackSettingsDialog) {
        var crossfadeVal by remember { mutableFloatStateOf(settings.crossfadeDurationSec.toFloat()) }
        var gaplessVal by remember { mutableStateOf(settings.gaplessPlayback) }

        AlertDialog(
            onDismissRequest = { showPlaybackSettingsDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text("Playback Settings", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
            },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Gapless Playback", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                            Text("Eliminates silence between tracks", fontSize = 11.sp, color = GobeatsTextMuted)
                        }
                        Switch(
                            checked = gaplessVal,
                            onCheckedChange = { gaplessVal = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF34D399)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text("Crossfade Duration", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                    Text("${crossfadeVal.toInt()} seconds", fontSize = 12.sp, color = GobeatsAmber)

                    Slider(
                        value = crossfadeVal,
                        onValueChange = { crossfadeVal = it },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = GobeatsAmber,
                            activeTrackColor = GobeatsAmber
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsRepository.setPlaybackSettings(crossfadeVal.toInt(), gaplessVal)
                        showPlaybackSettingsDialog = false
                    }
                ) {
                    Text("Save", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Equalizer Dialog
    if (showEqualizerDialog) {
        EqualizerDialog(
            equalizerManager = equalizerManager,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    // Theme Dialog
    if (showThemeDialog) {
        val themes = listOf("Dark", "Pitch Black (Obsidian)", "Midnight Indigo", "System Default")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = { Text("Choose Theme", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary) },
            text = {
                Column {
                    themes.forEach { t ->
                        val isSelected = settings.theme == t
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    settingsRepository.setTheme(t)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.15f) else Color.Transparent
                        ) {
                            Text(
                                text = t,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF38BDF8) else GobeatsTextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = GobeatsTextMuted)
                }
            }
        )
    }

    // Premium Upgrade Dialog
    if (showPremiumDialog) {
        AlertDialog(
            onDismissRequest = { showPremiumDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFBBF24))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("gobeats Access & Edition", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
                }
            },
            text = {
                Column {
                    Text(
                        text = "You are currently enjoying the Community Release with 100% full access unlocked! Every premium capability—including Offline AI Subtitles, Studio 5-Band DSP EQ, Hardware Audio Routing, 60 FPS Visualizer, and Wi-Fi PC Sync—is active.",
                        fontSize = 13.sp,
                        color = GobeatsTextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Notice: The developer reserves complete autonomy and commercial rights to introduce optional PRO tiers, lifetime licenses, or paid extensions in subsequent releases.",
                        fontSize = 11.sp,
                        color = GobeatsTextMuted,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPremiumDialog = false }) {
                    Text("Understood", color = Color(0xFFEC4899), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // About gobeats Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = GobeatsAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("About gobeats", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
                }
            },
            text = {
                Column {
                    Text("gobeats High-Fidelity Ecosystem", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
                    Text("Version 3.2.0 (Build 5)", fontSize = 12.sp, color = GobeatsAmber)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• Audio Engine: 32-bit Float Media3 ExoPlayer", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Text("• Hardware Profiles: Auto Earbud/Car EQ & Persistent AV Delay", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Text("• AI Video: Offline Dual Subtitles & Smart Skip Intro", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Text("• Connect: High-speed local Wi-Fi PC file transfer", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Text("• Edition: Community Release (Developer retains future monetization rights)", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Engineered for true sound lovers who value high craft and offline privacy.", fontSize = 12.sp, color = GobeatsTextMuted)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Downloads & Offline Cache Dialog
    if (showDownloadsDialog) {
        val cacheSizeBytes = try {
            context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } catch (_: Exception) { 0L }
        val cacheSizeMb = String.format("%.1f MB", cacheSizeBytes.toDouble() / (1024 * 1024))

        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Downloads & Storage", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
                }
            },
            text = {
                Column {
                    Text("Offline Music Storage", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                    Text("All imported & synced tracks reside locally on this device. You can listen anywhere without an internet connection.", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cached Artwork & Temp Data:", fontSize = 12.sp, color = GobeatsTextMuted)
                        Text(cacheSizeMb, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GobeatsAmber)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.clickable {
                            try {
                                context.cacheDir.deleteRecursively()
                                Toast.makeText(context, "Artwork cache cleared", Toast.LENGTH_SHORT).show()
                                showDownloadsDialog = false
                            } catch (_: Exception) {}
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF2A1F26)
                    ) {
                        Text(
                            text = "Clear Artwork Cache",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF87171),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadsDialog = false }) {
                    Text("Done", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showSleepTimerDialog && sleepTimerManager != null && playbackRepository != null) {
        com.example.gobeats.feature.nowplaying.SleepTimerDialog(
            sleepTimerManager = sleepTimerManager,
            playbackRepository = playbackRepository,
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailingValue: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = GobeatsSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsTextPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = GobeatsTextMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingValue != null) {
                    Text(
                        text = trailingValue,
                        fontSize = 12.sp,
                        color = GobeatsTextMuted,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = GobeatsTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AppIconOption(
    isSelected: Boolean,
    gradient: List<Color>,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (isSelected) Color(0xFF8B5CF6) else Color.Transparent)
            .padding(if (isSelected) 2.dp else 0.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Brush.linearGradient(gradient))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
    }
}
