package com.example.gobeats.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.gobeats.core.audio.AutoEqCatalog
import com.example.gobeats.core.audio.AutoEqProfile
import com.example.gobeats.core.audio.BitPerfectAudioEngine
import com.example.gobeats.core.audio.EqualizerManager
import com.example.gobeats.core.audio.ReplayGainMode
import com.example.gobeats.core.model.Track
import com.example.gobeats.theme.GobeatsAmber
import com.example.gobeats.theme.GobeatsBorderSubtle
import com.example.gobeats.theme.GobeatsSurface
import com.example.gobeats.theme.GobeatsSurfaceElevated
import com.example.gobeats.theme.GobeatsTextMuted
import com.example.gobeats.theme.GobeatsTextPrimary
import com.example.gobeats.theme.GobeatsTextSecondary

@Composable
fun EqualizerDialog(
    equalizerManager: EqualizerManager,
    currentTrack: Track? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val eqState by equalizerManager.state.collectAsState()
    var showAutoEqPicker by remember { mutableStateOf(false) }

    val hwStatus = remember(currentTrack) {
        BitPerfectAudioEngine.getHardwareStatus(context, currentTrack)
    }

    val presets = listOf(
        "Studio Reference",
        "Vocal Clarity",
        "Warm Analog",
        "Deep Bass Punch",
        "Electronic / EDM",
        "Acoustic / Live",
        "Flat",
        "Custom"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GobeatsSurfaceElevated,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Master DSP & Equalizer",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = GobeatsTextPrimary
                    )
                    Text(
                        text = "32-bit Floating-Point • Audiophile Pipeline",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GobeatsAmber
                    )
                }

                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = { equalizerManager.toggleEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = GobeatsAmber
                    )
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Direct-to-DAC / Bit-Perfect Hardware Signal Path Inspector
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GobeatsSurface,
                    border = BorderStroke(1.dp, GobeatsBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (hwStatus.isBitPerfectActive || hwStatus.isExternalDac) Color(0xFF10B981) else GobeatsAmber,
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AUDIO SIGNAL PATH",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GobeatsTextMuted,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (hwStatus.isBitPerfectActive) GobeatsAmber else Color(0xFF27272A)
                            ) {
                                Text(
                                    text = if (hwStatus.isBitPerfectActive) "BIT-PERFECT" else if (hwStatus.isExternalDac) "USB-DAC" else "32-BIT FLOAT",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hwStatus.isBitPerfectActive) Color.Black else GobeatsAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "${hwStatus.outputDeviceName} (${hwStatus.outputTypeLabel})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GobeatsTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = hwStatus.signalPathDescription,
                            fontSize = 11.sp,
                            color = GobeatsTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. AutoEQ Headphone Profile Selector
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GobeatsSurface,
                    border = BorderStroke(1.dp, if (eqState.activeAutoEqProfile != null) GobeatsAmber else GobeatsBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = "AutoEQ",
                                    tint = if (eqState.activeAutoEqProfile != null) GobeatsAmber else GobeatsTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AutoEQ (Harman Target)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GobeatsTextPrimary
                                )
                            }

                            if (eqState.activeAutoEqProfile != null) {
                                TextButton(
                                    onClick = { equalizerManager.applyAutoEqProfile(null) },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "Reset",
                                        fontSize = 11.sp,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        if (eqState.activeAutoEqProfile != null) {
                            val profile = eqState.activeAutoEqProfile!!
                            Text(
                                text = "${profile.brand} ${profile.model}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsAmber
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Calibrated: ${profile.category} • Pre-amp: ${profile.preampDb}dB • Harman Curve Target",
                                fontSize = 11.sp,
                                color = GobeatsTextSecondary
                            )
                        } else {
                            Text(
                                text = "Match your exact headphones to reference Harman curves curated by Crinacle & Oratory1990.",
                                fontSize = 11.sp,
                                color = GobeatsTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            onClick = { showAutoEqPicker = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF27272A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (eqState.activeAutoEqProfile != null) "Change Headphone Model" else "Select Headphone Model (27 Profiles)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GobeatsAmber,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. ReplayGain 2.0 / Volume Leveling
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GobeatsSurface,
                    border = BorderStroke(1.dp, GobeatsBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "ReplayGain",
                                    tint = GobeatsAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ReplayGain / Volume Leveling",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GobeatsTextPrimary
                                )
                            }

                            if (eqState.replayGainMode != ReplayGainMode.OFF && eqState.currentAppliedReplayGainDb != 0.0f) {
                                val gain = eqState.currentAppliedReplayGainDb
                                Text(
                                    text = "${if (gain > 0) "+" else ""}${String.format("%.1f", gain)}dB applied",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (gain < 0) Color(0xFF38BDF8) else Color(0xFF34D399)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Normalizes loudness to -18 LUFS with decade mastering era estimator & peak limiting.",
                            fontSize = 11.sp,
                            color = GobeatsTextMuted
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // ReplayGain Mode Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                ReplayGainMode.SMART to "Smart",
                                ReplayGainMode.TRACK to "Track",
                                ReplayGainMode.ALBUM to "Album",
                                ReplayGainMode.OFF to "Off"
                            ).forEach { (mode, label) ->
                                val isSelected = eqState.replayGainMode == mode
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { equalizerManager.setReplayGainMode(mode) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) GobeatsAmber else Color(0xFF1E1E22),
                                    border = if (isSelected) null else BorderStroke(1.dp, GobeatsBorderSubtle)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.Black else GobeatsTextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        if (eqState.replayGainMode != ReplayGainMode.OFF) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Pre-Amp Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Pre-Amp",
                                    fontSize = 11.sp,
                                    color = GobeatsTextSecondary,
                                    modifier = Modifier.width(55.dp)
                                )
                                Slider(
                                    value = eqState.replayGainPreAmpDb,
                                    onValueChange = { equalizerManager.setReplayGainPreAmp(it) },
                                    valueRange = -6.0f..6.0f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = GobeatsAmber,
                                        activeTrackColor = GobeatsAmber,
                                        inactiveTrackColor = GobeatsSurfaceElevated
                                    )
                                )
                                Text(
                                    text = "${if (eqState.replayGainPreAmpDb > 0) "+" else ""}${String.format("%.1f", eqState.replayGainPreAmpDb)}dB",
                                    fontSize = 11.sp,
                                    color = GobeatsAmber,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(44.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            // Prevent Clipping Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Peak Limiter (Anti-Clipping)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GobeatsTextPrimary
                                    )
                                    Text(
                                        text = "Scales gain down automatically if track peak > 0dBFS",
                                        fontSize = 10.sp,
                                        color = GobeatsTextMuted
                                    )
                                }
                                Switch(
                                    checked = eqState.replayGainPreventClipping,
                                    onCheckedChange = { equalizerManager.setReplayGainPreventClipping(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Acoustic Tuning Presets
                Text(
                    text = "Acoustic Tuning Presets",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GobeatsTextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        val isSelected = eqState.currentPreset == preset
                        Surface(
                            modifier = Modifier.clickable {
                                equalizerManager.applyPreset(preset)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF8B5CF6) else GobeatsSurface,
                            border = if (isSelected) null else BorderStroke(1.dp, GobeatsBorderSubtle)
                        ) {
                            Text(
                                text = preset,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else GobeatsTextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. 5-Band Graphic Equalizer Sliders
                Text(
                    text = "5-Band Graphic Equalizer",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GobeatsTextMuted
                )

                eqState.bands.forEach { band ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}kHz" else "${band.centerFreqHz}Hz",
                            fontSize = 12.sp,
                            color = GobeatsTextSecondary,
                            modifier = Modifier.width(50.dp)
                        )

                        Slider(
                            value = band.levelMb.toFloat(),
                            onValueChange = {
                                equalizerManager.setBandLevel(band.index, it.toInt())
                            },
                            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF8B5CF6),
                                activeTrackColor = Color(0xFF8B5CF6),
                                inactiveTrackColor = GobeatsSurface
                            )
                        )

                        Text(
                            text = "${if (band.levelMb > 0) "+" else ""}${band.levelMb / 100}dB",
                            fontSize = 11.sp,
                            color = GobeatsTextMuted,
                            modifier = Modifier.width(42.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 6. Loudness Enhancer & Dynamic Clarity Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GobeatsSurface,
                    border = BorderStroke(1.dp, GobeatsBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Acoustic Loudness & Clarity",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GobeatsTextPrimary
                                )
                                Text(
                                    text = "Dynamic range compression & peak leveling",
                                    fontSize = 10.sp,
                                    color = GobeatsTextMuted
                                )
                            }
                            Switch(
                                checked = eqState.isLoudnessEnabled,
                                onCheckedChange = { equalizerManager.toggleLoudness(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF34D399)
                                )
                            )
                        }

                        if (eqState.isLoudnessEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gain",
                                    fontSize = 12.sp,
                                    color = GobeatsTextSecondary,
                                    modifier = Modifier.width(42.dp)
                                )
                                Slider(
                                    value = eqState.loudnessGainMb.toFloat(),
                                    onValueChange = { equalizerManager.setLoudnessGain(it.toInt()) },
                                    valueRange = 0f..800f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF34D399),
                                        activeTrackColor = Color(0xFF34D399),
                                        inactiveTrackColor = GobeatsSurfaceElevated
                                    )
                                )
                                Text(
                                    text = "+${String.format("%.1f", eqState.loudnessGainMb.toFloat() / 100)}dB",
                                    fontSize = 11.sp,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(48.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 7. Bass Boost Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bass Boost",
                        fontSize = 13.sp,
                        color = GobeatsTextPrimary,
                        modifier = Modifier.width(74.dp)
                    )

                    Slider(
                        value = eqState.bassBoostStrength.toFloat(),
                        onValueChange = {
                            equalizerManager.setBassBoost(it.toInt())
                        },
                        valueRange = 0f..1000f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = GobeatsAmber,
                            activeTrackColor = GobeatsAmber,
                            inactiveTrackColor = GobeatsSurface
                        )
                    )

                    Text(
                        text = "${eqState.bassBoostStrength / 10}%",
                        fontSize = 11.sp,
                        color = GobeatsTextMuted,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 8. 3D Spatial Virtualizer Slider (HRTF Soundstage Expander)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3D Spatial",
                        fontSize = 13.sp,
                        color = GobeatsTextPrimary,
                        modifier = Modifier.width(74.dp)
                    )

                    Slider(
                        value = eqState.virtualizerStrength.toFloat(),
                        onValueChange = {
                            equalizerManager.setVirtualizer(it.toInt())
                        },
                        valueRange = 0f..1000f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8),
                            inactiveTrackColor = GobeatsSurface
                        )
                    )

                    Text(
                        text = "${eqState.virtualizerStrength / 10}%",
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = GobeatsAmber, fontWeight = FontWeight.Bold)
            }
        }
    )

    // AutoEQ Headphone Profile Picker Dialog
    if (showAutoEqPicker) {
        AutoEqPickerDialog(
            currentProfile = eqState.activeAutoEqProfile,
            onSelectProfile = { profile ->
                equalizerManager.applyAutoEqProfile(profile)
                showAutoEqPicker = false
            },
            onDismiss = { showAutoEqPicker = false }
        )
    }
}

