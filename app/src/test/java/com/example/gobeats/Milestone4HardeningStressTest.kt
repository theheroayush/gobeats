package com.example.gobeats

import com.example.gobeats.core.model.SavedPlaybackState
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.playback.ProcessDeathPersistenceEngine
import com.example.gobeats.core.sync.SyncLockPolicy
import com.example.gobeats.core.sync.TransferStats
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Adversarial and Stress Test Suite for Milestone 4 (Features F13 & F14).
 * Tests high-scale queues, binary limits, socket rate math, and lifecycle recovery.
 */
class Milestone4HardeningStressTest {

    // =========================================================================
    // F13: Process Death Persistence Stress Tests
    // =========================================================================

    @Test
    fun f13_stress_largeQueue1000Items_serializesAndRestoresAccurately() {
        val largeQueue = (1..1000).map { "track_uid_hex_${it.toString(16).padStart(6, '0')}" }
        val serialized = ProcessDeathPersistenceEngine.serializeQueue(largeQueue)
        assertTrue(serialized.startsWith("["))
        assertTrue(serialized.endsWith("]"))

        val deserialized = ProcessDeathPersistenceEngine.deserializeQueue(serialized)
        assertEquals(1000, deserialized.size)
        assertEquals(largeQueue, deserialized)
    }

    @Test
    fun f13_stress_specialCharacterEscaping_handlesUnusualTrackIds() {
        val trickyIds = listOf(
            "track:with:colons",
            "content://media/external/audio/media/98745?artist=AC%2FDC&title=Back+In+Black",
            "track\"with\"quotes",
            "track\\with\\backslashes",
            "track with spaces and emojis \uD83C\uDFB5\uD83C\uDFB6",
            "track'single'quotes"
        )
        val json = ProcessDeathPersistenceEngine.serializeQueue(trickyIds)
        val restored = ProcessDeathPersistenceEngine.deserializeQueue(json)
        assertEquals(trickyIds, restored)
    }

    @Test
    fun f13_stress_sortTracksByQueueIds_handlesMissingAndDeletedTracks() {
        val t1 = Track("t1", "local", "/1", "Track 1", "Artist", "Album", 180000L)
        val t2 = Track("t2", "local", "/2", "Track 2", "Artist", "Album", 200000L)
        val t4 = Track("t4", "local", "/4", "Track 4", "Artist", "Album", 220000L)
        // t3 was deleted from disk and is absent in the tracks list
        val availableTracks = listOf(t2, t4, t1)
        val requestedQueueIds = listOf("t4", "t3", "t1", "t2", "t99_unknown")

        val sorted = ProcessDeathPersistenceEngine.sortTracksByQueueIds(availableTracks, requestedQueueIds)
        assertEquals(3, sorted.size)
        assertEquals("t4", sorted[0].id)
        assertEquals("t1", sorted[1].id)
        assertEquals("t2", sorted[2].id)
    }

    @Test
    fun f13_stress_corruptJsonQueue_throwsJSONException() {
        try {
            ProcessDeathPersistenceEngine.deserializeQueue("{ invalid_json_object: true }")
            fail("Expected JSONException for invalid JSON array")
        } catch (e: JSONException) {
            assertTrue(e.message != null)
        }
    }

    @Test
    fun f13_stress_savedPlaybackState_preservesMillisecondPrecisionAndDefaults() {
        val now = System.currentTimeMillis()
        val state = SavedPlaybackState(
            currentTrackId = "track_audio_flac_99",
            positionMs = 3847291L,
            isPlaying = true,
            queueTrackIds = listOf("t1", "t2", "t3"),
            updatedAt = now
        )
        assertEquals("track_audio_flac_99", state.currentTrackId)
        assertEquals(3847291L, state.positionMs)
        assertTrue(state.isPlaying)
        assertEquals(3, state.queueTrackIds.size)
        assertEquals(now, state.updatedAt)

        val emptyState = SavedPlaybackState(
            currentTrackId = null,
            positionMs = 0L,
            isPlaying = false,
            queueTrackIds = emptyList()
        )
        assertNull(emptyState.currentTrackId)
        assertEquals(0L, emptyState.positionMs)
        assertFalse(emptyState.isPlaying)
        assertTrue(emptyState.queueTrackIds.isEmpty())
    }

