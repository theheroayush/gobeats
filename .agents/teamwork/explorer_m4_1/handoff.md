# Handoff Report — Feature F13: Process Death Persistence

**Author:** `explorer_m4_1`  
**Milestone:** Milestone 4 (Process Death & Network Hardening)  
**Feature:** F13: Process Death Persistence  
**Date:** 2026-10-07  
**Status:** COMPLETE (Investigation & Technical Architecture Report)

---

## 1. Observation

Direct observations from inspection of the GoBeats codebase, SQLite database helper, playback architecture, and testing framework:

### 1.1 Database Layer (`GobeatsDatabaseHelper.kt`)
- **Schema Presence**: In `app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt` (lines 216–224), the table `playback_state` is already defined in `onCreate`:
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
- **Singleton Row Constraint**: The table enforces a single record via `id INTEGER PRIMARY KEY CHECK (id = 1)`. All operations should target `id = 1` with `CONFLICT_REPLACE` semantics.
- **Migration Gap**: In `onUpgrade` (lines 52–76), upgrade checks exist for `oldVersion < 2` (adding video columns) and `oldVersion < 3` (creating `video_playback_positions`). The helper does not run `CREATE TABLE IF NOT EXISTS playback_state` in `onUpgrade`. Existing databases upgraded from prior versions risk missing this table unless guarded with `IF NOT EXISTS`.
- **Missing Helper Methods**: In `GobeatsDatabaseHelper.kt` (lines 922–966), methods exist for `getTrackById`, `saveVideoPosition`, `getVideoPosition`, and `clearVideoPosition`. However, **ZERO** methods exist for `playback_state`:
  - `savePlaybackState(...)` is **MISSING**.
  - `getPlaybackState()` is **MISSING**.
  - `clearPlaybackState()` is **MISSING**.
  - `getTracksByIds(trackIds: List<String>)` (batch query for restoring queue tracks) is **MISSING**.

### 1.2 Playback Connection Layer (`PlaybackConnection.kt`)
- **Controller Lifecycle**: In `PlaybackConnection.kt` (lines 74–88), `initController()` sets up `MediaController` from `GobeatsMediaSessionService` asynchronously:
  ```kotlin
  controllerFuture?.addListener({
      controller = controllerFuture?.get()
      setupPlayerListener()
      pendingPlay?.let { (t, q) ->
          pendingPlay = null
          playTrack(t, q)
      }
  }, MoreExecutors.directExecutor())
  ```
  Currently, when the controller connects, **no playback state restoration occurs**. If the process was terminated and restarted, `currentQueue` remains `emptyList()`, `_playbackState` remains default `PlaybackUiState()`, and the player has zero media items.
- **Missing State Saving Triggers**:
  - `onIsPlayingChanged(isPlaying)` (lines 98–106): Persists video position on pause (`persistCurrentVideoPosition()`), but does **NOT** persist audio queue or playback position.
  - `onMediaItemTransition(mediaItem, reason)` (lines 108–153): Transitions to new track but does **NOT** persist the updated track ID or queue state.
  - `startPositionUpdates()` (lines 348–471): Periodically ticks every 250ms and saves video position every 16 ticks, but does **NOT** persist audio playback position.
  - `playTrack(track, queue)` (lines 544–595): Prepares and plays media items, but does **NOT** persist state to SQLite.
  - `seekTo(positionMs)` (lines 607–615): Updates in-memory position, but does **NOT** persist updated position.
  - Queue mutations (`playNext`, `addToQueue`, `removeFromQueue`, `clearQueue`, `moveQueueItem`, `shuffleQueue` in lines 680–777): Mutate `currentQueue`, but do **NOT** persist updated queue IDs.
  - `release()` (lines 1038–1043): Releases controller, but does **NOT** persist final audio playback state.

### 1.3 Repository & UI Layers (`Repositories.kt`, `MainActivity.kt`)
- `PlaybackRepository` (lines 125–263) wraps `PlaybackConnection` methods but has no direct APIs to trigger manual persistence, queue state inspection, or explicit restoration.
- `MainActivity.kt` (lines 114–140) initializes Compose UI without hooking into activity backgrounding (`onStop`/`onPause`) for state flushing.

