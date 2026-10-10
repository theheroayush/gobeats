# Survey Report: YouTube & MX Player Grade Video Engine (R1)

**Agent**: Explorer Survey 1 (Video Engine)  
**Target Milestone**: R1 — YouTube & MX Player Grade Video Engine  
**Date**: 2026-10-06  
**Status**: Comprehensive Investigation Complete  
**Integrity Mode**: Read-Only Survey  

---

## Executive Summary

The GoBeats video architecture is built upon **AndroidX Media3 (ExoPlayer 1.4.1)** and **Jetpack Compose (BOM 2026.03.01)**. Playback is managed by `GobeatsMediaSessionService` (a `MediaSessionService` with a 32-bit float audio sink) and coordinated via `PlaybackConnection` and `PlaybackRepository`. UI components include `FullscreenVideoPlayer` (cinema mode), `VideoPlayerSurface` (now-playing sheet embedded surface), `VideoGestureOverlay` (touch gesture recognizer), and `VideoSettingsBottomSheet`.

While basic video playback, aspect-ratio switching, and 2x speed preview exist, the player lacks critical MX Player and YouTube-grade capabilities required for R1. Most critically, an active bug in `PlaybackConnection.kt` causes a **complete black screen** whenever a user selects lower quality on any single-rendition local video.

This survey establishes the complete architectural baseline, isolates root causes down to line numbers, and defines the technical blueprint for the 5 sub-features of R1:
1. **Loudness Booster (200% gain)**: Bridging `LoudnessEnhancer` to the vertical volume swipe gesture and settings menu.
2. **External `.srt` / `.vtt` Subtitle Engine**: Sibling directory auto-detection and SAF storage picker integration.
3. **Persistent Video Resume Memory**: SQLite database table, position tracking, and an auto-resume prompt overlay.
4. **Single-Rendition Black-Screen Prevention**: Elimination of synthetic track constraints on local files while preserving adaptive ladders for HLS/DASH.
5. **In-App Draggable Floating Video Miniplayer**: A top-level draggable/snappable Compose overlay hosting `PlayerView` while navigating the library.

---

## 1. Architectural Inventory & File Map

| Component / File | Current Responsibility | Extension Points for R1 |
|---|---|---|
| `app/build.gradle.kts` | Version catalog bindings (`media3-exoplayer:1.4.1`, `compose-material3`, `documentfile:1.0.1`) | Room/SQLite, Media3 text/subtitle modules |
| `core/media/PlaybackConnection.kt` | MediaController wrapper, queue manager, track selector, position poller (250ms) | Subtitle configuration attachment, quality selector fix, video position polling & save callbacks |
| `core/media/GobeatsMediaSessionService.kt` | Foreground MediaSessionService, audio sink configuration, `activePlayer` static hook | LoudnessEnhancer audioSessionId binding, Player commands |
| `core/audio/EqualizerManager.kt` | Manages `Equalizer`, `BassBoost`, `Virtualizer`, `LoudnessEnhancer` (clamped to 800 mB) | 200% boost headroom (up to 2000 mB), dedicated video booster API |
| `core/database/GobeatsDatabaseHelper.kt` | SQLiteOpenHelper (v2), tables: `tracks`, `playback_state`, `favorites`, etc. | Migration to v3: `video_playback_positions` table for persistent resume |
| `core/model/Models.kt` | Data models: `Track`, `PlaybackUiState`, `VideoQualityOption`, `VideoAspectMode` | `videoLoudnessBoostPercent`, `externalSubtitles`, `resumePromptState` |
| `feature/nowplaying/FullscreenVideoPlayer.kt` | Immersive cinema landscape player, insets controller, top/bottom HUD | Subtitle picker trigger, loudness booster indicators |
| `feature/nowplaying/VideoPlayerSurface.kt` | Portrait embedded video surface inside `NowPlayingBottomSheet` | Player detachment/reattachment, gesture integration |
| `feature/nowplaying/VideoGestureOverlay.kt` | Detects double-tap seek, 2x long-press, brightness & volume vertical drag | Right-edge volume drag extending to 200% with boost HUD |
| `feature/nowplaying/VideoSettingsBottomSheet.kt` | Bottom sheet for quality, speed, audio tracks, subtitles, aspect ratio | Load subtitle picker, volume boost slider/toggle, quality option disabling |
| `MainActivity.kt` | Root navigation, system PiP handler, `MiniPlayerBar` container | In-app draggable floating video miniplayer container |

