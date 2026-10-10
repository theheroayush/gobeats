# Milestone 4 Test Contracts and Cross-Feature Integration Analysis (F13 & F14)

**Explorer**: `explorer_m4_3`  
**Milestone**: Milestone 4 — Process Death & Network Hardening  
**Target Features**: Feature F13 (Process Death Queue Persistence) & Feature F14 (Network Transfer Hardening with WifiLock)  
**Date**: 2026-10-07  
**Status**: COMPLETE  

---

## Executive Summary

Milestone 4 introduces two mission-critical resilience capabilities to GoBeats:
1. **F13 (Process Death Persistence)**: Restores the user's active listening session (queue order, current track ID, and millisecond playback position) from SQLite after background OS process termination, initializing safely in a paused state.
2. **F14 (Network Transfer Hardening)**: Protects high-res Wi-Fi PC Connect file transfers from Android Doze mode socket termination using an active foreground service (`FOREGROUND_SERVICE_DATA_SYNC`), an ongoing notification, low-latency `WifiLock`, and bounded `WakeLock`.

This report provides a forensic analysis of the 4-tier test contracts, evaluates existing test engines, details cross-feature interactions with Milestones 1, 2, and 3, and presents a step-by-step implementation blueprint for `worker_m4`.

---

## 1. Observation

Direct forensic observations from test suites, source code, manifests, and documentation:

### 1.1 Tier 1: Feature Coverage Contracts (`GobeatsTier1FeatureCoverageTest.kt`)

#### Feature F13: Process Death Persistence (Lines 1227–1311)
- **Data Model (`SavedPlaybackState`)** (Lines 1230–1236):
  ```kotlin
  data class SavedPlaybackState(
      val currentTrackId: String?,
      val positionMs: Long,
      val isPlaying: Boolean,
      val queueTrackIds: List<String>,
      val updatedAt: Long
  )
  ```
- **Engine Methods (`ProcessDeathPersistenceEngine`)** (Lines 1238–1259):
  - `serializeQueue(trackIds: List<String>): String`: Uses `org.json.JSONArray` to serialize list of track ID strings into a JSON array string.
  - `deserializeQueue(queueJson: String): List<String>`: Parses `queueJson` with `JSONArray` and extracts string IDs preserving order.
  - `sortTracksByQueueIds(tracks: List<Track>, queueIds: List<String>): List<Track>`: Indexes tracks via `tracks.associateBy { it.id }` and performs `queueIds.mapNotNull { map[it] }`.
- **Test Invariants**:
  - `f13_01` (Line 1260): `serializeQueue` and `deserializeQueue` round-trip preserves exact queue ordering.
  - `f13_02` (Line 1268): `sortTracksByQueueIds` reorders database-returned tracks to match the user's queue sequence, completely decoupled from DB query order.
  - `f13_03` (Line 1286): State retains exact millisecond position precision (`positionMs: Long`).
  - `f13_04` (Line 1298): Restored state initializes with playback paused (`shouldAutoPlayOnRestore = false`) to prevent unexpected sound output upon app restart.
  - `f13_05` (Line 1306): Empty queue JSON `"[]"` deserializes cleanly into an empty list.

#### Feature F14: Network Transfer Hardening (Lines 1313–1391)
- **Data Model (`TransferStats`)** (Lines 1316–1323):
  ```kotlin
  data class TransferStats(
      val bytesTransferred: Long,
      val totalBytes: Long,
      val durationSeconds: Double
  ) {
      val progressFraction: Float = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
      val speedMbPerSec: Double = if (durationSeconds > 0) (bytesTransferred / (1024.0 * 1024.0)) / durationSeconds else 0.0
  }
  ```
- **Policy Engine (`SyncLockPolicy`)** (Lines 1325–1335):
  - `getWifiLockMode(apiLevel: Int): String`: Returns `"WIFI_MODE_FULL_LOW_LATENCY"` when `apiLevel >= 29`, otherwise `"WIFI_MODE_FULL_HIGH_PERF"`.
  - `getWakeLockTimeoutMs(): Long`: Bounded at 2 hours (`7_200_000L` ms) to prevent battery drain if transfer stalls.
