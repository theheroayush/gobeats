package com.example.gobeats.core.video

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.model.DualSubtitleTrack
import com.example.gobeats.core.model.SubtitleCue
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern

/**
 * Metadata for languages supported by the AI subtitle engine.
 */
data class SubtitleLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val isRegionalIndian: Boolean = false
)

/**
 * Offline on-device speech recognition orchestrator using Android's native SpeechRecognizer.
 * Focuses on Hindi ('hi-IN') and Indian regional languages ('ta-IN', 'te-IN', 'mr-IN', 'bn-IN')
 * with mandatory fallback to English ('en-IN', 'en-US').
 */
object AiSubtitleEngine {

    val LANG_HINDI = SubtitleLanguage("hi-IN", "Hindi", "हिन्दी", isRegionalIndian = true)
    val LANG_TAMIL = SubtitleLanguage("ta-IN", "Tamil", "தமிழ்", isRegionalIndian = true)
    val LANG_TELUGU = SubtitleLanguage("te-IN", "Telugu", "తెలుగు", isRegionalIndian = true)
    val LANG_MARATHI = SubtitleLanguage("mr-IN", "Marathi", "मराठी", isRegionalIndian = true)
    val LANG_BENGALI = SubtitleLanguage("bn-IN", "Bengali", "বাংলা", isRegionalIndian = true)
    val LANG_ENGLISH_IN = SubtitleLanguage("en-IN", "English (India)", "English (India)", isRegionalIndian = false)
    val LANG_ENGLISH_US = SubtitleLanguage("en-US", "English (US)", "English (US)", isRegionalIndian = false)

    /** Priority ordered list of supported languages. */
    val SUPPORTED_LANGUAGES: List<SubtitleLanguage> = listOf(
        LANG_HINDI,
        LANG_TAMIL,
        LANG_TELUGU,
        LANG_MARATHI,
        LANG_BENGALI,
        LANG_ENGLISH_IN,
        LANG_ENGLISH_US
    )

