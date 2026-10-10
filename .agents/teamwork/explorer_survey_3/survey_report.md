# GoBeats Codebase Survey & Architecture Report: Offline & Platform Experience

**Explorer**: Offline & Platform Explorer (survey round)  
**Date**: 2026-10-06  
**Scope**: R3 (Audiophile Offline Experience & Folder Browser), R4 (Process Death & Network Hardening), and Verification Setup  

---

## Executive Summary

This survey comprehensively investigates the GoBeats architecture to prepare for the implementation of:
1. **R3: Audiophile Offline Experience & Folder Browser**:
   - Physical "Folders" directory tree browser under the Library screen with breadcrumb navigation and instant playlist playback.
   - Gapless audio transitions and configurable 1–5s crossfade between queue items.
   - Multi-tier offline synced lyrics engine (local `<song>.lrc` file detection resolving MediaStore URIs + embedded `USLT`/`SYLT` ID3 tag decoding + LRCLIB fallback).
2. **R4: Process Death & Network Hardening**:
   - Full Room/SQLite persistence and restoration of active queue track IDs and millisecond playback position across Android process death.
   - `WifiLock` and foreground service with ongoing notification for Wi-Fi PC Connect transfers to eliminate Android Doze mode socket termination.
3. **Verification Environment**:
   - Build environment (Gradle 9.1.0, AGP 9.0.1, Kotlin 2.3.20, JVM 17, SDK 36), passing unit tests (`GobeatsCoreTest.kt`), and verified Gradle verification pipelines.

---

## 1. R3: Audiophile Offline Experience & Folder Browser

### 1.1 Folders Directory Tree Browser in Library Screen

#### Current State
- `LibraryScreen.kt` (lines 370–452) defines a `LazyRow` of category tiles:
  `"Songs"`, `"Albums"`, `"Artists"`, `"Genres"`, `"Videos"`, and `"Folders"`.
- When the `"Folders"` tile is tapped, it invokes `onFoldersCategoryClick()`, which in `MainActivity.kt` (line 705) navigates to `Destination.FolderSetup`:
  ```kotlin
  onFoldersCategoryClick = { navStack.add(Destination.FolderSetup) }
  ```
- `FolderSetupScreen.kt` is currently an onboarding/permissions media scanner screen with a circular progress indicator, **not** a folder browsing experience.
- There is currently no file-system hierarchy explorer or direct folder-to-playlist player in the app.

#### Technical Architecture for Folder Browser
1. **Directory Traversal Model**:
   - Root storage locations:
     * Internal Storage: `Environment.getExternalStorageDirectory()` (`/storage/emulated/0`)
     * Common Media Roots: `Environment.DIRECTORY_MUSIC`, `Environment.DIRECTORY_DOWNLOADS`
     * Removable Storage (MicroSD Cards / USB OTG): Discovered via `ContextCompat.getExternalFilesDirs(context, null)` (extracting the root prefix `/storage/XXXX-XXXX`) and `File("/storage")`.
   - Navigation State:
     * Current path: `File` (defaulting to storage root or `/storage/emulated/0/Music`).
     * Path history stack: `List<File>` for back-navigation and breadcrumb display.
   - Directory Contents:
     * Subdirectories: Displayed with folder icon, directory name, and item counts (e.g., `"14 tracks, 2 subfolders"`).
     * Audio files: Filtered using `isSupportedMediaFile()` (`.mp3`, `.flac`, `.wav`, `.m4a`, `.aac`, `.ogg`, `.opus`, `.wma`).
     * Display info: File name, file format badge (`FLAC`, `MP3`, etc.), size in MB, and duration.
2. **Instant Playlists from Folders**:
   - Header Action: `"Play All"` and `"Shuffle Folder"`.
   - Recursive or flat scan of audio files in current directory:
     * Resolves each file to an existing indexed `Track` in `GobeatsDatabaseHelper` via `file_uri` (`file://${file.absolutePath}` or matching `DATA` column), or creates an on-the-fly `Track` instance via `MetadataExtractor.extractMetadata(...)`.
   - Clicking any track in the folder browser starts playback of all tracks in that directory starting from the selected item.
3. **Integration Point in Library**:
   - When `"Folders"` is clicked in `LibraryScreen.kt`, it switches `selectedCategory = "Folders"` or displays the `FolderBrowserView` directly within `LibraryScreen` (with search and breadcrumb trail), or provides a dedicated destination screen in `navStack`.
   - Making it a native view mode within `LibraryScreen` or full screen via `Destination.FolderBrowser(path: String?)` provides a fluid experience matching audiophile players like Poweramp and Musicolet.

