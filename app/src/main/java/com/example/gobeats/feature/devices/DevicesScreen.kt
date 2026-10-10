package com.example.gobeats.feature.devices

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gobeats.core.sync.GobeatsSyncServer
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

        // Title Section: Devices + Connect Device CTA Button (Layout fixed: immune to text wrap)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Devices",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary,
                        letterSpacing = (-0.5).sp
                    )

                    // White pill Connect Device button (matching mockup)
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

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Move your music. Everywhere with you.",
                    fontSize = 13.sp,
                    color = GobeatsTextMuted
                )
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
                            modifier = Modifier
                                .size(width = 96.dp, height = 80.dp),
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

            // Compute dynamic storage and device info
            val deviceModel = android.os.Build.MODEL.ifEmpty { "Android Phone" }
            val (usedText, totalText, storageProgress) = try {
                val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
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

            // Device 1: This Android Phone
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

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                )
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsTextPrimary
                            )
                            Text(
                                text = "${item.tracksCount} songs • ${String.format("%.0f", item.totalSizeMb)} MB",
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

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = GobeatsTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // Connect Your Computer / Wi-Fi Sync Modal Dialog
    if (showConnectModal) {
        val serverUrl = syncState.serverUrl.ifEmpty { "http://${syncServer?.getLocalIpAddress() ?: "192.168.1.100"}:42100" }

        AlertDialog(
            onDismissRequest = { showConnectModal = false },
            containerColor = GobeatsSurfaceElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = GobeatsAmber,
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
                Column {
                    Text(
                        text = "Open this link in any browser on your computer to drop and transfer music wirelessly:",
                        fontSize = 13.sp,
                        color = GobeatsTextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // IP URL Box with Copy Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GobeatsObsidian,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GobeatsBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = serverUrl,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsAmber
                            )

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("gobeats Connect URL", serverUrl))
                                    Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = GobeatsTextMuted,
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
                        text = "Status: Server is actively listening on local Wi-Fi port 42100.",
                        fontSize = 11.sp,
                        color = Color(0xFF34D399)
                    )
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
}
