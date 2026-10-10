package com.example.gobeats.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.model.DeviceAudioProfile
import com.example.gobeats.core.model.DeviceAudioProfileType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Intelligent hardware-aware audio profile manager for gobeats.
 *
 * Automatically detects audio routing transitions (Bluetooth A2DP, BLE Audio,
 * Wired 3.5mm/USB-C, Car Audio, and Built-in Speakers), loads persisted EQ curves
 * and A/V sync delay compensations, and drives EqualizerManager to apply tailored
 * acoustic presets without user intervention.
 */
class AudioDeviceProfileManager(
    private val context: Context,
    private val dbHelper: GobeatsDatabaseHelper = GobeatsDatabaseHelper.getInstance(context),
    private val equalizerManager: EqualizerManager = EqualizerManager(context),
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    companion object {
        private const val TAG = "AudioDeviceProfile"

        // Default fallback profile when no external peripherals are connected
        val DEFAULT_SPEAKER_PROFILE = DeviceAudioProfile(
            deviceId = "speaker_builtin",
            name = "Phone Speaker",
            type = DeviceAudioProfileType.SPEAKER,
            preferredEqPreset = "Vocal Clarity",
            avDelayMs = 0L,
            updatedAt = System.currentTimeMillis()
        )

        private fun logI(tag: String, msg: String) {
            try { Log.i(tag, msg) } catch (_: Throwable) {}
        }
        private fun logD(tag: String, msg: String) {
            try { Log.d(tag, msg) } catch (_: Throwable) {}
        }
        private fun logW(tag: String, msg: String, tr: Throwable? = null) {
            try { if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg) } catch (_: Throwable) {}
        }
        private fun logE(tag: String, msg: String, tr: Throwable? = null) {
            try { if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg) } catch (_: Throwable) {}
        }
    }

    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentProfile = MutableStateFlow<DeviceAudioProfile>(DEFAULT_SPEAKER_PROFILE)
    val currentProfile: StateFlow<DeviceAudioProfile> = _currentProfile.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            logD(TAG, "Audio devices added: ${addedDevices?.size ?: 0}")
            refreshCurrentDeviceProfile(forceApply = false)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            logD(TAG, "Audio devices removed: ${removedDevices?.size ?: 0}")
            refreshCurrentDeviceProfile(forceApply = false)
        }
    }

    init {
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        } catch (e: Exception) {
            logE(TAG, "Failed to register AudioDeviceCallback", e)
        }
        // Immediately scan audio environment and configure initial hardware profile
        refreshCurrentDeviceProfile(forceApply = true)
    }

    /**
     * Inspects active hardware audio sinks and returns the highest priority connected output device.
     */
    fun resolveActiveOutputDevice(): AudioDeviceInfo? {
        val devices = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (e: Exception) {
            logW(TAG, "Unable to query AudioManager output devices", e)
            emptyArray()
        }

        if (devices.isEmpty()) return null

        val outputSinks = devices.filter { it.isSink }
        val candidateList = if (outputSinks.isNotEmpty()) outputSinks else devices.toList()

        return candidateList.maxByOrNull { getDevicePriorityScore(it) }
    }

    /**
     * Determines routing priority score based on Android media playback sink hierarchy.
     */
    private fun getDevicePriorityScore(info: AudioDeviceInfo): Int {
        return when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 100

            AudioDeviceInfo.TYPE_BLE_SPEAKER -> 95

            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> 80

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> 60

            AudioDeviceInfo.TYPE_DOCK -> 40
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 20
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 10
            else -> 0
        }
    }

    /**
     * Human-readable product display name resolution with fallbacks for unnamed peripherals.
     */
    fun getDeviceDisplayName(info: AudioDeviceInfo?): String {
        if (info == null) return "Phone Speaker"
        val rawName = info.productName?.toString()?.trim()
        if (!rawName.isNullOrBlank()) return rawName

        return when (info.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio Device"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Speaker"
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI Audio"
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE -> "Aux Audio Output"
            else -> "Audio Output"
        }
    }

    /**
     * Classifies connected audio output devices into DeviceAudioProfileType.
     *
     * Classification rules:
     * - TYPE_BLUETOOTH_A2DP, TYPE_BLE_HEADSET:
     *     - Name contains 'car' or 'auto' -> CAR_AUDIO
     *     - Name contains 'bud', 'pod', 'tws', or 'air' -> EARBUDS
     *     - Else -> HEADPHONES
     * - TYPE_WIRED_HEADPHONES, TYPE_WIRED_HEADSET, TYPE_USB_HEADSET -> HEADPHONES
     * - TYPE_BUILTIN_SPEAKER -> SPEAKER
     */
    fun classifyDeviceType(info: AudioDeviceInfo?): DeviceAudioProfileType {
        if (info == null) return DeviceAudioProfileType.SPEAKER
        val name = getDeviceDisplayName(info).lowercase()

        return when (info.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                if (name.contains("car") || name.contains("auto")) {
                    DeviceAudioProfileType.CAR_AUDIO
                } else if (name.contains("bud") || name.contains("pod") || name.contains("tws") || name.contains("air")) {
                    DeviceAudioProfileType.EARBUDS
                } else {
                    DeviceAudioProfileType.HEADPHONES
                }
            }

            AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                if (name.contains("car") || name.contains("auto")) {
                    DeviceAudioProfileType.CAR_AUDIO
                } else {
                    DeviceAudioProfileType.SPEAKER
                }
            }

            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                DeviceAudioProfileType.HEADPHONES
            }

            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                if (name.contains("car") || name.contains("auto")) {
                    DeviceAudioProfileType.CAR_AUDIO
                } else if (name.contains("bud") || name.contains("pod") || name.contains("tws") || name.contains("air")) {
                    DeviceAudioProfileType.EARBUDS
                } else {
                    DeviceAudioProfileType.HEADPHONES
                }
            }

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
                DeviceAudioProfileType.SPEAKER
            }

            else -> {
                if (name.contains("car") || name.contains("auto")) {
                    DeviceAudioProfileType.CAR_AUDIO
                } else if (name.contains("bud") || name.contains("pod") || name.contains("tws") || name.contains("air")) {
                    DeviceAudioProfileType.EARBUDS
                } else if (name.contains("speaker")) {
                    DeviceAudioProfileType.SPEAKER
                } else {
                    DeviceAudioProfileType.HEADPHONES
                }
            }
        }
    }

    /**
     * Generates a unique, deterministic device ID using MAC hardware address if accessible,
     * or a normalized product name string.
     */
    fun generateDeviceId(info: AudioDeviceInfo?, type: DeviceAudioProfileType): String {
        if (info == null) return "speaker_builtin"

        // MAC address detection for modern Android devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val address = info.address
                if (!address.isNullOrBlank() && address != "02:00:00:00:00:00") {
                    val sanitized = address.trim().replace(":", "").lowercase()
                    return "bt_${sanitized}"
                }
            } catch (_: Exception) {}
        }

        val rawName = getDeviceDisplayName(info)
        val normalized = rawName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

        return if (normalized.isNotEmpty()) {
            "${type.name.lowercase()}_$normalized"
        } else {
            when (type) {
                DeviceAudioProfileType.SPEAKER -> "speaker_builtin"
                DeviceAudioProfileType.HEADPHONES -> "headphones_wired"
                DeviceAudioProfileType.EARBUDS -> "earbuds_default"
                DeviceAudioProfileType.CAR_AUDIO -> "car_audio_default"
            }
        }
    }

    /**
     * Resolves the default acoustic EQ preset recommended for each peripheral form factor.
     */
    fun getDefaultPresetForType(type: DeviceAudioProfileType): String {
        return when (type) {
            DeviceAudioProfileType.EARBUDS -> "Deep Bass Punch"
            DeviceAudioProfileType.CAR_AUDIO -> "Warm Analog"
            DeviceAudioProfileType.SPEAKER -> "Vocal Clarity"
            DeviceAudioProfileType.HEADPHONES -> "Studio Reference"
        }
    }

    /**
     * Re-scans audio routing, persists profiles to GobeatsDatabaseHelper, and synchronizes EQ preset.
     */
    fun refreshCurrentDeviceProfile(forceApply: Boolean = false) {
        coroutineScope.launch {
            val activeDevice = resolveActiveOutputDevice()
            val type = classifyDeviceType(activeDevice)
            val deviceId = generateDeviceId(activeDevice, type)
            val displayName = getDeviceDisplayName(activeDevice)

            val current = _currentProfile.value
            if (!forceApply && current.deviceId == deviceId) {
                return@launch
            }

            val savedProfile = dbHelper.getDeviceAudioProfile(deviceId)
            val profileToActivate = if (savedProfile != null) {
                savedProfile
            } else {
                val defaultPreset = getDefaultPresetForType(type)
                val newProfile = DeviceAudioProfile(
                    deviceId = deviceId,
                    name = displayName,
                    type = type,
                    preferredEqPreset = defaultPreset,
                    avDelayMs = 0L,
                    updatedAt = System.currentTimeMillis()
                )
                dbHelper.saveDeviceAudioProfile(newProfile)
                newProfile
            }

            _currentProfile.value = profileToActivate
            logI(TAG, "Audio routing switched to [${profileToActivate.name}] (${profileToActivate.type}), applying preset: ${profileToActivate.preferredEqPreset}")

            withContext(Dispatchers.Main) {
                equalizerManager.applyPreset(profileToActivate.preferredEqPreset)
            }
        }
    }

    /**
     * Updates the user's preferred EQ preset for the specified device and persists it to the database.
     */
    fun setPreferredPreset(deviceId: String, preset: String) {
        coroutineScope.launch {
            val current = _currentProfile.value
            val targetProfile = if (current.deviceId == deviceId) {
                current.copy(preferredEqPreset = preset, updatedAt = System.currentTimeMillis())
            } else {
                val saved = dbHelper.getDeviceAudioProfile(deviceId)
                saved?.copy(preferredEqPreset = preset, updatedAt = System.currentTimeMillis())
            }

            if (targetProfile != null) {
                dbHelper.saveDeviceAudioProfile(targetProfile)
                if (current.deviceId == deviceId) {
                    _currentProfile.value = targetProfile
                    withContext(Dispatchers.Main) {
                        equalizerManager.applyPreset(preset)
                    }
                }
            }
        }
    }

    /**
     * Updates the hardware A/V latency compensation offset in milliseconds for the specified device.
     */
    fun setAvDelay(deviceId: String, delayMs: Long) {
        coroutineScope.launch {
            val clampedDelay = delayMs.coerceAtLeast(0L)
            val current = _currentProfile.value
            val targetProfile = if (current.deviceId == deviceId) {
                current.copy(avDelayMs = clampedDelay, updatedAt = System.currentTimeMillis())
            } else {
                val saved = dbHelper.getDeviceAudioProfile(deviceId)
                saved?.copy(avDelayMs = clampedDelay, updatedAt = System.currentTimeMillis())
            }

            if (targetProfile != null) {
                dbHelper.saveDeviceAudioProfile(targetProfile)
                if (current.deviceId == deviceId) {
                    _currentProfile.value = targetProfile
                }
            }
        }
    }

    /**
     * Returns the A/V delay in milliseconds for the currently connected audio output device.
     */
    fun getAvDelayForCurrentDevice(): Long {
        return _currentProfile.value.avDelayMs
    }

    /**
     * Returns all saved hardware profiles from the local database.
     */
    suspend fun getAllSavedProfiles(): List<DeviceAudioProfile> {
        return dbHelper.getAllDeviceAudioProfiles()
    }

    /**
     * Unregisters hardware audio device callbacks and cancels internal coroutines.
     */
    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            logW(TAG, "Error unregistering AudioDeviceCallback", e)
        }
        coroutineScope.cancel()
    }
}