### 1.4 Test Suite Specifications & Ground-Truth Expectations
Inspected all F13 test implementations across the test suite:
- **Tier 1 (`GobeatsTier1FeatureCoverageTest.kt` lines 1227–1311)**:
  - `f13_01`: Queue serialization/deserialization with `JSONArray` preserves exact ordering.
  - `f13_02`: `sortTracksByQueueIds(tracks, queueIds)` restores exact sequence independent of database query ordering (`tracks.associateBy { it.id }` then `queueIds.mapNotNull { map[it] }`).
  - `f13_03`: `SavedPlaybackState` maintains millisecond precision (`positionMs: Long`).
  - `f13_04`: Restored state **must always initialize paused** (`shouldAutoPlayOnRestore = false`) to avoid blaring music upon app launch.
  - `f13_05`: Empty queue deserializes safely into an empty list.
- **Tier 2 (`GobeatsTier2BoundaryCornerTest.kt` lines 696–739)**:
  - `f13_b01`: Corrupt JSON queue throws `JSONException` (handled gracefully with clean reset).
  - `f13_b02`: Large queues (500 items) serialize and deserialize without truncation.
  - `f13_b03`: Special characters in track IDs (quotes, colons, slashes, URL query params) survive round-trip via JSON escaping.
  - `f13_b04`: Deleted tracks present in `queueIds` but missing from DB are filtered out without crashing.
  - `f13_b05`: Negative restored positions are clamped to `0L` (`coerceAtLeast(0L)`).
- **Tier 3 (`GobeatsTier3CrossFeatureTest.kt` lines 77–108, 288–318)**:
  - `cross_03`: Video resume timestamps in `video_playback_positions` coexist with audio queue state in `playback_state` without interference.
  - `cross_10`: Restoring playback state after process death with crossfade enabled properly prepares crossfade engine when playback resumes.
- **Tier 4 (`GobeatsTier4RealWorldWorkflowsTest.kt` lines 208–261)**:
  - `workflow_04`: Complete E2E simulation: User builds 5-track queue -> plays to 87,450ms -> saves snapshot to `playback_state` -> process killed -> restart with empty state -> load from SQLite -> deserialize queue IDs -> sort tracks -> locate active index -> restore player at 87,450ms paused (`playWhenReady = false`).