---

## 2. Deep-Dive Investigation by Requirement

### R1.1: Software Loudness Booster (up to 200% Gain via `LoudnessEnhancer`)

#### Current State & Root Cause
- In `VideoGestureOverlay.kt` (lines 309–317):
  ```kotlin
  val dragDelta = -dragAmount.y / (size.height * 0.75f)
  ...
  val newVol = (volumeLevel + dragDelta).coerceIn(0f, 1f)
  volumeLevel = newVol
  val index = (newVol * max).toInt()
  am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
  ```
  The gesture hard-clamps volume at `1.0f` (100% device stream volume).
- In `EqualizerManager.kt` (lines 150–153, 333–340):
  ```kotlin
  loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
      enabled = _state.value.isLoudnessEnabled
      setTargetGain(_state.value.loudnessGainMb)
  }
  ...
  fun setLoudnessGain(gainMb: Int) {
      val clamped = gainMb.coerceIn(0, 800)
      ...
  }
  ```
  `LoudnessEnhancer` is restricted to ReplayGain headroom (+800 mB = +8.0 dB max). It is not exposed to the video player or connected to touch gestures.

#### Technical Blueprint for Implementation
1. **Mathematical Model & Gain Mapping**:
   - Volume range: `0.0f .. 2.0f` (0% to 200%).
   - `0.0f .. 1.0f` (0% – 100%): Controls Android `AudioManager.STREAM_MUSIC` hardware stream volume (`am.setStreamVolume`). LoudnessEnhancer boost is set to `0 mB`.
   - `1.01f .. 2.0f` (101% – 200%): Hardware stream volume stays locked at maximum (`maxStreamVolume`). The excess fraction `(volume - 1.0f)` maps linearly or logarithmically to `LoudnessEnhancer.setTargetGain(gainMb)`:
     $$\text{gainMb} = (\text{volume} - 1.0f) \times 1200 \text{ mB} \quad (\text{up to } +12.0\text{ dB})$$
     (or up to $1500\text{ mB}$ / $+15\text{ dB}$).
   - Android's `LoudnessEnhancer` internally employs a dynamic compressor/limiter that boosts dialogue intelligibility without hard digital clipping or crashing the OpenSL/AAudio HAL.
2. **AudioSession Hook**:
   - In `EqualizerManager.kt`, add dedicated methods:
     ```kotlin
     fun setVideoLoudnessBoostPercent(percent: Int) // 100 to 200
     ```
   - Bind `EqualizerManager` through `PlaybackConnection` or `PlaybackRepository` so `VideoGestureOverlay` can directly set the boost.
3. **UX / Visual Polish**:
   - In `VideoGestureOverlay.kt`:
     - Volume HUD bar extends or changes color from `GobeatsAmber` (0–100%) to a distinct Amber-Red gradient (`#FF5722` to `#FF9800`) for 101%–200%.
     - Display readout: `"100%"` $\to$ `"150% [BOOST +6dB]"` $\to$ `"200% [MAX BOOST]"`.
     - Haptic feedback tick (`HapticFeedbackConstants.CLOCK_TICK` or `CONTEXT_CLICK`) triggered when crossing the 100% threshold.
   - In `VideoSettingsBottomSheet.kt`: Add a "Volume Booster" slider / preset chips (100%, 125%, 150%, 175%, 200%).

---

### R1.2: External `.srt` / `.vtt` Subtitle Engine

#### Current State
- `PlaybackConnection.kt` (lines 330–349, `buildMediaItem`): Builds `MediaItem` with only URI and metadata. It passes **zero** `MediaItem.SubtitleConfiguration` objects.
- Subtitle tracks are only discovered if embedded in the container (e.g. MKV embedded ASS/SRT tracks) in `onTracksChanged`.
- No scanning occurs for sibling files in the local filesystem.
- `VideoSettingsBottomSheet.kt` provides only an "Off" option and whatever embedded tracks ExoPlayer detected, with no action to load custom subtitle files.