---

### 1.2 Gapless Playback & Configurable 1–5s Crossfade

#### Current State
- **Media Engine**: ExoPlayer 1.4.1 in `GobeatsMediaSessionService.kt`.
- **Audio Sink**: `BitPerfectAudioEngine.createHighPrecisionAudioSink` uses `DefaultAudioSink` with `setEnableFloatOutput(true)` for 32-bit float PCM audio pipeline.
- **Queue Queuing**: In `PlaybackConnection.kt` (`playTrack`), the entire queue is submitted to ExoPlayer via `player.setMediaItems(mediaItems, trackIndex, 0L)`.
- **Gapless Capabilities**: Media3 ExoPlayer natively supports gapless playback for audio formats with padding/delay metadata (MP3 iTunSMPB/LAME, AAC, FLAC) when items are pre-loaded in the player's playlist.
- **Crossfade Status**:
  * `AppSettings` in `Repositories.kt` contains `crossfadeDurationSec: Int = 0` and `gaplessPlayback: Boolean = true`.
  * `SettingsScreen.kt` (lines 796–846) already has a UI slider for "Crossfade Duration" (0 to 10 seconds).
  * However, **neither `PlaybackConnection.kt` nor `GobeatsMediaSessionService.kt` reads or applies `crossfadeDurationSec` or `gaplessPlayback`!**

#### Architectural Analysis for Crossfade (1–5s)
ExoPlayer processes a single media item stream at a time in its standard decoding pipeline. True audio crossfade between tracks requires handling the transition boundary:

| Approach | Implementation | Pros | Cons | Recommendation |
|---|---|---|---|---|
| **Approach 1: Dynamic Volume Fader (Volume Ramping)** | In `PlaybackConnection`'s position update loop, when `currentPositionMs >= durationMs - (crossfadeSec * 1000L)`, ramp `player.volume` from `1.0f` down to `0.0f`. On `onMediaItemTransition`, ramp `player.volume` up from `0.0f` to `1.0f` over `crossfadeSec`. | Zero extra CPU/memory; single ExoPlayer instance; 100% compatible with MediaSession and lockscreen notifications; no dual-decoder conflicts. | Sequential fade-out/fade-in rather than simultaneous dual-track overlap. | Ideal fallback and low-power mode. |
| **Approach 2: Dual ExoPlayer Audio Crossfader** | Maintain two lightweight ExoPlayer instances (`playerA` and `playerB`). When track N on `playerA` reaches `durationMs - crossfadeSec * 1000L`, `playerB` prepares track N+1 and starts playback at volume `0.0f`. A smooth linear or logarithmic curve cross-fades `playerA.volume` (1.0 → 0.0) and `playerB.volume` (0.0 → 1.0) simultaneously. Once complete, `playerA` pauses and `playerB` takes over as primary. | True audiophile DJ-grade crossfade where both tracks play concurrently. | Requires managing two players and routing MediaSession callbacks to the currently active player. | Recommended for Tier-1 audiophile implementation with 1–5s range. |

#### Crossfade Implementation Specification:
- Configurable range: 1s to 5s (stored in `crossfade_sec` SharedPreferences, defaults to 0s = gapless off/direct transition).
- If `crossfadeDurationSec == 0`: ExoPlayer uses pure native gapless transitions.
- If `crossfadeDurationSec in 1..5`: Activate the crossfader engine.

---

### 1.3 Offline Synced Lyrics Architecture

#### Current State
- `LrcParser.kt` is fully functional and parses standard timestamped `.lrc` formats (`[mm:ss.xx]`) as well as plain text lyrics.
- `LyricsRepository.kt`:
  ```kotlin
  suspend fun getLyricsForTrack(track: Track): LyricsData
  ```
  1. Checks `filesDir/lyrics/${track.id}.lrc`.
  2. Checks companion file in the same directory:
     ```kotlin
     val uri = Uri.parse(track.fileUri)
     if (uri.scheme == "file" || track.fileUri.startsWith("/")) { ... }
     ```
     **Critical Bug Identified**: MediaStore audio tracks have `fileUri` like `content://media/external/audio/media/12345`. `uri.scheme` is `"content"`, so this block is completely skipped!
  3. Tries `MediaMetadataRetriever.METADATA_KEY_COMPILATION` (which is a boolean flag for Various Artists, **not** lyrics!).
  4. Calls `LrclibClient.fetchLyrics` (requires active internet).