### 1.5 Environment & Toolchain
- Default PowerShell session had `JAVA_HOME` pointing to JDK 21 (`C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\`), causing Gradle toolchain resolution error on Java 17 requirements.
- Java 17 runtime verified at `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`.
- Command `cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest ..."` successfully executes the Gradle test suite.

---

## 2. Logic Chain

1. **Root Cause of Process Death Data Loss**:
   When Android kills GoBeats due to memory pressure in the background, all JVM in-memory structures in `PlaybackConnection` (`currentQueue`, `_playbackState`) and `MediaController` are destroyed. Because the production codebase has no writes to `playback_state`, all user state (queue, track, position) is permanently lost upon recreation.

2. **Database Contract Derivation**:
   From `PROJECT.md` §Interface Contracts and `playback_state` schema, `GobeatsDatabaseHelper` must supply:
   - `savePlaybackState(trackId: String?, positionMs: Long, queueIds: List<String>, isPlaying: Boolean)`: Encodes `queueIds` into JSON string, clamps `positionMs >= 0L`, sets `id = 1`, and persists using `insertWithOnConflict(CONFLICT_REPLACE)`.
   - `getPlaybackState(): SavedPlaybackState?`: Queries `id = 1`, parses JSON into `List<String>` safely (catching `JSONException`), and returns `SavedPlaybackState`.
   - `clearPlaybackState()`: Deletes `id = 1` row.
   - `getTracksByIds(trackIds: List<String>): List<Track>`: Efficiently fetches tracks matching the requested IDs so the queue can be reconstructed without doing an unbounded full library scan.

3. **Glitch-Free Audio Restoration Mechanics**:
   To restore playback seamlessly without audio pops, skips, or blaring music:
   - When `MediaController` finishes connecting in `PlaybackConnection.initController()`, check if `player.mediaItemCount == 0` (indicating cold process recreation).
   - Fetch `savedState = dbHelper.getPlaybackState()`.
   - If `savedState != null && savedState.queueTrackIds.isNotEmpty()`:
     - Fetch matching tracks: `val dbTracks = dbHelper.getTracksByIds(savedState.queueTrackIds)`.
     - Order tracks to match original queue: `val restoredQueue = savedState.queueTrackIds.mapNotNull { id -> trackMap[id] }`.
     - If `restoredQueue.isNotEmpty()`:
       - Find active index: `val targetIndex = restoredQueue.indexOfFirst { it.id == savedState.currentTrackId }.coerceAtLeast(0)`.
       - Map tracks to `MediaItem` instances.
       - Call `player.setMediaItems(mediaItems, targetIndex, savedState.positionMs.coerceAtLeast(0L))`.
       - Call `player.prepare()`.
       - **DO NOT** call `player.play()`! Leave `player.playWhenReady = false`.
       - Update `_playbackState` with restored tracks, current track, position, duration, and `isPlaying = false`.
   - Because ExoPlayer receives `targetIndex` and `startPositionMs` directly in `setMediaItems`, it initializes its internal decoders directly at that timestamp without playing from 0s or clicking.

4. **Persistence Scheduling & Throttling**:
   Writing to SQLite on every 250ms progress tick would cause unnecessary disk I/O and battery drain. State persistence must be triggered:
   - **Immediately on discrete events**:
     - Pause (`onIsPlayingChanged(false)`)
     - Track change (`onMediaItemTransition`)
     - User seek (`seekTo(positionMs)`)
     - Queue changes (`playTrack`, `addToQueue`, `removeFromQueue`, `clearQueue`, `moveQueueItem`, `shuffleQueue`)
     - App release/teardown (`release()`)
   - **Throttled during active playback**:
     - In `startPositionUpdates()`, write to SQLite every ~5 seconds (e.g. `tick % 20 == 0` given 250ms ticks) while `isPlaying == true`. This guarantees at most a 5-second delta if the process is suddenly killed by `SIGKILL` without lifecycle callbacks.

---

## 3. Caveats

1. **Deleted Tracks on Storage**:
   If tracks are deleted from device storage while the app is killed, `dbHelper.getTracksByIds` will not return those tracks. The ordering algorithm (`mapNotNull`) safely drops them. If the currently playing track itself was deleted, the target index will safely coerce to 0.
2. **Room vs. SQLiteOpenHelper**:
   The prompt mentions "Room/SQLite playback_state". The GoBeats project uses pure Android `SQLiteOpenHelper` (`GobeatsDatabaseHelper`), not Room annotations. The implementation must remain on `GobeatsDatabaseHelper` consistent with all other tables in the project.
3. **Java Toolchain in Windows Terminal**:
   The machine environment defaults `JAVA_HOME` to JDK 21. When running Gradle from command line or test tasks, `set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot` must be prepended.

---

## 4. Conclusion & Actionable Blueprint for Worker

The worker should implement Feature F13 across the following files:

### File 1: `com/example/gobeats/core/model/Models.kt`
Define `SavedPlaybackState`:
```kotlin
data class SavedPlaybackState(
    val currentTrackId: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val queueTrackIds: List<String>,
    val updatedAt: Long
)
```

### File 2: `com/example/gobeats/core/database/GobeatsDatabaseHelper.kt`
1. In `onUpgrade(db, oldVersion, newVersion)`:
   Add defensive creation:
   ```kotlin
   db.execSQL(
       """
       CREATE TABLE IF NOT EXISTS playback_state (
           id INTEGER PRIMARY KEY CHECK (id = 1),
           current_track_id TEXT,
           position_ms INTEGER NOT NULL DEFAULT 0,
           is_playing INTEGER NOT NULL DEFAULT 0,
           queue_json TEXT NOT NULL DEFAULT '[]',
           updated_at INTEGER NOT NULL
       );
       """.trimIndent()
   )
   ```
2. Add CRUD helper methods:
   - `suspend fun savePlaybackState(trackId: String?, positionMs: Long, queueIds: List<String>, isPlaying: Boolean): Unit`
   - `suspend fun getPlaybackState(): SavedPlaybackState?`
   - `suspend fun clearPlaybackState(): Unit`
   - `suspend fun getTracksByIds(trackIds: List<String>): List<Track>`

### File 3: `com/example/gobeats/core/media/PlaybackConnection.kt`
1. Add persistence helper method:
   ```kotlin
   fun persistCurrentPlaybackState() {
       val player = controller ?: return
       val currentTrackId = _playbackState.value.currentTrack?.id
       val queueIds = currentQueue.map { it.id }
       val pos = player.currentPosition.coerceAtLeast(0L)
       val isPlaying = player.isPlaying
       scope.launch(Dispatchers.IO) {
           dbHelper.savePlaybackState(
               trackId = currentTrackId,
               positionMs = pos,
               queueIds = queueIds,
               isPlaying = isPlaying
           )
       }
   }
   ```
2. Wire persistence triggers:
   - In `onIsPlayingChanged`: when paused, call `persistCurrentPlaybackState()`.
   - In `onMediaItemTransition`: call `persistCurrentPlaybackState()`.
   - In `startPositionUpdates`: every 20 ticks (~5s), call `persistCurrentPlaybackState()`.
   - In `playTrack`: after updating queue, call `persistCurrentPlaybackState()`.
   - In `seekTo`: after seeking, call `persistCurrentPlaybackState()`.
   - In `addToQueue`, `removeFromQueue`, `clearQueue`, `moveQueueItem`, `shuffleQueue`: call `persistCurrentPlaybackState()`.
   - In `release`: call `persistCurrentPlaybackState()`.
3. Wire restoration logic in `initController()`:
   After connecting, if `player.mediaItemCount == 0`, launch coroutine to load state via `dbHelper.getPlaybackState()`, fetch tracks via `dbHelper.getTracksByIds()`, rebuild queue with `sortTracksByQueueIds`, call `player.setMediaItems(items, index, pos)`, call `player.prepare()`, and update `_playbackState` in paused state (`isPlaying = false`).

### File 4: `com/example/gobeats/data/repository/Repositories.kt`
Expose on `PlaybackRepository`:
- `fun persistPlaybackState() = playbackConnection.persistCurrentPlaybackState()`
- `suspend fun getSavedPlaybackState() = dbHelper.getPlaybackState()`
- `suspend fun clearSavedPlaybackState() = dbHelper.clearPlaybackState()`

### File 5: `com/example/gobeats/MainActivity.kt`
Optionally hook `onStop()` / `onPause()` to call `app.playbackRepository.persistPlaybackState()`.

---

## 5. Verification Method

### 5.1 Unit & Feature Test Commands
Run the targeted F13 test suites:
```cmd
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest --tests com.example.gobeats.GobeatsTier1FeatureCoverageTest.f13*"
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest --tests com.example.gobeats.GobeatsTier2BoundaryCornerTest.f13*"
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest --tests com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_03 --tests com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_10"
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest --tests com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest.workflow_04"
```

### 5.2 Full Test Suite Regression Command
Run all unit tests across the entire project:
```cmd
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest"
```

### 5.3 Compilation Check Command
```cmd
cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat compileDebugKotlin"
```

### 5.4 Invalidation Conditions
- Any test in `f13_01`–`f13_05`, `f13_b01`–`f13_b05`, `cross_03`, `cross_10`, or `workflow_04` fails.
- Player auto-starts playing sound upon process restart (`playWhenReady == true`).
- Restored queue tracks do not match the exact order saved prior to process death.
- Millisecond precision is truncated or rounded to nearest second.
- Corrupted JSON or deleted tracks cause an unhandled exception or crash.
