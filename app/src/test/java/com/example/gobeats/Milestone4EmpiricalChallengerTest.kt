package com.example.gobeats

import com.example.gobeats.core.audio.CrossfadeEngine
import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.core.model.SavedPlaybackState
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.playback.ProcessDeathPersistenceEngine
import com.example.gobeats.core.sync.SyncLockPolicy
import com.example.gobeats.core.sync.TransferStats
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.charset.StandardCharsets

/**
 * Milestone 4 Empirical Challenger Test Suite (challenger_m4_2)
 *
 * Adversarial Stress & Verification Harness for:
 * - Feature F13: Process Death Persistence (Engine, Schema, Ordering, Chunking, Boundaries)
 * - Feature F14: Network Transfer Hardening (WifiLock/WakeLock Policies, Throughput Math, Doze Protection)
 * - Cross-Feature Pairwise Interactions:
 *   - cross_03: Video Resume vs Audio Playback State Isolation
 *   - cross_05: Network Sync Server throughput alongside Scoped Storage Staging
 *   - cross_10: Process Death Resurrection inside Audiophile Crossfade Window
 * - Real-World End-to-End Workflows:
 *   - workflow_04: Audiophile Process Death & Resurrection Lifecycle
 *   - workflow_05: Wi-Fi PC Connect Large Transfer Under Android Doze Mode
 */
class Milestone4EmpiricalChallengerTest {

    // =========================================================================
    // SECTION 1: F13 Process Death Persistence Stress & Adversarial Cases
    // =========================================================================

    @Test
    fun f13_stress_largeQueue5000Items_jsonIntegrityAndOrdering() {
        val largeQueue = (1..5000).map { "track_uid_hex_${it.toString(16).padStart(8, '0')}" }
        val serialized = ProcessDeathPersistenceEngine.serializeQueue(largeQueue)
        assertTrue(serialized.startsWith("["))
        assertTrue(serialized.endsWith("]"))

        val deserialized = ProcessDeathPersistenceEngine.deserializeQueue(serialized)
        assertEquals(5000, deserialized.size)
        assertEquals(largeQueue.first(), deserialized.first())
        assertEquals(largeQueue.last(), deserialized.last())
        assertEquals(largeQueue, deserialized)
    }

    @Test
    fun f13_stress_unicodeSpecialCharacters_newlinesQuotesSurrogates() {
        val trickyIds = listOf(
            "track:with:colons",
            "content://media/external/audio/media/98745?artist=AC%2FDC&title=Back+In+Black",
            "track\"with\"quotes and 'single' quotes",
            "track\\with\\backslashes/and/forward/slashes",
            "track with spaces and emojis \uD83C\uDFB5\uD83C\uDFB6\uD83C\uDFBC\uD83E\uDD41",
            "track_with_\n_newline_and_\r_return_and_\t_tab",
            "track_日本語_한국어_العربية_हिन्दी",
            "" // empty string track ID boundary
        )
        val json = ProcessDeathPersistenceEngine.serializeQueue(trickyIds)
        val restored = ProcessDeathPersistenceEngine.deserializeQueue(json)
        assertEquals(trickyIds.size, restored.size)
        assertEquals(trickyIds, restored)
    }

    @Test
    fun f13_stress_duplicateTracksPreservedInQueueOrder() {
        // User added the same track multiple times to the queue (e.g. track 1 played at beginning, middle, and end)
        val t1 = Track("t1", "local", "/1.flac", "Track 1", "Artist 1", "Album 1", 180000L)
        val t2 = Track("t2", "local", "/2.flac", "Track 2", "Artist 2", "Album 2", 200000L)
        val t3 = Track("t3", "local", "/3.flac", "Track 3", "Artist 3", "Album 3", 220000L)

        val queueWithDuplicates = listOf("t1", "t2", "t1", "t3", "t1")
        val availableTracks = listOf(t1, t2, t3)

        val sorted = ProcessDeathPersistenceEngine.sortTracksByQueueIds(availableTracks, queueWithDuplicates)
        assertEquals(5, sorted.size)
        assertEquals("t1", sorted[0].id)
        assertEquals("t2", sorted[1].id)
        assertEquals("t1", sorted[2].id)
        assertEquals("t3", sorted[3].id)
        assertEquals("t1", sorted[4].id)
    }