    private val TIMESTAMP_PATTERN = Pattern.compile(
        "(\\d{1,2}):(\\d{2}):(\\d{2})[.,](\\d{3})\\s*-->\\s*(\\d{1,2}):(\\d{2}):(\\d{2})[.,](\\d{3})"
    )

    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]*>")

    /**
     * Checks if on-device offline speech recognition is supported and available on this hardware.
     * Uses [SpeechRecognizer.isOnDeviceRecognitionAvailable] on Android 12+ (API 31+).
     */
    fun isOnDeviceRecognitionAvailable(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } catch (_: Throwable) {
                false
            }
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }
    }

    /**
     * Provides the mandatory fallback language if the requested language model is missing or unsupported.
     * Hindi and Indian regional languages fallback to English (India) 'en-IN',
     * which in turn falls back to English (US) 'en-US'.
     */
    fun getFallbackLanguage(languageCode: String): String {
        return when (languageCode.lowercase(Locale.ROOT)) {
            "hi-in", "hi", "ta-in", "ta", "te-in", "te", "mr-in", "mr", "bn-in", "bn" -> "en-IN"
            "en-in" -> "en-US"
            else -> "en-US"
        }
    }

    /**
     * Creates an Intent to navigate the user to Android voice / speech recognition settings
     * to download offline language packs when the model is not downloaded.
     */
    fun getLanguagePackIntent(languageCode: String): Intent {
        val voiceSettingsIntent = Intent("android.speech.action.VOICE_SEARCH_SETTINGS").apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra("android.speech.extra.LANGUAGE_MODEL", RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return voiceSettingsIntent
    }

    /**
     * Secondary fallback intent to open speech/locale settings if VOICE_SEARCH_SETTINGS is not resolved.
     */
    fun getFallbackSettingsIntent(): Intent {
        return Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Builds a [RecognizerIntent] configured for offline on-device speech recognition.
     */
    fun buildRecognitionIntent(languageCode: String, preferOffline: Boolean = true): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageCode)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
    }

    /**
     * Creates a timed [SubtitleCue] from recognized text and position offset.
     */
    fun createTimedCue(
        text: String,
        startMs: Long,
        durationMs: Long = 3200L
    ): SubtitleCue {
        val sanitized = HTML_TAG_PATTERN.matcher(text.trim()).replaceAll("").trim()
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = safeStart + durationMs.coerceAtLeast(1000L)
        return SubtitleCue(
            startMs = safeStart,
            endMs = safeEnd,
            text = sanitized
        )
    }

    /**
     * Converts a series of timestamped transcription segments into ordered, non-overlapping [SubtitleCue] objects.
     */
    fun buildTimedCues(
        segments: List<Pair<Long, String>>,
        defaultDurationMs: Long = 3000L
    ): List<SubtitleCue> {
        if (segments.isEmpty()) return emptyList()
        val sorted = segments.sortedBy { it.first }
        val cues = mutableListOf<SubtitleCue>()

        for (i in sorted.indices) {
            val (startMs, text) = sorted[i]
            val cleanText = HTML_TAG_PATTERN.matcher(text.trim()).replaceAll("").trim()
            if (cleanText.isBlank()) continue

            val nextStart = sorted.getOrNull(i + 1)?.first
            val endMs = if (nextStart != null && nextStart > startMs) {
                (startMs + defaultDurationMs).coerceAtMost(nextStart)
            } else {
                startMs + defaultDurationMs
            }
            cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = cleanText))
        }
        return cues
    }

    /**
     * Parses SubRip (.srt) subtitle content into a typed list of [SubtitleCue] objects.
     * Supports both comma and dot millisecond delimiters and strips any embedded HTML tags.
     */
    fun parseSrt(srtContent: String): List<SubtitleCue> {
        if (srtContent.isBlank()) return emptyList()

        val cues = mutableListOf<SubtitleCue>()
        val lines = srtContent.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        var currentStartMs: Long = -1L
        var currentEndMs: Long = -1L
        val textLines = mutableListOf<String>()

        fun commitCue() {
            if (currentStartMs >= 0 && currentEndMs > currentStartMs && textLines.isNotEmpty()) {
                val combinedText = textLines.joinToString("\n").trim()
                val cleanText = HTML_TAG_PATTERN.matcher(combinedText).replaceAll("").trim()
                if (cleanText.isNotBlank()) {
                    cues.add(
                        SubtitleCue(
                            startMs = currentStartMs,
                            endMs = currentEndMs,
                            text = cleanText
                        )
                    )
                }
            }
            currentStartMs = -1L
            currentEndMs = -1L
            textLines.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                commitCue()
                continue
            }

            // Check if this line is an SRT timestamp line: 00:00:01,000 --> 00:00:04,500
            val matcher = TIMESTAMP_PATTERN.matcher(trimmed)
            if (matcher.matches()) {
                // If previous cue wasn't committed, commit now
                commitCue()
                val startHours = matcher.group(1)?.toLong() ?: 0L
                val startMins = matcher.group(2)?.toLong() ?: 0L
                val startSecs = matcher.group(3)?.toLong() ?: 0L
                val startMillis = matcher.group(4)?.toLong() ?: 0L

                val endHours = matcher.group(5)?.toLong() ?: 0L
                val endMins = matcher.group(6)?.toLong() ?: 0L
                val endSecs = matcher.group(7)?.toLong() ?: 0L
                val endMillis = matcher.group(8)?.toLong() ?: 0L

                currentStartMs = (startHours * 3600_000L) + (startMins * 60_000L) + (startSecs * 1000L) + startMillis
                currentEndMs = (endHours * 3600_000L) + (endMins * 60_000L) + (endSecs * 1000L) + endMillis
                continue
            }

            // Skip cue sequence index numbers (e.g. 1, 2, 3) when not inside text
            if (currentStartMs < 0 && trimmed.all { it.isDigit() }) {
                continue
            }

            // Text line of active cue
            if (currentStartMs >= 0) {
                textLines.add(trimmed)
            }
        }
        // Commit trailing cue
        commitCue()

        return cues
    }

    /**
     * Serializes a list of [SubtitleCue] objects into standard SubRip (.srt) format.
     */
    fun toSrt(cues: List<SubtitleCue>): String {
        if (cues.isEmpty()) return ""
        val sb = StringBuilder()
        val sortedCues = cues.sortedBy { it.startMs }

        sortedCues.forEachIndexed { index, cue ->
            val cueNumber = index + 1
            val startTime = formatTimeSrt(cue.startMs)
            val endTime = formatTimeSrt(cue.endMs)
            val cleanText = HTML_TAG_PATTERN.matcher(cue.text.trim()).replaceAll("").trim()

            sb.append(cueNumber).append("\n")
            sb.append(startTime).append(" --> ").append(endTime).append("\n")
            sb.append(cleanText).append("\n\n")
        }

        return sb.toString().trimEnd() + "\n"
    }

    /**
     * Formats milliseconds into standard SRT timestamp format: HH:mm:ss,SSS
     */
    internal fun formatTimeSrt(timeMs: Long): String {
        val totalMs = timeMs.coerceAtLeast(0L)
        val millis = totalMs % 1000L
        val totalSeconds = totalMs / 1000L
        val seconds = totalSeconds % 60L
        val totalMinutes = totalSeconds / 60L
        val minutes = totalMinutes % 60L
        val hours = totalMinutes / 60L
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    // ==========================================
    // DATABASE INTEGRATION
    // ==========================================

    /**
     * Saves generated AI subtitles to the persistent SQLite database for the specified track and language.
     */
    suspend fun saveSubtitlesToDatabase(
        dbHelper: GobeatsDatabaseHelper,
        trackId: String,
        languageCode: String,
        cues: List<SubtitleCue>
    ) = withContext(Dispatchers.IO) {
        val srtContent = toSrt(cues)
        dbHelper.saveGeneratedSubtitle(trackId, languageCode, srtContent)
    }

    /**
     * Retrieves cached AI subtitles from the database and parses them into [SubtitleCue] objects.
     */
    suspend fun getSubtitlesFromDatabase(
        dbHelper: GobeatsDatabaseHelper,
        trackId: String,
        languageCode: String
    ): List<SubtitleCue>? = withContext(Dispatchers.IO) {
        val srt = dbHelper.getGeneratedSubtitle(trackId, languageCode)
        if (!srt.isNullOrBlank()) {
            parseSrt(srt)
        } else null
    }

    /**
     * Retrieves or creates a [DualSubtitleTrack] for the specified track and language code.
     */
    suspend fun getOrGenerateDualTrack(
        dbHelper: GobeatsDatabaseHelper,
        track: Track,
        languageCode: String
    ): DualSubtitleTrack = withContext(Dispatchers.IO) {
        val cachedCues = getSubtitlesFromDatabase(dbHelper, track.id, languageCode)
        val langMeta = SUPPORTED_LANGUAGES.find { it.code.equals(languageCode, ignoreCase = true) }
        val label = langMeta?.displayName ?: languageCode.uppercase()

        if (!cachedCues.isNullOrEmpty()) {
            DualSubtitleTrack(
                id = "ai_${track.id}_$languageCode",
                label = "$label (AI Subtitles)",
                languageCode = languageCode,
                isAiGenerated = true,
                cues = cachedCues
            )
        } else {
            DualSubtitleTrack(
                id = "ai_${track.id}_$languageCode",
                label = "$label (AI Subtitles)",
                languageCode = languageCode,
                isAiGenerated = true,
                cues = emptyList()
            )
        }
    }
}