#### Technical Blueprint for Implementation
1. **Sibling Subtitle Auto-Detection**:
   - When playing a local video (e.g. `file:///storage/emulated/0/Movies/film.mp4`):
     - Identify parent directory `parentDir = File(filePath).parentFile`.
     - Extract base name: `film` from `film.mp4`.
     - Query sibling files matching patterns:
       - Exact match: `film.srt`, `film.vtt`
       - Language tagged: `film.en.srt`, `film.eng.srt`, `film.hi.srt`, `film.es.vtt`, etc.
       - Generic defaults: `subtitles.srt`, `default.srt`.
   - For Android 10+ scoped storage / `content://` URIs:
     - Query `MediaStore.Files` in the same `RELATIVE_PATH` / `BUCKET_ID` for display names starting with the video base name and ending with `.srt` or `.vtt`.
2. **SAF Storage Subtitle Picker**:
   - Register an `ActivityResultLauncher` with `ActivityResultContracts.OpenDocument()`:
     - MIME types: `arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")`.
   - Take persistable URI read permission:
     `context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`.
3. **Media3 Subtitle Configuration Attachment**:
   ```kotlin
   val mimeType = when {
       subUriString.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
       else -> MimeTypes.APPLICATION_SUBRIP // "application/x-subrip"
   }
   val subConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
       .setMimeType(mimeType)
       .setLanguage(langCode) // e.g. "en"
       .setLabel(displayName) // e.g. "English (External .srt)"
       .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
       .build()

   val updatedMediaItem = currentMediaItem.buildUpon()
       .setSubtitleConfigurations(listOf(subConfig) + existingConfigs)
       .build()
   ```
4. **Dynamic Track Replacement**:
   - When user picks a subtitle while video is actively playing:
     - Record `currentPosition = player.currentPosition` and `playWhenReady = player.playWhenReady`.
     - Replace current media item via `player.replaceMediaItem(currentIndex, updatedMediaItem)`.
     - Select the newly added subtitle track via `TrackSelectionParameters`.
5. **Subtitle Styling in `PlayerView`**:
   - Configure `playerView.subtitleView`:
     - Set font size (e.g. 18.sp), edge type (`CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW` or `OUTLINE`), text color white with dark outline, ensuring readability on light scenes.

---

### R1.3: Persistent Video Resume Memory & Auto-Resume Prompt

#### Current State
- `GobeatsDatabaseHelper.kt` only has a singleton `playback_state` table (`CHECK (id = 1)`) saving the global last played track and position.
- There is no table or column for tracking individual video playback timestamps across multiple videos.
- If a user watches 45 minutes of a 2-hour video, closes the app, or plays another track, the previous video's progress is lost.

#### Technical Blueprint for Implementation
1. **Database Schema (Migration from v2 to v3)**:
   - In `GobeatsDatabaseHelper.kt`:
     ```sql
     CREATE TABLE video_playback_positions (
         video_id TEXT PRIMARY KEY NOT NULL,
         position_ms INTEGER NOT NULL,
         duration_ms INTEGER NOT NULL,
         updated_at INTEGER NOT NULL,
         FOREIGN KEY (video_id) REFERENCES tracks(id) ON DELETE CASCADE
     );
     CREATE INDEX idx_video_positions_updated ON video_playback_positions(updated_at DESC);
     ```
   - In `onUpgrade(db, oldVersion, newVersion)`:
     ```kotlin
     if (oldVersion < 3) {
         db.execSQL("""
             CREATE TABLE IF NOT EXISTS video_playback_positions (
                 video_id TEXT PRIMARY KEY NOT NULL,
                 position_ms INTEGER NOT NULL,
                 duration_ms INTEGER NOT NULL,
                 updated_at INTEGER NOT NULL,
                 FOREIGN KEY (video_id) REFERENCES tracks(id) ON DELETE CASCADE
             );
         """.trimIndent())
         db.execSQL("CREATE INDEX IF NOT EXISTS idx_video_positions_updated ON video_playback_positions(updated_at DESC);")
     }
     ```