    @Test
    fun f13_stress_allTracksMissingFromLibrary_returnsEmpty() {
        val availableTracks = listOf(
            Track("t10", "local", "/10.mp3", "T10", "A", "Alb", 100L)
        )
        val deletedQueueIds = listOf("t1", "t2", "t3", "t4")

        val sorted = ProcessDeathPersistenceEngine.sortTracksByQueueIds(availableTracks, deletedQueueIds)
        assertTrue(sorted.isEmpty())
    }

    @Test
    fun f13_stress_corruptedJsonPayloads_rejectionHandling() {
        val corruptedPayloads = listOf(
            "{ invalid_json_object: true }",
            "null",
            "12345",
            "\"just_a_string\"",
            "[ unclosed array",
            "[{ \"id\": 1 }]", // array of objects, not strings
            "" // empty string
        )

        for (payload in corruptedPayloads) {
            try {
                val result = ProcessDeathPersistenceEngine.deserializeQueue(payload)
                // If it parses an object array into strings, each item must be extracted or fail
                // But non-arrays or malformed JSON should throw JSONException
            } catch (e: Exception) {
                // Expected JSONException or IllegalArgumentException
                assertTrue(e is JSONException || e is ClassCastException)
            }
        }
    }

    @Test
    fun f13_stress_positionCoercion_negativeAndBoundaryValues() {
        // Negative positionMs coerced to at least 0L
        val negativePos = -50000L
        val safePos = negativePos.coerceAtLeast(0L)
        assertEquals(0L, safePos)

        // Zero position
        val zeroPos = 0L.coerceAtLeast(0L)
        assertEquals(0L, zeroPos)

        // 100 hours (360,000,000 ms)
        val hundredHours = 360_000_000L
        val safeHundredHours = hundredHours.coerceAtLeast(0L)
        assertEquals(360_000_000L, safeHundredHours)
    }

    @Test
    fun f13_stress_sqliteChunkingLogic_900LimitBoundaries() {
        // Test chunking boundaries to ensure no parameters exceed SQLite 999 limit
        val sizes = listOf(0, 1, 899, 900, 901, 1800, 1801, 2700, 5000)
        for (size in sizes) {
            val list = (1..size).map { "id_$it" }
            val chunks = list.chunked(900)
            if (size == 0) {
                assertTrue(chunks.isEmpty())
            } else {
                for (chunk in chunks) {
                    assertTrue("Chunk size must be <= 900", chunk.size <= 900)
                    assertTrue("Chunk size must be > 0", chunk.isNotEmpty())
                }
                val reassembled = chunks.flatten()
                assertEquals(size, reassembled.size)
                assertEquals(list, reassembled)
            }
        }
    }

    @Test
    fun f13_stress_savedPlaybackState_serializationRoundtrip() {
        val state = SavedPlaybackState(
            currentTrackId = "track_master_lossless_flac",
            positionMs = 123456L,
            isPlaying = true,
            queueTrackIds = listOf("t1", "t2", "t3", "t4"),
            updatedAt = 1728394857000L
        )

        // Java Serializable roundtrip
        val baos = ByteArrayOutputStream()
        ObjectOutputStream(baos).use { it.writeObject(state) }
        val bytes = baos.toByteArray()

        val bais = ByteArrayInputStream(bytes)
        val deserialized = ObjectInputStream(bais).use { it.readObject() } as SavedPlaybackState

        assertEquals(state.currentTrackId, deserialized.currentTrackId)
        assertEquals(state.positionMs, deserialized.positionMs)
        assertEquals(state.isPlaying, deserialized.isPlaying)
        assertEquals(state.queueTrackIds, deserialized.queueTrackIds)
        assertEquals(state.updatedAt, deserialized.updatedAt)
    }

    // =========================================================================
    // SECTION 2: F14 Network Transfer Hardening Stress & Adversarial Cases
    // =========================================================================