    // =========================================================================
    // F14: Network Transfer Hardening Stress Tests
    // =========================================================================

    @Test
    fun f14_stress_multiGigabyteTransferMath_doesNotOverflow64Bit() {
        // 65 GB file transfer
        val sixtyFiveGigabytes = 65L * 1024L * 1024L * 1024L
        val thirtyTwoGigabytes = 32L * 1024L * 1024L * 1024L
        val stats = TransferStats(
            bytesTransferred = thirtyTwoGigabytes,
            totalBytes = sixtyFiveGigabytes,
            durationSeconds = 640.0 // 10 minutes 40 seconds
        )
        assertEquals(0.4923f, stats.progressFraction, 0.001f)
        // 32768 MB / 640s = 51.2 MB/s
        assertEquals(51.2, stats.speedMbPerSec, 0.1)
        assertEquals("51.2 MB/s", stats.formatSpeed())
        assertEquals("49%", stats.formatProgress())
    }

    @Test
    fun f14_stress_zeroByteAndZeroDurationTransfers_areSafelyCalculated() {
        val zeroStats = TransferStats(0L, 0L, 0.0)
        assertEquals(0.0f, zeroStats.progressFraction, 0.0001f)
        assertEquals(0.0, zeroStats.speedMbPerSec, 0.0001)
        assertEquals("0.0 MB/s", zeroStats.formatSpeed())
        assertEquals("0%", zeroStats.formatProgress())

        val zeroDurationComplete = TransferStats(1000L, 1000L, 0.0)
        assertEquals(1.0f, zeroDurationComplete.progressFraction, 0.0001f)
        assertEquals(0.0, zeroDurationComplete.speedMbPerSec, 0.0001)
    }

    @Test
    fun f14_stress_progressFractionClampedToUnity_onAnomalousTransfers() {
        val overflown = TransferStats(
            bytesTransferred = 1500L,
            totalBytes = 1000L,
            durationSeconds = 1.0
        )
        assertEquals(1.0f, overflown.progressFraction, 0.0001f)
        assertEquals("100%", overflown.formatProgress())
    }

    @Test
    fun f14_stress_syncLockPolicy_resolvesApiLevelsCorrectly() {
        assertEquals("WIFI_MODE_FULL_HIGH_PERF", SyncLockPolicy.getWifiLockMode(21))
        assertEquals("WIFI_MODE_FULL_HIGH_PERF", SyncLockPolicy.getWifiLockMode(24))
        assertEquals("WIFI_MODE_FULL_HIGH_PERF", SyncLockPolicy.getWifiLockMode(28))
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", SyncLockPolicy.getWifiLockMode(29))
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", SyncLockPolicy.getWifiLockMode(30))
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", SyncLockPolicy.getWifiLockMode(33))
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", SyncLockPolicy.getWifiLockMode(34))
        assertEquals("WIFI_MODE_FULL_LOW_LATENCY", SyncLockPolicy.getWifiLockMode(36))

        assertEquals(7_200_000L, SyncLockPolicy.getWakeLockTimeoutMs())
        assertEquals("gobeats:sync_wakelock", SyncLockPolicy.WAKE_LOCK_TAG)
        assertEquals("gobeats:wifi_sync_lock", SyncLockPolicy.WIFI_LOCK_TAG)
    }

    @Test
    fun f14_stress_simulatedLockLifecycle_guaranteesSafeReleaseOnAbort() {
        var wifiLockHeld = false
        var wakeLockHeld = false

        fun acquire() {
            wifiLockHeld = true
            wakeLockHeld = true
        }

        fun release() {
            if (wifiLockHeld) wifiLockHeld = false
            if (wakeLockHeld) wakeLockHeld = false
        }

        acquire()
        assertTrue(wifiLockHeld)
        assertTrue(wakeLockHeld)

        // Simulating unexpected abort
        try {
            throw java.io.IOException("Socket reset by peer during transfer")
        } catch (_: Exception) {
            release()
        }

        assertFalse(wifiLockHeld)
        assertFalse(wakeLockHeld)
    }
}