@Composable
fun AutoEqPickerDialog(
    currentProfile: AutoEqProfile?,
    onSelectProfile: (AutoEqProfile?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredProfiles = remember(searchQuery, selectedCategory) {
        AutoEqCatalog.PROFILES.filter { profile ->
            val matchesCategory = selectedCategory == "All" || profile.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    profile.fullName.contains(searchQuery, ignoreCase = true) ||
                    profile.brand.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GobeatsSurfaceElevated,
            border = BorderStroke(1.dp, GobeatsBorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "AutoEQ Headphone Selector",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = GobeatsTextPrimary
                        )
                        Text(
                            text = "${AutoEqCatalog.PROFILES.size} Reference Harman Target Curves",
                            fontSize = 11.sp,
                            color = GobeatsAmber
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

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search brand or model (Sony, Sennheiser...)", fontSize = 12.sp, color = GobeatsTextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = GobeatsTextMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = GobeatsTextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GobeatsAmber,
                        unfocusedBorderColor = GobeatsBorderSubtle,
                        focusedTextColor = GobeatsTextPrimary,
                        unfocusedTextColor = GobeatsTextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AutoEqCatalog.CATEGORIES) { category ->
                        val isSelected = selectedCategory == category
                        Surface(
                            modifier = Modifier.clickable { selectedCategory = category },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) GobeatsAmber else GobeatsSurface,
                            border = if (isSelected) null else BorderStroke(1.dp, GobeatsBorderSubtle)
                        ) {
                            Text(
                                text = category,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.Black else GobeatsTextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Reset to None Option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectProfile(null) },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF27272A),
                    border = BorderStroke(1.dp, GobeatsBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "None / Standard EQ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GobeatsTextPrimary
                            )
                            Text(
                                text = "Revert to flat studio curve or manual sliders",
                                fontSize = 10.sp,
                                color = GobeatsTextMuted
                            )
                        }
                        if (currentProfile == null) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = GobeatsAmber, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Headphone List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredProfiles, key = { it.id }) { profile ->
                        val isSelected = currentProfile?.id == profile.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProfile(profile) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF2E261A) else GobeatsSurface,
                            border = BorderStroke(1.dp, if (isSelected) GobeatsAmber else GobeatsBorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = profile.fullName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) GobeatsAmber else GobeatsTextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF27272A)
                                        ) {
                                            Text(
                                                text = profile.category,
                                                fontSize = 9.sp,
                                                color = GobeatsTextSecondary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Pre-amp: ${profile.preampDb}dB • Bass: +${profile.recommendedBassBoost / 10}% • Spatial: +${profile.recommendedVirtualizer / 10}%",
                                        fontSize = 10.sp,
                                        color = GobeatsTextMuted
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = GobeatsAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