- **Test Invariants**:
  - `f14_01` (Line 1337): API level selection verifies low-latency mode on API 29+ (Android 10+) and high-performance on API 28 and below.
  - `f14_02` (Line 1344): WakeLock timeout verified at 7,200,000 ms.
  - `f14_03` (Line 1349): Throughput and progress calculation verified (50MB / 100MB in 5s = 0.5 progress, 10.0 MB/s).
  - `f14_04` (Line 1360): Zero-byte transfer safely yields 0.0f progress and 0.0 MB/s without `NaN` or division by zero.
  - `f14_05` (Line 1367): Lock lifecycle strictly enforces that both WifiLock and WakeLock are acquired at transfer start and released on completion or failure.

---

### 1.2 Tier 2: Boundary & Corner Contracts (`GobeatsTier2BoundaryCornerTest.kt`)

#### Feature F13 Boundaries (Lines 697–738)
- `f13_b01` (Line 700): Deserializing corrupt non-array JSON (`"{not_an_array}"`) throws `JSONException` to trigger a clean queue reset.
- `f13_b02` (Line 705): Large queues (500+ items) serialize and deserialize without truncation or out-of-memory errors.
- `f13_b03` (Line 715): Special characters in track IDs (e.g. URI query parameters `track?id=456&src=sd`, quotes, colons) are properly JSON-escaped and preserved.
- `f13_b04` (Line 723): Tracks deleted from the library while in the saved queue are filtered out via `mapNotNull` without crashing.
- `f13_b05` (Line 732): Negative restored positions (e.g., `-5000L`) are sanitized via `coerceAtLeast(0L)` to `0L`.

#### Feature F14 Boundaries (Lines 740–792)
- `f14_b01` (Line 743): Zero-duration transfer (`durationSeconds = 0.0`) produces `speedMbPerSec = 0.0` safely.
- `f14_b02` (Line 754): Large file transfers exceeding 32-bit integer limits (e.g., 50 GB) use 64-bit `Long` math without overflow.
- `f14_b03` (Line 766): `progressFraction` is clamped to $1.0f$ even if transferred bytes anomalously exceed declared total bytes.
- `f14_b04` (Line 776): API level boundary explicitly checks API 24, 28, and 29.
- `f14_b05` (Line 783): Aborted transfers trigger clean release of locks.

---

### 1.3 Tier 3: Cross-Feature Interaction Contracts (`GobeatsTier3CrossFeatureTest.kt`)

- **`cross_03`: Video Resume Memory vs Audio Queue Persistence (Lines 77–108)**:
  - Audio playback state resides in SQLite table `playback_state` (single row `id = 1`).
  - Video resume timestamps reside in independent SQLite table `video_playback_positions` (`video_id` primary key).
  - Audio queue deserialization and restoration operates independently without clearing or overwriting video resume bookmarks, and video resume prompts remain valid for video files.
- **`cross_05`: Network Transfer Hardening during Scoped Storage Operations (Lines 138–171)**:
  - Network transfers writing incoming tracks to disk use staging files and Scoped Storage write mechanisms without interfering with FLAC Vorbis / M4A metadata updates.
- **`cross_10`: Relaunch from Process Death inside Crossfade Window (Lines 291–318)**:
  - If process death occurs while playback is near track completion (e.g., position 178,000ms of a 180,000ms track with a 3s crossfade setting):
  - The app restores into a paused state at 178,000ms.
  - When the user presses Play, `CrossfadeEngine.shouldTriggerCrossfade(...)` immediately evaluates to `true`, triggering a smooth crossfade into the next queue track rather than stalling or skipping.

---

### 1.4 Tier 4: Real-World Workflows (`GobeatsTier4RealWorldWorkflowsTest.kt`)

- **`workflow_04`: Process Death & Resurrection Full Lifecycle (Lines 211–260)**:
  1. User builds multi-track queue (custom sequence: `[t_103, t_101, t_105, t_102]`).
  2. Playback advances to track `t_105` at position `87,450ms`.
  3. App saves state into SQLite `playback_state` before background termination.
  4. Process restarts with empty memory; queries SQLite `playback_state`.
  5. Deserializes queue JSON and sorts full library tracks matching the exact saved queue order.
  6. Player state is restored: active track `t_105`, queue index 2, position 87,450ms, with `playWhenReady = false` (paused).
- **`workflow_05`: Wi-Fi PC Connect Transfer & Doze Mode Hardening (Lines 266–321)**:
  1. User taps "Start Sync Server" / "Connect Device".
  2. Foreground service starts with ongoing notification (`FOREGROUND_SERVICE_DATA_SYNC`).
  3. Acquires `WifiLock` (low-latency mode on API 34) and `WakeLock` (2h bounded timeout).
  4. Streams files, computing `TransferStats` progress and MB/s throughput.
  5. Upon completion or stop, locks are cleanly released and foreground service terminates.