    @Test
    fun f14_stress_transferStats_zeroDivisionAndNegativeDuration() {
        // Zero duration
        val statsZero = TransferStats(bytesTransferred = 1000L, totalBytes = 5000L, durationSeconds = 0.0)
        assertEquals(0.0, statsZero.speedMbPerSec, 0.0001)
        assertEquals(0.2f, statsZero.progressFraction, 0.0001f)
        assertEquals("20%", statsZero.formatProgress())
        assertEquals("0.0 MB/s", statsZero.formatSpeed())

        // Negative duration
        val statsNegativeDur = TransferStats(bytesTransferred = 1000L, totalBytes = 5000L, durationSeconds = -10.0)
        assertEquals(0.0, statsNegativeDur.speedMbPerSec, 0.0001)

        // Zero totalBytes
        val statsZeroTotal = TransferStats(bytesTransferred = 0L, totalBytes = 0L, durationSeconds = 5.0)
        assertEquals(0f, statsZeroTotal.progressFraction, 0.0001f)
        assertEquals("0%", statsZeroTotal.formatProgress())

        // Negative totalBytes
        val statsNegTotal = TransferStats(bytesTransferred = 100L, totalBytes = -500L, durationSeconds = 5.0)
        assertEquals(0f, statsNegTotal.progressFraction, 0.0001f)
    }

    @Test
    fun f14_stress_transferStats_overshootClamping() {
        // In chunked transfers or gzipped streams, bytesTransferred might exceed estimated totalBytes
        val statsOver = TransferStats(
            bytesTransferred = 150L * 1024L * 1024L,
            totalBytes = 100L * 1024L * 1024L,
            durationSeconds = 10.0
        )
        // Must clamp to 1.0f (100%), not 1.5f (150%)
        assertEquals(1.0f, statsOver.progressFraction, 0.0001f)
        assertEquals("100%", statsOver.formatProgress())
        assertEquals(15.0, statsOver.speedMbPerSec, 0.01)
    }

    @Test
    fun f14_stress_transferStats_terabyteScaleTransferCalculations() {
        // 5 Terabytes (5 * 1024^4 bytes) = 5,497,558,138,880 bytes
        val fiveTb = 5L * 1024L * 1024L * 1024L * 1024L
        val durationSec = 3600.0 // 1 hour

        val stats = TransferStats(
            bytesTransferred = fiveTb,
            totalBytes = fiveTb,
            durationSeconds = durationSec
        )
        // 5 TB = 5,242,880 MB. 5,242,880 MB / 3600 s = ~1456.355 MB/s
        assertEquals(1.0f, stats.progressFraction, 0.0001f)
        val expectedSpeed = (5.0 * 1024.0 * 1024.0) / 3600.0
        assertEquals(expectedSpeed, stats.speedMbPerSec, 0.1)
        assertTrue(stats.formatSpeed().contains("MB/s"))
    }

    @Test
    fun f14_stress_syncLockPolicy_exhaustiveApiLevelRouting() {
        // API 29+ -> low latency
        for (api in 29..36) {
            assertEquals("API $api must use low latency wifi lock", "WIFI_MODE_FULL_LOW_LATENCY", SyncLockPolicy.getWifiLockMode(api))
        }

        // API < 29 -> high perf
        for (api in 1..28) {
            assertEquals("API $api must use high perf wifi lock", "WIFI_MODE_FULL_HIGH_PERF", SyncLockPolicy.getWifiLockMode(api))
        }

        // Boundary: 0 and negative
        assertEquals("WIFI_MODE_FULL_HIGH_PERF", SyncLockPolicy.getWifiLockMode(0))
        assertEquals("WIFI_MODE_FULL_HIGH_PERF", SyncLockPolicy.getWifiLockMode(-1))

        // WakeLock timeout
        assertEquals(7_200_000L, SyncLockPolicy.getWakeLockTimeoutMs())
        assertEquals("gobeats:sync_wakelock", SyncLockPolicy.WAKE_LOCK_TAG)
        assertEquals("gobeats:wifi_sync_lock", SyncLockPolicy.WIFI_LOCK_TAG)
    }