2. **Persistence Lifecycle**:
   - In `PlaybackConnection.kt`:
     - In `startPositionUpdates()`: every 4 seconds, if `state.isVideoMode` and `player.currentPosition > 5000L`, call `dbHelper.saveVideoPosition(trackId, position, duration)`.
     - On `pause()`, `onMediaItemTransition()`, and `release()`: synchronously persist current video position.
     - Completion threshold: If `position >= duration * 0.95` (user reached final credits), delete the entry or set `position = 0` so reopening starts from the beginning.
3. **Auto-Resume Prompt UX**:
   - When a video is launched:
     - Query `dbHelper.getVideoPosition(track.id)`.
     - If `savedPosition >= 5_000L` and `savedPosition < duration - 15_000L`:
       - Show a sleek non-intrusive bottom banner / overlay on top of `FullscreenVideoPlayer` and `VideoPlayerSurface`:
         - Message: `"Resume from ${formatDuration(savedPosition)}?"`
         - Action buttons: `[Resume]` and `[Start Over]`.
         - Auto-dismiss countdown (e.g. 5 seconds), defaulting to resuming or staying at 0 until user interaction based on preference.

---

### R1.4: Quality Selection & Black-Screen Prevention on Single-Rendition Local Files

#### Current State & Exact Bug Analysis
- In `PlaybackConnection.kt` (lines 214–236, `onTracksChanged`):
  ```kotlin
  val ladder = listOf(
      Triple("2160p", "4K (2160p)", 2160 to 3840),
      Triple("1440p", "1440p (QHD)", 1440 to 2560),
      Triple("1080p", "1080p (FHD)", 1080 to 1920),
      Triple("720p", "720p (HD)", 720 to 1280),
      Triple("480p", "480p (SD)", 480 to 854),
      Triple("360p", "360p (Data Saver)", 360 to 640)
  )

  for ((id, label, dims) in ladder) {
      if (nativeHeight <= 0 || dims.first <= nativeHeight || id == "1080p" || id == "720p" || id == "480p") {
          qualityList.add(...)
      }
  }
  ```
- And in `PlaybackConnection.kt` (lines 636–657, `selectVideoQuality`):
  ```kotlin
  fun selectVideoQuality(optionId: String) {
      controller?.let { player ->
          val paramsBuilder = player.trackSelectionParameters.buildUpon()
          when (optionId) {
              "auto" -> paramsBuilder.clearVideoSizeConstraints()
              "2160p" -> paramsBuilder.setMaxVideoSize(3840, 2160)
              "1440p" -> paramsBuilder.setMaxVideoSize(2560, 1440)
              "1080p" -> paramsBuilder.setMaxVideoSize(1920, 1080)
              "720p" -> paramsBuilder.setMaxVideoSize(1280, 720)
              "480p" -> paramsBuilder.setMaxVideoSize(854, 480)
              "360p" -> paramsBuilder.setMaxVideoSize(640, 360)
          }
          player.trackSelectionParameters = paramsBuilder.build()
  ```
- **The Black-Screen Failure Mechanism**:
  1. A user plays a standard 1080p MP4 file from storage.
  2. The MP4 has only **one** video stream (1920x1080).
  3. The settings menu shows 1080p, 720p, 480p, 360p.
  4. The user selects "720p".
  5. `paramsBuilder.setMaxVideoSize(1280, 720)` sets a hard ceiling on ExoPlayer's `DefaultTrackSelector`.
  6. The 1080p stream exceeds 720p. Since there is no 720p stream in the file, ExoPlayer evaluates that **zero** video tracks meet the criteria.
  7. ExoPlayer **disables the video renderer**.
  8. Audio continues playing, but the video surface goes **completely black**.

#### Technical Blueprint for Implementation
1. **Stream Type & Rendition Introspection**:
   - Inspect `player.currentTracks`:
     - Filter `tracks.groups` where `group.type == C.TRACK_TYPE_VIDEO`.
     - Count total available video formats across all groups: `totalVideoFormats = videoGroups.sumOf { it.length }`.
     - Inspect track URI scheme: `isAdaptiveStream = uri.endsWith(".m3u8", ignoreCase = true) || uri.endsWith(".mpd", ignoreCase = true) || uri.contains("hls") || uri.contains("dash")`.