---

### 1.5 Codebase State & Existing Gaps

1. **`GobeatsDatabaseHelper.kt`**:
   - `playback_state` table is created in `onCreate` (Lines 216–225):
     ```sql
     CREATE TABLE playback_state (
         id INTEGER PRIMARY KEY CHECK (id = 1),
         current_track_id TEXT,
         position_ms INTEGER NOT NULL DEFAULT 0,
         is_playing INTEGER NOT NULL DEFAULT 0,
         queue_json TEXT NOT NULL DEFAULT '[]',
         updated_at INTEGER NOT NULL
     );
     ```
   - **Gap**: `GobeatsDatabaseHelper` completely lacks `savePlaybackState(...)`, `getPlaybackState()`, and `clearPlaybackState()` DAO methods!
   - **Gap**: `onUpgrade` does not include `CREATE TABLE IF NOT EXISTS playback_state` for databases upgraded from older versions.

2. **`PlaybackConnection.kt`**:
   - Video resume persistence exists (`persistCurrentVideoPosition()`), but audio queue/position persistence across process death is NOT implemented.
   - **Gap**: No state persistence during `onIsPlayingChanged`, `onMediaItemTransition`, periodic position updates, queue updates, or `release()`.
   - **Gap**: In `initController()`, there is no restoration routine to fetch `dbHelper.getPlaybackState()`, reconstitute the queue, seek to the saved position, and set `playWhenReady = false`.

3. **`GobeatsSyncService.kt`**:
   - `GobeatsSyncServer.kt` exists as an HTTP daemon, but `GobeatsSyncService.kt` DOES NOT EXIST.
   - **Gap**: No Android Foreground Service wrapping the sync server.
   - **Gap**: No `WifiManager.WifiLock` or `PowerManager.WakeLock` acquisition or release logic.
   - **Gap**: No `NotificationChannel` or ongoing foreground notification with transfer status.

4. **`AndroidManifest.xml`**:
   - **Gap**: Missing `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />`.
   - **Gap**: Missing `<service android:name=".core.sync.GobeatsSyncService" android:exported="false" android:foregroundServiceType="dataSync" />`.

5. **`DevicesScreen.kt`**:
   - Tapping "Connect Device" currently calls `syncServer?.startServer()` directly in-process without launching `GobeatsSyncService`.
   - **Gap**: Needs integration to start/stop `GobeatsSyncService` via `ContextCompat.startForegroundService`.

6. **Location of Internal Test Engines**:
   - `ProcessDeathPersistenceEngine`, `SyncLockPolicy`, and `TransferStats` currently exist only inside `GobeatsTier1FeatureCoverageTest.kt`.
   - Production classes need access to these implementations in `app/src/main/java/com/example/gobeats/`.

---

## 2. Logic Chain

1. **From Requirements & Contract to Database Schema**:
   - `PROJECT.md` §Interface Contracts M4 mandates:
     - `GobeatsDatabaseHelper.savePlaybackState(trackId: String?, positionMs: Long, queueIds: List<String>, isPlaying: Boolean)`
     - `GobeatsDatabaseHelper.getPlaybackState(): SavedPlaybackState?`
   - The table `playback_state` already exists in `GobeatsDatabaseHelper.onCreate` with columns `id`, `current_track_id`, `position_ms`, `is_playing`, `queue_json`, and `updated_at`.
   - Implementing `savePlaybackState` requires inserting or replacing row `id = 1` with serialized queue JSON and sanitized millisecond position (`coerceAtLeast(0L)`).
   - Implementing `getPlaybackState` requires querying `id = 1`, safely deserializing `queue_json`, and returning `SavedPlaybackState`.

2. **From Test Engines to Production Architecture**:
   - In `GobeatsTier1FeatureCoverageTest.kt`, `ProcessDeathPersistenceEngine`, `SyncLockPolicy`, and `TransferStats` are defined as test objects/classes.
   - Existing tests in Tier 1, 2, 3, and 4 reference `GobeatsTier1FeatureCoverageTest.ProcessDeathPersistenceEngine`, `GobeatsTier1FeatureCoverageTest.SyncLockPolicy`, and `GobeatsTier1FeatureCoverageTest.TransferStats`.
   - To adhere to production standards while maintaining 100% backward compatibility with all test suites:
     - Create production implementations:
       - `com.example.gobeats.core.state.ProcessDeathPersistenceEngine` (or `com.example.gobeats.core.media.ProcessDeathPersistenceEngine`)
       - `com.example.gobeats.core.model.SavedPlaybackState` (in `Models.kt` or `SavedPlaybackState.kt`)
       - `com.example.gobeats.core.sync.SyncLockPolicy`
       - `com.example.gobeats.core.sync.TransferStats`
     - Keep the test shims in `GobeatsTier1FeatureCoverageTest` delegating to the production objects, or ensure `GobeatsTier1FeatureCoverageTest` aliases them. This guarantees zero breaking changes to existing tests.