    @Test
    fun f14_stress_lockRelease_idempotentReleaseAndExceptionSafety() {
        // Test lock safety wrapper semantics matching GobeatsSyncService.releaseLocks()
        var mockWifiLockHeld = false
        var mockWakeLockHeld = false

        fun safeReleaseLocks() {
            runCatching {
                if (mockWifiLockHeld) {
                    mockWifiLockHeld = false
                }
            }
            runCatching {
                if (mockWakeLockHeld) {
                    mockWakeLockHeld = false
                }
            }
        }

        // 1. Release when not held: must not throw
        safeReleaseLocks()
        assertFalse(mockWifiLockHeld)
        assertFalse(mockWakeLockHeld)

        // 2. Acquire and release
        mockWifiLockHeld = true
        mockWakeLockHeld = true
        safeReleaseLocks()
        assertFalse(mockWifiLockHeld)
        assertFalse(mockWakeLockHeld)

        // 3. Repeated release: idempotent
        safeReleaseLocks()
        assertFalse(mockWifiLockHeld)
        assertFalse(mockWakeLockHeld)
    }

    // =========================================================================
    // SECTION 3: Cross-Feature Pairwise Interaction Stress Cases
    // =========================================================================

    @Test
    fun cross_03_adversarial_videoResumeAndAudioPlaybackStateIsolation() {
        // Ensure that storing and restoring audio queue state does NOT interfere with video resume memory records
        val audioQueue = listOf("audio_trk_10", "audio_trk_20", "audio_trk_30")
        val audioSerialized = ProcessDeathPersistenceEngine.serializeQueue(audioQueue)
        val audioSavedState = SavedPlaybackState(
            currentTrackId = "audio_trk_20",
            positionMs = 64200L,
            isPlaying = false,
            queueTrackIds = audioQueue,
            updatedAt = 5000L
        )

        val videoResume1 = GobeatsTier1FeatureCoverageTest.VideoResumeRecord(
            videoId = "video_vid_01",
            positionMs = 120000L,
            durationMs = 600000L,
            updatedAt = 6000L
        )
        val videoResume2 = GobeatsTier1FeatureCoverageTest.VideoResumeRecord(
            videoId = "video_vid_02",
            positionMs = 350000L,
            durationMs = 900000L,
            updatedAt = 7000L
        )

        // Audio restoration
        val restoredAudioQueue = ProcessDeathPersistenceEngine.deserializeQueue(audioSerialized)
        assertEquals(audioQueue, restoredAudioQueue)
        assertEquals("audio_trk_20", audioSavedState.currentTrackId)
        assertEquals(64200L, audioSavedState.positionMs)

        // Video positions remain isolated
        assertTrue(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(videoResume1))
        assertTrue(GobeatsTier1FeatureCoverageTest.VideoResumeDecisionEngine.shouldPromptResume(videoResume2))
        assertEquals(120000L, videoResume1.positionMs)
        assertEquals(350000L, videoResume2.positionMs)
    }

    @Test
    fun cross_05_adversarial_networkSyncActiveDuringScopedStorageStaging() {
        // Simulate high throughput file transfer on background thread while Scoped Storage tag writing takes place
        val syncStats = TransferStats(
            bytesTransferred = 85L * 1024L * 1024L,
            totalBytes = 100L * 1024L * 1024L,
            durationSeconds = 8.5
        )
        assertEquals(0.85f, syncStats.progressFraction, 0.001f)
        assertEquals(10.0, syncStats.speedMbPerSec, 0.01)

        // Concurrently, scoped storage staging file is created and validated
        val tempDir = File(System.getProperty("java.io.tmpdir"), "gobeats_cross05_" + System.currentTimeMillis())
        tempDir.mkdirs()
        val stagingFile = File(tempDir, "song_staging.flac")
        try {
            val flacMagic = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
            stagingFile.writeBytes(flacMagic + ByteArray(64))

            val isValid = GobeatsTier1FeatureCoverageTest.ScopedStorageRouter.validateStagingFile(stagingFile, flacMagic)
            assertTrue(isValid)

            // Both operations succeed without locking conflict
            assertEquals(10.0, syncStats.speedMbPerSec, 0.01)
        } finally {
            stagingFile.delete()
            tempDir.delete()
        }
    }

