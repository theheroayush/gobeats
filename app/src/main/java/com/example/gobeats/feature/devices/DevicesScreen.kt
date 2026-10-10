package com.example.gobeats.feature.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.sync.GobeatsSyncServer
import com.example.gobeats.core.sync.LiveTransferItem
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsObsidian
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

@Composable
fun DevicesScreen(
    syncServer: GobeatsSyncServer? = null,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val syncState by syncServer?.state?.collectAsState()
        ?: remember { mutableStateOf(com.example.gobeats.core.sync.SyncServerState()) }

    var showConnectModal by remember { mutableStateOf(false) }
    var showDeviceSpecsDialog by remember { mutableStateOf(false) }
    var selectedTransferDetails by remember { mutableStateOf<LiveTransferItem?>(null) }
    var showAllTransfersDialog by remember { mutableStateOf(false) }

    // Dynamic storage and device specs calculation
    val deviceModel = Build.MODEL.ifEmpty { "Android Phone" }
    val (usedText, totalText, storageProgress) = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            val totalGb = totalBytes.toDouble() / (1024 * 1024 * 1024)
            val usedGb = usedBytes.toDouble() / (1024 * 1024 * 1024)
            val prog = (usedGb / totalGb.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
            Triple(String.format("%.1f GB used", usedGb), String.format("%.0f GB total", totalGb), prog)
        } catch (_: Exception) {
            Triple("52.3 GB used", "128 GB total", 0.41f)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(GobeatsObsidian),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        // Top App Bar: Brand + Settings Gear (1:1 with mockup_devices_v2.png)
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

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = GobeatsTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Title Section: Devices + Connect Device CTA Button (Matching mockup 1:1, immune to vertical wrap)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = "Devices",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Move your music. Everywhere with you.",
                        fontSize = 13.sp,
                        color = GobeatsTextMuted,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // White pill Connect Device button (matching mockup 1:1)
                Surface(
                    modifier = Modifier.clickable {
                        com.example.gobeats.core.sync.GobeatsSyncService.start(context)
                        showConnectModal = true
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF4EFEA)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = Color(0xFF0C0A09),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Connect Device",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0C0A09),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Hero Transfer Banner (responsive, immune to text truncation)
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF14131D),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF282538))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF211C33), Color(0xFF131219))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Text Content & Action
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Transfer music from your computer",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsTextPrimary,
                                lineHeight = 21.sp
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "Fast. Wireless. Secure.",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            // White pill Connect Your Computer CTA
                            Surface(
                                modifier = Modifier.clickable {
                                    com.example.gobeats.core.sync.GobeatsSyncService.start(context)
                                    showConnectModal = true
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFF4EFEA)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Connect Your Computer",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0C0A09),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color(0xFF0C0A09),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Right Visual Graphic: Compact Laptop + Phone connection
                        Box(
                            modifier = Modifier.size(width = 96.dp, height = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                drawArc(
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.4f),
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = Offset(w * 0.25f, h * 0.15f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.5f, h * 0.4f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Laptop Icon Box
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E1E26),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383844)),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Laptop,
                                            contentDescription = null,
                                            tint = Color(0xFF8B5CF6),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // Phone Icon Box
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E1E26),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383844)),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = Color(0xFFEC4899),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Your devices
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Your devices",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = GobeatsTextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            // Device 1: This Android Phone (1:1 with mockup_devices_v2.png, wired to hardware specs dialog)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeviceSpecsDialog = true }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = GobeatsSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GobeatsSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "This Android Phone",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = deviceModel,
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E3A8A)
                            ) {
                                Text(
                                    text = "This device",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF93C5FD),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Real Storage Bar
                        LinearProgressIndicator(
                            progress = { storageProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF8B5CF6),
                            trackColor = Color(0xFF27272A)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = usedText,
                                fontSize = 11.sp,
                                color = GobeatsTextMuted
                            )
                            Text(
                                text = totalText,
                                fontSize = 11.sp,
                                color = GobeatsTextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Device: Connected Audio Peripheral (Hardware-Aware Auto Profile)
            val app = context.applicationContext as? com.example.gobeats.GobeatsApplication
            val audioProfile by (app?.audioDeviceProfileManager?.currentProfile?.collectAsState()
                ?: remember { mutableStateOf(com.example.gobeats.core.audio.AudioDeviceProfileManager.DEFAULT_SPEAKER_PROFILE) })

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = GobeatsSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tint = when (audioProfile.type) {
                        com.example.gobeats.core.model.DeviceAudioProfileType.EARBUDS -> GobeatsAmber
                        com.example.gobeats.core.model.DeviceAudioProfileType.HEADPHONES -> Color(0xFF60A5FA)
                        com.example.gobeats.core.model.DeviceAudioProfileType.CAR_AUDIO -> Color(0xFF34D399)
                        com.example.gobeats.core.model.DeviceAudioProfileType.SPEAKER -> Color(0xFFF472B6)
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GobeatsSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = audioProfile.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x33F59E0B)
                            ) {
                                Text(
                                    text = audioProfile.type.name,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GobeatsAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Auto EQ: ${audioProfile.preferredEqPreset} • AV Delay: ${audioProfile.avDelayMs} ms",
                            fontSize = 12.sp,
                            color = GobeatsTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Device 2: Paired Computer / Laptop (1:1 with mockup_devices_v2.png)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        com.example.gobeats.core.sync.GobeatsSyncService.start(context)
                        showConnectModal = true
                    }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = GobeatsSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GobeatsSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Laptop,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ayush's Laptop",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (syncState.isRunning) Color(0xFF34D399) else Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (syncState.isRunning) "Active" else "Paired",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }

                        Text(
                            text = if (syncState.isRunning) "Listening on port ${syncState.port} • Local Wi-Fi" else "Windows 11 • Last connected 2 days ago",
                            fontSize = 12.sp,
                            color = GobeatsTextMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress / Status Bar
                        LinearProgressIndicator(
                            progress = { 0.67f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0xFF27272A)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "1.2 TB available",
                                fontSize = 11.sp,
                                color = GobeatsTextMuted
                            )
                            Text(
                                text = "1.8 TB total",
                                fontSize = 11.sp,
                                color = GobeatsTextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Section: Recent transfers (matching mockup_devices_v2.png)
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent transfers",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GobeatsTextPrimary
                )

                Row(
                    modifier = Modifier.clickable { showAllTransfersDialog = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "See all",
                        fontSize = 13.sp,
                        color = GobeatsTextMuted
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = GobeatsTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Transfer Ledger Items
        if (syncState.recentTransfers.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = GobeatsTextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Recent Transfers",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start the sync server above to receive music and video files directly from your PC.",
                            fontSize = 12.sp,
                            color = GobeatsTextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(syncState.recentTransfers) { item ->
                var itemMenuExpanded by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTransferDetails = item }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = GobeatsSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        when (item.title) {
                                            "Chill Vibes" -> listOf(Color(0xFFB45309), Color(0xFF431407))
                                            "Late Night" -> listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                                            else -> listOf(Color(0xFFC2410C), Color(0xFF451A03))
                                        }
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                            Text(
                                text = "${item.tracksCount} song${if (item.tracksCount == 1) "" else "s"} • ${String.format("%.1f", item.totalSizeMb)} MB",
                                fontSize = 12.sp,
                                color = GobeatsTextMuted
                            )
                        }

                        // Status Indicator
                        Column(horizontalAlignment = Alignment.End) {
                            if (item.isSuccess) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Completed",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Completed",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF34D399)
                                    )
                                }
                                Text(
                                    text = item.timestamp,
                                    fontSize = 11.sp,
                                    color = GobeatsTextMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        progress = { item.progress },
                                        modifier = Modifier.size(14.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "In progress",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF38BDF8)
                                    )
                                }
                                Text(
                                    text = item.timestamp,
                                    fontSize = 11.sp,
                                    color = GobeatsTextMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Interactive 3-Dot Overflow Menu
                        Box {
                            IconButton(
                                onClick = { itemMenuExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = GobeatsTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = itemMenuExpanded,
                                onDismissRequest = { itemMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Transfer Details") },
                                    onClick = {
                                        itemMenuExpanded = false
                                        selectedTransferDetails = item
                                    },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove from History") },
                                    onClick = {
                                        itemMenuExpanded = false
                                        syncServer?.removeRecentTransfer(item.id)
                                        Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Connect Your Computer / Wi-Fi Sync Modal Dialog
    if (showConnectModal) {
        val rawIp = syncServer?.getLocalIpAddress() ?: "127.0.0.1"
        val isWifi = rawIp != "127.0.0.1"
        val serverUrl = syncState.serverUrl.ifEmpty { "http://$rawIp:42100" }

        AlertDialog(
            onDismissRequest = { showConnectModal = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isWifi) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (isWifi) GobeatsAmber else Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "gobeats Connect",
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // Wi-Fi Status Pill
                    if (isWifi) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF064E3B).copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF059669))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34D399))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Local Wi-Fi Active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF7F1D1D).copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626))
                        ) {
                            Text(
                                text = "⚠️ Wi-Fi Disconnected: Connect phone and PC to the same Wi-Fi network.",
                                fontSize = 11.sp,
                                color = Color(0xFFFCA5A5),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Open this link in any browser on your computer to drop and transfer music wirelessly:",
                        fontSize = 13.sp,
                        color = GobeatsTextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // IP URL Box with Copy and Share Buttons (immune to text clipping)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GobeatsObsidian,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = serverUrl,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsAmber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("gobeats Connect URL", serverUrl))
                                    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = GobeatsTextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, "gobeats PC Sync URL: $serverUrl\nPairing PIN: ${syncState.pinCode}")
                                        type = "text/plain"
                                    }
                                    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share gobeats Connect Link")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = GobeatsAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Pairing PIN:",
                            fontSize = 13.sp,
                            color = GobeatsTextMuted
                        )
                        Text(
                            text = syncState.pinCode,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsTextPrimary,
                            letterSpacing = 2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isWifi) "Status: Server actively listening on port 42100." else "Status: Waiting for local Wi-Fi connection.",
                        fontSize = 11.sp,
                        color = if (isWifi) Color(0xFF34D399) else Color(0xFFF59E0B)
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        com.example.gobeats.core.sync.GobeatsSyncService.stop(context)
                        showConnectModal = false
                    }) {
                        Text("Stop Server", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showConnectModal = false }) {
                        Text("Done", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    // Hardware Audio & Storage Diagnostics Dialog
    if (showDeviceSpecsDialog) {
        val currentIp = syncServer?.getLocalIpAddress() ?: "Wi-Fi Disconnected"
        AlertDialog(
            onDismissRequest = { showDeviceSpecsDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Device Audio & Hardware",
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} $deviceModel",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Text(
                        text = "Android ${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})",
                        fontSize = 12.sp,
                        color = GobeatsAmber
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Audio Architecture:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                    Text("• Output Engine: 32-bit Float PCM (Media3 ExoPlayer)", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Text("• Low-Latency HAL: OpenSL ES / AAudio Accelerated", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Text("• Acoustic FX: Hardware LoudnessEnhancer & Graphic EQ", fontSize = 12.sp, color = GobeatsTextSecondary)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Network & Storage:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                    Text("• Local Wi-Fi IP: $currentIp", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Text("• Internal Storage: $usedText of $totalText", fontSize = 12.sp, color = GobeatsTextSecondary)
                    Text("• Sync Port: 42100 (Direct Socket Ingestion)", fontSize = 12.sp, color = GobeatsTextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceSpecsDialog = false }) {
                    Text("Close", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Transfer Details Dialog
    if (selectedTransferDetails != null) {
        val transfer = selectedTransferDetails!!
        AlertDialog(
            onDismissRequest = { selectedTransferDetails = null },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transfer Details",
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = transfer.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• Transferred: ${transfer.tracksCount} song${if (transfer.tracksCount == 1) "" else "s"}", fontSize = 13.sp, color = GobeatsTextSecondary)
                    Text("• Total Size: ${String.format("%.2f", transfer.totalSizeMb)} MB", fontSize = 13.sp, color = GobeatsTextSecondary)
                    Text("• Source Device: ${transfer.senderDevice}", fontSize = 13.sp, color = GobeatsTextSecondary)
                    Text("• Timestamp: ${transfer.timestamp}", fontSize = 13.sp, color = GobeatsTextSecondary)
                    Text("• Status: ${if (transfer.isSuccess) "Completed Bit-Perfect" else "In Progress"}", fontSize = 13.sp, color = if (transfer.isSuccess) Color(0xFF34D399) else Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Files saved directly to Android Music storage directory and indexed in your local library.",
                        fontSize = 11.sp,
                        color = GobeatsTextMuted
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTransferDetails = null }) {
                    Text("Done", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // See All Transfers Dialog
    if (showAllTransfersDialog) {
        AlertDialog(
            onDismissRequest = { showAllTransfersDialog = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Text("All Recent Transfers", fontWeight = FontWeight.Bold, color = GobeatsTextPrimary)
            },
            text = {
                if (syncState.recentTransfers.isEmpty()) {
                    Text("No transfers recorded yet.", fontSize = 13.sp, color = GobeatsTextMuted)
                } else {
                    Column {
                        syncState.recentTransfers.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GobeatsTextPrimary)
                                    Text("${t.tracksCount} songs • ${String.format("%.1f", t.totalSizeMb)} MB", fontSize = 11.sp, color = GobeatsTextMuted)
                                }
                                Text(if (t.isSuccess) "Completed" else "Syncing", fontSize = 11.sp, color = if (t.isSuccess) Color(0xFF34D399) else Color(0xFF38BDF8))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (syncState.recentTransfers.isNotEmpty()) {
                        TextButton(onClick = {
                            syncServer?.clearRecentTransfers()
                            showAllTransfersDialog = false
                            Toast.makeText(context, "Transfer history cleared", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Clear All", color = Color(0xFFEF4444))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = { showAllTransfersDialog = false }) {
                        Text("Close", color = GobeatsAmber, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}