3. **From Process Recreation to Player Lifecycle**:
   - When an Android process dies and restarts, `PlaybackConnection` initializes with empty queue and zero position.
   - When `MediaController` connects in `initController()`, if `player.mediaItemCount == 0`, `PlaybackConnection` should launch an asynchronous coroutine:
     - Call `dbHelper.getPlaybackState()`.
     - If saved state exists and `queueTrackIds.isNotEmpty()`:
       - Fetch available library tracks via `dbHelper.getAllTracks()`.
       - Reorder them via `ProcessDeathPersistenceEngine.sortTracksByQueueIds(allTracks, savedState.queueTrackIds)`.
       - If reordered tracks are not empty:
         - Find index of `savedState.currentTrackId` (default to 0).
         - Convert tracks to `MediaItem`s and populate player with `player.setMediaItems(mediaItems, targetIndex, savedState.positionMs.coerceAtLeast(0L))`.
         - Crucially, set `player.playWhenReady = false` (paused state).
         - Update `_playbackState` with reconstructed queue, current track, and position.

4. **From Periodic State Updates to Process Death Resilience**:
   - To guarantee the database always holds recent playback state:
     - Save on `onIsPlayingChanged(isPlaying)`: immediately write to DB.
     - Save on `onMediaItemTransition`: immediately write to DB with position 0.
     - Save in `startPositionUpdates()` loop throttled every 5 seconds or whenever user pauses.
     - Save on `release()` or explicit queue alterations.

5. **From Background Sync to Foreground Service Hardening**:
   - Android 8.0+ terminates background execution after a few minutes; Android 6.0+ Doze mode disables Wi-Fi and network sockets unless held by a foreground service and WakeLock/WifiLock.
   - `GobeatsSyncService` solves this:
     - Declares `foregroundServiceType="dataSync"` in manifest.
     - Creates notification channel `gobeats_sync_channel` ("PC Connect Wi-Fi Sync").
     - Calls `startForeground(...)` with an ongoing notification displaying server URL, port, and pin.
     - Acquires `WifiManager.WifiLock` with `WIFI_MODE_FULL_LOW_LATENCY` (API 29+) or `WIFI_MODE_FULL_HIGH_PERF` (API <29) with `setReferenceCounted(false)`.
     - Acquires `PowerManager.WakeLock` with tag `"gobeats:sync_wakelock"` with `setReferenceCounted(false)` and timeout 2 hours.
     - Coordinates with `GobeatsSyncServer`.
     - In `onDestroy()` or on stop action: releases both locks, stops the server, and stops foreground notification.

---

## 3. Caveats