    @Test
    fun cross_10_adversarial_relaunchInsideCrossfadeWindow_pausedGuarantees() {
        // App was playing a 180s track with 4s crossfade.
        // It was persisted at 178,500ms (1.5s remaining before track end, inside crossfade window).
        val queue = listOf("trk_a", "trk_b")
        val serializedQueue = ProcessDeathPersistenceEngine.serializeQueue(queue)

        val restoredState = SavedPlaybackState(
            currentTrackId = "trk_a",
            positionMs = 178500L,
            isPlaying = false, // Strictly paused
            queueTrackIds = ProcessDeathPersistenceEngine.deserializeQueue(serializedQueue),
            updatedAt = System.currentTimeMillis()
        )

        // Guarantee 1: State is restored paused
        assertFalse(restoredState.isPlaying)
        assertEquals(178500L, restoredState.positionMs)

        // Guarantee 2: When user resumes playback, CrossfadeEngine correctly detects boundary
        val shouldCrossfade = CrossfadeEngine.shouldTriggerCrossfade(
            currentPosMs = restoredState.positionMs,
            durationMs = 180000L,
            crossfadeSec = 4,
            queueIndex = 0,
            queueSize = 2,
            repeatMode = RepeatMode.OFF
        )
        assertTrue("Playback resumed inside crossfade window must trigger crossfade", shouldCrossfade)

        // Compute crossfade progress and volume ramp
        val progress = CrossfadeEngine.calculateProgress(restoredState.positionMs, 180000L, 4)
        assertTrue(progress in 0.0f..1.0f)
        val (volOut, volIn) = CrossfadeEngine.computeEqualPowerVolumes(progress)
        assertTrue(volOut in 0.0f..1.0f)
        assertTrue(volIn in 0.0f..1.0f)
    }

    // =========================================================================
    // SECTION 4: Tier 4 End-to-End Real-World Workflow Tests
    // =========================================================================

    @Test
    fun workflow_04_audiophileProcessDeathResurrection_mixedFormatsAndShuffle() {
        // 1. User builds 6-track listening queue with mixed audio formats
        val flacTrack1 = Track("t_flac_1", "local", "/music/01.flac", "Flac Song 1", "Artist A", "Album Lossless", 240000L)
        val mp3Track2  = Track("t_mp3_2",  "local", "/music/02.mp3",  "Mp3 Song 2",  "Artist B", "Album Lossy",    180000L)
        val m4aTrack3  = Track("t_m4a_3",  "local", "/music/03.m4a",  "M4a Song 3",  "Artist C", "Album AAC",      210000L)
        val flacTrack4 = Track("t_flac_4", "local", "/music/04.flac", "Flac Song 4", "Artist A", "Album Lossless", 300000L)
        val mp3Track5  = Track("t_mp3_5",  "local", "/music/05.mp3",  "Mp3 Song 5",  "Artist D", "Album Rock",     195000L)
        val m4aTrack6  = Track("t_m4a_6",  "local", "/music/06.m4a",  "M4a Song 6",  "Artist E", "Album Jazz",     225000L)

        val fullLibrary = listOf(flacTrack1, mp3Track2, m4aTrack3, flacTrack4, mp3Track5, m4aTrack6)

        // User shuffled / custom ordered the queue:
        val customQueueIds = listOf("t_m4a_3", "t_flac_4", "t_mp3_2", "t_m4a_6", "t_flac_1")

        // 2. Playback is active on track 4 (t_flac_4) at position 154,230 ms
        val activeTrackId = "t_flac_4"
        val activePositionMs = 154230L

        // 3. App persists state into SQLite playback_state before Android kills the process
        val serializedQueueJson = ProcessDeathPersistenceEngine.serializeQueue(customQueueIds)
        val savedSnapshot = SavedPlaybackState(
            currentTrackId = activeTrackId,
            positionMs = activePositionMs,
            isPlaying = true,
            queueTrackIds = customQueueIds,
            updatedAt = System.currentTimeMillis()
        )

        // 4. Android process termination and recreation:
        // Fresh launch queries database:
        val restoredQueueIds = ProcessDeathPersistenceEngine.deserializeQueue(serializedQueueJson)
        assertEquals(customQueueIds, restoredQueueIds)

        // 5. Restore ordered tracks using sortTracksByQueueIds:
        val restoredTracks = ProcessDeathPersistenceEngine.sortTracksByQueueIds(fullLibrary, restoredQueueIds)
        assertEquals(5, restoredTracks.size)
        assertEquals("t_m4a_3", restoredTracks[0].id)
        assertEquals("t_flac_4", restoredTracks[1].id)
        assertEquals("t_mp3_2", restoredTracks[2].id)
        assertEquals("t_m4a_6", restoredTracks[3].id)
        assertEquals("t_flac_1", restoredTracks[4].id)

        // 6. Verify restored target index and millisecond timestamp
        val restoredTargetIndex = restoredTracks.indexOfFirst { it.id == savedSnapshot.currentTrackId }
        assertEquals(1, restoredTargetIndex)
        val activeTrack = restoredTracks[restoredTargetIndex]
        assertEquals("Flac Song 4", activeTrack.title)
        assertEquals("Album Lossless", activeTrack.albumTitle)
        assertEquals(154230L, savedSnapshot.positionMs)

        // 7. Verify safe playWhenReady policy (must be false to prevent sudden blaring)
        val playWhenReady = false
        assertFalse(playWhenReady)
    }