#### Required Architecture:
1. **Resolving Real File Paths for MediaStore URIs**:
   - For `content://` URIs, query `MediaStore.Audio.Media.DATA` using `context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)`.
   - On Android 10–14, `MediaStore.Audio.Media.DATA` is readable when media permissions are granted.
2. **Local Companion `<song>.lrc` Detection**:
   - From the resolved path `/storage/.../SongTitle.mp3`:
     * Check `/storage/.../SongTitle.lrc`
     * Check `/storage/.../SongTitle.LRC`
     * Check `/storage/.../SongTitle.txt`
   - If found, read immediately and parse via `LrcParser.parse(content, source = "companion")`.
3. **Embedded ID3 Tag Lyrics Parser (`USLT` & `SYLT`)**:
   - Build a lightweight, dedicated ID3v2 frame reader:
     * Open audio file via `File` or `contentResolver.openInputStream(uri)`.
     * Inspect ID3v2 header: check identifier `"ID3"`, version (v2.3 or v2.4), and synchsafe tag size.
     * Iterate ID3 frames:
       - **`USLT` (Unsynchronised lyrics/text transcription)**:
         Header: 4 bytes ID (`USLT`), 4 bytes size, 2 bytes flags.
         Payload: Encoding byte (0=ISO-8859-1, 1=UTF-16 with BOM), 3 bytes language (e.g. `"eng"`), null-terminated descriptor, followed by lyrics body.
         *If the lyrics body contains timestamps (e.g. `[00:15.20]`), `LrcParser` parses it as synchronized lyrics!*
       - **`SYLT` (Synchronised lyrics/text transcription)**:
         Header: 4 bytes ID (`SYLT`), 4 bytes size, 2 bytes flags.
         Payload: Encoding byte, language (3 bytes), time stamp format (1 byte: 1=MPEG frames, 2=milliseconds), content type (1 byte), descriptor string, followed by text-time pairs.
4. **Execution Priority**:
   1. Cache in app data (`filesDir/lyrics/<track_id>.lrc`)
   2. Local `<song>.lrc` companion file in same directory
   3. Embedded `USLT` / `SYLT` ID3 tag
   4. Online LRCLIB lookup (only when network is available)

---

## 2. R4: Process Death & Network Hardening

### 2.1 State Persistence Across Process Death

#### Current State
- `GobeatsDatabaseHelper.kt` defines `playback_state` in `onCreate()` (lines 198–209):
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
- **Current Defect**:
  * There are **zero** database helper methods to insert, update, or read `playback_state`.
  * `PlaybackConnection` does not persist queue or playback position.
  * When the process is terminated in the background by Android OS memory management, relaunching GoBeats results in an empty queue, null current track, and zero playback position.
  * In `onUpgrade()`, if a user upgrades from database version 1, `playback_state` table creation was not included in `onUpgrade` (only `is_video` columns were altered).

#### Architectural Solution:
1. **Database Layer (`GobeatsDatabaseHelper.kt`)**:
   - Ensure table exists via `db.execSQL("CREATE TABLE IF NOT EXISTS playback_state ...")` in `onConfigure` or version bump to version 3 in `onUpgrade`.
   - Add DAO methods:
     * `suspend fun savePlaybackState(currentTrackId: String?, positionMs: Long, isPlaying: Boolean, queueTrackIds: List<String>)`
     * `suspend fun getSavedPlaybackState(): SavedPlaybackState?`
     * `suspend fun getTracksByIds(trackIds: List<String>): List<Track>` (preserving the exact ordering of `trackIds`).
     * `suspend fun saveVideoResumePosition(trackId: String, positionMs: Long)`
     * `suspend fun getVideoResumePosition(trackId: String): Long?`
2. **Persistence Triggers in `PlaybackConnection.kt`**:
   - **Queue Changes**: When `playTrack(track, queue)` or `addToQueue` is called, persist track ID and queue track IDs.
   - **Position Updates**: Debounce position saves during playback:
     * Save when paused (`onIsPlayingChanged(isPlaying = false)`).
     * Save on track change (`onMediaItemTransition`).
     * Save every 3–5 seconds during active playback.