2. **Behavior on Single-Rendition Local Files (`totalVideoFormats <= 1` and `!isAdaptiveStream`)**:
   - `qualityList` should NOT contain downscaling options that cannot be fulfilled.
   - Present a single, clear option:
     - Label: `"Original (${nativeHeight}p - Local File)"`
     - Resolution: `"${nativeWidth}x${nativeHeight}"`
     - Badge: `"NATIVE"`
   - In `VideoSettingsBottomSheet.kt`:
     - Show informational banner or subtitle: `"Single stream — downscaling requires cloud transcoding"`.
     - Do not apply `setMaxVideoSize` constraints that would filter out the only rendition.
3. **Behavior on Adaptive Streams (HLS / DASH, `totalVideoFormats > 1`)**:
   - Populate `qualityList` dynamically using the **actual** formats extracted from the manifest.
   - Allow user selection using `TrackSelectionOverride` on the specific `TrackGroup` and track index, OR `setMaxVideoSize` matching exact manifest renditions.

---

### R1.5: In-App Draggable/Floating Video Miniplayer Overlay

#### Current State
- In `MainActivity.kt` (lines 569–584):
  - When `showNowPlayingSheet == false`, only the audio `MiniPlayerBar` is displayed at the bottom.
  - Video frames are completely hidden. If user browses Library or Playlists, they only hear audio.
- While Android OS system Picture-in-Picture (`enterPictureInPictureMode`) is implemented in `onUserLeaveHint()`, there is **no in-app floating overlay** within Compose.

#### Technical Blueprint for Implementation
1. **Architectural Placement in Compose Tree**:
   - Place `FloatingVideoMiniplayer` inside `MainActivity.kt`'s root `Box(modifier = Modifier.fillMaxSize())`:
     - Positioned **above** `Scaffold` (above bottom navigation and content).
     - Positioned **below** modals (`TagEditorBottomSheet`, `AddToPlaylistBottomSheet`, `FullscreenVideoPlayer`).
   - Visibility condition:
     ```kotlin
     val isFloatingMiniplayerVisible = playbackState.isVideoMode &&
         playbackState.currentTrack?.isVideo == true &&
         !showNowPlayingSheet &&
         !playbackState.isFullscreenCinema &&
         !isLandscape
     ```
2. **Draggable Gestures & Physics**:
   - Use `pointerInput(Unit)` with `detectDragGestures`.
   - Maintain `offsetX` and `offsetY` state using `IntOffset`.
   - Clamp within screen bounds (subtracting status bar, navigation bar, and miniplayer dimensions).
   - **Magnetic Edge Snapping**:
     - On drag release (`onDragEnd`), animate smoothly (`animateIntOffsetAsState`) to snap to the nearest horizontal edge (left margin: 16.dp, right margin: 16.dp) and standard vertical anchor (top: 80.dp or bottom: 120.dp above nav bar).
3. **Player Surface Hand-off**:
   - `PlayerView` attachment:
     - `FloatingVideoMiniplayer` creates and holds a `PlayerView(context)` with `useController = false`.
     - When `NowPlayingBottomSheet` opens, `FloatingVideoMiniplayer` is hidden and disposes its `playerView.player`, transferring the surface to `VideoPlayerSurface` without leaking surfaces or causing black-frame glitches.
4. **Miniplayer Controls & Sizing**:
   - Size: 192.dp width, 108.dp height (16:9 aspect ratio) with 14.dp rounded corners, subtle amber border (`GobeatsBorderSubtle`), elevation shadow.
   - Overlay on tap:
     - Play/Pause icon button.
     - Close button (pauses or switches video mode to audio-only).
     - Tap body: expands `showNowPlayingSheet = true`.
   - Suppress bottom audio `MiniPlayerBar` when `FloatingVideoMiniplayer` is active to eliminate redundant UI clutter.

---

## 3. Data Flow & State Transitions

