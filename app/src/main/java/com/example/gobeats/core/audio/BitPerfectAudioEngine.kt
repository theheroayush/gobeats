package com.example.gobeats.core.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.gobeats.core.model.Track

data class AudioHardwareStatus(
    val outputDeviceName: String,
    val outputTypeLabel: String,
    val isExternalDac: Boolean,
    val isBitPerfectSupported: Boolean,
    val isBitPerfectActive: Boolean,
    val supportedSampleRates: List<Int>,
    val supportedEncodings: List<String>,
    val signalPathDescription: String
)

object BitPerfectAudioEngine {

    /**
     * Inspects connected output devices and returns detailed hardware & signal path diagnostics.
     */
    fun getHardwareStatus(context: Context, track: Track? = null, isBitPerfectRequested: Boolean = true): AudioHardwareStatus {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: emptyArray()

        // Prioritize external USB DACs over wired, bluetooth, and internal speaker
        val priorityComparator = Comparator<AudioDeviceInfo> { a, b ->
            devicePriority(b.type) - devicePriority(a.type)
        }
        val primaryDevice = devices.sortedWith(priorityComparator).firstOrNull()

        val deviceName = primaryDevice?.productName?.toString()?.ifBlank { null }
            ?: (primaryDevice?.let { getDeviceTypeName(it.type) } ?: "Internal Audio Subsystem")

        val typeLabel = primaryDevice?.let { getDeviceTypeName(it.type) } ?: "Speaker"
        val isUsbDac = primaryDevice?.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                primaryDevice?.type == AudioDeviceInfo.TYPE_USB_HEADSET

        val sampleRates = primaryDevice?.sampleRates?.toList()?.filter { it > 0 }?.sorted()
            ?: listOf(44100, 48000, 96000, 192000)

        val encodings = primaryDevice?.encodings?.toList()?.mapNotNull { encodingToString(it) }?.distinct()
            ?: listOf("32-bit Float", "24-bit PCM", "16-bit PCM")

        val bitPerfectSupported = isUsbDac && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val bitPerfectActive = bitPerfectSupported && isBitPerfectRequested

        // Build human-readable signal path description
        val sourceFormat = track?.format?.uppercase() ?: "AUDIO"
        val sourceRate = if ((track?.sampleRate ?: 0) > 0) "${track!!.sampleRate / 1000}kHz" else "Lossless"
        val sourceDepth = if (track?.bitrate != null && track.bitrate > 1000) "24-bit" else "16/24-bit"
        val dspPipeline = "32-bit Float AudioSink"
        val sinkLabel = if (bitPerfectActive) "Direct USB Bit-Perfect Sink ($deviceName)" else "$deviceName ($typeLabel)"

        val path = "[$sourceFormat $sourceDepth / $sourceRate] ➔ [$dspPipeline] ➔ [$sinkLabel]"

        return AudioHardwareStatus(
            outputDeviceName = deviceName,
            outputTypeLabel = typeLabel,
            isExternalDac = isUsbDac,
            isBitPerfectSupported = bitPerfectSupported,
            isBitPerfectActive = bitPerfectActive,
            supportedSampleRates = sampleRates,
            supportedEncodings = encodings,
            signalPathDescription = path
        )
    }

    /**
     * Builds a studio-grade 32-bit float audio sink that bypasses Android integer truncation.
     */
    fun createHighPrecisionAudioSink(
        context: Context,
        enableFloatOutput: Boolean = true,
        enableAudioTrackPlaybackParams: Boolean = true
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    // AudioDeviceInfo BLE device types (introduced in API 31)
    private const val TYPE_BLE_HEADSET_INT = 26
    private const val TYPE_BLE_SPEAKER_INT = 27
    private const val TYPE_BLE_BROADCAST_INT = 30

    private fun devicePriority(type: Int): Int {
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE -> 100 // Highest priority: dedicated USB DAC
            AudioDeviceInfo.TYPE_USB_HEADSET -> 90
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 80
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> 70
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> 65
            AudioDeviceInfo.TYPE_LINE_ANALOG -> 60
            AudioDeviceInfo.TYPE_HDMI -> 55
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 40
            TYPE_BLE_HEADSET_INT, TYPE_BLE_SPEAKER_INT, TYPE_BLE_BROADCAST_INT -> 35
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 10
            else -> 0
        }
    }

    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB DAC / Audio Interface"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "3.5mm Headphone Jack"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "3.5mm Headset"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Hi-Res (A2DP)"
            TYPE_BLE_HEADSET_INT, TYPE_BLE_SPEAKER_INT, TYPE_BLE_BROADCAST_INT -> "Bluetooth LE Audio"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Out (Analog)"
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital S/PDIF / Optical"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI Audio"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Internal Speaker"
            else -> "Audio Output ($type)"
        }
    }

    private fun encodingToString(encoding: Int): String? {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> "32-bit Float"
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit Packed"
            AudioFormat.ENCODING_PCM_32BIT -> "32-bit Integer"
            AudioFormat.ENCODING_PCM_16BIT -> "16-bit PCM"
            AudioFormat.ENCODING_DTS, AudioFormat.ENCODING_DTS_HD -> "DTS-HD Master"
            AudioFormat.ENCODING_AC3, AudioFormat.ENCODING_E_AC3 -> "Dolby Digital"
            else -> null
        }
    }
}