3. **Restoration Flow on App Launch / Process Recreation**:
   - In `PlaybackConnection.initController()` (after `controllerFuture` completes):
     * Query `dbHelper.getSavedPlaybackState()`.
     * If saved state exists and `queueTrackIds.isNotEmpty()`:
       1. Load tracks via `dbHelper.getTracksByIds(queueTrackIds)`.
       2. Find index of `currentTrackId`.
       3. Build `MediaItem`s and submit to player: `player.setMediaItems(mediaItems, savedIndex, savedPositionMs)`.
       4. Ensure player remains paused (`player.pause()`).
       5. Update `_playbackState` with restored queue, track, position, and duration.
   - **Result**: The user sees the MiniPlayer, Now Playing sheet, and Queue restored to the exact millisecond, ready for instant one-tap resume.

---

### 2.2 Network Hardening: Wi-Fi PC Connect Transfers

#### Current State
- `GobeatsSyncServer.kt` (lines 48–118) runs a custom `ServerSocket` on port 42100 inside a background coroutine `scope.launch(Dispatchers.IO)`.
- It is triggered directly from `DevicesScreen.kt`:
  ```kotlin
  syncServer?.startServer()
  ```
- **Critical Vulnerability**:
  * Android Doze mode and App Standby restrict background network sockets when the app is in the background or screen is turned off.
  * No `WifiLock` is acquired.
  * No `WakeLock` is acquired.
  * No Foreground Service is running.
  * Large multi-album transfers (e.g. 100+ FLAC files over several gigabytes) get terminated mid-transfer when the screen turns off or user switches to another app.

#### Technical Architecture for Hardened Wi-Fi Transfers
1. **Dedicated Foreground Service (`GobeatsSyncService`)**:
   - A dedicated `Service` managing the `GobeatsSyncServer` lifecycle.
   - Declared in `AndroidManifest.xml` with:
     ```xml
     <service
         android:name=".core.sync.GobeatsSyncService"
         android:exported="false"
         android:foregroundServiceType="dataSync" />
     ```
   - Required permission in manifest:
     ```xml
     <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
     ```
2. **Lock Acquisition & Management**:
   - **`WifiManager.WifiLock`**:
     ```kotlin
     val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
     val wifiLock = wifiManager.createWifiLock(
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
             WifiManager.WIFI_MODE_FULL_LOW_LATENCY
         } else {
             WifiManager.WIFI_MODE_FULL_HIGH_PERF
         },
         "gobeats:pc_connect_wifilock"
     ).apply {
         setReferenceCounted(false)
         acquire()
     }
     ```
   - **`PowerManager.WakeLock`**:
     ```kotlin
     val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
     val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gobeats:pc_connect_wakelock").apply {
         acquire(2 * 60 * 60 * 1000L) // 2-hour timeout
     }
     ```
3. **Ongoing Notification**:
   - Channel: `CHANNEL_PC_CONNECT` ("Wi-Fi PC Connect Transfers").
   - Notification content:
     * Title: "Wi-Fi PC Connect Active"
     * Content: "Receiving music on http://192.168.X.X:42100"
     * Progress bar when a file transfer is active.
     * "Stop" action button to gracefully terminate the server and release locks.
4. **Lifecycle Hooks**:
   - Starting sync in `DevicesScreen`: starts `GobeatsSyncService`.
   - Stopping sync or server completion: stops foreground service, which cleanly releases `wifiLock`, `wakeLock`, and shuts down `serverSocket`.

---

## 3. Verification Environment & Test Pipeline

