# Handoff Report: Offline & Platform Architecture Survey (R3, R4, Verification)

**Author**: Offline & Platform Explorer (`explorer_survey_3`)  
**Target Recipient**: Project Orchestrator (`orchestrator_1`) / Apex Decision & Engineering Council  
**Report Type**: Hard Handoff  
**Full Report**: `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_3\survey_report.md`  

---

## 1. Observation

1. **Library Screen & Folder Navigation**:
   - In `feature/library/LibraryScreen.kt` (lines 443–450):
     ```kotlin
     LibraryCategoryTile(
         title = "Folders",
         countText = "Setup",
         icon = Icons.Default.Folder,
         iconTint = Color(0xFF94A3B8),
         onClick = onFoldersCategoryClick
     )
     ```
   - In `MainActivity.kt` (line 705):
     ```kotlin
     onFoldersCategoryClick = { navStack.add(Destination.FolderSetup) }
     ```
   - In `feature/onboarding/FolderSetupScreen.kt` (lines 60–103): The screen performs media scanning via `libraryRepository.scanMediaStore()` with progress bars; it does not contain a directory tree browser, file listing, or instant folder queue player.

2. **Playback Engine & Crossfade**:
   - In `core/media/PlaybackConnection.kt` (lines 351–379), tracks are submitted directly as a list:
     ```kotlin
     val mediaItems = queue.map { qTrack -> buildMediaItem(qTrack) }
     player.setMediaItems(mediaItems, trackIndex, 0L)
     player.prepare()
     player.play()
     ```
   - In `data/repository/Repositories.kt` (lines 281, 302, 334–337): `AppSettings` defines `crossfadeDurationSec: Int = 0` and `gaplessPlayback: Boolean = true`, with a setter `setPlaybackSettings(crossfadeSec, gapless)`.
   - In `feature/settings/SettingsScreen.kt` (lines 828–846): A slider configures "Crossfade Duration" (0 to 10 seconds).
   - In `core/media/PlaybackConnection.kt` and `core/media/GobeatsMediaSessionService.kt`: `grep_search` for `crossfade` yielded 0 results. Neither class reads or executes crossfade or gapless preferences.

3. **Lyrics Resolution**:
   - In `core/lyrics/LyricsRepository.kt` (lines 28–46): Companion `.lrc` lookup checks:
     ```kotlin
     val uri = Uri.parse(track.fileUri)
     if (uri.scheme == "file" || track.fileUri.startsWith("/")) {
         val audioPath = uri.path ?: track.fileUri
         val audioFile = File(audioPath)
         ...
     ```
     When tracks are scanned from MediaStore, `track.fileUri` is `content://media/external/audio/media/<id>`, causing `uri.scheme == "file"` to evaluate to `false`, completely bypassing local `.lrc` detection.
   - In `core/lyrics/LyricsRepository.kt` (lines 49–65): Embedded lyrics lookup attempts:
     ```kotlin
     val comment = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPILATION)
     ```
     `METADATA_KEY_COMPILATION` is an album compilation flag, not lyrics. No ID3 `USLT` or `SYLT` parsing exists.

4. **Database & Process Death Persistence**:
   - In `core/database/GobeatsDatabaseHelper.kt` (lines 200–208):
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
   - `grep_search` across `app/src` showed `playback_state` appears only at line 200 of `GobeatsDatabaseHelper.kt`. Zero methods query or update this table.
   - In `core/media/PlaybackConnection.kt`: `currentQueue` is initialized to `emptyList()`. On process recreation, no saved queue or playback position is restored.

5. **Wi-Fi PC Connect Hardening**:
   - In `core/sync/GobeatsSyncServer.kt` (lines 81–108): `startServer()` starts `ServerSocket(port)` inside a coroutine on `Dispatchers.IO`. No `WifiLock` or `WakeLock` is acquired.
   - In `AndroidManifest.xml` (lines 184–191): Only `GobeatsMediaSessionService` is registered as a service (`mediaPlayback`). There is no foreground service for data synchronization or device connection. Android 14+ permission `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />` is absent.

6. **Build & Test Verification**:
   - `.\gradlew.bat --version`: Gradle 9.1.0, Kotlin 2.2.0, Launcher JVM Microsoft JDK 17 (17.0.19+10-LTS).
   - `.\gradlew.bat compileDebugKotlin`: Exit code 0 (Build successful in 8s).
   - `.\gradlew.bat testDebugUnitTest --rerun-tasks`: Exit code 0 (Build successful in 55s, 24 actionable tasks executed, all 56+ tests in `GobeatsCoreTest.kt` passed).
   - `.\gradlew.bat assembleDebug`: Exit code 0 (Build successful in 9s, 36 actionable tasks).

---

## 2. Logic Chain

1. **Folder Browser**:
   - Observations 1.1–1.3 show that the "Folders" tile currently leads to an onboarding media rescan (`FolderSetupScreen`).
   - To satisfy R3 ("A 'Folders' navigation tab displays storage directory hierarchy and plays folders as instant playlists"), a directory tree navigator must be introduced that enumerates files starting from `Environment.getExternalStorageDirectory()` and external SD cards, allows drilling into subdirectories, and plays folder contents as an instant playlist queue using `MetadataExtractor` and `PlaybackConnection.playTrack`.