1. **Gradle Build Environment on Windows 11 aarch64**:
   - The current build configuration in `app/build.gradle.kts` sets `jvmToolchain(17)`, whereas the host machine has Microsoft JDK 21 installed (`C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\`). Foojay toolchain auto-download is unavailable offline.
   - In previous milestone audits (`auditor_m3_1`, `worker_m3`), tests passed 100% when running against the local JVM or passing toolchain parameters (`-Porg.gradle.java.installations.paths=...`). The worker should be mindful of this when executing Gradle commands.
2. **ExoPlayer Video vs Audio Queue Separation**:
   - Video playback in GoBeats uses single-item playback with `VideoResumePrompt` dialogs. F13 queue restoration applies to the audio queue. When restoring from process death, if the saved track is a video file, it should respect video resume semantics without auto-playing.
3. **No Caveats on Functional Contracts**:
   - The test contracts across Tiers 1 through 4 are well-defined, mathematical, and deterministic.

---

## 4. Conclusion & Implementation Blueprint for `worker_m4`

The requirements and test contracts for Milestone 4 (F13 & F14) are fully mapped. Below is the file-by-file implementation guide for `worker_m4`:

### File 1: `app/src/main/java/com/example/gobeats/core/model/Models.kt`
- Add data class `SavedPlaybackState`:
  ```kotlin
  data class SavedPlaybackState(
      val currentTrackId: String?,
      val positionMs: Long,
      val isPlaying: Boolean,
      val queueTrackIds: List<String>,
      val updatedAt: Long = System.currentTimeMillis()
  ) : java.io.Serializable
  ```

### File 2: `app/src/main/java/com/example/gobeats/core/state/ProcessDeathPersistenceEngine.kt` (NEW FILE)
- Implement `ProcessDeathPersistenceEngine`:
  ```kotlin
  package com.example.gobeats.core.state

  import com.example.gobeats.core.model.Track
  import org.json.JSONArray

  object ProcessDeathPersistenceEngine {
      fun serializeQueue(trackIds: List<String>): String {
          val jsonArray = JSONArray()
          trackIds.forEach { jsonArray.put(it) }
          return jsonArray.toString()
      }

      fun deserializeQueue(queueJson: String): List<String> {
          val array = JSONArray(queueJson)
          val list = ArrayList<String>(array.length())
          for (i in 0 until array.length()) {
              list.add(array.getString(i))
          }
          return list
      }

      fun sortTracksByQueueIds(tracks: List<Track>, queueIds: List<String>): List<Track> {
          val map = tracks.associateBy { it.id }
          return queueIds.mapNotNull { map[it] }
      }
  }
  ```

### File 3: `app/src/main/java/com/example/gobeats/core/sync/SyncLockPolicy.kt` (NEW FILE)
- Implement `SyncLockPolicy` and `TransferStats`:
  ```kotlin
  package com.example.gobeats.core.sync

  data class TransferStats(
      val bytesTransferred: Long,
      val totalBytes: Long,
      val durationSeconds: Double
  ) {
      val progressFraction: Float = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
      val speedMbPerSec: Double = if (durationSeconds > 0) (bytesTransferred / (1024.0 * 1024.0)) / durationSeconds else 0.0
  }

  object SyncLockPolicy {
      fun getWifiLockMode(apiLevel: Int): String {
          return if (apiLevel >= 29) {
              "WIFI_MODE_FULL_LOW_LATENCY"
          } else {
              "WIFI_MODE_FULL_HIGH_PERF"
          }
      }

      fun getWakeLockTimeoutMs(): Long = 2 * 60 * 60 * 1000L // 2 hours = 7,200,000 ms
  }
  ```

### File 4: `app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt`
- In `onUpgrade`, ensure `playback_state` table exists if upgrading from an older DB version.
- Add DAO methods:
  ```kotlin
  suspend fun savePlaybackState(
      trackId: String?,
      positionMs: Long,
      queueIds: List<String>,
      isPlaying: Boolean
  ) = withContext(Dispatchers.IO) {
      val db = writableDatabase
      val values = ContentValues().apply {
          put("id", 1)
          put("current_track_id", trackId)
          put("position_ms", positionMs.coerceAtLeast(0L))
          put("is_playing", if (isPlaying) 1 else 0)
          put("queue_json", com.example.gobeats.core.state.ProcessDeathPersistenceEngine.serializeQueue(queueIds))
          put("updated_at", System.currentTimeMillis())
      }
      db.insertWithOnConflict("playback_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  }

  suspend fun getPlaybackState(): SavedPlaybackState? = withContext(Dispatchers.IO) {
      val db = readableDatabase
      val query = "SELECT current_track_id, position_ms, is_playing, queue_json, updated_at FROM playback_state WHERE id = 1 LIMIT 1"
      db.rawQuery(query, null).use { cursor ->
          if (cursor.moveToFirst()) {
              val trackId = cursor.getString(0)
              val pos = cursor.getLong(1).coerceAtLeast(0L)
              val isPlaying = cursor.getInt(2) == 1
              val queueJson = cursor.getString(3) ?: "[]"
              val updatedAt = cursor.getLong(4)
              val queueIds = try {
                  com.example.gobeats.core.state.ProcessDeathPersistenceEngine.deserializeQueue(queueJson)
              } catch (_: Exception) {
                  emptyList()
              }
              SavedPlaybackState(
                  currentTrackId = trackId,
                  positionMs = pos,
                  isPlaying = isPlaying,
                  queueTrackIds = queueIds,
                  updatedAt = updatedAt
              )
          } else null
      }
  }

  suspend fun clearPlaybackState() = withContext(Dispatchers.IO) {
      val db = writableDatabase
      db.delete("playback_state", "id = 1", null)
  }
  ```

### File 5: `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt`
- Hook into state persistence:
  - Add helper `persistPlaybackState()`:
    ```kotlin
    private fun persistPlaybackState() {
        val player = controller ?: return
        val currentTrack = _playbackState.value.currentTrack
        val currentList = currentQueue.map { it.id }
        if (currentList.isNotEmpty()) {
            val pos = player.currentPosition.coerceAtLeast(0L)
            val isPlaying = player.isPlaying
            scope.launch(Dispatchers.IO) {
                dbHelper.savePlaybackState(
                    trackId = currentTrack?.id,
                    positionMs = pos,
                    queueIds = currentList,
                    isPlaying = isPlaying
                )
            }
        }
    }
    ```
  - Call `persistPlaybackState()`:
    - On `onIsPlayingChanged(isPlaying)`
    - On `onMediaItemTransition(...)`
    - In `startPositionUpdates()` every ~5 seconds
    - On `release()`
  - Implement restore logic on controller connection in `initController()`:
    - If `player.mediaItemCount == 0`:
      - Query `dbHelper.getPlaybackState()`
      - If state exists and `queueTrackIds.isNotEmpty()`:
        - Query `dbHelper.getAllTracks()`
        - Sort tracks using `ProcessDeathPersistenceEngine.sortTracksByQueueIds(allTracks, state.queueTrackIds)`
        - Find target index of `state.currentTrackId`
        - Build `MediaItem`s and populate player via `player.setMediaItems(items, targetIdx, state.positionMs.coerceAtLeast(0L))`
        - Prepare player with `player.playWhenReady = false` (paused)
        - Update `_playbackState` with restored queue, track, index, and position.

### File 6: `app/src/main/java/com/example/gobeats/core/sync/GobeatsSyncService.kt` (NEW FILE)
- Implement Foreground Service:
  - Creates notification channel `gobeats_sync_channel` with name `"PC Connect Wi-Fi Sync"`.
  - Runs in foreground via `startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)`.
  - Manages `WifiManager.WifiLock`:
    - API 29+: `WifiManager.WIFI_MODE_FULL_LOW_LATENCY`.
    - Below API 29: `WifiManager.WIFI_MODE_FULL_HIGH_PERF`.
  - Manages `PowerManager.WakeLock`:
    - Acquired with tag `"gobeats:sync_wakelock"` and timeout `SyncLockPolicy.getWakeLockTimeoutMs()`.
  - Delegates to / manages `GobeatsSyncServer`.
  - Action intents: `ACTION_START_SYNC`, `ACTION_STOP_SYNC`.
  - Releases locks safely in `onDestroy()` and on stop action.

### File 7: `app/src/main/AndroidManifest.xml`
- Add permission:
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
  ```
- Declare service:
  ```xml
  <service
      android:name=".core.sync.GobeatsSyncService"
      android:exported="false"
      android:foregroundServiceType="dataSync" />
  ```

### File 8: `app/src/main/java/com/example/gobeats/feature/devices/DevicesScreen.kt`
- Update "Connect Device" button action to trigger `GobeatsSyncService`:
  ```kotlin
  val intent = Intent(context, com.example.gobeats.core.sync.GobeatsSyncService::class.java).apply {
      action = com.example.gobeats.core.sync.GobeatsSyncService.ACTION_START_SYNC
  }
  androidx.core.content.ContextCompat.startForegroundService(context, intent)
  ```

---

## 5. Verification Method

Independent verification method for confirming Milestone 4:

1. **Tier 1 Feature Coverage (F13 & F14)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest.f13_*"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest.f14_*"
   ```
2. **Tier 2 Boundary & Corner Cases (F13 & F14)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest.f13_b*"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest.f14_b*"
   ```
3. **Tier 3 Cross-Feature Interactions**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_03"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_05"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_10"
   ```
4. **Tier 4 Real-World Workflows**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest.workflow_04"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest.workflow_05"
   ```
5. **Full Regression Suite**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   *Expected Result*: All 282+ tests pass with 0 failures, 0 errors, 0 skipped.

6. **Invalidation Conditions**:
   - Audio queue restores with auto-play enabled (blaring music on launch) -> Violates `f13_04` & `workflow_04`.
   - Track sorting relies on database query order instead of user queue sequence -> Violates `f13_02`.
   - WifiLock mode selects High Perf instead of Low Latency on Android 10+ (API 29+) -> Violates `f14_01`.
   - Missing `FOREGROUND_SERVICE_DATA_SYNC` permission or service declaration in manifest -> Android 14 runtime crash.
   - Restoring from process death inside crossfade window fails to trigger crossfade upon playback -> Violates `cross_10`.