### 3.1 Environment Specification
- **Operating System**: Windows 11 (aarch64)
- **Gradle Version**: 9.1.0
- **Android Gradle Plugin (AGP)**: 9.0.1
- **Kotlin Version**: 2.3.20 (Compose compiler plugin: 2.3.20)
- **Java / JVM Version**: Microsoft JDK 17 (17.0.19+10-LTS)
- **Android SDK**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`

### 3.2 Verification Command Status

| Command | Action | Execution Result | Exit Code | Notes |
|---|---|---|---|---|
| `.\gradlew.bat compileDebugKotlin` | Compiles all Kotlin source code | **SUCCESS** (8s) | 0 | 0 errors. Compiler warnings on deprecated Android APIs (`Virtualizer`, `statusBarColor`). |
| `.\gradlew.bat testDebugUnitTest` | Executes local JVM unit test suite | **SUCCESS** (55s) | 0 | 24 actionable tasks executed. All 56+ unit tests in `GobeatsCoreTest.kt` passed cleanly. |
| `.\gradlew.bat assembleDebug` | Packages the debug APK | **SUCCESS** (9s) | 0 | 36 actionable tasks. Output APK generated in `app/build/outputs/apk/debug/app-debug.apk`. |

### 3.3 Test Suite Insights (`GobeatsCoreTest.kt`)
- 1,124 lines of existing unit tests testing:
  * Duration formatting
  * Data model properties and serialization
  * Playback UI state transitions
  * Equalizer presets (5-band integrity)
  * AutoEQ headphone profile catalog
  * ReplayGain calculation and dynamic range
  * ID3v2 tag writing (TIT2, TPE1, TALB, APIC)
  * M3U/M3U8 playlist parsing and importing
  * Sync server data structures
- **Extension Points for New Tests**:
  * Unit tests for `LrcParser` and offline companion `.lrc` resolution.
  * Unit tests for `USLT`/`SYLT` frame extraction.
  * Unit tests for `playback_state` Room/SQLite persistence and ordering.
  * Unit tests for folder tree hierarchy traversal and media filtering.

---

## 4. Key Implementation Files & Extension Points

| Feature | Primary Files | Key Classes / Functions | Extension Strategy |
|---|---|---|---|
| **Folder Browser** | `feature/library/LibraryScreen.kt`<br>`core/media/MediaScanner.kt`<br>`MainActivity.kt` | `LibraryCategoryTile`<br>`isSupportedMediaFile()`<br>`Destination.FolderSetup` | Replace `FolderSetup` navigation from Folders tile with native directory browser; implement folder queue loader. |
| **Gapless & Crossfade** | `core/media/PlaybackConnection.kt`<br>`core/media/GobeatsMediaSessionService.kt`<br>`data/repository/Repositories.kt` | `playTrack()`<br>`setupPlayerListener()`<br>`SettingsRepository` | Connect `crossfade_sec` preference to volume ramp / dual player fader; ensure ExoPlayer gapless buffering is active. |
| **Offline Synced Lyrics** | `core/lyrics/LyricsRepository.kt`<br>`core/lyrics/LrcParser.kt`<br>`core/media/Id3TagWriter.kt` | `getLyricsForTrack()`<br>`parse()` | Resolve `content://` MediaStore URI to physical file; detect `<song>.lrc`; implement embedded `USLT`/`SYLT` frame reader before calling LRCLIB. |
| **Process Death State** | `core/database/GobeatsDatabaseHelper.kt`<br>`core/media/PlaybackConnection.kt`<br>`GobeatsApplication.kt` | `playback_state` table<br>`initController()` | Implement DB read/write for `playback_state`; debounce position saves; restore queue, track, and position in `initController()`. |
| **Network Hardening** | `core/sync/GobeatsSyncServer.kt`<br>`feature/devices/DevicesScreen.kt`<br>`AndroidManifest.xml` | `startServer()`<br>`SyncServerState` | Wrap sync server in `GobeatsSyncService` with `FOREGROUND_SERVICE_DATA_SYNC`, `WifiLock`, `WakeLock`, and ongoing notification. |

---

## 5. Potential Pitfalls & Architectural Constraints

1. **Android 14+ Foreground Service Policy**:
   Target SDK is 36. Any foreground service started on API 34+ must declare a valid `foregroundServiceType` (e.g. `dataSync`) in `AndroidManifest.xml` and acquire `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />`. Starting a foreground service without this crashes immediately with `SecurityException`.
2. **MediaStore URI vs Direct File Access**:
   Starting with Android 10 (Scoped Storage), `Uri.parse(track.fileUri)` often returns a `content://` URI. Direct `File(uri.path)` fails for content URIs. Querying `MediaStore.Audio.Media.DATA` column or using `ContentResolver.openInputStream` is required for reliable file access.
3. **Database Version Migration**:
   If `DATABASE_VERSION` is increased in `GobeatsDatabaseHelper`, `onUpgrade` must handle migrations safely, and table creations must use `CREATE TABLE IF NOT EXISTS` so existing installations never throw `SQLiteException: table already exists`.
4. **Queue Order Preservation**:
   When restoring queue tracks by IDs from SQLite, SQL `WHERE id IN (...)` does **not** return rows in the original queue order. Tracks must be sorted according to their index in the restored JSON array.

---
*Report generated for Project Orchestrator and Council Review.*