/**
 * Live session manager for on-device SpeechRecognizer recognition callbacks.
 */
class AiSubtitleSession(
    private val context: Context,
    private val languageCode: String,
    private val onCueGenerated: (SubtitleCue) -> Unit,
    private val onError: (errorCode: Int, fallbackLanguage: String?) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var currentPositionProvider: (() -> Long)? = null

    fun startListening(positionProvider: () -> Long) {
        if (isListening) return
        currentPositionProvider = positionProvider

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = AiSubtitleEngine.buildRecognitionIntent(languageCode, preferOffline = true)

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                isListening = false
                val fallback = if (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                    error == SpeechRecognizer.ERROR_SERVER ||
                    error == SpeechRecognizer.ERROR_NO_MATCH
                ) {
                    AiSubtitleEngine.getFallbackLanguage(languageCode)
                } else null
                onError(error, fallback)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    val pos = currentPositionProvider?.invoke() ?: 0L
                    val cue = AiSubtitleEngine.createTimedCue(text, pos)
                    onCueGenerated(cue)
                }
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    val pos = currentPositionProvider?.invoke() ?: 0L
                    val cue = AiSubtitleEngine.createTimedCue(text, pos, durationMs = 2000L)
                    onCueGenerated(cue)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            isListening = false
            onError(SpeechRecognizer.ERROR_CLIENT, AiSubtitleEngine.getFallbackLanguage(languageCode))
        }
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        isListening = false
    }
}