```
[ Local MP4 / Sibling Subtitle / HLS Stream ]
                     │
                     ▼
       ┌───────────────────────────┐
       │   MediaScanner / SAF      │
       │   MetadataExtractor       │
       └─────────────┬─────────────┘
                     │ (Track + Subtitle URIs)
                     ▼
       ┌───────────────────────────┐
       │     GobeatsDatabase       │
       │ video_playback_positions  │ ◄─────── Periodic Position Sync (every 4s)
       └─────────────┬─────────────┘
                     │
                     ▼
       ┌───────────────────────────┐
       │    PlaybackConnection     │
       │   ExoPlayer / Media3      │ ◄─────── LoudnessEnhancer (100% - 200%)
       │  Single-Rendition Guard   │
       └─────────────┬─────────────┘
                     │
       ┌─────────────┴─────────────────────────┐
       │                                       │
       ▼                                       ▼
┌───────────────────────────┐   ┌───────────────────────────┐
│ NowPlaying / Fullscreen   │   │  Floating Video Miniplayer│
│ VideoPlayerSurface        │   │  Draggable In-App Window  │
│ Subtitle View & HUD       │   │  Live Frames + Edge Snap  │
└───────────────────────────┘   └───────────────────────────┘
```

---

## 4. Potential Pitfalls & Edge Cases

1. **Audio HAL Crash on Excessive Loudness Gain**:
   - *Risk*: Passing extreme millibel values (> 3000 mB) to `LoudnessEnhancer` can cause `AudioEffect.ERROR_BAD_VALUE` or audio HAL crashes on some vendor ROMs.
   - *Mitigation*: Strictly clamp `gainMb` between 0 and 1500 mB (+15 dB), wrap effect mutations in `try-catch`, and gracefully fallback without interrupting playback.
2. **Surface Re-allocation Glitch**:
   - *Risk*: Switching between `FloatingVideoMiniplayer`, `VideoPlayerSurface`, and `FullscreenVideoPlayer` can cause a brief black frame if two `PlayerView` instances attempt to bind to `ExoPlayer` simultaneously.
   - *Mitigation*: Ensure `playerView.player = null` executes synchronously in `onDispose` before the new surface assigns `playerView.player = player`.
3. **SAF Subtitle File Permissions Across Restarts**:
   - *Risk*: URIs obtained from `ActivityResultContracts.OpenDocument` lose access rights after app process recreation if permission is not persisted.
   - *Mitigation*: Always invoke `context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)` immediately upon receipt.
4. **Database Migration Safety**:
   - *Risk*: Altering database version without handling existing user data can cause `SQLiteException` on startup.
   - *Mitigation*: Use `CREATE TABLE IF NOT EXISTS` in `onUpgrade` for `video_playback_positions`, incrementing `DATABASE_VERSION` from 2 to 3 cleanly.

---

## 5. Verification Strategy & Acceptance Map

| Requirement | Target Verification Method | Expected Result |
|---|---|---|
| **R1.1: Loudness Booster** | Unit test gain calculation & clamp limits; manual gesture test beyond 100% | Volume reaches 200%, LoudnessEnhancer targetGain set up to +12dB, HUD displays boost badge |
| **R1.2: External Subtitles** | Unit test subtitle MIME resolver & parser; verify `MediaItem.subtitleConfigurations` | Adjacent `.srt` automatically loaded; picked `.vtt` renders subtitles on PlayerView |
| **R1.3: Video Resume** | Unit test `GobeatsDatabaseHelper` insert/query for `video_playback_positions` | Reopened video displays resume prompt with last timestamp; resumes on tap |
| **R1.4: Black Screen Prevention** | Unit test `PlaybackConnection` quality ladder for single-rendition files | Quality option displays "Original"; track selector never applies invalid max size constraint |
| **R1.5: Floating Miniplayer** | Compose preview & unit test state transitions for floating miniplayer | Video continues playing in floating draggable card when navigating between tabs |
| **Compilation** | `.\gradlew.bat compileDebugKotlin` | Success with 0 warnings/errors |
| **Unit Test Suite** | `.\gradlew.bat testDebugUnitTest` | 100% green pass rate |

---
*Report delivered to Orchestrator for Phase Planning.*