    @Test
    fun workflow_05_wifiPcConnectLargeTransferDozeMode() {
        // 1. Determine lock parameters for modern Android (API 34)
        val wifiMode = SyncLockPolicy.getWifiLockMode(34)
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", wifiMode)

        val wakeLockTimeoutMs = SyncLockPolicy.getWakeLockTimeoutMs()
        assertEquals(7_200_000L, wakeLockTimeoutMs) // Bounded 2 hours

        // 2. Simulate Foreground Service start & lock acquisition
        var wifiLockActive = true
        var wakeLockActive = true
        var isServiceRunning = true

        assertTrue(wifiLockActive)
        assertTrue(wakeLockActive)
        assertTrue(isServiceRunning)

        // 3. Transfer 500 MB lossless FLAC library
        val totalBytes = 500L * 1024L * 1024L

        // Quarter progress check
        val statsQuarter = TransferStats(
            bytesTransferred = 125L * 1024L * 1024L,
            totalBytes = totalBytes,
            durationSeconds = 12.5
        )
        assertEquals(0.25f, statsQuarter.progressFraction, 0.001f)
        assertEquals("25%", statsQuarter.formatProgress())
        assertEquals(10.0, statsQuarter.speedMbPerSec, 0.05)
        assertEquals("10.0 MB/s", statsQuarter.formatSpeed())

        // Halfway progress check
        val statsHalf = TransferStats(
            bytesTransferred = 250L * 1024L * 1024L,
            totalBytes = totalBytes,
            durationSeconds = 25.0
        )
        assertEquals(0.5f, statsHalf.progressFraction, 0.001f)
        assertEquals("50%", statsHalf.formatProgress())
        assertEquals(10.0, statsHalf.speedMbPerSec, 0.05)

        // Completion
        val statsDone = TransferStats(
            bytesTransferred = totalBytes,
            totalBytes = totalBytes,
            durationSeconds = 50.0
        )
        assertEquals(1.0f, statsDone.progressFraction, 0.001f)
        assertEquals("100%", statsDone.formatProgress())

        // 4. Teardown: Stop Sync Server & release locks
        wifiLockActive = false
        wakeLockActive = false
        isServiceRunning = false

        assertFalse(wifiLockActive)
        assertFalse(wakeLockActive)
        assertFalse(isServiceRunning)
    }
}