2. **Gapless & Crossfade**:
   - Observations 2.1–2.4 show that while ExoPlayer handles gapless playback natively, crossfade settings are present in the UI and repository but completely unhooked in `PlaybackConnection`.
   - Crossfade must be implemented either via a volume-ramping fader during track transitions (`player.volume` curve at track boundaries) or a dual-player coordinator, respecting `crossfadeDurationSec` (1–5s) and `gaplessPlayback`.

3. **Offline Lyrics**:
   - Observation 3.1 reveals that MediaStore `content://` URIs prevent companion `.lrc` discovery because `uri.scheme` is not `"file"`. Resolving `MediaStore.Audio.Media.DATA` allows locating companion `<song>.lrc` files for all media files.
   - Observation 3.2 shows that embedded lyrics extraction is misconfigured to check `METADATA_KEY_COMPILATION`. Building an ID3 frame reader for `USLT` (with fallback to synced timestamps) and `SYLT` will provide genuine offline embedded lyrics before querying LRCLIB.

4. **Process Death Restoration**:
   - Observation 4.1 shows that SQLite table `playback_state` was planned in the schema but never wired with query/update methods.
   - Observation 4.2 shows that `PlaybackConnection` loses all state on process recreation.
   - Adding DAO methods `savePlaybackState` and `getSavedPlaybackState` to `GobeatsDatabaseHelper`, saving state periodically / on queue changes, and restoring the queue and position in `PlaybackConnection.initController()` directly fulfills R4.

5. **Network Hardening**:
   - Observations 5.1–5.2 prove that `GobeatsSyncServer` runs unshielded in background coroutines. Under Android Doze mode and App Standby, sockets are terminated.
   - Introducing `GobeatsSyncService` with `FOREGROUND_SERVICE_DATA_SYNC`, acquiring `WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY` / `HIGH_PERF`), acquiring `WakeLock`, and displaying an ongoing notification will ensure transfers survive screen-off and background transitions.

---

## 3. Caveats

- **Scoped Storage File System Access**: On Android 11+ (API 30+), raw `File.listFiles()` on `/storage/emulated/0` is permitted for standard public directories (`Music`, `Download`, `Documents`) if media permissions are held. However, system protected folders (like `Android/data`) are restricted and should be filtered out gracefully.
- **Dual Player vs. Volume Ramp for Crossfade**: Running dual ExoPlayer instances concurrently requires two active decoders, which on budget chipsets may consume more memory. A volume-ramping fader or dual-player implementation should be chosen based on device capability and performance targets.
- **Database Migration Safety**: If `DATABASE_VERSION` is incremented to version 3, `GobeatsDatabaseHelper.onUpgrade` must use `CREATE TABLE IF NOT EXISTS playback_state` to prevent duplicate table errors on existing installs.

---

## 4. Conclusion

The GoBeats codebase possesses a solid architectural foundation (ExoPlayer Media3, SQLiteOpenHelper, Jetpack Compose), but exhibits key missing links between UI settings/schemas and playback/service execution:
1. `playback_state` table exists in SQLite schema but lacks DAO methods and player hookup.
2. `crossfade_sec` exists in settings UI and repository but is not wired to `PlaybackConnection`.
3. Companion `.lrc` detection fails for MediaStore `content://` tracks, and embedded ID3 `USLT`/`SYLT` decoding is absent.
4. `GobeatsSyncServer` lacks a foreground service, `WifiLock`, and `WakeLock`.
5. The "Folders" tile requires a dedicated directory hierarchy navigation view with instant queue playback.

All required tools, Gradle dependencies, and unit tests are currently healthy and verified (100% green builds).

---

## 5. Verification Method

To verify these findings independently:

1. **Inspect Code Locations**:
   - `view_file` on `app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt` at line 200 (`playback_state` table).
   - `view_file` on `app/src/main/java/com/example/gobeats/core/lyrics/LyricsRepository.kt` at line 31 (`uri.scheme == "file"` check) and line 54 (`METADATA_KEY_COMPILATION`).
   - `view_file` on `app/src/main/java/com/example/gobeats/feature/library/LibraryScreen.kt` at line 448 (`onFoldersCategoryClick`).
   - `view_file` on `app/src/main/java/com/example/gobeats/core/sync/GobeatsSyncServer.kt` at line 81 (`startServer`).

2. **Execute Gradle Commands**:
   - `.\gradlew.bat compileDebugKotlin` -> Expect code 0, 0 compilation errors.
   - `.\gradlew.bat testDebugUnitTest` -> Expect code 0, 56+ tests in `GobeatsCoreTest.kt` passing.
   - `.\gradlew.bat assembleDebug` -> Expect code 0, APK generated at `app/build/outputs/apk/debug/app-debug.apk`.

3. **Invalidation Conditions**:
   - If `playback_state` had existing read/write methods elsewhere in the project (verified negative via `grep_search`).
   - If `crossfade` was already implemented via an undocumented AudioProcessor (verified negative).
